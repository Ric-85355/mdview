/*
 * ReaderToolbarModelTest.kt — created 2026-09-02, version 0.1.0.
 * Purpose: verify the context-sensitive Reader TOC control labels and depth cycle.
 * Algorithm: exercise closed/open labels and repeated pure depth transitions.
 */

package org.mdview.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderToolbarModelTest {
    @Test
    fun closedTocShowsContentsWithoutDepth() {
        assertEquals("Contents", ReaderToolbarModel.tocLabel(tocOpen = false, depth = 2))
    }

    @Test
    fun openTocShowsCurrentDepth() {
        assertEquals("TOC 2", ReaderToolbarModel.tocLabel(tocOpen = true, depth = 2))
    }

    @Test
    fun centerControlOpensClosedTocAndCyclesOpenToc() {
        assertEquals(
            ReaderToolbarModel.TocAction.Open,
            ReaderToolbarModel.tocAction(tocOpen = false),
        )
        assertEquals(
            ReaderToolbarModel.TocAction.CycleDepth,
            ReaderToolbarModel.tocAction(tocOpen = true),
        )
    }

    @Test
    fun depthCyclesOneThroughThree() {
        assertEquals(2, ReaderToolbarModel.nextTocDepth(1))
        assertEquals(3, ReaderToolbarModel.nextTocDepth(2))
        assertEquals(1, ReaderToolbarModel.nextTocDepth(3))
    }
}
