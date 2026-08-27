/*
 * ReadingPositionStoreTest.kt — created 2026-08-26, version 0.1.0.
 * Purpose: verify stable, per-document persistence keys without Android UI.
 * Algorithm: compare deterministic SHA-256 keys for equal and distinct URIs.
 */

package org.mdview.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPositionStoreTest {
    @Test
    fun keyIsStableForTheSameUri() {
        val uri = "content://documents/document/primary%3ADocs%2Fguide.md"

        assertEquals(
            ReadingPositionKey.forDocument(uri),
            ReadingPositionKey.forDocument(uri),
        )
    }

    @Test
    fun distinctUrisHaveDistinctOpaqueKeys() {
        val first = ReadingPositionKey.forDocument("content://provider/document/first.md")
        val second = ReadingPositionKey.forDocument("content://provider/document/second.md")

        assertNotEquals(first, second)
        assertTrue(first.startsWith("position_"))
        assertEquals("position_".length + 64, first.length)
    }
}
