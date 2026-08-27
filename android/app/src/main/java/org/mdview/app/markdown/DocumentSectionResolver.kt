/*
 * DocumentSectionResolver.kt — created 2026-08-27, version 0.1.0.
 * Purpose: resolve one current visible TOC section from a document position.
 * Algorithm: filter headings by active depth and choose the last heading whose
 * block starts at or before the current block, naturally falling back to a parent.
 */

package org.mdview.app.markdown

internal object DocumentSectionResolver {
    fun visibleHeadingIndex(
        headings: List<MarkdownHeading>,
        blockIndex: Int,
        depth: Int,
    ): Int {
        val visible = headings.filter { it.level <= depth.coerceIn(1, 3) }
        return visible.indexOfLast { it.blockIndex <= blockIndex }.coerceAtLeast(0)
    }
}
