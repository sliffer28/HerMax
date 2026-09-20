package com.hermes.client.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.util.regex.Pattern

/**
 * Lightweight, fast syntax highlighter for code blocks that generates styled [AnnotatedString]s
 * without external heavy parser dependencies.
 */
object CodeHighlighter {

    // Harmonious colors tailored for dark/light obsidian theme
    private val KEYWORD_COLOR = Color(0xFFFF7B72)    // Warm Coral/Red
    private val STRING_COLOR = Color(0xFF7EE787)     // Soft Green
    private val NUMBER_COLOR = Color(0xFF79C0FF)     // Soft Blue
    private val COMMENT_COLOR = Color(0xFF8B949E)    // Muted Gray
    private val TYPE_COLOR = Color(0xFFFFA657)       // Warm Amber/Orange
    private val FUNCTION_COLOR = Color(0xFFD2A8FF)   // Lavender Purple

    private val KEYWORDS = setOf(
        // Kotlin / Java / Swift
        "fun", "val", "var", "class", "interface", "object", "enum", "sealed", "data",
        "public", "private", "protected", "internal", "override", "open", "abstract",
        "import", "package", "return", "if", "else", "when", "while", "for", "do",
        "try", "catch", "finally", "throw", "null", "true", "false", "this", "super",
        "new", "extends", "implements", "static", "final", "void", "const", "let",
        // Python
        "def", "lambda", "elif", "from", "as", "with", "yield", "pass", "None", "True", "False",
        "is", "not", "and", "or", "self", "async", "await", "raise", "except", "assert",
        // JS / TS
        "function", "export", "default", "typeof", "instanceof", "switch", "case", "break",
        "continue", "undefined", "NaN", "require",
        // SQL
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
        "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "ON", "GROUP", "BY", "ORDER",
        "HAVING", "LIMIT", "OFFSET", "CREATE", "TABLE", "DROP", "ALTER", "PRIMARY", "KEY",
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
        "join", "inner", "left", "right", "group", "by", "order", "limit",
        // Rust / Go / C++
        "fn", "mut", "pub", "impl", "struct", "trait", "match", "type", "go", "chan",
        "defer", "include"
    )

    private val COMMENT_PATTERN = Pattern.compile("(//.*$)|(/\\*[\\s\\S]*?\\*/)|(#.*$)", Pattern.MULTILINE)
    private val STRING_PATTERN = Pattern.compile("(\"[^\"]*\"|'[^']*'|`[^`]*`)")
    private val NUMBER_PATTERN = Pattern.compile("\\b(\\d+\\.?\\d*|0x[0-9a-fA-F]+)\\b")
    private val WORD_PATTERN = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b")

    /**
     * Highlights code text into an [AnnotatedString] using token rules.
     */
    fun highlight(code: String, language: String?, defaultColor: Color): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")

        val builder = AnnotatedString.Builder(code)
        val len = code.length

        // Track occupied ranges to avoid coloring inside comments or strings
        val occupied = BooleanArray(len)

        // 1. Comments
        val commentMatcher = COMMENT_PATTERN.matcher(code)
        while (commentMatcher.find()) {
            val start = commentMatcher.start()
            val end = commentMatcher.end()
            builder.safeAddStyle(
                SpanStyle(color = COMMENT_COLOR, fontStyle = FontStyle.Italic),
                start, end, len
            )
            for (i in start until end.coerceAtMost(len)) occupied[i] = true
        }

        // 2. Strings
        val stringMatcher = STRING_PATTERN.matcher(code)
        while (stringMatcher.find()) {
            val start = stringMatcher.start()
            val end = stringMatcher.end()
            if (start in 0 until len && !occupied[start]) {
                builder.safeAddStyle(SpanStyle(color = STRING_COLOR), start, end, len)
                for (i in start until end.coerceAtMost(len)) occupied[i] = true
            }
        }

        // 3. Numbers
        val numberMatcher = NUMBER_PATTERN.matcher(code)
        while (numberMatcher.find()) {
            val start = numberMatcher.start()
            val end = numberMatcher.end()
            if (start in 0 until len && !occupied[start]) {
                builder.safeAddStyle(SpanStyle(color = NUMBER_COLOR), start, end, len)
                for (i in start until end.coerceAtMost(len)) occupied[i] = true
            }
        }

        // 4. Keywords & Types
        val wordMatcher = WORD_PATTERN.matcher(code)
        while (wordMatcher.find()) {
            val start = wordMatcher.start()
            val end = wordMatcher.end()
            if (start in 0 until len && !occupied[start]) {
                val word = wordMatcher.group()
                if (KEYWORDS.contains(word)) {
                    builder.safeAddStyle(
                        SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.SemiBold),
                        start, end, len
                    )
                } else if (word.first().isUpperCase() && word.length > 1) {
                    builder.safeAddStyle(SpanStyle(color = TYPE_COLOR), start, end, len)
                }
            }
        }

        return builder.toAnnotatedString()
    }

    private fun AnnotatedString.Builder.safeAddStyle(style: SpanStyle, start: Int, end: Int, maxLen: Int) {
        if (start in 0 until end && end <= maxLen) {
            try {
                addStyle(style, start, end)
            } catch (ignored: Throwable) {}
        }
    }
}
