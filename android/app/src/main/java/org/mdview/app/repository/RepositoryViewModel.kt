/*
 * RepositoryViewModel.kt — created 2026-09-01, version 0.1.0.
 * Purpose: retain Repository, Settings, Reader routing, refresh, and upload state.
 * Algorithm: serialize network/SFTP operations in viewModelScope, preserve the
 * relative current folder, and expose one-shot picker/overwrite decisions to UI.
 */

package org.mdview.app.repository

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mdview.app.settings.RepositoryCredentials
import org.mdview.app.settings.RepositorySettings
import org.mdview.app.settings.RepositorySettingsStore

enum class AppScreen { Repository, Reader, Settings }

enum class RepositoryOperation(val progressText: String?) {
    Idle(null),
    Loading("Loading…"),
    Uploading("Uploading…"),
    CreatingFolder("Creating folder…"),
    Renaming("Renaming…"),
    Deleting("Deleting…"),
    Refreshing("Refreshing…"),
}

class RepositoryViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val settingsStore = RepositorySettingsStore(application)
    private val http = RepositoryHttpClient()
    private val uploadSource = LocalUploadSource(application.contentResolver)
    private val uploader = SftpUploader(application, uploadSource)

    var settings by mutableStateOf(settingsStore.load())
        private set
    var repository by mutableStateOf<MarkdownRepository?>(null)
        private set
    var currentDirectoryPath by mutableStateOf(savedStateHandle["repositoryPath"] ?: "")
        private set
    var screen by mutableStateOf(
        if (settings.repositoryUrl.isBlank()) AppScreen.Settings else AppScreen.Repository,
    )
        private set
    var operation by mutableStateOf(RepositoryOperation.Idle)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var overwriteSelection by mutableStateOf<UploadSelection?>(null)
        private set

    val currentDirectory: RepositoryDirectory?
        get() = repository?.nearestDirectory(currentDirectoryPath)

    val canUpload: Boolean
        get() = settings.sftpEnabled && operation == RepositoryOperation.Idle

    val canManage: Boolean
        get() = settings.sftpEnabled && operation == RepositoryOperation.Idle

    val canCreateFolder: Boolean
        get() {
            val depth = if (currentDirectoryPath.isEmpty()) 0 else currentDirectoryPath.count { it == '/' } + 1
            return canManage && depth < 2
        }

    init {
        if (settings.repositoryUrl.isNotBlank()) refresh()
    }

    fun showRepository() {
        screen = AppScreen.Repository
    }

    fun showReader() {
        screen = AppScreen.Reader
    }

    fun showSettings() {
        screen = AppScreen.Settings
    }

    fun openDirectory(path: String) {
        val directory = repository?.findDirectory(path) ?: return
        currentDirectoryPath = directory.path
        savedStateHandle["repositoryPath"] = directory.path
        message = null
    }

    fun goToParentDirectory() {
        if (currentDirectoryPath.isEmpty()) return
        openDirectory(currentDirectoryPath.substringBeforeLast('/', ""))
    }

    fun refresh() {
        if (operation != RepositoryOperation.Idle || settings.repositoryUrl.isBlank()) return
        viewModelScope.launch { refreshNow(RepositoryOperation.Loading, null) }
    }

    fun openDocument(
        document: RepositoryDocument,
        onLoaded: (id: String, title: String, source: String) -> Unit,
    ) {
        if (operation != RepositoryOperation.Idle) return
        operation = RepositoryOperation.Loading
        message = null
        viewModelScope.launch {
            runCatching {
                http.loadDocument(settings, settingsStore.credentials(), document)
            }.onSuccess { source ->
                val id = RepositoryPaths.documentUrl(settings.repositoryUrl, document.path)
                onLoaded(id, document.name, source)
                screen = AppScreen.Reader
            }.onFailure { message = userMessage("Could not open document", it) }
            operation = RepositoryOperation.Idle
        }
    }

    fun saveSettings(
        updated: RepositorySettings,
        httpPassword: String?,
        sftpPassword: String?,
    ): Boolean = runCatching {
        require(updated.name.isNotBlank()) { "Repository name is required" }
        RepositoryPaths.normalizeBaseUrl(updated.repositoryUrl)
        require(updated.sftpPort in 1..65535) { "SFTP port must be between 1 and 65535" }
        if (updated.sftpEnabled) {
            require(updated.sftpHost.isNotBlank()) { "SFTP host is required" }
            require(updated.sftpUser.isNotBlank()) { "SFTP user is required" }
            require(updated.sftpRoot.isNotBlank()) { "SFTP root is required" }
            require(sftpPassword?.isNotEmpty() == true || settingsStore.credentials().sftpPassword.isNotEmpty()) {
                "SFTP password is required"
            }
        }
        settingsStore.save(updated, httpPassword, sftpPassword)
        settings = updated.copy(repositoryUrl = RepositoryPaths.normalizeBaseUrl(updated.repositoryUrl))
        settingsStore.save(settings, null, null)
        repository = null
        currentDirectoryPath = ""
        savedStateHandle["repositoryPath"] = ""
        screen = AppScreen.Repository
        message = "Settings saved"
        refresh()
    }.onFailure { message = it.message ?: "Could not save settings" }.isSuccess

    fun clearPasswords() {
        settingsStore.save(settings, "", "")
        message = "Stored passwords cleared"
    }

    fun onUploadUri(uri: Uri?) {
        if (uri == null || operation != RepositoryOperation.Idle) return
        runCatching { uploadSource.selection(uri) }
            .onSuccess { upload(it, overwrite = false) }
            .onFailure { message = userMessage("Could not use selected file", it) }
    }

    fun confirmOverwrite() {
        val selection = overwriteSelection ?: return
        overwriteSelection = null
        upload(selection, overwrite = true)
    }

    fun cancelOverwrite() {
        overwriteSelection = null
    }

    private fun upload(selection: UploadSelection, overwrite: Boolean) {
        if (operation != RepositoryOperation.Idle) return
        val destinationDirectory = currentDirectoryPath
        operation = RepositoryOperation.Uploading
        message = "Uploading…"
        viewModelScope.launch {
            runCatching {
                uploader.upload(
                    settings = settings,
                    credentials = settingsStore.credentials(),
                    repositoryDirectory = destinationDirectory,
                    selection = selection,
                    overwrite = overwrite,
                )
            }.onSuccess { result ->
                when (result) {
                    UploadResult.AlreadyExists -> {
                        overwriteSelection = selection
                        operation = RepositoryOperation.Idle
                        message = null
                    }
                    UploadResult.Completed -> {
                        currentDirectoryPath = destinationDirectory
                        refreshNow(
                            operationWhileLoading = RepositoryOperation.Refreshing,
                            successMessage = UploadOutcomeText.afterRefresh(true),
                            refreshFailureMessage = UploadOutcomeText.afterRefresh(false),
                        )
                    }
                }
            }.onFailure {
                operation = RepositoryOperation.Idle
                message = userMessage("Upload failed", it)
            }
        }
    }

    fun createFolder(name: String) {
        mutateRepository(
            operationWhileRunning = RepositoryOperation.CreatingFolder,
            completedMessage = "Folder created",
            failurePrefix = "Could not create folder",
            alreadyExistsMessage = "Folder already exists",
        ) { credentials ->
            uploader.createFolder(settings, credentials, currentDirectoryPath, name)
        }
    }

    fun renameDocument(document: RepositoryDocument, newName: String) {
        val oldName = document.path.substringAfterLast('/')
        mutateRepository(
            operationWhileRunning = RepositoryOperation.Renaming,
            completedMessage = "File renamed",
            failurePrefix = "Could not rename file",
            alreadyExistsMessage = "File already exists",
        ) { credentials ->
            uploader.renameDocument(settings, credentials, currentDirectoryPath, oldName, newName)
        }
    }

    fun renameFolder(directory: RepositoryDirectory, newName: String) {
        mutateRepository(
            operationWhileRunning = RepositoryOperation.Renaming,
            completedMessage = "Folder renamed",
            failurePrefix = "Could not rename folder",
            alreadyExistsMessage = "Folder already exists",
        ) { credentials ->
            uploader.renameFolder(settings, credentials, currentDirectoryPath, directory.name, newName)
        }
    }

    fun deleteDocument(document: RepositoryDocument) {
        val fileName = document.path.substringAfterLast('/')
        mutateRepository(
            operationWhileRunning = RepositoryOperation.Deleting,
            completedMessage = "File deleted",
            failurePrefix = "Could not delete file",
            alreadyExistsMessage = "File already exists",
        ) { credentials ->
            uploader.deleteDocument(settings, credentials, currentDirectoryPath, fileName)
        }
    }

    fun deleteFolder(directory: RepositoryDirectory) {
        mutateRepository(
            operationWhileRunning = RepositoryOperation.Deleting,
            completedMessage = "Folder deleted",
            failurePrefix = "Could not delete folder",
            alreadyExistsMessage = "Folder already exists",
        ) { credentials ->
            uploader.deleteEmptyFolder(settings, credentials, currentDirectoryPath, directory.name)
        }
    }

    private fun mutateRepository(
        operationWhileRunning: RepositoryOperation,
        completedMessage: String,
        failurePrefix: String,
        alreadyExistsMessage: String,
        mutation: suspend (RepositoryCredentials) -> RepositoryMutationResult,
    ) {
        if (operation != RepositoryOperation.Idle) return
        operation = operationWhileRunning
        message = operationWhileRunning.progressText
        viewModelScope.launch {
            runCatching { mutation(settingsStore.credentials()) }
                .onSuccess { result ->
                    when (result) {
                        RepositoryMutationResult.AlreadyExists -> {
                            operation = RepositoryOperation.Idle
                            message = alreadyExistsMessage
                        }
                        RepositoryMutationResult.FolderNotEmpty -> {
                            operation = RepositoryOperation.Idle
                            message = "Folder is not empty"
                        }
                        RepositoryMutationResult.Completed -> refreshNow(
                            operationWhileLoading = RepositoryOperation.Refreshing,
                            successMessage = RepositoryMutationOutcomeText.afterRefresh(completedMessage, true),
                            refreshFailureMessage = RepositoryMutationOutcomeText.afterRefresh(completedMessage, false),
                        )
                    }
                }
                .onFailure {
                    operation = RepositoryOperation.Idle
                    message = userMessage(failurePrefix, it)
                }
        }
    }

    private suspend fun refreshNow(
        operationWhileLoading: RepositoryOperation,
        successMessage: String?,
        refreshFailureMessage: String? = null,
    ) {
        operation = operationWhileLoading
        message = if (operationWhileLoading == RepositoryOperation.Refreshing) "Refreshing…" else null
        val preservedPath = currentDirectoryPath
        runCatching { http.loadIndex(settings, settingsStore.credentials()) }
            .onSuccess { loaded ->
                repository = loaded
                currentDirectoryPath = loaded.nearestDirectory(preservedPath).path
                savedStateHandle["repositoryPath"] = currentDirectoryPath
                message = successMessage
            }
            .onFailure {
                message = refreshFailureMessage ?: userMessage("Repository refresh failed", it)
            }
        operation = RepositoryOperation.Idle
    }

    private fun userMessage(prefix: String, failure: Throwable): String {
        val detail = failure.message.orEmpty()
        return if (detail.isBlank()) prefix else "$prefix: $detail"
    }
}
