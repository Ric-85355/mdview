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

enum class RepositoryOperation { Idle, Loading, Uploading, Refreshing }

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
                            uploadAlreadyCompleted = true,
                        )
                    }
                }
            }.onFailure {
                operation = RepositoryOperation.Idle
                message = userMessage("Upload failed", it)
            }
        }
    }

    private suspend fun refreshNow(
        operationWhileLoading: RepositoryOperation,
        successMessage: String?,
        uploadAlreadyCompleted: Boolean = false,
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
                message = if (uploadAlreadyCompleted) {
                    UploadOutcomeText.afterRefresh(false)
                } else {
                    userMessage("Repository refresh failed", it)
                }
            }
        operation = RepositoryOperation.Idle
    }

    private fun userMessage(prefix: String, failure: Throwable): String {
        val detail = failure.message.orEmpty()
        return if (detail.isBlank()) prefix else "$prefix: $detail"
    }
}
