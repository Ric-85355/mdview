/*
 * MarkdownParser.kt — created 2026-08-26, version 0.1.0.
 * Purpose: parse common technical Markdown into readable Android UI blocks.
 * Algorithm: scan lines once for fences/headings/lists/paragraphs, then scan
 * inline text for bold, italic, code, and link spans without a heavy library.
 */

package org.mdview.app.markdown

object MarkdownParser {
    private val headingPattern = Regex("^ {0,3}(#{1,6})[ \\t]+(.+?)[ \\t]*#*[ \\t]*$")
    private val unorderedPattern = Regex("^( {0,8})[-*+]\\s+(?!\\[[ xX]\\](?:\\s|$))(.*)$")
    private val orderedPattern = Regex("^( {0,8})(\\d+\\.)\\s+(.*)$")

    fun parse(id: String, title: String, source: String): MarkdownDocument {
        val lines = source.splitToSequence('\n').map { it.removeSuffix("\r") }.toList()
        val blocks = mutableListOf<MarkdownBlock>()
        val headings = mutableListOf<MarkdownHeading>()
        var lineIndex = 0

        while (lineIndex < lines.size) {
            val line = lines[lineIndex]
            if (line.trimStart().startsWith("```")) {
                val code = mutableListOf<String>()
                lineIndex++
                while (lineIndex < lines.size && !lines[lineIndex].trimStart().startsWith("```")) {
                    code += lines[lineIndex]
                    lineIndex++
                }
                if (lineIndex < lines.size) lineIndex++
                blocks += MarkdownBlock.CodeBlock(code.joinToString("\n"))
                continue
            }
            val heading = headingPattern.matchEntire(line)
            if (heading != null) {
                val level = heading.groupValues[1].length
                val content = parseInline(heading.groupValues[2].trim())
                val blockIndex = blocks.size
                blocks += MarkdownBlock.Heading(level, content)
                if (level <= 3) headings += MarkdownHeading(level, content.plainText, blockIndex)
                lineIndex++
                continue
            }
            val unordered = unorderedPattern.matchEntire(line)
            val ordered = orderedPattern.matchEntire(line)
            if (unordered != null || ordered != null) {
                val match = unordered ?: requireNotNull(ordered)
                val indentation = match.groupValues[1].length
                val marker = if (unordered != null) "•" else match.groupValues[2]
                val body = if (unordered != null) match.groupValues[2] else match.groupValues[3]
                blocks += MarkdownBlock.ListItem(
                    level = (indentation / 2).coerceIn(0, 2),
                    ordered = ordered != null,
                    marker = marker,
                    content = parseInline(body),
                )
                lineIndex++
                continue
            }
            if (line.isBlank()) {
                if (blocks.lastOrNull() !is MarkdownBlock.Spacer) blocks += MarkdownBlock.Spacer
                lineIndex++
                continue
            }
            val paragraph = mutableListOf(line.trim())
            lineIndex++
            while (lineIndex < lines.size && isParagraphContinuation(lines[lineIndex])) {
                paragraph += lines[lineIndex].trim()
                lineIndex++
            }
            blocks += MarkdownBlock.Paragraph(parseInline(paragraph.joinToString(" ")))
        }
        while (blocks.lastOrNull() is MarkdownBlock.Spacer) {
            blocks.removeAt(blocks.lastIndex)
        }
        return MarkdownDocument(id, title, source, blocks, headings)
    }

    private fun isParagraphContinuation(line: String): Boolean =
        line.isNotBlank() &&
            !line.trimStart().startsWith("```") &&
            headingPattern.matchEntire(line) == null &&
            unorderedPattern.matchEntire(line) == null &&
            orderedPattern.matchEntire(line) == null

    fun parseInline(source: String): InlineContent {
        val output = StringBuilder()
        val spans = mutableListOf<InlineSpan>()
        var position = 0
        while (position < source.length) {
            val link = parseLink(source, position)
            if (link != null) {
                val start = output.length
                output.append(link.label)
                spans += InlineSpan(start, output.length, InlineStyle.Link, link.destination)
                position = link.end
                continue
            }
            val marker = when {
                source.startsWith("**", position) -> "**"
                source[position] == '`' -> "`"
                source[position] == '*' || source[position] == '_' -> source[position].toString()
                else -> null
            }
            if (marker != null) {
                val close = source.indexOf(marker, position + marker.length)
                if (close > position + marker.length) {
                    val start = output.length
                    output.append(source, position + marker.length, close)
                    val style = when (marker) {
                        "**" -> InlineStyle.Bold
                        "`" -> InlineStyle.Code
                        else -> InlineStyle.Italic
                    }
                    spans += InlineSpan(start, output.length, style)
                    position = close + marker.length
                    continue
                }
            }
            output.append(source[position])
            position++
        }
        return InlineContent(output.toString(), spans)
    }

    private data class Link(val label: String, val destination: String, val end: Int)

    private fun parseLink(source: String, start: Int): Link? {
        if (source.getOrNull(start) != '[') return null
        val closeLabel = source.indexOf(']', start + 1)
        if (closeLabel <= start + 1 || source.getOrNull(closeLabel + 1) != '(') return null
        val closeDestination = source.indexOf(')', closeLabel + 2)
        if (closeDestination <= closeLabel + 2) return null
        return Link(
            source.substring(start + 1, closeLabel),
            source.substring(closeLabel + 2, closeDestination),
            closeDestination + 1,
        )
    }
}
