package com.hermes.client.data.provider

import com.hermes.client.domain.model.*
import com.hermes.client.domain.provider.AIProvider
import com.hermes.client.domain.repository.HermesRepository
import com.hermes.client.hermes.api.HermesEvent
import kotlinx.coroutines.flow.Flow

/**
 * Hermes Agent provider communicating with the local PC Hermes server.
 */
class HermesProvider(
    private val hermesRepository: HermesRepository
) : AIProvider {

    override val providerType: AIProviderType = AIProviderType.HERMES_AGENT

    override suspend fun getModels(): Result<List<Model>> {
        return hermesRepository.getModelOptions().fold(
            onSuccess = { Result.success(it) },
            onFailure = { hermesRepository.getModels() }
        )
    }

    override fun streamMessage(
        conversationId: String,
        message: String,
        model: String?,
        history: List<Message>,
        settings: ModelSettings?,
        attachments: List<Attachment>
    ): Flow<HermesEvent> {
        return hermesRepository.streamMessage(
            conversationId = conversationId,
            message = message,
            model = model,
            provider = null,
            sessionId = null,
            history = history
        )
    }

    override suspend fun testConnection(): Result<ConnectionTestResult> {
        return hermesRepository.testConnection().fold(
            onSuccess = { info ->
                Result.success(ConnectionTestResult.Success(modelsFound = info.modelCount))
            },
            onFailure = { error ->
                Result.success(
                    ConnectionTestResult.Failure(
                        message = "Cannot connect to Hermes server. Ensure Hermes is running on your PC: ${error.message}"
                    )
                )
            }
        )
    }

    override fun getCapabilities(): ModelCapabilities = ModelCapabilities(
        streaming = true,
        reasoning = false,
        vision = true,
        tools = true
    )
}
