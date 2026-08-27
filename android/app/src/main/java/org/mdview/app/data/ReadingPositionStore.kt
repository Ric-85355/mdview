/*
 * ReadingPositionStore.kt — created 2026-08-26, version 0.1.0.
 * Purpose: persist a small independent reading position for each Android URI.
 * Algorithm: hash the stable URI string into a SharedPreferences key and store
 * the first visible Markdown block index without requiring a database.
 */

package org.mdview.app.data

import android.content.Context
import java.security.MessageDigest

class ReadingPositionStore(context: Context) {
    private val preferences = context.getSharedPreferences("reading_positions", Context.MODE_PRIVATE)

    fun load(documentId: String): Int =
        preferences.getInt(ReadingPositionKey.forDocument(documentId), 0).coerceAtLeast(0)

    fun save(documentId: String, blockIndex: Int) {
        preferences.edit()
            .putInt(ReadingPositionKey.forDocument(documentId), blockIndex.coerceAtLeast(0))
            .apply()
    }
}

internal object ReadingPositionKey {
    fun forDocument(documentId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(documentId.toByteArray(Charsets.UTF_8))
        return digest.joinToString(prefix = "position_", separator = "") { "%02x".format(it) }
    }
}
