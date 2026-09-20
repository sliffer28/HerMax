package com.hermes.client.domain.repository

import com.hermes.client.domain.model.*
import com.hermes.client.hermes.api.HermesEvent
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level interface for Hermes server interactions.
 * UI and ViewModels depend only on this interface.
 */
interface HermesRepository {

    suspend fun testConnection(): Result<ServerInfo>

    suspend fun getModels(): Result<List<Model>>

    suspend fun getModelOptions(): Result<List<Model>>

    suspend fun sendMessage(
        conversationId: String,
        message: String,
        model: String?,
        provider: String?,
        sessionId: String?
    ): Result<Message>

    fun streamMessage(
        conversationId: String,
        message: String,
        model: String?,
        provider: String?,
        sessionId: String?,
        history: List<Message>
    ): Flow<HermesEvent>

    suspend fun createRun(
        input: String,
        sessionId: String?,
        model: String?,
        provider: String?
    ): Result<Task>

    fun streamRunEvents(runId: String): Flow<HermesEvent>

    suspend fun cancelRun(runId: String): Result<Unit>

    suspend fun approveToolCall(taskId: String, approvalId: String): Result<Unit>

    suspend fun rejectToolCall(taskId: String, approvalId: String): Result<Unit>

    suspend fun uploadFile(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray
    ): Result<String>
}

/**
 * Domain-level interface for local conversation persistence.
 */
interface ConversationRepository {

    fun getAllConversations(): Flow<List<Conversation>>

    fun searchConversations(query: String): Flow<List<Conversation>>

    suspend fun getConversation(id: String): Conversation?

    suspend fun createConversation(conversation: Conversation): Conversation

    suspend fun updateConversation(conversation: Conversation)

    suspend fun deleteConversation(id: String)

    fun getMessages(conversationId: String): Flow<List<Message>>

    suspend fun getMessage(id: String): Message?

    suspend fun insertMessage(message: Message): Message

    suspend fun updateMessage(message: Message)

    suspend fun deleteMessage(id: String)

    suspend fun clearAllConversations()

    fun searchMessages(query: String): Flow<List<Message>>
}

/**
 * Domain-level interface for server profile management.
 */
interface ServerProfileRepository {

    fun getAllProfiles(): Flow<List<ServerProfile>>

    suspend fun getActiveProfile(): ServerProfile?

    suspend fun createProfile(profile: ServerProfile): ServerProfile

    suspend fun updateProfile(profile: ServerProfile)

    suspend fun deleteProfile(id: String)

    suspend fun setActiveProfile(id: String)

    // ── Chat-header preferences (persisted in DataStore) ──────────────────

    val currentProviderId: Flow<String?>
    val currentModelId: Flow<String?>
    val currentTemperature: Flow<Float?>
    val currentTopP: Flow<Float?>
    val currentThinkingEnabled: Flow<Boolean?>
    val currentThinkingLevel: Flow<com.hermes.client.domain.model.ThinkingLevel?>
    val currentResponseEffort: Flow<com.hermes.client.domain.model.ResponseEffortLevel?>

    fun updateProviderId(id: String)
    fun updateModelId(id: String)
    fun updateTemperature(value: Float)
    fun updateTopP(value: Float)
    fun updateThinkingEnabled(enabled: Boolean)
    fun updateThinkingLevel(level: com.hermes.client.domain.model.ThinkingLevel)
    fun updateResponseEffort(effort: com.hermes.client.domain.model.ResponseEffortLevel?)
}
