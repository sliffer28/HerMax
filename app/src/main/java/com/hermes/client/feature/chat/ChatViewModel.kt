package com.hermes.client.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.client.domain.model.*
import com.hermes.client.domain.provider.ModelCapabilityHelper
import com.hermes.client.domain.repository.ConversationRepository
import com.hermes.client.domain.repository.HermesRepository
import com.hermes.client.domain.repository.ServerProfileRepository
import com.hermes.client.hermes.api.HermesEvent
import com.hermes.client.util.FileAttachmentHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PendingApproval(
    val taskId: String,
    val approvalId: String,
    val toolName: String,
    val description: String
)

data class ChatHeaderState(
    val selectedProvider: AIProviderType? = null,
    val selectedModelId: String? = null,
    val selectedModel: UnifiedModel? = null,
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val thinkingEnabled: Boolean = false,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    val responseEffort: ResponseEffortLevel? = null
)

data class ChatUiState(
    val currentConversationId: String? = null,
    val conversationTitle: String = "New Chat",
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val isThinking: Boolean = false,
    val streamingContent: String = "",
    val statusMessage: String? = null,
    val error: String? = null,
    val connectionState: ConnectionState = ConnectionState.UNKNOWN,
    val activeToolCalls: List<ToolCall> = emptyList(),
    val pendingApprovals: List<PendingApproval> = emptyList(),
    val selectedModel: String? = null  // derived from headerState.selectedModelId
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val hermesRepository: HermesRepository,
    private val conversationRepository: ConversationRepository,
    private val serverProfileRepository: ServerProfileRepository,
    private val aiProviderRegistry: com.hermes.client.data.provider.AIProviderRegistry,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _headerState = MutableStateFlow(ChatHeaderState())
    val headerState: StateFlow<ChatHeaderState> = _headerState.asStateFlow()

    private var streamingJob: Job? = null
    private var messagesJob: Job? = null
    private var currentConversation: Conversation? = null

    // Track if the current chat is a new, unsaved one
    private val _isNewConversation = MutableStateFlow(false)

    // Pending attachments ready to be sent with the next message
    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()

    init {
        loadHeaderState()
        checkConnection()
    }

    private fun loadHeaderState() {
        viewModelScope.launch {
            serverProfileRepository.currentProviderId.collect { providerId ->
                if (providerId != null) {
                    providerId.toProviderType()?.let { providerType ->
                        _headerState.update { it.copy(selectedProvider = providerType) }
                        checkConnection()
                    }
                }
            }
        }
        viewModelScope.launch {
            serverProfileRepository.currentModelId.collect { modelId ->
                _headerState.update { it.copy(selectedModelId = modelId) }
                _uiState.update { it.copy(selectedModel = modelId) }
            }
        }
        viewModelScope.launch {
            serverProfileRepository.currentTemperature.collect { temp ->
                _headerState.update { it.copy(temperature = temp ?: 0.7f) }
            }
        }
        viewModelScope.launch {
            serverProfileRepository.currentTopP.collect { topP ->
                _headerState.update { it.copy(topP = topP ?: 1.0f) }
            }
        }
        viewModelScope.launch {
            serverProfileRepository.currentThinkingEnabled.collect { thinkingEnabled ->
                _headerState.update { it.copy(thinkingEnabled = thinkingEnabled ?: false) }
            }
        }
        viewModelScope.launch {
            serverProfileRepository.currentThinkingLevel.collect { level ->
                _headerState.update { it.copy(thinkingLevel = level ?: ThinkingLevel.OFF) }
            }
        }
        viewModelScope.launch {
            serverProfileRepository.currentResponseEffort.collect { effort ->
                _headerState.update { it.copy(responseEffort = effort) }
            }
        }
    }

    private fun String.toProviderType(): AIProviderType? {
        return try {
            AIProviderType.valueOf(this)
        } catch (e: Exception) {
            AIProviderType.entries.firstOrNull {
                it.displayName.equals(this, ignoreCase = true) ||
                it.name.equals(this, ignoreCase = true)
            }
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            try {
                val providerType = headerState.value.selectedProvider ?: AIProviderType.HERMES_AGENT
                val provider = aiProviderRegistry.getProvider(providerType)
                provider.testConnection()
                    .onSuccess { result ->
                        when (result) {
                            is ConnectionTestResult.Success -> {
                                _uiState.update { it.copy(connectionState = ConnectionState.CONNECTED) }
                            }
                            is ConnectionTestResult.Failure -> {
                                _uiState.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }
                            }
                        }
                    }
                    .onFailure {
                        _uiState.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }
                    }
            } catch (_: Exception) {
                _uiState.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }
            }
        }
    }

    private fun observeMessages(conversationId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            conversationRepository.getMessages(conversationId).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            val conversation = conversationRepository.getConversation(conversationId)
            if (conversation != null) {
                currentConversation = conversation
                _uiState.update {
                    it.copy(
                        currentConversationId = conversation.id,
                        conversationTitle = conversation.title
                    )
                }
                _isNewConversation.value = false
                observeMessages(conversationId)
            }
        }
    }

    fun createNewConversation() {
        messagesJob?.cancel()
        val draftId = UUID.randomUUID().toString()
        val draftConversation = Conversation(
            id = draftId,
            title = "New Chat",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        currentConversation = draftConversation
        _messages.value = emptyList()
        _pendingAttachments.value = emptyList()
        _isNewConversation.value = true
        _uiState.update {
            ChatUiState(
                currentConversationId = draftId,
                conversationTitle = "New Chat",
                connectionState = it.connectionState,
                selectedModel = it.selectedModel
            )
        }
        observeMessages(draftId)
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage(text: String, attachmentsOverride: List<Attachment>? = null) {
        val attachmentsToSend = attachmentsOverride ?: _pendingAttachments.value
        val actualText = text.trim()
        if (actualText.isBlank() && attachmentsToSend.isEmpty()) return

        val providerType = headerState.value.selectedProvider ?: AIProviderType.HERMES_AGENT
        val selectedModel = headerState.value.selectedModelId

        // Vision capability check
        val hasImages = attachmentsToSend.any { FileAttachmentHelper.isImageMime(it.mimeType) }
        if (hasImages && !ModelCapabilityHelper.isVisionSupported(providerType, selectedModel)) {
            _uiState.update {
                it.copy(error = ModelCapabilityHelper.getUnsupportedVisionMessage(providerType, selectedModel))
            }
            return
        }

        val promptText = if (actualText.isNotBlank()) actualText else "Please analyze the attached file(s)."
        val convId = currentConversation?.id ?: UUID.randomUUID().toString()

        viewModelScope.launch {
            // Only persist conversation to Room when the first message is sent
            if (_isNewConversation.value) {
                val title = promptText.take(40) + if (promptText.length > 40) "..." else ""
                val newConversation = (currentConversation ?: Conversation(id = convId)).copy(
                    id = convId,
                    title = title,
                    lastMessage = promptText,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                conversationRepository.createConversation(newConversation)
                currentConversation = newConversation
                _isNewConversation.value = false
                _uiState.update {
                    it.copy(
                        currentConversationId = convId,
                        conversationTitle = newConversation.title
                    )
                }
                observeMessages(convId)
            }

            _uiState.update { it.copy(inputText = "", error = null) }
            if (attachmentsOverride == null) {
                _pendingAttachments.value = emptyList()
            }

            // Process attachments
            val processedAttachments = mutableListOf<Attachment>()
            val fileUploadNotes = mutableListOf<String>()

            for (att in attachmentsToSend) {
                val fileBytes = FileAttachmentHelper.readFileBytes(context, att)

                if (providerType == AIProviderType.HERMES_AGENT && fileBytes != null) {
                    val uploadResult = hermesRepository.uploadFile(att.fileName, att.mimeType, fileBytes)
                    if (uploadResult.isSuccess) {
                        val uploadedId = uploadResult.getOrNull() ?: att.id
                        processedAttachments.add(att.copy(id = uploadedId, isUploaded = true))
                        fileUploadNotes.add("[Attached file: ${att.fileName} (ID: $uploadedId)]")
                    } else {
                        processedAttachments.add(att)
                        fileUploadNotes.add("[Attached file: ${att.fileName}]")
                    }
                } else if (FileAttachmentHelper.isTextMime(att.mimeType, att.fileName)) {
                    val textContent = FileAttachmentHelper.readTextContent(context, att)
                    if (!textContent.isNullOrBlank()) {
                        fileUploadNotes.add("--- File: ${att.fileName} ---\n$textContent\n--- End of ${att.fileName} ---")
                    }
                    processedAttachments.add(att)
                } else {
                    // Images and binary files (e.g. PDFs) remain in processedAttachments for native multimodal sending
                    processedAttachments.add(att)
                }
            }

            val messageContentWithFiles = if (fileUploadNotes.isNotEmpty()) {
                "$promptText\n\n${fileUploadNotes.joinToString("\n\n")}"
            } else {
                promptText
            }

            val userMessage = Message(
                conversationId = convId,
                role = MessageRole.USER,
                content = promptText,
                attachments = processedAttachments,
                status = MessageStatus.SENT
            )
            conversationRepository.insertMessage(userMessage)

            currentConversation?.let { conv ->
                val updated = conv.copy(
                    updatedAt = System.currentTimeMillis(),
                    lastMessage = promptText
                )
                conversationRepository.updateConversation(updated)
                currentConversation = updated
            }

            startStreaming(convId, messageContentWithFiles, processedAttachments)
        }
    }

    private fun startStreaming(
        conversationId: String,
        message: String,
        attachments: List<Attachment> = emptyList()
    ) {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStreaming = true,
                    isThinking = true,
                    streamingContent = "",
                    statusMessage = "Thinking...",
                    activeToolCalls = emptyList(),
                    pendingApprovals = emptyList()
                )
            }

            val currentMessages = _messages.value.filter { it.id != "streaming" }
            val providerType = headerState.value.selectedProvider ?: AIProviderType.HERMES_AGENT
            val provider = aiProviderRegistry.getProvider(providerType)

            val modelSettings = ModelSettings(
                provider = providerType,
                modelId = headerState.value.selectedModelId ?: "",
                providerModelId = headerState.value.selectedModelId ?: "",
                thinkingEnabled = headerState.value.thinkingEnabled,
                thinkingLevel = headerState.value.thinkingLevel,
                responseEffort = headerState.value.responseEffort,
                temperature = headerState.value.temperature.toDouble()
            )

            provider.streamMessage(
                conversationId = conversationId,
                message = message,
                model = headerState.value.selectedModelId,
                history = currentMessages,
                settings = modelSettings,
                attachments = attachments
            ).catch { e ->
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        isThinking = false,
                        statusMessage = null,
                        error = e.message ?: "Stream connection failed"
                    )
                }
            }.collect { event ->
                when (event) {
                    is HermesEvent.Connected -> {
                        _uiState.update {
                            it.copy(connectionState = ConnectionState.CONNECTED)
                        }
                    }
                    is HermesEvent.TextDelta -> {
                        _uiState.update {
                            it.copy(
                                isThinking = false,
                                streamingContent = it.streamingContent + event.text,
                                statusMessage = null
                            )
                        }
                    }
                    is HermesEvent.ThinkingStarted -> {
                        _uiState.update {
                            it.copy(isThinking = true, statusMessage = "Thinking...")
                        }
                    }
                    is HermesEvent.ThinkingDelta -> {
                        _uiState.update {
                            it.copy(isThinking = true, statusMessage = "Thinking...")
                        }
                    }
                    is HermesEvent.ToolStarted -> {
                        val toolCall = ToolCall(
                            id = event.taskId,
                            name = event.toolName,
                            input = event.toolInput,
                            status = ToolCallStatus.RUNNING
                        )
                        _uiState.update {
                            it.copy(
                                activeToolCalls = it.activeToolCalls + toolCall,
                                statusMessage = "Running ${event.toolName}...",
                                isThinking = false
                            )
                        }
                    }
                    is HermesEvent.ToolFinished -> {
                        _uiState.update { state ->
                            state.copy(
                                activeToolCalls = state.activeToolCalls.map { tc ->
                                    if (tc.id == event.taskId) {
                                        tc.copy(
                                            status = ToolCallStatus.COMPLETED,
                                            output = event.summary
                                        )
                                    } else tc
                                },
                                statusMessage = null
                            )
                        }
                    }
                    is HermesEvent.ApprovalRequired -> {
                        val approval = PendingApproval(
                            taskId = event.taskId,
                            approvalId = event.approvalId,
                            toolName = event.toolName,
                            description = event.description
                        )
                        _uiState.update {
                            it.copy(
                                pendingApprovals = it.pendingApprovals + approval,
                                statusMessage = "Waiting for approval...",
                                isThinking = false
                            )
                        }
                    }
                    is HermesEvent.StatusUpdate -> {
                        _uiState.update {
                            it.copy(
                                statusMessage = event.status,
                                isThinking = event.status.contains("think", ignoreCase = true)
                            )
                        }
                    }
                    is HermesEvent.Completed -> {
                        val content = _uiState.value.streamingContent
                        if (content.isNotBlank()) {
                            val assistantMessage = Message(
                                id = event.messageId,
                                conversationId = conversationId,
                                role = MessageRole.ASSISTANT,
                                content = content,
                                status = MessageStatus.COMPLETED,
                                model = headerState.value.selectedModelId,
                                toolCalls = _uiState.value.activeToolCalls
                            )
                            conversationRepository.insertMessage(assistantMessage)
                        }

                        _uiState.update {
                            it.copy(
                                isStreaming = false,
                                isThinking = false,
                                streamingContent = "",
                                statusMessage = null,
                                activeToolCalls = emptyList()
                            )
                        }
                    }
                    is HermesEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isStreaming = false,
                                isThinking = false,
                                statusMessage = null,
                                error = event.message
                            )
                        }
                    }
                    is HermesEvent.Disconnected -> {
                        if (_uiState.value.isStreaming) {
                            val content = _uiState.value.streamingContent
                            if (content.isNotBlank()) {
                                val assistantMessage = Message(
                                    conversationId = conversationId,
                                    role = MessageRole.ASSISTANT,
                                    content = content,
                                    status = MessageStatus.COMPLETED,
                                    model = headerState.value.selectedModelId
                                )
                                conversationRepository.insertMessage(assistantMessage)
                            }
                        }
                        _uiState.update {
                            it.copy(
                                isStreaming = false,
                                isThinking = false,
                                streamingContent = "",
                                statusMessage = null
                            )
                        }
                    }
                    is HermesEvent.TaskProgress -> {
                        _uiState.update {
                            it.copy(statusMessage = event.status)
                        }
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        streamingJob?.cancel()
        val content = _uiState.value.streamingContent
        viewModelScope.launch {
            if (content.isNotBlank()) {
                val conversationId = _uiState.value.currentConversationId ?: return@launch
                val assistantMessage = Message(
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT,
                    content = content,
                    status = MessageStatus.COMPLETED,
                    model = headerState.value.selectedModelId
                )
                conversationRepository.insertMessage(assistantMessage)
            }
        }
        _uiState.update {
            it.copy(
                isStreaming = false,
                isThinking = false,
                streamingContent = "",
                statusMessage = null
            )
        }
    }

    fun retryMessage(message: Message) {
        if (message.role == MessageRole.USER) {
            sendMessage(message.content, message.attachments)
        }
    }

    fun retryLastMessage() {
        val lastUserMessage = _messages.value.lastOrNull { it.role == MessageRole.USER }
        if (lastUserMessage != null) {
            sendMessage(lastUserMessage.content, lastUserMessage.attachments)
        }
    }

    fun copyMessage(message: Message) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Hermes Message", message.content)
        clipboard.setPrimaryClip(clip)
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            conversationRepository.deleteMessage(message.id)
        }
    }

    fun editMessage(message: Message, newContent: String) {
        viewModelScope.launch {
            conversationRepository.updateMessage(message.copy(content = newContent))
            sendMessage(newContent)
        }
    }

    fun approveToolCall(approval: PendingApproval) {
        viewModelScope.launch {
            hermesRepository.approveToolCall(approval.taskId, approval.approvalId)
            _uiState.update {
                it.copy(
                    pendingApprovals = it.pendingApprovals.filter { a ->
                        a.approvalId != approval.approvalId
                    }
                )
            }
        }
    }

    fun rejectToolCall(approval: PendingApproval) {
        viewModelScope.launch {
            hermesRepository.rejectToolCall(approval.taskId, approval.approvalId)
            _uiState.update {
                it.copy(
                    pendingApprovals = it.pendingApprovals.filter { a ->
                        a.approvalId != approval.approvalId
                    }
                )
            }
        }
    }

    fun toggleToolCallExpand(toolCallId: String) {
        _uiState.update { state ->
            state.copy(
                activeToolCalls = state.activeToolCalls.map { tc ->
                    if (tc.id == toolCallId) tc.copy(isExpanded = !tc.isExpanded) else tc
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // Header state functions
    fun updateHeaderProvider(provider: AIProviderType) {
        _headerState.update { it.copy(selectedProvider = provider) }
        serverProfileRepository.updateProviderId(provider.name)
    }

    fun updateHeaderModel(modelId: String) {
        _headerState.update { it.copy(selectedModelId = modelId) }
        serverProfileRepository.updateModelId(modelId)
    }

    fun updateHeaderTemperature(temperature: Float) {
        _headerState.update { it.copy(temperature = temperature) }
        serverProfileRepository.updateTemperature(temperature)
    }

    fun updateHeaderTopP(topP: Float) {
        _headerState.update { it.copy(topP = topP) }
        serverProfileRepository.updateTopP(topP)
    }

    fun updateHeaderThinkingEnabled(enabled: Boolean) {
        _headerState.update { it.copy(thinkingEnabled = enabled) }
        serverProfileRepository.updateThinkingEnabled(enabled)
    }

    fun updateHeaderThinkingLevel(level: ThinkingLevel) {
        _headerState.update { it.copy(thinkingLevel = level) }
        serverProfileRepository.updateThinkingLevel(level)
    }

    fun updateHeaderResponseEffort(effort: ResponseEffortLevel?) {
        _headerState.update { it.copy(responseEffort = effort) }
        serverProfileRepository.updateResponseEffort(effort)
    }

    fun setModel(modelId: String, provider: String?) {
        updateHeaderModel(modelId)
        _uiState.update { it.copy(selectedModel = modelId) }
        provider?.let { providerType ->
            updateHeaderProvider(providerType.toProviderType() ?: AIProviderType.HERMES_AGENT)
        }
        currentConversation?.let { conv ->
            viewModelScope.launch {
                conversationRepository.updateConversation(
                    conv.copy(selectedModel = modelId, selectedProvider = provider)
                )
            }
        }
    }

    fun addAttachment(attachment: Attachment) {
        _pendingAttachments.update { it + attachment }
    }

    fun addAttachments(attachments: List<Attachment>) {
        _pendingAttachments.update { it + attachments }
    }

    fun removeAttachment(attachmentId: String) {
        _pendingAttachments.update { it.filter { att -> att.id != attachmentId } }
    }

    fun clearAttachments() {
        _pendingAttachments.value = emptyList()
    }

    fun appendVoiceText(text: String) {
        val current = _uiState.value.inputText.trim()
        val updated = if (current.isBlank()) text else "$current $text"
        _uiState.update { it.copy(inputText = updated) }
    }

    fun setErrorMessage(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
    }
}