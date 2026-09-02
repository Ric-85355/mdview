/*
 * RepositoryModel.kt — created 2026-09-01, version 0.1.0.
 * Purpose: model and validate the read-only HTTP repository index.
 * Algorithm: parse format-1 JSON recursively to two directory levels and
 * resolve current/nearest folders by normalized repository-relative paths.
 */

package org.mdview.app.repository

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class RepositoryDocument(
    val name: String,
    val path: String,
)

data class RepositoryDirectory(
    val name: String,
    val path: String,
    val documents: List<RepositoryDocument>,
    val directories: List<RepositoryDirectory>,
)

/** A stable, presentation-ready item in the current repository directory. */
sealed interface RepositoryBrowserEntry {
    data object Parent : RepositoryBrowserEntry
    data class Directory(val value: RepositoryDirectory) : RepositoryBrowserEntry
    data class Document(val value: RepositoryDocument) : RepositoryBrowserEntry
}

/** Builds file-manager ordering without retaining UI links across index refreshes. */
fun RepositoryDirectory.browserEntries(): List<RepositoryBrowserEntry> = buildList {
    if (path.isNotEmpty()) add(RepositoryBrowserEntry.Parent)
    directories.sortedBy { it.name.lowercase() }.forEach {
        add(RepositoryBrowserEntry.Directory(it))
    }
    documents.sortedBy { it.name.lowercase() }.forEach {
        add(RepositoryBrowserEntry.Document(it))
    }
}

data class MarkdownRepository(
    val name: String,
    val description: String?,
    val root: RepositoryDirectory,
) {
    fun findDirectory(path: String): RepositoryDirectory? {
        val normalized = RepositoryPaths.normalizeRelativeDirectory(path)
        if (normalized.isEmpty()) return root
        return allDirectories().firstOrNull { it.path == normalized }
    }

    fun nearestDirectory(path: String): RepositoryDirectory {
        var candidate = RepositoryPaths.normalizeRelativeDirectory(path)
        while (true) {
            findDirectory(candidate)?.let { return it }
            if (candidate.isEmpty()) return root
            candidate = candidate.substringBeforeLast('/', "")
        }
    }

    fun allDirectories(): List<RepositoryDirectory> = buildList {
        fun visit(directory: RepositoryDirectory) {
            add(directory)
            directory.directories.forEach(::visit)
        }
        visit(root)
    }
}

object RepositoryParser {
    fun parse(source: String): MarkdownRepository {
        try {
            val json = JSONObject(source)
            require(json.getInt("format") == 1) { "Unsupported repository format" }
            val name = requiredText(json, "name")
            val root = parseDirectoryItems("", json.getJSONArray("items"), 0)
            return MarkdownRepository(
                name = name,
                description = json.optString("description").takeIf { it.isNotBlank() },
                root = RepositoryDirectory("/", "", root.second, root.first),
            )
        } catch (error: JSONException) {
            throw IllegalArgumentException("Invalid repository.json", error)
        }
    }

    private fun parseDirectoryItems(
        parentPath: String,
        items: JSONArray,
        depth: Int,
    ): Pair<List<RepositoryDirectory>, List<RepositoryDocument>> {
        val directories = mutableListOf<RepositoryDirectory>()
        val documents = mutableListOf<RepositoryDocument>()
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            when (item.getString("type")) {
                "directory" -> {
                    require(depth < 2) { "Repository directory depth exceeds two levels" }
                    val name = safeSegment(requiredText(item, "name"))
                    val path = joinRelative(parentPath, name)
                    val children = parseDirectoryItems(path, item.getJSONArray("items"), depth + 1)
                    directories += RepositoryDirectory(name, path, children.second, children.first)
                }
                "document" -> {
                    val name = requiredText(item, "name")
                    val path = RepositoryPaths.normalizeDocumentPath(requiredText(item, "path"))
                    require(path.substringBeforeLast('/', "") == parentPath) {
                        "Document path does not match its directory"
                    }
                    documents += RepositoryDocument(name, path)
                }
                else -> throw IllegalArgumentException("Unsupported repository item type")
            }
        }
        return directories.sortedBy { it.name.lowercase() } to
            documents.sortedBy { it.name.lowercase() }
    }

    private fun requiredText(json: JSONObject, key: String): String =
        json.getString(key).trim().also { require(it.isNotEmpty()) { "Missing $key" } }

    private fun safeSegment(value: String): String {
        require(value != "." && value != ".." && '/' !in value && '\\' !in value) {
            "Unsafe repository path"
        }
        return value
    }

    private fun joinRelative(parent: String, name: String): String =
        if (parent.isEmpty()) name else "$parent/$name"
}

object RepositoryPaths {
    fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim()
        require(trimmed.startsWith("https://")) {
            "Repository URL must use HTTPS"
        }
        return trimmed.trimEnd('/') + "/"
    }

    fun normalizeRelativeDirectory(path: String): String {
        val normalized = path.trim().trim('/').replace('\\', '/')
        if (normalized.isEmpty()) return ""
        val segments = normalized.split('/')
        require(segments.size <= 2 && segments.all { it.isNotBlank() && it != "." && it != ".." }) {
            "Unsafe repository directory"
        }
        return segments.joinToString("/")
    }

    fun normalizeDocumentPath(path: String): String {
        val normalized = path.trim().trimStart('/').replace('\\', '/')
        val segments = normalized.split('/')
        require(
            segments.size <= 3 &&
                segments.all { it.isNotBlank() && it != "." && it != ".." } &&
                segments.last().endsWith(".md", ignoreCase = true),
        ) { "Unsafe Markdown document path" }
        return segments.joinToString("/")
    }

    fun documentUrl(baseUrl: String, documentPath: String): String =
        normalizeBaseUrl(baseUrl) + normalizeDocumentPath(documentPath)
            .split('/')
            .joinToString("/", transform = ::encodePathSegment)

    private fun encodePathSegment(segment: String): String = buildString {
        segment.toByteArray(Charsets.UTF_8).forEach { byte ->
            val value = byte.toInt() and 0xff
            if (
                value in 'a'.code..'z'.code || value in 'A'.code..'Z'.code ||
                value in '0'.code..'9'.code || value in listOf('-'.code, '.'.code, '_'.code, '~'.code)
            ) {
                append(value.toChar())
            } else {
                append('%')
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
