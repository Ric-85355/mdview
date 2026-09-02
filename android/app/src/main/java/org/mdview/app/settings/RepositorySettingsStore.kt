/*
 * RepositorySettingsStore.kt — created 2026-09-01, version 0.1.0.
 * Purpose: persist one Android repository configuration.
 * Algorithm: store ordinary fields in private preferences and delegate both
 * passwords to Android-Keystore-backed encrypted storage.
 */

package org.mdview.app.settings

import android.content.Context

class RepositorySettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("repository_settings", Context.MODE_PRIVATE)
    private val secrets = SecureSecretStore(context)

    fun load(): RepositorySettings = RepositorySettings(
        name = preferences.getString("name", "Repository").orEmpty(),
        repositoryUrl = preferences.getString("repository_url", "").orEmpty(),
        httpUser = preferences.getString("http_user", "").orEmpty(),
        sftpEnabled = preferences.getBoolean("sftp_enabled", false),
        sftpHost = preferences.getString("sftp_host", "").orEmpty(),
        sftpPort = preferences.getInt("sftp_port", 22),
        sftpUser = preferences.getString("sftp_user", "").orEmpty(),
        sftpRoot = preferences.getString("sftp_root", "").orEmpty(),
    )

    fun credentials(): RepositoryCredentials = RepositoryCredentials(
        httpPassword = secrets.get("http_password").orEmpty(),
        sftpPassword = secrets.get("sftp_password").orEmpty(),
    )

    fun save(
        settings: RepositorySettings,
        httpPassword: String?,
        sftpPassword: String?,
    ) {
        preferences.edit()
            .putString("name", settings.name)
            .putString("repository_url", settings.repositoryUrl)
            .putString("http_user", settings.httpUser)
            .putBoolean("sftp_enabled", settings.sftpEnabled)
            .putString("sftp_host", settings.sftpHost)
            .putInt("sftp_port", settings.sftpPort)
            .putString("sftp_user", settings.sftpUser)
            .putString("sftp_root", settings.sftpRoot)
            .apply()
        httpPassword?.let { secrets.put("http_password", it) }
        sftpPassword?.let { secrets.put("sftp_password", it) }
    }
}
