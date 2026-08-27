/*
 * DocumentSearch.kt — created 2026-08-26, version 0.1.0.
 * Purpose: provide deterministic case-insensitive search over rendered blocks.
 * Algorithm: lowercase query/block text with Locale.ROOT, collect ordered
 * ranges tied to block indexes, and locate code-block hits by line and column.
 */

package org.mdview.app.markdown

import java.util.Locale

object DocumentSearch {
    fun find(document: MarkdownDocument, query: String): List<SearchMatch> {
        val foldedQuery = query.lowercase(Locale.ROOT)
        if (foldedQuery.isEmpty()) return emptyList()
        return buildList {
            document.blocks.forEachIndexed { blockIndex, block ->
                val text = block.plainText.lowercase(Locale.ROOT)
                var from = 0
                while (from <= text.length - foldedQuery.length) {
                    val found = text.indexOf(foldedQuery, from)
                    if (found < 0) break
                    add(SearchMatch(blockIndex, found, found + foldedQuery.length))
                    from = found + foldedQuery.length
                }
            }
        }
    }

    internal fun positionInCodeBlock(text: String, match: SearchMatch): CodeBlockMatchPosition {
        val offset = match.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0))
            .let { if (it < 0 || offset == 0) 0 else it + 1 }
        return CodeBlockMatchPosition(
            characterOffset = offset,
            lineIndex = text.take(offset).count { it == '\n' },
            columnIndex = offset - lineStart,
        )
    }
}

internal data class CodeBlockMatchPosition(
    val characterOffset: Int,
    val lineIndex: Int,
    val columnIndex: Int,
)
