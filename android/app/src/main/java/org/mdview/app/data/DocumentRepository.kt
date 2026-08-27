/*
 * DocumentRepository.kt — created 2026-08-26, version 0.1.0.
 * Purpose: read Markdown safely from Android content/file URIs.
 * Algorithm: query a display name, decode the stream as strict UTF-8, and
 * return a parsed immutable document or a diagnostic result without crashing.
 */

package org.mdview.app.data

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mdview.app.markdown.MarkdownDocument
import org.mdview.app.markdown.MarkdownParser

class DocumentRepository(private val resolver: ContentResolver) {
    suspend fun load(uri: Uri): Result<MarkdownDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val title = displayName(uri) ?: uri.lastPathSegment ?: "Markdown document"
            val source = resolver.openInputStream(uri)?.use { stream ->
                val decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                InputStreamReader(stream, decoder).readText()
            } ?: error("The selected document is unavailable")
            MarkdownParser.parse(uri.toString(), title, source)
        }
    }

    private fun displayName(uri: Uri): String? {
        val cursor: Cursor = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        ) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val column = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            return if (column >= 0) it.getString(column) else null
        }
    }
}
