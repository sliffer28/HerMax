package com.hermes.client.feature.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.hermes.client.domain.model.Attachment
import com.hermes.client.domain.model.Message
import com.hermes.client.domain.model.MessageRole
import com.hermes.client.domain.model.MessageStatus
import com.hermes.client.util.FileAttachmentHelper

@Composable
fun MessageBubble(
    message: Message,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (Message, String) -> Unit
) {
    val isUser = message.role == MessageRole.USER
    var showActions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { showActions = !showActions },
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Role label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Icon(
                if (isUser) Icons.Filled.Person else Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (isUser) "You" else "Hermes",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
            if (message.model != null && !isUser) {
                Spacer(Modifier.width(8.dp))
                Text(
                    message.model,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (message.isStreaming) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // Message content bubble
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = if (isUser) Modifier.widthIn(max = 340.dp) else Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.attachments.isNotEmpty()) {
                    MessageAttachmentsView(
                        attachments = message.attachments,
                        isUser = isUser,
                        modifier = Modifier.padding(bottom = if (message.content.isNotBlank()) 8.dp else 0.dp)
                    )
                }

                if (message.content.isNotBlank()) {
                    SelectionContainer {
                        MarkdownContent(
                            content = message.content,
                            isUser = isUser
                        )
                    }
                }
            }
        }

        // Error status
        if (message.status == MessageStatus.ERROR) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null,
                    modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                Text("Failed to send", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Action row
        AnimatedVisibility(
            visible = showActions && !message.isStreaming,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy",
                        modifier = Modifier.size(16.dp))
                }
                if (message.status == MessageStatus.ERROR ||
                    (isUser && message.status == MessageStatus.SENT)) {
                    IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry",
                            modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete",
                        modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun MessageAttachmentsView(
    attachments: List<Attachment>,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (att in attachments) {
            val isImage = FileAttachmentHelper.isImageMime(att.mimeType)

            if (isImage) {
                val imageBitmap = remember(att.id, att.localPath, att.uri) {
                    try {
                        FileAttachmentHelper.openInputStream(context, att)?.use { stream ->
                            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                            BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
                        }
                    } catch (_: Exception) {
                        null
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = att.fileName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp, max = 220.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = att.fileName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (att.size > 0) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = FileAttachmentHelper.formatFileSize(att.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            } else {
                val isPdf = FileAttachmentHelper.isPdf(att.mimeType, att.fileName)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            if (isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (isPdf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = att.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (att.size > 0) {
                                Text(
                                    text = FileAttachmentHelper.formatFileSize(att.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Markdown renderer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders full markdown using the robust CommonMark + GFM parser.
 * Supports tables, headings, syntax-highlighted code blocks, nested lists,
 * block quotes, checklists, links, math, and HTML normalization.
 */
@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false
) {
    com.hermes.client.ui.markdown.MarkdownRenderer(
        content = content,
        modifier = modifier,
        isUser = isUser
    )
}

/**
 * Backward-compatible helper for inline styled text.
 */
fun buildStyledText(raw: String, defaultColor: Color): AnnotatedString {
    return com.hermes.client.ui.markdown.InlineMarkdownParser.parse(
        raw = raw,
        defaultColor = defaultColor,
        linkColor = defaultColor
    )
}
