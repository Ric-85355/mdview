/*
 * MarkdownModel.kt — created 2026-08-26, version 0.1.0.
 * Purpose: define the immutable Android Markdown document model.
 * Algorithm: represent parsed block/inline content, headings, and search hits
 * with document block indexes suitable for LazyColumn navigation.
 */

package org.mdview.app.markdown

data class MarkdownDocument(
    val id: String,
    val title: String,
    val source: String,
    val blocks: List<MarkdownBlock>,
    val headings: List<MarkdownHeading>,
)

sealed interface MarkdownBlock {
    val plainText: String

    data class Heading(
        val level: Int,
        val content: InlineContent,
    ) : MarkdownBlock {
        override val plainText: String = content.plainText
    }

    data class Paragraph(val content: InlineContent) : MarkdownBlock {
        override val plainText: String = content.plainText
    }

    data class ListItem(
        val level: Int,
        val ordered: Boolean,
        val marker: String,
        val content: InlineContent,
    ) : MarkdownBlock {
        override val plainText: String = content.plainText
    }

    data class CodeBlock(override val plainText: String) : MarkdownBlock

    data object Spacer : MarkdownBlock {
        override val plainText: String = ""
    }
}

data class MarkdownHeading(
    val level: Int,
    val title: String,
    val blockIndex: Int,
)

data class InlineContent(
    val plainText: String,
    val spans: List<InlineSpan>,
)

data class InlineSpan(
    val start: Int,
    val end: Int,
    val style: InlineStyle,
    val destination: String? = null,
)

enum class InlineStyle {
    Bold,
    Italic,
    Code,
    Link,
}

data class SearchMatch(
    val blockIndex: Int,
    val start: Int,
    val end: Int,
)
