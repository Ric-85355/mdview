/*
 * ReaderToolbarModel.kt — created 2026-09-02, version 0.1.0.
 * Purpose: define the context-sensitive Reader TOC control without Compose state.
 * Algorithm: label a closed control as Contents, expose the open depth, and cycle 1–3.
 */

package org.mdview.app.ui

internal object ReaderToolbarModel {
    enum class TocAction { Open, CycleDepth }

    fun tocLabel(tocOpen: Boolean, depth: Int): String =
        if (tocOpen) "TOC $depth" else "Contents"

    fun tocAction(tocOpen: Boolean): TocAction =
        if (tocOpen) TocAction.CycleDepth else TocAction.Open

    fun nextTocDepth(depth: Int): Int = depth % 3 + 1
}
