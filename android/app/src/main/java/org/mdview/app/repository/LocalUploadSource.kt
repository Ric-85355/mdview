/*
 * LocalUploadSource.kt — created 2026-09-01, version 0.1.0.
 * Purpose: resolve a safe upload filename and stream from a SAF content URI.
 * Algorithm: read OpenableColumns metadata, reject non-Markdown basenames,
 * and never use provider URI paths as remote paths.
 */

package org.mdview.app.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

data class UploadSelection(val uri: Uri, val fileName: String)

class LocalUploadSource(private val resolver: ContentResolver) {
    fun selection(uri: Uri): UploadSelection {
        val name = displayName(uri) ?: throw IllegalArgumentException("Could not determine file name")
        return UploadSelection(uri, UploadPaths.safeFileName(name))
    }

    fun open(selection: UploadSelection) =
        resolver.openInputStream(selection.uri) ?: error("The selected file is unavailable")

    private fun displayName(uri: Uri): String? = resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (column >= 0) cursor.getString(column) else null
    }
}
