package com.hermes.client.feature.chat

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.client.domain.model.Attachment
import com.hermes.client.util.FileAttachmentHelper
import com.hermes.client.util.VoiceState

@Composable
fun ChatComposer(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit,
    onAttachFile: () -> Unit,
    onVoiceInput: () -> Unit,
    isStreaming: Boolean,
    isConnected: Boolean,
    selectedModel: String?,
    onModelClick: () -> Unit,
    attachments: List<Attachment> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    voiceState: VoiceState = VoiceState.Idle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Model selector chip
            if (selectedModel != null) {
                AssistChip(
                    onClick = onModelClick,
                    label = {
                        Text(
                            selectedModel,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Attachments Preview Row
            if (attachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachments, key = { it.id }) { attachment ->
                        AttachmentPreviewChip(
                            attachment = attachment,
                            onRemove = { onRemoveAttachment(attachment.id) }
                        )
                    }
                }
            }

            // Voice status indicator row
            AnimatedVisibility(
                visible = voiceState !is VoiceState.Idle,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (voiceState) {
                        is VoiceState.Listening -> {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseAlpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red.copy(alpha = alpha))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Listening... speak now (tap mic to stop)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        is VoiceState.Transcribing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Transcribing...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is VoiceState.Error -> {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                voiceState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        else -> {}
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Attach button
                IconButton(
                    onClick = onAttachFile,
                    modifier = Modifier.size(40.dp),
                    enabled = !isStreaming
                ) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = "Attach file",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Text input
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp, max = 160.dp),
                    placeholder = {
                        Text(
                            if (voiceState is VoiceState.Listening) "Listening to speech..."
                            else if (isStreaming) "Generating response..."
                            else if (!isConnected) "Message (Hermes offline / Cloud ready)..."
                            else "Message Hermes...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    ),
                    enabled = !isStreaming,
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.width(4.dp))

                // Voice input button
                IconButton(
                    onClick = onVoiceInput,
                    modifier = Modifier.size(40.dp),
                    enabled = !isStreaming,
                    colors = if (voiceState is VoiceState.Listening) {
                        IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    } else {
                        IconButtonDefaults.iconButtonColors()
                    }
                ) {
                    Icon(
                        if (voiceState is VoiceState.Listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = if (voiceState is VoiceState.Listening) "Stop voice input" else "Voice input",
                        modifier = Modifier.size(22.dp),
                        tint = if (voiceState is VoiceState.Listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Send / Stop button
                AnimatedContent(
                    targetState = isStreaming,
                    transitionSpec = {
                        scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
                    },
                    label = "send_stop_animation"
                ) { streaming ->
                    if (streaming) {
                        FilledIconButton(
                            onClick = onStopGeneration,
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "Stop generation",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        val canSend = (inputText.isNotBlank() || attachments.isNotEmpty())
                        FilledIconButton(
                            onClick = onSend,
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            enabled = canSend
                        ) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = "Send message",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewChip(
    attachment: Attachment,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val isImage = FileAttachmentHelper.isImageMime(attachment.mimeType)

    val imageBitmap = remember(attachment.id, attachment.localPath, attachment.uri) {
        if (isImage) {
            try {
                FileAttachmentHelper.openInputStream(context, attachment)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = attachment.fileName,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                val icon = when {
                    isImage -> Icons.Filled.Image
                    attachment.mimeType.contains("pdf", ignoreCase = true) -> Icons.Filled.PictureAsPdf
                    else -> Icons.Filled.Description
                }
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.widthIn(max = 140.dp)) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (attachment.size > 0) {
                    Text(
                        text = FileAttachmentHelper.formatFileSize(attachment.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove attachment",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

