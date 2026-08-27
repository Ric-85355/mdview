/*
 * DocumentSectionResolverTest.kt — created 2026-08-27, version 0.1.0.
 * Purpose: regress current-section synchronization at all TOC depths.
 * Algorithm: resolve manual/search block positions against a three-level TOC
 * and verify exact headings, parent fallback, transitions, and stable positions.
 */

package org.mdview.app.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSectionResolverTest {
    private val headings = listOf(
        MarkdownHeading(level = 1, title = "H1 A", blockIndex = 0),
        MarkdownHeading(level = 2, title = "H2 A", blockIndex = 3),
        MarkdownHeading(level = 3, title = "H3 A", blockIndex = 6),
        MarkdownHeading(level = 2, title = "H2 B", blockIndex = 10),
        MarkdownHeading(level = 3, title = "H3 B", blockIndex = 13),
        MarkdownHeading(level = 1, title = "H1 B", blockIndex = 20),
    )

    @Test
    fun h3PositionResolvesToExactHeadingAtDepthThree() {
        assertEquals(2, resolve(blockIndex = 8, depth = 3))
    }

    @Test
    fun h3PositionFallsBackToH2AtDepthTwo() {
        assertEquals(1, resolve(blockIndex = 8, depth = 2))
    }

    @Test
    fun h3PositionFallsBackToH1AtDepthOne() {
        assertEquals(0, resolve(blockIndex = 8, depth = 1))
    }

    @Test
    fun manualScrollChangesTheCurrentSection() {
        assertEquals(2, resolve(blockIndex = 8, depth = 3))
        assertEquals(4, resolve(blockIndex = 15, depth = 3))
        assertEquals(5, resolve(blockIndex = 22, depth = 3))
    }

    @Test
    fun searchMatchPositionChangesTheCurrentSection() {
        val firstMatch = SearchMatch(blockIndex = 7, start = 0, end = 4)
        val nextMatch = SearchMatch(blockIndex = 14, start = 2, end = 6)

        assertEquals(2, resolve(firstMatch.blockIndex, depth = 3))
        assertEquals(4, resolve(nextMatch.blockIndex, depth = 3))
    }

    @Test
    fun changingDepthDoesNotChangeDocumentPosition() {
        val documentPosition = 8

        assertEquals(2, resolve(documentPosition, depth = 3))
        assertEquals(1, resolve(documentPosition, depth = 2))
        assertEquals(0, resolve(documentPosition, depth = 1))
        assertEquals(8, documentPosition)
    }

    private fun resolve(blockIndex: Int, depth: Int): Int =
        DocumentSectionResolver.visibleHeadingIndex(headings, blockIndex, depth)
}
