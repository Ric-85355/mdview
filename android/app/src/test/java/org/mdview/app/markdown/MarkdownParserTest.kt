/*
 * MarkdownParserTest.kt — created 2026-08-26, version 0.1.0.
 * Purpose: verify Android Markdown parsing independently from Compose.
 * Algorithm: parse small deterministic documents and assert blocks, TOC,
 * inline styling, nesting, fences, and depth filtering behavior.
 */

package org.mdview.app.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun headingsBuildTocForLevelsOneThroughThree() {
        val document = parse("# One\n## Two\n### Three\n#### Four")

        assertEquals(listOf(1, 2, 3), document.headings.map { it.level })
        assertEquals(listOf("One", "Two", "Three"), document.headings.map { it.title })
        assertTrue(document.blocks[3] is MarkdownBlock.Heading)
    }

    @Test
    fun tocDepthFiltersWithoutChangingBlockIndexes() {
        val document = parse("# One\ntext\n## Two\ntext\n### Three")

        assertEquals(listOf(0), document.headings.filter { it.level <= 1 }.map { it.blockIndex })
        assertEquals(listOf(0, 2), document.headings.filter { it.level <= 2 }.map { it.blockIndex })
        assertEquals(listOf(0, 2, 4), document.headings.map { it.blockIndex })
    }

    @Test
    fun fencedCodeDoesNotCreateHeadings() {
        val document = parse("# Visible\n```md\n## Hidden\n```\n## Visible too")

        assertEquals(listOf("Visible", "Visible too"), document.headings.map { it.title })
        assertTrue(document.blocks.any { it is MarkdownBlock.CodeBlock && "Hidden" in it.plainText })
    }

    @Test
    fun parsesListsAndNesting() {
        val document = parse("- first\n  - second\n    3. third")
        val items = document.blocks.filterIsInstance<MarkdownBlock.ListItem>()

        assertEquals(listOf(0, 1, 2), items.map { it.level })
        assertEquals(listOf(false, false, true), items.map { it.ordered })
        assertEquals("3.", items.last().marker)
    }

    @Test
    fun parsesReadableInlineStylesAndLinks() {
        val inline = MarkdownParser.parseInline(
            "Use **bold**, *italic*, `code`, and [site](https://example.org)",
        )

        assertEquals("Use bold, italic, code, and site", inline.plainText)
        assertEquals(
            listOf(InlineStyle.Bold, InlineStyle.Italic, InlineStyle.Code, InlineStyle.Link),
            inline.spans.map { it.style },
        )
        assertEquals("https://example.org", inline.spans.last().destination)
    }

    @Test
    fun emptyDocumentIsValid() {
        val document = parse("")

        assertTrue(document.headings.isEmpty())
        assertTrue(document.blocks.isEmpty())
    }

    @Test
    fun taskListSyntaxIsLeftReadableRatherThanMisparsed() {
        val document = parse("- [ ] unfinished")

        assertFalse(document.blocks.single() is MarkdownBlock.ListItem)
        assertEquals("- [ ] unfinished", document.blocks.single().plainText)
    }

    private fun parse(source: String): MarkdownDocument =
        MarkdownParser.parse("content://test/document", "test.md", source)
}
