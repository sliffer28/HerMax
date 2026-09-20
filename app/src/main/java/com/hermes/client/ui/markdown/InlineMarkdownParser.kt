package com.hermes.client.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Robust inline Markdown & HTML parser for Jetpack Compose.
 * Handles:
 * - **bold** / <b> / <strong>
 * - *italic* / <i> / <em>
 * - ~~strikethrough~~ / <s> / <del>
 * - `inline code` / <code>
 * - [Link Text](https://...)
 * - $inline math$
 * - Line breaks (\n or <br>)
 * - Escaped characters (\*, \`, \_)
 */
object InlineMarkdownParser {

    fun parse(
        raw: String,
        defaultColor: Color,
        linkColor: Color,
        codeBgColor: Color = Color(0x22FFFFFF)
    ): AnnotatedString {
        if (raw.isEmpty()) return AnnotatedString("")

        // Normalize <br> to \n first
        val normalized = raw
            .replace("[[HERMAX_BR]]", "\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")

        return buildAnnotatedString {
            var i = 0
            val len = normalized.length

            while (i < len) {
                // Escaped characters: \* \` \_ \[ \] \|
                if (normalized[i] == '\\' && i + 1 < len) {
                    val nextChar = normalized[i + 1]
                    if (nextChar in listOf('*', '`', '_', '[', ']', '|', '~', '$', '<')) {
                        append(nextChar)
                        i += 2
                        continue
                    }
                }

                when {
                    // ── [Link Text](url) ─────────────────────────────────────
                    normalized[i] == '[' -> {
                        val closeBracket = normalized.indexOf(']', i + 1)
                        if (closeBracket != -1 && closeBracket + 1 < len && normalized[closeBracket + 1] == '(') {
                            val closeParen = normalized.indexOf(')', closeBracket + 2)
                            if (closeParen != -1) {
                                val linkText = normalized.substring(i + 1, closeBracket)
                                val url = normalized.substring(closeBracket + 2, closeParen).trim()

                                val startPos = length
                                append(linkText.ifEmpty { url })
                                if (length > startPos && url.isNotBlank()) {
                                    try {
                                        addLink(
                                            LinkAnnotation.Url(
                                                url = url,
                                                styles = TextLinkStyles(
                                                    style = SpanStyle(
                                                        color = linkColor,
                                                        textDecoration = TextDecoration.Underline,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                            ),
                                            startPos,
                                            length
                                        )
                                    } catch (e: Throwable) {
                                        try {
                                            addStyle(
                                                SpanStyle(
                                                    color = linkColor,
                                                    textDecoration = TextDecoration.Underline
                                                ),
                                                startPos,
                                                length
                                            )
                                        } catch (ignored: Throwable) {}
                                    }
                                }
                                i = closeParen + 1
                                continue
                            }
                        }
                        append(normalized[i])
                        i++
                    }

                    // ── **Bold** or __Bold__ ─────────────────────────────────
                    (normalized.startsWith("**", i) || normalized.startsWith("__", i)) -> {
                        val delimiter = normalized.substring(i, i + 2)
                        val end = normalized.indexOf(delimiter, i + 2)
                        if (end != -1) {
                            val inner = normalized.substring(i + 2, end)
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(parse(inner, defaultColor, linkColor, codeBgColor))
                            pop()
                            i = end + 2
                        } else {
                            append(normalized[i])
                            i++
                        }
                    }

                    // ── ~~Strikethrough~~ ────────────────────────────────────
                    normalized.startsWith("~~", i) -> {
                        val end = normalized.indexOf("~~", i + 2)
                        if (end != -1) {
                            val inner = normalized.substring(i + 2, end)
                            pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                            append(parse(inner, defaultColor, linkColor, codeBgColor))
                            pop()
                            i = end + 2
                        } else {
                            append(normalized[i])
                            i++
                        }
                    }

                    // ── `Inline Code` ────────────────────────────────────────
                    normalized[i] == '`' -> {
                        val end = normalized.indexOf('`', i + 1)
                        if (end != -1) {
                            val code = normalized.substring(i + 1, end)
                            pushStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    background = codeBgColor,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            append(" $code ")
                            pop()
                            i = end + 1
                        } else {
                            append(normalized[i])
                            i++
                        }
                    }

                    // ── *Italic* or _Italic_ ──────────────────────────────────
                    (normalized[i] == '*' || normalized[i] == '_') -> {
                        val delimiter = normalized[i]
                        val end = normalized.indexOf(delimiter, i + 1)
                        if (end != -1 && end > i + 1) {
                            val inner = normalized.substring(i + 1, end)
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(parse(inner, defaultColor, linkColor, codeBgColor))
                            pop()
                            i = end + 1
                        } else {
                            append(normalized[i])
                            i++
                        }
                    }

                    // ── $Inline Math$ ────────────────────────────────────────
                    normalized[i] == '$' && (i == 0 || normalized[i - 1] != '$') -> {
                        val end = normalized.indexOf('$', i + 1)
                        if (end != -1 && end > i + 1 && (end + 1 >= len || normalized[end + 1] != '$')) {
                            val math = normalized.substring(i + 1, end)
                            pushStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.SemiBold,
                                    color = linkColor
                                )
                            )
                            append(math)
                            pop()
                            i = end + 1
                        } else {
                            append(normalized[i])
                            i++
                        }
                    }

                    // ── HTML <b>, <strong> ───────────────────────────────────
                    normalized.startsWith("<b>", i, ignoreCase = true) -> {
                        val end = normalized.indexOf("</b>", i + 3, ignoreCase = true)
                        if (end != -1) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(parse(normalized.substring(i + 3, end), defaultColor, linkColor, codeBgColor))
                            pop()
                            i = end + 4
                        } else {
                            i += 3
                        }
                    }
                    normalized.startsWith("<strong>", i, ignoreCase = true) -> {
                        val end = normalized.indexOf("</strong>", i + 8, ignoreCase = true)
                        if (end != -1) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(parse(normalized.substring(i + 8, end), defaultColor, linkColor, codeBgColor))
                            pop()
                            i = end + 9
                        } else {
                            i += 8
                        }
                    }

                    // ── HTML <i>, <em> ───────────────────────────────────────
                    normalized.startsWith("<i>", i, ignoreCase = true) -> {
                        val end = normalized.indexOf("</i>", i + 3, ignoreCase = true)
                        if (end != -1) {
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(parse(normalized.substring(i + 3, end), defaultColor, linkColor, codeBgColor))
                            pop()
                            i = end + 4
                        } else {
                            i += 3
                        }
                    }
                    normalized.startsWith("<em>", i, ignoreCase = true) -> {
                        val end = normalized.indexOf("</em>", i + 4, ignoreCase = true)
                        if (end != -1) {
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(parse(normalized.substring(i + 4, end), defaultColor, linkColor, codeBgColor))
                            pop()
                            i = end + 5
                        } else {
                            i += 4
                        }
                    }

                    // ── HTML <code> ──────────────────────────────────────────
                    normalized.startsWith("<code>", i, ignoreCase = true) -> {
                        val end = normalized.indexOf("</code>", i + 6, ignoreCase = true)
                        if (end != -1) {
                            val code = normalized.substring(i + 6, end)
                            pushStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    background = codeBgColor,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            append(" $code ")
                            pop()
                            i = end + 7
                        } else {
                            i += 6
                        }
                    }

                    // ── Default character ────────────────────────────────────
                    else -> {
                        append(normalized[i])
                        i++
                    }
                }
            }
        }
    }
}
