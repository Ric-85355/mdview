/*
 * SftpUploader.kt — created 2026-09-01, version 0.1.0.
 * Purpose: perform confined SFTP mutations in the configured repository root.
 * Algorithm: connect for one operation with password auth and TOFU host-key
 * verification, validate paths, mutate one object, then close channel/session.
 */

package org.mdview.app.repository

import android.content.Context
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.UserInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mdview.app.settings.RepositoryCredentials
import org.mdview.app.settings.RepositorySettings

sealed interface UploadResult {
    data object Completed : UploadResult
    data object AlreadyExists : UploadResult
}

class SftpUploader(
    context: Context,
    private val source: LocalUploadSource,
) {
    private val knownHosts = File(context.filesDir, "sftp_known_hosts")

    /** Uploads one SAF-backed Markdown file, optionally replacing its remote peer. */
    suspend fun upload(
        settings: RepositorySettings,
        credentials: RepositoryCredentials,
        repositoryDirectory: String,
        selection: UploadSelection,
        overwrite: Boolean,
    ): UploadResult = withChannel(settings, credentials) { channel ->
        val destination = UploadPaths.destination(
            settings.sftpRoot,
            repositoryDirectory,
            selection.fileName,
        )
        if (!overwrite && remoteExists(channel, destination)) return@withChannel UploadResult.AlreadyExists
        source.open(selection).use { stream ->
            channel.put(stream, destination, ChannelSftp.OVERWRITE)
        }
        UploadResult.Completed
    }

    /** Creates one validated child directory without replacing an existing object. */
    suspend fun createFolder(
        settings: RepositorySettings,
        credentials: RepositoryCredentials,
        repositoryDirectory: String,
        folderName: String,
    ): RepositoryMutationResult {
        val destination = RepositoryMutationPaths.newFolderPath(
            settings.sftpRoot,
            repositoryDirectory,
            folderName,
        )
        return withChannel(settings, credentials) { channel ->
            RepositoryMutationRules.collisionResult(remoteExists(channel, destination))?.let {
                return@withChannel it
            }
            channel.mkdir(destination)
            RepositoryMutationResult.Completed
        }
    }

    /** Renames one Markdown file inside its current repository directory. */
    suspend fun renameDocument(
        settings: RepositorySettings,
        credentials: RepositoryCredentials,
        repositoryDirectory: String,
        oldName: String,
        newName: String,
    ): RepositoryMutationResult = rename(
        settings,
        credentials,
        repositoryDirectory,
        RepositoryMutationPaths.safeDocumentName(oldName),
        RepositoryMutationPaths.safeDocumentName(newName),
    )

    /** Renames one directory inside its current parent directory. */
    suspend fun renameFolder(
        settings: RepositorySettings,
        credentials: RepositoryCredentials,
        repositoryDirectory: String,
        oldName: String,
        newName: String,
    ): RepositoryMutationResult = rename(
        settings,
        credentials,
        repositoryDirectory,
        RepositoryMutationPaths.safeFolderName(oldName),
        RepositoryMutationPaths.safeFolderName(newName),
    )

    /** Deletes exactly one validated Markdown file. */
    suspend fun deleteDocument(
        settings: RepositorySettings,
        credentials: RepositoryCredentials,
        repositoryDirectory: String,
        fileName: String,
    ): RepositoryMutationResult {
        val path = RepositoryMutationPaths.objectPath(
            settings.sftpRoot,
            repositoryDirectory,
            RepositoryMutationPaths.safeDocumentName(fileName),
        )
        return withChannel(settings, credentials) { channel ->
            channel.rm(path)
            RepositoryMutationResult.Completed
        }
    }

    /** Deletes one directory only after the server confirms that it is empty. */
    suspend fun deleteEmptyFolder(
        settings: RepositorySettings,
        credentials: RepositoryCredentials,
        repositoryDirectory: String,
        folderName: String,
    ): RepositoryMutationResult {
        val path = RepositoryMutationPaths.objectPath(
            settings.sftpRoot,
            repositoryDirectory,
            RepositoryMutationPaths.safeFolderName(folderName),
        )
        return withChannel(settings, credentials) { channel ->
            val hasChildren = channel.ls(path).asSequence()
                .map { it as ChannelSftp.LsEntry }
                .any { it.filename != "." && it.filename != ".." }
            RepositoryMutationRules.folderDeleteResult(hasChildren).also { result ->
                if (result == RepositoryMutationResult.Completed) channel.rmdir(path)
            }
        }
    }

    private suspend fun rename(
        settings: RepositorySettings,
        credentials: RepositoryCredentials,
        repositoryDirectory: String,
        oldName: String,
        newName: String,
    ): RepositoryMutationResult {
        val sourcePath = RepositoryMutationPaths.objectPath(
            settings.sftpRoot,
            repositoryDirectory,
            oldName,
        )
        val destinationPath = RepositoryMutationPaths.objectPath(
            settings.sftpRoot,
            repositoryDirectory,
            newName,
        )
        if (sourcePath == destinationPath) return RepositoryMutationResult.Completed
        return withChannel(settings, credentials) { channel ->
            RepositoryMutationRules.collisionResult(remoteExists(channel, destinationPath))?.let {
                return@withChannel it
            }
            channel.rename(sourcePath, destinationPath)
            RepositoryMutationResult.Completed
        }
    }

    private suspend fun <T> withChannel(
        settings: RepositorySettings,
        credentials: RepositoryCredentials,
        action: (ChannelSftp) -> T,
    ): T = withContext(Dispatchers.IO) {
        require(settings.sftpEnabled) { "SFTP operations are disabled" }
        require(settings.sftpHost.isNotBlank()) { "SFTP host is required" }
        require(settings.sftpUser.isNotBlank()) { "SFTP user is required" }
        require(credentials.sftpPassword.isNotEmpty()) { "SFTP password is required" }
        knownHosts.parentFile?.mkdirs()
        if (!knownHosts.exists()) knownHosts.createNewFile()
        val jsch = JSch().apply { setKnownHosts(knownHosts.absolutePath) }
        val session = jsch.getSession(settings.sftpUser, settings.sftpHost, settings.sftpPort)
        var channel: ChannelSftp? = null
        try {
            session.setPassword(credentials.sftpPassword.toByteArray(Charsets.UTF_8))
            session.setConfig("StrictHostKeyChecking", "ask")
            session.userInfo = FirstUseHostKeyConfirmation
            session.connect(CONNECT_TIMEOUT_MS)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(CONNECT_TIMEOUT_MS)
            action(channel)
        } finally {
            channel?.takeIf { it.isConnected }?.disconnect()
            if (session.isConnected) session.disconnect()
        }
    }

    private fun remoteExists(channel: ChannelSftp, path: String): Boolean = try {
        channel.lstat(path)
        true
    } catch (error: SftpException) {
        if (error.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) false else throw error
    }

    private object FirstUseHostKeyConfirmation : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = null
        override fun promptPassphrase(message: String?): Boolean = false
        override fun promptPassword(message: String?): Boolean = false
        override fun showMessage(message: String?) = Unit

        override fun promptYesNo(message: String?): Boolean =
            message?.contains("changed", ignoreCase = true) != true
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
    }
}
