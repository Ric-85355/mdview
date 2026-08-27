/*
 * DocumentSearchTest.kt — created 2026-08-26, version 0.1.0.
 * Purpose: verify Android search ordering, ranges, and Unicode behavior.
 * Algorithm: search parsed block text and assert non-overlapping matches tied
 * to the document block indexes used for UI navigation.
 */

package org.mdview.app.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSearchTest {
    @Test
    fun searchIsCaseInsensitiveAndOrdered() {
        val document = parse("# Alpha\nAlpha beta alpha")
        val matches = DocumentSearch.find(document, "ALPHA")

        assertEquals(3, matches.size)
        assertEquals(listOf(0, 1, 1), matches.map { it.blockIndex })
        assertEquals(listOf(0, 0, 11), matches.map { it.start })
    }

    @Test
    fun searchSupportsCyrillic() {
        val document = parse("Русский текст\n\nЕщё русский текст")

        assertEquals(2, DocumentSearch.find(document, "РУССКИЙ").size)
    }

    @Test
    fun searchUsesRenderedTextWithoutMarkdownMarkers() {
        val document = parse("Read **important** [docs](https://example.org)")
        val matches = DocumentSearch.find(document, "important docs")

        assertEquals(1, matches.size)
        assertEquals(5, matches.single().start)
    }

    @Test
    fun emptyQueryHasNoMatches() {
        assertTrue(DocumentSearch.find(parse("text"), "").isEmpty())
    }

    @Test
    fun matchesDoNotOverlap() {
        val matches = DocumentSearch.find(parse("aaaa"), "aa")

        assertEquals(listOf(0, 2), matches.map { it.start })
    }

    @Test
    fun codeBlockMatchPositionIdentifiesItsSourceLineAndColumn() {
        val document = parse("""
            ```yaml
            first: actions
            second: no match
            third: github-actions
            ```
        """.trimIndent())
        val matches = DocumentSearch.find(document, "actio")
        val code = document.blocks.single() as MarkdownBlock.CodeBlock

        assertEquals(2, matches.size)
        assertEquals(
            CodeBlockMatchPosition(characterOffset = 7, lineIndex = 0, columnIndex = 7),
            DocumentSearch.positionInCodeBlock(code.plainText, matches[0]),
        )
        assertEquals(
            CodeBlockMatchPosition(characterOffset = 46, lineIndex = 2, columnIndex = 14),
            DocumentSearch.positionInCodeBlock(code.plainText, matches[1]),
        )
    }

    private fun parse(source: String): MarkdownDocument =
        MarkdownParser.parse("content://test/document", "test.md", source)
}
