package com.hermes.client.ui.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser
import org.junit.Assert.*
import org.junit.Test

class MarkdownRenderingTest {

    private val parser = Parser.builder()
        .extensions(
            listOf(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create(),
                TaskListItemsExtension.create()
            )
        )
        .build()

    @Test
    fun `test HtmlSanitizer converts basic formatting tags`() {
        val input = "This is <strong>bold</strong> and <em>italic</em> and <code>code</code> and <del>strikethrough</del>."
        val sanitized = HtmlSanitizer.sanitize(input)
        assertEquals("This is **bold** and *italic* and `code` and ~~strikethrough~~.", sanitized)
    }

    @Test
    fun `test HtmlSanitizer converts regular line breaks`() {
        val input = "Line 1<br>Line 2<br/>Line 3<BR />Line 4"
        val sanitized = HtmlSanitizer.sanitize(input)
        assertEquals("Line 1\nLine 2\nLine 3\nLine 4", sanitized)
    }

    @Test
    fun `test HtmlSanitizer preserves code blocks untouched`() {
        val input = """
            Here is some code:
            ```html
            <br>
            <strong>Do not touch</strong>
            <div>Hello</div>
            ```
            And inline `<code>tag</code>`.
            Outside <strong>should be converted</strong>.
        """.trimIndent()

        val sanitized = HtmlSanitizer.sanitize(input)
        assertTrue(sanitized.contains("<br>\n<strong>Do not touch</strong>\n<div>Hello</div>"))
        assertTrue(sanitized.contains("`<code>tag</code>`"))
        assertTrue(sanitized.contains("**should be converted**"))
    }

    @Test
    fun `test HtmlSanitizer preserves table row single-line integrity`() {
        val input = """
            | Provider | Features |
            |----------|----------|
            | OpenAI | Tool flag <br>• 30 sec timeout <br>• Only GET |
        """.trimIndent()

        val sanitized = HtmlSanitizer.sanitize(input)
        val lines = sanitized.lines()
        // Must maintain 3 lines so CommonMark table parser succeeds
        assertEquals(3, lines.size)
        assertTrue(lines[2].contains("[[HERMAX_BR]]"))

        val doc = parser.parse(sanitized)
        val tableBlock = doc.firstChild as? TableBlock
        assertNotNull("TableBlock should be parsed from sanitized GFM table", tableBlock)

        val tableModel = TableExtractor.extract(tableBlock!!)
        assertEquals(2, tableModel.headers.size)
        assertEquals("Provider", tableModel.headers[0].text)
        assertEquals("Features", tableModel.headers[1].text)
        assertEquals(1, tableModel.rows.size)

        val cellText = tableModel.rows[0][1].text
        assertTrue(cellText.contains("\n• 30 sec timeout"))
    }

    @Test
    fun `test TableExtractor with screenshot example`() {
        val input = """
            | Provider | Model(s) | How you get it | Notable limits / quirks |
            |---|---|---|---|
            | OpenAI | ChatGPT-4o-browse | • ChatGPT web UI <br>• OpenAI API | • 30 sec timeout <br>• HTTPS only <br>• Cached for 5 min |
            | Microsoft | Bing Chat | • bing.com chat UI | • Rate-limited |
        """.trimIndent()

        val sanitized = HtmlSanitizer.sanitize(input)
        val doc = parser.parse(sanitized)
        val tableBlock = doc.firstChild as? TableBlock
        assertNotNull("TableBlock should parse successfully", tableBlock)

        val tableModel = TableExtractor.extract(tableBlock!!)
        assertEquals(4, tableModel.headers.size)
        assertEquals("Provider", tableModel.headers[0].text)
        assertEquals("Notable limits / quirks", tableModel.headers[3].text)
        assertEquals(2, tableModel.rows.size)

        val firstRowLimits = tableModel.rows[0][3].text
        // Newlines should be properly restored from <br>
        val limitLines = firstRowLimits.lines()
        assertEquals(3, limitLines.size)
        assertEquals("• 30 sec timeout", limitLines[0].trim())
        assertEquals("• HTTPS only", limitLines[1].trim())
        assertEquals("• Cached for 5 min", limitLines[2].trim())
    }

    @Test
    fun `test InlineMarkdownParser styled string generation`() {
        val raw = "Hello **world** and *italic* and `code` and ~~strike~~ and [HERMAX](https://hermax.ai)"
        val parsed = InlineMarkdownParser.parse(
            raw = raw,
            defaultColor = androidx.compose.ui.graphics.Color.White,
            linkColor = androidx.compose.ui.graphics.Color.Red
        )

        assertEquals("Hello world and italic and  code  and strike and HERMAX", parsed.text)
        assertTrue(parsed.spanStyles.isNotEmpty())
    }
}
