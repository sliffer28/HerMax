package com.hermes.client.data.repository

import com.hermes.client.domain.model.*
import com.hermes.client.domain.repository.HermesRepository
import com.hermes.client.hermes.api.HermesClient
import com.hermes.client.hermes.api.HermesEvent
import com.hermes.client.hermes.models.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HermesRepositoryImpl @Inject constructor(
    private val client: HermesClient
) : HermesRepository {

    override suspend fun testConnection(): Result<ServerInfo> {
        return client.getServerInfo().map { dto ->
            ServerInfo(
                version = dto.version,
                apiVersion = dto.api,
                modelCount = dto.models,
                agentCount = dto.agents,
                capabilities = dto.capabilities?.let {
                    ServerCapabilities(
                        streaming = it.streaming,
                        models = it.models,
                        agents = it.agents,
                        tools = it.tools,
                        fileUploads = it.fileUploads,
                        tasks = it.tasks,
                        approvals = it.approvals,
                        runs = it.runs,
                        responses = it.responses
                    )
                } ?: ServerCapabilities()
            )
        }
    }

    override suspend fun getModels(): Result<List<Model>> {
        return client.getModels().map { dtos ->
            dtos.map { dto ->
                Model(
                    id = dto.id,
                    name = dto.id,
                    provider = dto.ownedBy
                )
            }
        }
    }

    override suspend fun getModelOptions(): Result<List<Model>> {
        return client.getModelOptions().map { response ->
            val models = mutableListOf<Model>()
            response.providers?.forEach { provider ->
                provider.models?.forEach { modelOption ->
                    models.add(
                        Model(
                            id = modelOption.id,
                            name = modelOption.name.ifBlank { modelOption.id },
                            provider = provider.name.ifBlank { provider.id },
                            contextWindow = modelOption.contextWindow,
                            capabilities = modelOption.capabilities ?: emptyList(),
                            isAvailable = modelOption.available
                        )
                    )
                }
            }
            // Also add top-level models if any
            response.models?.forEach { modelOption ->
                if (models.none { it.id == modelOption.id }) {
                    models.add(
                        Model(
                            id = modelOption.id,
                            name = modelOption.name.ifBlank { modelOption.id },
                            provider = modelOption.provider,
                            contextWindow = modelOption.contextWindow,
                            capabilities = modelOption.capabilities ?: emptyList(),
                            isAvailable = modelOption.available
                        )
                    )
                }
            }
            models
        }
    }

    override suspend fun sendMessage(
        conversationId: String,
        message: String,
        model: String?,
        provider: String?,
        sessionId: String?
    ): Result<Message> {
        val request = ChatCompletionRequestDto(
            model = model ?: "hermes-agent",
            messages = listOf(ChatMessageDto(role = "user", content = message)),
            stream = false,
            provider = provider
        )
        return client.chatCompletion(request).map { response ->
            val choice = response.choices.firstOrNull()
            Message(
                id = response.id,
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = choice?.message?.content ?: "",
                status = MessageStatus.COMPLETED,
                model = response.model
            )
        }
    }

    override fun streamMessage(
        conversationId: String,
        message: String,
        model: String?,
        provider: String?,
        sessionId: String?,
        history: List<Message>
    ): Flow<HermesEvent> {
        val messages = history.map { msg ->
            ChatMessageDto(
                role = when (msg.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "system"
                },
                content = msg.content
            )
        } + ChatMessageDto(role = "user", content = message)

        val request = ChatCompletionRequestDto(
            model = model ?: "hermes-agent",
            messages = messages,
            stream = true,
            provider = provider
        )
        return client.streamChatCompletion(request)
    }

    override suspend fun createRun(
        input: String,
        sessionId: String?,
        model: String?,
        provider: String?
    ): Result<Task> {
        val request = RunRequestDto(
            input = input,
            sessionId = sessionId,
            model = model,
            provider = provider
        )
        return client.createRun(request).map { response ->
            Task(
                id = response.id,
                name = input.take(30),
                status = TaskStatus.RUNNING,
                sessionId = sessionId
            )
        }
    }

    override fun streamRunEvents(runId: String): Flow<HermesEvent> {
        return client.streamRunEvents(runId)
    }

    override suspend fun cancelRun(runId: String): Result<Unit> {
        return client.cancelRun(runId)
    }

    override suspend fun approveToolCall(taskId: String, approvalId: String): Result<Unit> {
        return client.approveToolCall(taskId, approvalId)
    }

    override suspend fun rejectToolCall(taskId: String, approvalId: String): Result<Unit> {
        return client.rejectToolCall(taskId, approvalId)
    }

    override suspend fun uploadFile(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray
    ): Result<String> {
        return client.uploadFile(fileName, mimeType, fileBytes).map { it.id }
    }
}
