package com.hermes.client.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.ext.gfm.tables.*
import org.commonmark.node.*

/**
 * Data model for parsed Markdown tables.
 */
data class TableModel(
    val headers: List<CellModel>,
    val rows: List<List<CellModel>>,
    val alignments: List<TableCell.Alignment?>
)

data class CellModel(
    val text: String,
    val isHeader: Boolean,
    val alignment: TableCell.Alignment?
)

/**
 * Converts CommonMark TableBlock into TableModel safely.
 */
object TableExtractor {

    fun extract(tableBlock: TableBlock): TableModel {
        val headers = mutableListOf<CellModel>()
        val rows = mutableListOf<List<CellModel>>()
        val alignments = mutableListOf<TableCell.Alignment?>()

        try {
            var node = tableBlock.firstChild
            while (node != null) {
                when (node) {
                    is TableHead -> {
                        var rowNode = node.firstChild
                        while (rowNode != null) {
                            if (rowNode is TableRow) {
                                var cellNode = rowNode.firstChild
                                while (cellNode != null) {
                                    if (cellNode is TableCell) {
                                        val cellText = extractCellMarkdown(cellNode)
                                        headers.add(CellModel(cellText, isHeader = true, cellNode.alignment))
                                        alignments.add(cellNode.alignment)
                                    }
                                    cellNode = cellNode.next
                                }
                            }
                            rowNode = rowNode.next
                        }
                    }
                    is TableBody -> {
                        var rowNode = node.firstChild
                        while (rowNode != null) {
                            if (rowNode is TableRow) {
                                val rowCells = mutableListOf<CellModel>()
                                var cellIndex = 0
                                var cellNode = rowNode.firstChild
                                while (cellNode != null) {
                                    if (cellNode is TableCell) {
                                        val cellText = extractCellMarkdown(cellNode)
                                        val align = cellNode.alignment ?: alignments.getOrNull(cellIndex)
                                        rowCells.add(CellModel(cellText, isHeader = false, align))
                                        cellIndex++
                                    }
                                    cellNode = cellNode.next
                                }
                                if (rowCells.isNotEmpty()) {
                                    rows.add(rowCells)
                                }
                            }
                            rowNode = rowNode.next
                        }
                    }
                }
                node = node.next
            }
        } catch (e: Exception) {
            // Ignore unexpected traversal errors
        }

        return TableModel(headers, rows, alignments)
    }

    private fun extractCellMarkdown(cell: TableCell): String {
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
                    else -> walk(child)
                }
                child = child.next
            }
        }
        walk(cell)
        return HtmlSanitizer.restoreCellLineBreaks(sb.toString())
    }
}

/**
 * Beautiful, crash-proof Markdown Table Composable using standard Row/Column primitives
 * with calculated column widths and contained horizontal scrolling.
 */
@Composable
fun MarkdownTable(
    tableModel: TableModel,
    modifier: Modifier = Modifier,
    bodyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (tableModel.headers.isEmpty()) return

    val columnCount = tableModel.headers.size
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val headerBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val evenRowBgColor = MaterialTheme.colorScheme.surfaceContainerLow
    val oddRowBgColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val linkColor = MaterialTheme.colorScheme.primary

    // Compute comfortable column widths based on max content length
    val columnWidths = remember(tableModel) {
        (0 until columnCount).map { col ->
            val headerLen = tableModel.headers.getOrNull(col)?.text?.length ?: 0
            val maxRowLen = tableModel.rows.maxOfOrNull { it.getOrNull(col)?.text?.length ?: 0 } ?: 0
            val maxLen = maxOf(headerLen, maxRowLen)
            when {
                maxLen <= 12 -> 110.dp
                maxLen <= 30 -> 150.dp
                maxLen <= 80 -> 210.dp
                maxLen <= 150 -> 260.dp
                else -> 300.dp
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(evenRowBgColor)
    ) {
        Box(
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Column {
                // ── Header Row ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.background(headerBgColor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tableModel.headers.forEachIndexed { colIndex, cell ->
                        val colWidth = columnWidths.getOrElse(colIndex) { 150.dp }
                        TableCellBox(
                            cell = cell,
                            isHeader = true,
                            width = colWidth,
                            bodyColor = MaterialTheme.colorScheme.onSurface,
                            linkColor = linkColor
                        )
                    }
                }

                HorizontalDivider(color = borderColor, thickness = 1.dp)

                // ── Body Rows ────────────────────────────────────────────────
                tableModel.rows.forEachIndexed { rowIndex, row ->
                    val rowBg = if (rowIndex % 2 == 0) evenRowBgColor else oddRowBgColor
                    Row(
                        modifier = Modifier.background(rowBg),
                        verticalAlignment = Alignment.Top
                    ) {
                        for (colIndex in 0 until columnCount) {
                            val cell = row.getOrNull(colIndex)
                                ?: CellModel("", isHeader = false, tableModel.alignments.getOrNull(colIndex))
                            val colWidth = columnWidths.getOrElse(colIndex) { 150.dp }
                            TableCellBox(
                                cell = cell,
                                isHeader = false,
                                width = colWidth,
                                bodyColor = bodyColor,
                                linkColor = linkColor
                            )
                        }
                    }

                    if (rowIndex < tableModel.rows.size - 1) {
                        HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCellBox(
    cell: CellModel,
    isHeader: Boolean,
    width: Dp,
    bodyColor: Color,
    linkColor: Color
) {
    val textAlign = when (cell.alignment) {
        TableCell.Alignment.CENTER -> TextAlign.Center
        TableCell.Alignment.RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }

    val styledText = InlineMarkdownParser.parse(
        raw = cell.text,
        defaultColor = bodyColor,
        linkColor = linkColor
    )

    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = when (cell.alignment) {
            TableCell.Alignment.CENTER -> Alignment.Center
            TableCell.Alignment.RIGHT -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        Text(
            text = styledText,
            style = if (isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            color = bodyColor,
            textAlign = textAlign,
            fontSize = if (isHeader) 13.sp else 12.5.sp,
            lineHeight = 18.sp
        )
    }
}
