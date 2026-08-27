/*
 * SearchUiStateTest.kt — created 2026-08-27, version 0.1.0.
 * Purpose: regress closing search without restoring the previous document position.
 * Algorithm: close transient search state after navigation and verify the
 * independently tracked current document position remains at the chosen hit.
 */

package org.mdview.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mdview.app.markdown.SearchMatch

class SearchUiStateTest {
    @Test
    fun closingSearchClearsHighlightsWithoutChangingCurrentDocumentPosition() {
        val originalPosition = 2
        var currentDocumentPosition = originalPosition
        var state = SearchUiState(
            open = true,
            query = "target",
            matches = listOf(SearchMatch(blockIndex = 9, start = 4, end = 10)),
            currentIndex = 0,
        )

        currentDocumentPosition = state.currentMatch!!.blockIndex
        state = state.closed()

        assertEquals(9, currentDocumentPosition)
        assertFalse(state.open)
        assertTrue(state.matches.isEmpty())
        assertEquals(-1, state.currentIndex)
        assertEquals("", state.query)
    }
}
