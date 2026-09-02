/*
 * UploadPaths.kt — created 2026-09-01, version 0.1.0.
 * Purpose: validate local Markdown names and build confined SFTP destinations.
 * Algorithm: accept one safe basename, normalize repository/root segments,
 * then join only these trusted components using POSIX separators.
 */

package org.mdview.app.repository

object UploadPaths {
    fun isMarkdownFileName(name: String): Boolean =
        runCatching { safeFileName(name) }.isSuccess

    fun safeFileName(name: String): String {
        val trimmed = name.trim()
        require(
            trimmed.isNotEmpty() &&
                trimmed != "." &&
                trimmed != ".." &&
                '/' !in trimmed &&
                '\\' !in trimmed &&
                trimmed.endsWith(".md", ignoreCase = true),
        ) { "Select a Markdown (.md) file" }
        return trimmed
    }

    fun destination(root: String, repositoryDirectory: String, fileName: String): String {
        val safeName = safeFileName(fileName)
        val safeDirectory = RepositoryPaths.normalizeRelativeDirectory(repositoryDirectory)
        val safeRoot = normalizeSftpRoot(root)
        return listOf(safeRoot, safeDirectory, safeName)
            .filter { it.isNotEmpty() }
            .joinToString("/")
            .let { if (safeRoot.startsWith('/')) "/${it.trimStart('/')}" else it }
    }

    private fun normalizeSftpRoot(root: String): String {
        val normalized = root.trim().replace('\\', '/').trimEnd('/')
        require(normalized.isNotEmpty()) { "SFTP root is required" }
        val absolute = normalized.startsWith('/')
        val parts = normalized.trim('/').split('/').filter { it.isNotEmpty() }
        require(parts.all { it != "." && it != ".." }) { "Unsafe SFTP root" }
        val joined = parts.joinToString("/")
        return if (absolute) "/$joined" else joined
    }
}

object UploadOutcomeText {
    fun afterRefresh(refreshSucceeded: Boolean): String =
        if (refreshSucceeded) "Upload completed" else "Upload completed, repository refresh failed"
}
