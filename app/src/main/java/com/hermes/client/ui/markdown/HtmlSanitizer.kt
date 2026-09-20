package com.hermes.client.ui.markdown

import java.util.regex.Pattern

/**
 * Normalizes mixed HTML/Markdown text so that HTML tags like `<br>`, `<strong>`,
 * `<em>`, `<code>`, `<a>`, etc. are converted to their Markdown or visual equivalents,
 * and structural wrapper tags (`<div>`, `<p>`, `<span>`) are cleanly stripped or normalized.
 *
 * Code blocks (fenced ``` and inline `) are strictly preserved so raw HTML inside
 * code samples is NOT altered.
 */
object HtmlSanitizer {

    private val FENCED_CODE_PATTERN = Pattern.compile("```[\\s\\S]*?```")
    private val INLINE_CODE_PATTERN = Pattern.compile("`[^`\\n]+`")

    /**
     * Sanitizes and normalizes markdown text before feeding into the CommonMark parser.
     */
    fun sanitize(input: String): String {
        if (input.isBlank()) return input

        // 1. Separate code blocks to protect them from HTML substitution
        val segments = splitByCodeBlocks(input)

        val result = StringBuilder()
        for (segment in segments) {
            if (segment.isCode) {
                // Keep code block verbatim
                result.append(segment.content)
            } else {
                result.append(sanitizeNonCodeText(segment.content))
            }
        }

        return result.toString()
    }

    private data class Segment(val content: String, val isCode: Boolean)

    private fun splitByCodeBlocks(text: String): List<Segment> {
        val list = mutableListOf<Segment>()
        // Match fenced code blocks first
        val fencedMatcher = FENCED_CODE_PATTERN.matcher(text)
        var lastEnd = 0

        while (fencedMatcher.find()) {
            val start = fencedMatcher.start()
            val end = fencedMatcher.end()
            if (start > lastEnd) {
                // Non-fenced text might have inline code
                splitInlineCode(text.substring(lastEnd, start), list)
            }
            list.add(Segment(fencedMatcher.group(), isCode = true))
            lastEnd = end
        }

        if (lastEnd < text.length) {
            splitInlineCode(text.substring(lastEnd), list)
        }

        return list
    }

    private fun splitInlineCode(text: String, outList: MutableList<Segment>) {
        val inlineMatcher = INLINE_CODE_PATTERN.matcher(text)
        var lastEnd = 0
        while (inlineMatcher.find()) {
            val start = inlineMatcher.start()
            val end = inlineMatcher.end()
            if (start > lastEnd) {
                outList.add(Segment(text.substring(lastEnd, start), isCode = false))
            }
            outList.add(Segment(inlineMatcher.group(), isCode = true))
            lastEnd = end
        }
        if (lastEnd < text.length) {
            outList.add(Segment(text.substring(lastEnd), isCode = false))
        }
    }

    private fun sanitizeNonCodeText(text: String): String {
        var s = text

        // Convert common formatting HTML tags to Markdown
        // Strong / Bold
        s = s.replace(Regex("(?i)<(strong|b)>(.*?)</\\1>"), "**$2**")
        // Italic
        s = s.replace(Regex("(?i)<(em|i)>(.*?)</\\1>"), "*$2*")
        // Inline code
        s = s.replace(Regex("(?i)<code>(.*?)</code>"), "`$1`")
        // Strikethrough
        s = s.replace(Regex("(?i)<(strike|del|s)>(.*?)</\\1>"), "~~$2~~")
        // Links: <a href="url">text</a>
        s = s.replace(Regex("(?i)<a\\s+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>"), "[$2]($1)")
        // Horizontal rule
        s = s.replace(Regex("(?i)<hr\\s*/?>"), "\n---\n")

        // Headings: <h1>title</h1> -> # title
        s = s.replace(Regex("(?i)<h1>(.*?)</h1>"), "\n# $1\n")
        s = s.replace(Regex("(?i)<h2>(.*?)</h2>"), "\n## $1\n")
        s = s.replace(Regex("(?i)<h3>(.*?)</h3>"), "\n### $1\n")
        s = s.replace(Regex("(?i)<h4>(.*?)</h4>"), "\n#### $1\n")

        // Handle line breaks:
        // In GFM tables, rows must stay on a single line. If <br> is inside a table line (| ... |),
        // we preserve a special token that the TableCell renderer will convert into newlines,
        // so the table parser doesn't break into multiple lines!
        val lines = s.split("\n")
        val processedLines = lines.map { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("|") && (trimmed.endsWith("|") || trimmed.contains("|"))) {
                // Table row line: preserve line breaks with placeholder
                line.replace(Regex("(?i)<br\\s*/?>"), "[[HERMAX_BR]]")
            } else {
                // Regular line: convert <br> to newline
                line.replace(Regex("(?i)<br\\s*/?>"), "\n")
            }
        }
        s = processedLines.joinToString("\n")

        // Strip structural wrapper tags that shouldn't appear raw:
        // <p>, </p>, <div>, </div>, <span>, </span>, etc.
        s = s.replace(Regex("(?i)</?(?:div|p|span|section|article|header|footer)[^>]*>"), "\n")
        // Strip table wrapper tags if model vomited mixed html table tags around a markdown table
        s = s.replace(Regex("(?i)</?(?:tbody|thead|tfoot)[^>]*>"), "")

        // Normalize multiple consecutive blank lines
        s = s.replace(Regex("\n{3,}"), "\n\n")

        return s
    }

    /**
     * Converts any table cell line-break placeholders into actual newlines for cell rendering.
     */
    fun restoreCellLineBreaks(cellText: String): String {
        return cellText
            .replace(Regex("\\s*\\[\\[HERMAX_BR\\]\\]\\s*"), "\n")
            .replace(Regex("(?i)\\s*<br\\s*/?>\\s*"), "\n")
            .trim()
    }
}
