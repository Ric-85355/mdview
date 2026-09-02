/*
 * SftpUploader.kt — created 2026-09-01, version 0.1.0.
 * Purpose: upload one SAF Markdown stream to the current repository folder.
 * Algorithm: connect for one operation with password auth and TOFU host-key
 * verification, check remote existence, transfer, then close channel/session.
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

    suspend fun upload(
        settings: RepositorySettings,
        credentials: RepositoryCredentials,
        repositoryDirectory: String,
        selection: UploadSelection,
        overwrite: Boolean,
    ): UploadResult = withContext(Dispatchers.IO) {
        require(settings.sftpEnabled) { "SFTP upload is disabled" }
        require(settings.sftpHost.isNotBlank()) { "SFTP host is required" }
        require(settings.sftpUser.isNotBlank()) { "SFTP user is required" }
        require(credentials.sftpPassword.isNotEmpty()) { "SFTP password is required" }
        val destination = UploadPaths.destination(
            settings.sftpRoot,
            repositoryDirectory,
            selection.fileName,
        )
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
            if (!overwrite && remoteExists(channel, destination)) return@withContext UploadResult.AlreadyExists
            source.open(selection).use { stream ->
                channel.put(stream, destination, ChannelSftp.OVERWRITE)
            }
            UploadResult.Completed
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
