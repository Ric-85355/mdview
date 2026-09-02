/*
 * UploadPaths.kt — created 2026-09-01, version 0.1.0.
 * Purpose: validate repository object names and build confined SFTP destinations.
 * Algorithm: accept one safe basename, normalize repository/root segments,
 * join trusted components, and expose testable mutation decisions.
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

    internal fun normalizeSftpRoot(root: String): String {
        val normalized = root.trim().replace('\\', '/').trimEnd('/')
        require(normalized.isNotEmpty()) { "SFTP root is required" }
        val absolute = normalized.startsWith('/')
        val parts = normalized.trim('/').split('/').filter { it.isNotEmpty() }
        require(parts.all { it != "." && it != ".." }) { "Unsafe SFTP root" }
        val joined = parts.joinToString("/")
        return if (absolute) "/$joined" else joined
    }
}

object RepositoryMutationPaths {
    fun safeFolderName(name: String): String = safeObjectName(name)

    fun safeDocumentName(name: String): String = UploadPaths.safeFileName(name)

    fun newFolderPath(root: String, repositoryDirectory: String, folderName: String): String {
        val safeDirectory = RepositoryPaths.normalizeRelativeDirectory(repositoryDirectory)
        val depth = if (safeDirectory.isEmpty()) 0 else safeDirectory.count { it == '/' } + 1
        require(depth < 2) {
            "Repository supports at most two directory levels"
        }
        return objectPath(root, safeDirectory, safeFolderName(folderName))
    }

    fun objectPath(root: String, repositoryDirectory: String, objectName: String): String {
        val safeName = safeObjectName(objectName)
        val safeDirectory = RepositoryPaths.normalizeRelativeDirectory(repositoryDirectory)
        val safeRoot = UploadPaths.normalizeSftpRoot(root)
        return listOf(safeRoot, safeDirectory, safeName)
            .filter { it.isNotEmpty() }
            .joinToString("/")
            .let { if (safeRoot.startsWith('/')) "/${it.trimStart('/')}" else it }
    }

    private fun safeObjectName(name: String): String {
        val trimmed = name.trim()
        require(
            trimmed.isNotEmpty() &&
                trimmed != "." &&
                trimmed != ".." &&
                '/' !in trimmed &&
                '\\' !in trimmed,
        ) { "Enter one safe name without path separators" }
        return trimmed
    }
}

enum class RepositoryMutationResult {
    Completed,
    AlreadyExists,
    FolderNotEmpty,
}

object RepositoryMutationRules {
    fun collisionResult(targetExists: Boolean): RepositoryMutationResult? =
        if (targetExists) RepositoryMutationResult.AlreadyExists else null

    fun folderDeleteResult(hasChildren: Boolean): RepositoryMutationResult =
        if (hasChildren) RepositoryMutationResult.FolderNotEmpty else RepositoryMutationResult.Completed
}

object RepositoryMutationOutcomeText {
    fun afterRefresh(completedMessage: String, refreshSucceeded: Boolean): String =
        if (refreshSucceeded) completedMessage else "$completedMessage, repository refresh failed"
}

object UploadOutcomeText {
    fun afterRefresh(refreshSucceeded: Boolean): String =
        if (refreshSucceeded) "Upload completed" else "Upload completed, repository refresh failed"
}
