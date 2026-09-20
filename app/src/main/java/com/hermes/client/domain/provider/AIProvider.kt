package com.hermes.client.domain.provider

import com.hermes.client.domain.model.*
import com.hermes.client.hermes.api.HermesEvent
import kotlinx.coroutines.flow.Flow

/**
 * Unified provider interface for communicating with AI models.
 * Implemented by Hermes, Google AI Studio, OpenRouter, Anthropic, OpenAI, Ollama, and NVIDIA.
 */
interface AIProvider {
    val providerType: AIProviderType

    /** Dynamically retrieve available models from the provider */
    suspend fun getModels(): Result<List<Model>>

    /** Stream messages directly to the provider via SSE */
    fun streamMessage(
        conversationId: String,
        message: String,
        model: String?,
        history: List<Message>,
        settings: ModelSettings? = null,
        attachments: List<Attachment> = emptyList()
    ): Flow<HermesEvent>

    /** Test connection and validate credentials */
    suspend fun testConnection(): Result<ConnectionTestResult>

    /** Get provider-level or model capabilities */
    fun getCapabilities(): ModelCapabilities
}
