package com.hermes.client.domain.repository

import com.hermes.client.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Interface for AI providers
 */
interface AIProvider {
    val providerType: AIProviderType
    
    /**
     * Get available models from this provider
     */
    suspend fun getModels(): Result<List<UnifiedModel>>
    
    /**
     * Send a non-streaming chat message
     */
    suspend fun sendMessage(
        request: ChatRequest
    ): Result<ChatResponse>
    
    /**
     * Stream chat responses
     */
    fun streamMessage(
        request: ChatRequest
    ): Flow<StreamingChatResponse>
    
    /**
     * Test connection to the provider
     */
    suspend fun testConnection(): ConnectionTestResult
    
    /**
     * Get model capabilities
     */
    suspend fun getModelCapabilities(modelId: String): Result<ModelCapabilities>
    
    /**
     * Update configuration
     */
    fun updateConfig(config: ProviderConfig)
}

/**
 * Chat request abstraction
 */
data class ChatRequest(
    val messages: List<ChatMessage>,
    val settings: ModelSettings,
    val conversationId: String? = null
)

/**
 * Chat message
 */
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val images: List<ImageContent> = emptyList()
)

enum class MessageRole {
    SYSTEM, USER, ASSISTANT, TOOL
}

/**
 * Image content for vision models
 */
data class ImageContent(
    val base64: String,
    val mimeType: String = "image/png"
)

/**
 * Chat response
 */
data class ChatResponse(
    val id: String,
    val content: String,
    val model: String,
    val usage: Usage? = null,
    val finishReason: String? = null
)

data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * Streaming chat response events
 */
sealed class StreamingChatResponse {
    data class ContentDelta(val content: String) : StreamingChatResponse()
    data class ThinkingDelta(val content: String) : StreamingChatResponse()
    object Completed : StreamingChatResponse()
    data class Error(val message: String) : StreamingChatResponse()
    data class ToolCallStarted(val id: String, val name: String, val input: String?) : StreamingChatResponse()
    data class ToolCallDelta(val id: String, val input: String?) : StreamingChatResponse()
    data class ToolCallCompleted(val id: String, val output: String?) : StreamingChatResponse()
}