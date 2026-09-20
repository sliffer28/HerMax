package com.hermes.client.ui.markdown

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser

/**
 * High-performance, rich Markdown renderer for Jetpack Compose.
 *
 * Fully supports:
 * - CommonMark + GitHub Flavored Markdown (GFM)
 * - GFM Tables with horizontal scrolling, cell text wrapping, alignment, and dividers
 * - Headings (H1 to H6) with clean font scales
 * - Fenced code blocks with language badge, copy button, syntax highlighting, and horizontal scroll
 * - Nested bullet lists (•, ◦, ▪) and numbered lists (1., 2.)
 * - Task list items / checklists (- [ ] and - [x])
 * - Blockquotes with accent line and italic text
 * - Clickable links opening directly via UriHandler
 * - Bold, italic, strikethrough, inline code, and inline math ($...$)
 * - Clean HTML normalization preventing raw <br>, <p>, <div>, <strong>, etc.
 * - Incremental streaming resilience
 */
object MarkdownParserInstance {
    private val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        AutolinkExtension.create(),
        TaskListItemsExtension.create()
    )

    val parser: Parser = Parser.builder()
        .extensions(extensions)
        .build()
}

@Composable
fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false
) {
    if (content.isBlank()) return

    val bodyColor = if (isUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    val linkColor = if (isUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.primary

    // 1. Sanitize HTML tags outside code blocks and handle <br>
    val sanitized = remember(content) {
        HtmlSanitizer.sanitize(content)
    }

    // 2. Parse Markdown AST
    val document = remember(sanitized) {
        try {
            MarkdownParserInstance.parser.parse(sanitized)
        } catch (e: Exception) {
            // Fallback to plain document if parser encounters anomalous token
            MarkdownParserInstance.parser.parse(content)
        }
    }

    Column(modifier = modifier) {
        var node = document.firstChild
        while (node != null) {
            RenderBlockNode(
                node = node,
                bodyColor = bodyColor,
                linkColor = linkColor,
                isUser = isUser
            )
            node = node.next
        }
    }
}

@Composable
private fun RenderBlockNode(
    node: Node,
    bodyColor: Color,
    linkColor: Color,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    when (node) {
        // ── Headings ──────────────────────────────────────────────────────────
        is Heading -> {
            val headingText = extractNodeText(node)
            val (style, topPadding, bottomPadding) = when (node.level) {
                1 -> Triple(MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold), 12.dp, 4.dp)
                2 -> Triple(MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), 10.dp, 4.dp)
                3 -> Triple(MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), 8.dp, 2.dp)
                4 -> Triple(MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), 6.dp, 2.dp)
                else -> Triple(MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), 4.dp, 2.dp)
            }
            Text(
                text = InlineMarkdownParser.parse(headingText, bodyColor, linkColor),
                style = style,
                color = if (isUser) bodyColor else MaterialTheme.colorScheme.onSurface,
                modifier = modifier.padding(top = topPadding, bottom = bottomPadding)
            )
        }

        // ── Paragraph ─────────────────────────────────────────────────────────
        is Paragraph -> {
            val paragraphText = extractNodeText(node)
            if (paragraphText.isNotBlank()) {
                Text(
                    text = InlineMarkdownParser.parse(paragraphText, bodyColor, linkColor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyColor,
                    lineHeight = 20.sp,
                    modifier = modifier.padding(vertical = 3.dp)
                )
            }
        }

        // ── Fenced & Indented Code Blocks ─────────────────────────────────────
        is FencedCodeBlock -> {
            CodeBlockView(
                code = node.literal ?: "",
                language = node.info?.trim() ?: "code",
                modifier = modifier
            )
        }
        is IndentedCodeBlock -> {
            CodeBlockView(
                code = node.literal ?: "",
                language = "code",
                modifier = modifier
            )
        }

        // ── GFM Table ─────────────────────────────────────────────────────────
        is TableBlock -> {
            val tableModel = remember(node) {
                TableExtractor.extract(node)
            }
            MarkdownTable(
                tableModel = tableModel,
                modifier = modifier,
                bodyColor = bodyColor
            )
        }

        // ── Bullet List ───────────────────────────────────────────────────────
        is BulletList -> {
            Column(modifier = modifier.padding(vertical = 2.dp)) {
                var item = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        RenderListItem(
                            item = item,
                            bullet = "• ",
                            bodyColor = bodyColor,
                            linkColor = linkColor,
                            isUser = isUser,
                            nestingLevel = 0
                        )
                    }
                    item = item.next
                }
            }
        }

        // ── Ordered List ──────────────────────────────────────────────────────
        is OrderedList -> {
            var counter = node.markerStartNumber
            Column(modifier = modifier.padding(vertical = 2.dp)) {
                var item = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        RenderListItem(
                            item = item,
                            bullet = "$counter. ",
                            bodyColor = bodyColor,
                            linkColor = linkColor,
                            isUser = isUser,
                            nestingLevel = 0
                        )
                        counter++
                    }
                    item = item.next
                }
            }
        }

        // ── BlockQuote ────────────────────────────────────────────────────────
        is BlockQuote -> {
            val quoteText = extractNodeText(node)
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(22.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = InlineMarkdownParser.parse(quoteText, bodyColor, linkColor),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = bodyColor.copy(alpha = 0.9f)
                )
            }
        }

        // ── Thematic Break / Horizontal Rule ──────────────────────────────────
        is ThematicBreak -> {
            HorizontalDivider(
                modifier = modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }

        // ── Raw HTML Block ────────────────────────────────────────────────────
        is HtmlBlock -> {
            val cleaned = HtmlSanitizer.sanitize(node.literal ?: "").trim()
            if (cleaned.isNotBlank()) {
                Text(
                    text = InlineMarkdownParser.parse(cleaned, bodyColor, linkColor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyColor,
                    modifier = modifier.padding(vertical = 2.dp)
                )
            }
        }

        // ── Fallback ──────────────────────────────────────────────────────────
        else -> {
            val text = extractNodeText(node)
            if (text.isNotBlank()) {
                Text(
                    text = InlineMarkdownParser.parse(text, bodyColor, linkColor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyColor
                )
            }
        }
    }
}

@Composable
private fun RenderListItem(
    item: ListItem,
    bullet: String,
    bodyColor: Color,
    linkColor: Color,
    isUser: Boolean,
    nestingLevel: Int
) {
    // Check if this item is a task list item (- [ ] or - [x])
    var isTaskItem = false
    var isChecked = false
    var child = item.firstChild

    while (child != null) {
        if (child is TaskListItemMarker) {
            isTaskItem = true
            isChecked = child.isChecked
            break
        }
        child = child.next
    }

    val itemText = extractNodeText(item).let { raw ->
        if (raw.startsWith("[ ] ")) {
            isTaskItem = true; isChecked = false
            raw.removePrefix("[ ] ")
        } else if (raw.startsWith("[x] ", ignoreCase = true)) {
            isTaskItem = true; isChecked = true
            raw.removePrefix("[x] ").removePrefix("[X] ")
        } else {
            raw
        }
    }

    val indent = (nestingLevel * 16).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (isTaskItem) {
            Icon(
                if (isChecked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (isChecked) "Checked" else "Unchecked",
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp, end = 6.dp),
                tint = if (isChecked) MaterialTheme.colorScheme.primary else bodyColor.copy(alpha = 0.6f)
            )
        } else {
            val bulletSymbol = when (nestingLevel % 3) {
                0 -> bullet
                1 -> "◦ "
                else -> "▪ "
            }
            Text(
                text = bulletSymbol,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isUser) bodyColor else MaterialTheme.colorScheme.primary
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            if (itemText.isNotBlank()) {
                Text(
                    text = InlineMarkdownParser.parse(itemText, bodyColor, linkColor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyColor,
                    lineHeight = 20.sp
                )
            }

            // Render any nested lists inside this list item
            var inner = item.firstChild
            while (inner != null) {
                when (inner) {
                    is BulletList -> {
                        var sub = inner.firstChild
                        while (sub != null) {
                            if (sub is ListItem) {
                                RenderListItem(
                                    item = sub,
                                    bullet = "◦ ",
                                    bodyColor = bodyColor,
                                    linkColor = linkColor,
                                    isUser = isUser,
                                    nestingLevel = nestingLevel + 1
                                )
                            }
                            sub = sub.next
                        }
                    }
                    is OrderedList -> {
                        var subCounter = inner.markerStartNumber
                        var sub = inner.firstChild
                        while (sub != null) {
                            if (sub is ListItem) {
                                RenderListItem(
                                    item = sub,
                                    bullet = "$subCounter. ",
                                    bodyColor = bodyColor,
                                    linkColor = linkColor,
                                    isUser = isUser,
                                    nestingLevel = nestingLevel + 1
                                )
                                subCounter++
                            }
                            sub = sub.next
                        }
                    }
                }
                inner = inner.next
            }
        }
    }
}

@Composable
private fun CodeBlockView(
    code: String,
    language: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val trimmedCode = code.trimEnd('\n')
    val highlightedCode = remember(trimmedCode, language) {
        CodeHighlighter.highlight(
            code = trimmedCode,
            language = language,
            defaultColor = Color.White
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    AnimatedVisibility(
                        visible = copied,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Text(
                            text = "Copied!",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(trimmedCode))
                            copied = true
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Copy code",
                            modifier = Modifier.size(15.dp),
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Code content with horizontal scrolling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = highlightedCode,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Extracts markdown text from AST node and its children.
 */
private fun extractNodeText(node: Node): String {
    val sb = StringBuilder()
    fun walk(n: Node) {
        var child = n.firstChild
        while (child != null) {
            when (child) {
                is Text -> sb.append(child.literal)
                is Code -> sb.append("`").append(child.literal).append("`")
                is StrongEmphasis -> {
                    sb.append("**")
                    walk(child)
                    sb.append("**")
                }
                is Emphasis -> {
                    sb.append("*")
                    walk(child)
                    sb.append("*")
                }
                is Link -> {
                    sb.append("[")
                    walk(child)
                    sb.append("](${child.destination})")
                }
                is HtmlInline -> {
                    val lit = child.literal
                    if (lit.matches(Regex("(?i)<br\\s*/?>"))) {
                        sb.append("\n")
                    } else {
                        sb.append(lit)
                    }
                }
                is SoftLineBreak, is HardLineBreak -> sb.append("\n")
                // Do not traverse nested lists inside extractNodeText to prevent duplication
                is BulletList, is OrderedList -> {}
                else -> walk(child)
            }
            child = child.next
        }
    }
    walk(node)
    return sb.toString()
}
