/*
 * RepositorySettings.kt — created 2026-09-01, version 0.1.0.
 * Purpose: define the single-repository Android settings and credentials.
 * Algorithm: keep non-secret connection metadata separate from ephemeral
 * credential values supplied by secure platform storage.
 */

package org.mdview.app.settings

data class RepositorySettings(
    val name: String = "Repository",
    val repositoryUrl: String = "",
    val httpUser: String = "",
    val sftpEnabled: Boolean = false,
    val sftpHost: String = "",
    val sftpPort: Int = 22,
    val sftpUser: String = "",
    val sftpRoot: String = "",
)

data class RepositoryCredentials(
    val httpPassword: String,
    val sftpPassword: String,
)
