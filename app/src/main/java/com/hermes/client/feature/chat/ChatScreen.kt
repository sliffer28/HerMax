package com.hermes.client.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.client.domain.model.*
import com.hermes.client.feature.models.ModelListContent
import com.hermes.client.feature.models.ModelSettingsSheet
import com.hermes.client.feature.models.ModelSelectorViewModel
import com.hermes.client.util.FileAttachmentHelper
import com.hermes.client.util.VoiceInputManager
import com.hermes.client.util.VoiceState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String?,
    onNavigateToConversations: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onNewChat: (() -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val headerState by viewModel.headerState.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val validAttachments = mutableListOf<Attachment>()
            for (uri in uris) {
                FileAttachmentHelper.createAttachmentFromUri(context, uri).fold(
                    onSuccess = { validAttachments.add(it) },
                    onFailure = { error ->
                        val msg = error.message ?: "Unable to read selected file."
                        viewModel.setErrorMessage(msg)
                    }
                )
            }
            if (validAttachments.isNotEmpty()) {
                viewModel.addAttachments(validAttachments)
            }
        }
    }

    // Voice input manager and permissions
    val voiceInputManager = remember {
        VoiceInputManager(context) { text ->
            viewModel.appendVoiceText(text)
        }
    }
    DisposableEffect(voiceInputManager) {
        onDispose {
            voiceInputManager.destroy()
        }
    }
    val voiceState by voiceInputManager.voiceState.collectAsStateWithLifecycle()

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceInputManager.startListening()
        }
    }

    val handleVoiceInputClick: () -> Unit = {
        if (voiceState is VoiceState.Listening) {
            voiceInputManager.stopListening()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                voiceInputManager.startListening()
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Inline model picker sheet state
    var showModelPicker by remember { mutableStateOf(false) }

    // Load conversation on launch
    LaunchedEffect(conversationId) {
        if (conversationId != null) {
            viewModel.loadConversation(conversationId)
        } else {
            viewModel.createNewConversation()
        }
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size, uiState.streamingContent) {
        if (messages.isNotEmpty()) {
            try {
                listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    // Tapping the title area opens the inline model picker
                    Column(
                        modifier = Modifier
                            .clickable { showModelPicker = true }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.conversationTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = "Pick model",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (uiState.selectedModel != null) {
                            Text(
                                text = buildString {
                                    append(uiState.selectedModel)
                                    headerState.selectedProvider?.let { provider ->
                                        append(" • ")
                                        append(provider.displayName)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (conversationId != null && onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        IconButton(onClick = onNavigateToConversations) {
                            Icon(Icons.Filled.Menu, contentDescription = "Conversations")
                        }
                    }
                },
                actions = {
                    // Connection status indicator
                    ConnectionStatusDot(uiState.connectionState)

                    IconButton(onClick = {
                        if (onNewChat != null) onNewChat() else viewModel.createNewConversation()
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "New Chat")
                    }
                    IconButton(onClick = onNavigateToModels) {
                        Icon(Icons.Outlined.SmartToy, contentDescription = "Models")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (messages.isEmpty() && !uiState.isLoading) {
                    item {
                        EmptyConversationView(
                            onSuggestionClick = { viewModel.sendMessage(it) }
                        )
                    }
                }

                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onRetry = { viewModel.retryMessage(message) },
                        onCopy = { viewModel.copyMessage(message) },
                        onDelete = { viewModel.deleteMessage(message) },
                        onEdit = { msg, newContent -> viewModel.editMessage(msg, newContent) }
                    )
                }

                // Streaming response
                if (uiState.isStreaming && uiState.streamingContent.isNotEmpty()) {
                    item {
                        MessageBubble(
                            message = Message(
                                id = "streaming",
                                conversationId = uiState.currentConversationId ?: "",
                                role = MessageRole.ASSISTANT,
                                content = uiState.streamingContent,
                                status = MessageStatus.STREAMING,
                                model = uiState.selectedModel,
                                isStreaming = true
                            ),
                            onRetry = {},
                            onCopy = {},
                            onDelete = {},
                            onEdit = { _, _ -> }
                        )
                    }
                }

                // Tool activity cards
                uiState.activeToolCalls.forEach { toolCall ->
                    item(key = "tool_${toolCall.id}") {
                        ToolActivityCard(
                            toolCall = toolCall,
                            onToggleExpand = { viewModel.toggleToolCallExpand(toolCall.id) }
                        )
                    }
                }

                // Approval cards
                uiState.pendingApprovals.forEach { approval ->
                    item(key = "approval_${approval.approvalId}") {
                        ApprovalCard(
                            approval = approval,
                            onApprove = { viewModel.approveToolCall(approval) },
                            onReject = { viewModel.rejectToolCall(approval) }
                        )
                    }
                }

                // Status indicator
                if (uiState.statusMessage != null) {
                    item {
                        StreamingIndicator(
                            status = uiState.statusMessage!!,
                            isThinking = uiState.isThinking
                        )
                    }
                }
            }

            // Error banner
            if (uiState.error != null) {
                ErrorBanner(
                    message = uiState.error!!,
                    onRetry = { viewModel.retryLastMessage() },
                    onDismiss = { viewModel.clearError() }
                )
            }

            // Chat composer
            ChatComposer(
                inputText = uiState.inputText,
                onInputChanged = { viewModel.updateInput(it) },
                onSend = { viewModel.sendMessage(uiState.inputText) },
                onStopGeneration = { viewModel.stopGeneration() },
                onAttachFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                onVoiceInput = handleVoiceInputClick,
                isStreaming = uiState.isStreaming,
                isConnected = uiState.connectionState == ConnectionState.CONNECTED,
                selectedModel = uiState.selectedModel,
                onModelClick = { showModelPicker = true },
                attachments = pendingAttachments,
                onRemoveAttachment = { viewModel.removeAttachment(it) },
                voiceState = voiceState
            )
        }
    }

    // ── Inline model picker ModalBottomSheet ────────────────────────────────────────
    if (showModelPicker) {
        // Share a ModelSelectorViewModel scoped to this composable
        val modelPickerVm: ModelSelectorViewModel = hiltViewModel()
        val modelUiState by modelPickerVm.uiState.collectAsStateWithLifecycle()
        var showSettingsSheet by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showModelPicker = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            // Sheet header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Select Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Response settings")
                    }
                    IconButton(onClick = { modelPickerVm.loadModels() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh models")
                    }
                }
            }
            HorizontalDivider()

            // Model list with source and provider selector
            ModelListContent(
                uiState = modelUiState,
                currentModelId = uiState.selectedModel,
                onModelSelected = { model ->
                    viewModel.setModel(model.id, modelUiState.selectedProvider.name)
                    showModelPicker = false
                },
                onSourceSelected = { modelPickerVm.selectSource(it) },
                onProviderSelected = { modelPickerVm.selectProvider(it) },
                onRetry = { modelPickerVm.loadModels() },
                onNavigateToSettings = {
                    showModelPicker = false
                    onNavigateToSettings()
                },
                onManualModelSubmit = { manualId ->
                    modelPickerVm.setManualModel(manualId)
                    showModelPicker = false
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp, max = 560.dp)
            )
        }

        // Response settings sheet (opened from within the picker sheet)
        if (showSettingsSheet) {
            ModelSettingsSheet(
                provider = headerState.selectedProvider,
                temperature = headerState.temperature,
                topP = headerState.topP,
                thinkingEnabled = headerState.thinkingEnabled,
                thinkingLevel = headerState.thinkingLevel,
                responseEffort = headerState.responseEffort,
                onTemperatureChange = { viewModel.updateHeaderTemperature(it) },
                onTopPChange = { viewModel.updateHeaderTopP(it) },
                onThinkingEnabledChange = { viewModel.updateHeaderThinkingEnabled(it) },
                onThinkingLevelChange = { viewModel.updateHeaderThinkingLevel(it) },
                onResponseEffortChange = { viewModel.updateHeaderResponseEffort(it) },
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}

@Composable
private fun ConnectionStatusDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
        ConnectionState.ERROR -> MaterialTheme.colorScheme.error
        ConnectionState.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(8.dp)
            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
    )
}

@Composable
fun EmptyConversationView(
    onSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(com.hermes.client.R.drawable.hermax_logo),
            contentDescription = "HerMax",
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "HerMax",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your AI agent, ready to assist.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        data class SuggestionItem(
            val icon: androidx.compose.ui.graphics.vector.ImageVector,
            val text: String
        )

        val suggestions = listOf(
            SuggestionItem(Icons.Outlined.ChatBubbleOutline, "What can you help me with?"),
            SuggestionItem(Icons.Outlined.Code, "Analyze my project structure"),
            SuggestionItem(Icons.Outlined.Search, "Search the web for latest news"),
            SuggestionItem(Icons.Outlined.Description, "Help me write code")
        )

        suggestions.forEach { item ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { onSuggestionClick(item.text) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
            }
        }
    }
}
