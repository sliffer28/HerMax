package com.hermes.client.hermes.api

import android.util.Log
import com.hermes.client.data.security.SecureStorage
import com.hermes.client.hermes.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

/**
 * REST/SSE implementation of [HermesClient] that talks to the real Hermes API.
 *
 * Endpoints used:
 *   POST /v1/chat/completions        — OpenAI-compatible chat (streaming via SSE)
 *   POST /v1/responses               — Server-side conversation state
 *   GET  /v1/responses/{id}          — Retrieve response
 *   DELETE /v1/responses/{id}        — Delete response
 *   GET  /v1/models                  — Basic model discovery
 *   GET  /api/model/options          — Hermes model/provider inventory
 *   POST /v1/runs                    — Long-running agent operations
 */
@Singleton
class RestHermesClient @Inject constructor(
    private val apiService: HermesApiService,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val secureStorage: SecureStorage
) : HermesClient {

    companion object {
        private const val TAG = "RestHermesClient"
    }

    private var connected = false

    private fun getBaseUrl(): String {
        return secureStorage.getServerUrl() ?: "http://192.168.0.197:8642"
    }

    override suspend fun getServerInfo(): Result<ServerInfoDto> = runCatching {
        withContext(Dispatchers.IO) {
            // Try to hit the models endpoint to verify connectivity
            val modelsResponse = apiService.getModels()
            if (modelsResponse.isSuccessful) {
                connected = true
                val modelsData = modelsResponse.body()
                ServerInfoDto(
                    version = "Hermes",
                    api = "v1",
                    models = modelsData?.data?.size ?: 0,
                    agents = 0,
                    capabilities = ServerCapabilitiesDto()
                )
            } else {
                connected = false
                throw Exception("Server returned ${modelsResponse.code()}: ${modelsResponse.message()}")
            }
        }
    }

    override suspend fun getModels(): Result<List<ModelDto>> = runCatching {
        withContext(Dispatchers.IO) {
            val response = apiService.getModels()
            if (response.isSuccessful) {
                response.body()?.data ?: emptyList()
            } else {
                throw Exception("Failed to fetch models: ${response.code()} ${response.message()}")
            }
        }
    }

    override suspend fun getModelOptions(): Result<ModelOptionsResponseDto> = runCatching {
        withContext(Dispatchers.IO) {
            val response = apiService.getModelOptions()
            if (response.isSuccessful) {
                response.body() ?: ModelOptionsResponseDto()
            } else {
                throw Exception("Failed to fetch model options: ${response.code()} ${response.message()}")
            }
        }
    }

    override suspend fun chatCompletion(request: ChatCompletionRequestDto): Result<ChatCompletionResponseDto> =
        runCatching {
            withContext(Dispatchers.IO) {
                val sessionId = secureStorage.getCurrentSessionId()
                val response = apiService.chatCompletion(
                    request = request.copy(stream = false),
                    sessionId = sessionId
                )
                if (response.isSuccessful) {
                    response.body() ?: throw Exception("Empty response body")
                } else {
                    throw Exception("Chat completion failed: ${response.code()} ${response.message()}")
                }
            }
        }

    override fun streamChatCompletion(request: ChatCompletionRequestDto): Flow<HermesEvent> =
        callbackFlow {
            val baseUrl = getBaseUrl()
            val url = "${baseUrl}/v1/chat/completions"

            val streamRequest = request.copy(stream = true)
            val requestBody = json.encodeToString(ChatCompletionRequestDto.serializer(), streamRequest)

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .apply {
                    val apiKey = secureStorage.getApiKey()
                    if (!apiKey.isNullOrBlank()) {
                        addHeader("Authorization", "Bearer $apiKey")
                    }
                    val sessionId = secureStorage.getCurrentSessionId()
                    if (!sessionId.isNullOrBlank()) {
                        addHeader("X-Hermes-Session-Id", sessionId)
                    }
                }
                .build()

            val messageId = "msg_${System.currentTimeMillis()}"

            val listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    trySend(HermesEvent.Connected)
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    if (data == "[DONE]") {
                        trySend(HermesEvent.Completed(messageId))
                        return
                    }

                    try {
                        val chunk = json.decodeFromString(StreamChunkDto.serializer(), data)
                        val choice = chunk.choices.firstOrNull()

                        when {
                            // Tool-related events (Hermes custom extensions)
                            type == "tool_start" || type == "tool_started" -> {
                                trySend(
                                    HermesEvent.ToolStarted(
                                        taskId = chunk.id,
                                        toolName = choice?.delta?.content ?: "tool"
                                    )
                                )
                            }
                            type == "tool_end" || type == "tool_finished" -> {
                                trySend(
                                    HermesEvent.ToolFinished(
                                        taskId = chunk.id,
                                        toolName = "",
                                        summary = choice?.delta?.content
                                    )
                                )
                            }
                            type == "approval_required" -> {
                                trySend(
                                    HermesEvent.ApprovalRequired(
                                        taskId = chunk.id,
                                        approvalId = id ?: "",
                                        toolName = "",
                                        description = choice?.delta?.content ?: ""
                                    )
                                )
                            }
                            type == "status" -> {
                                trySend(
                                    HermesEvent.StatusUpdate(
                                        status = choice?.delta?.content ?: "",
                                        detail = null
                                    )
                                )
                            }
                            // Standard text delta
                            choice?.delta?.content != null -> {
                                trySend(
                                    HermesEvent.TextDelta(
                                        messageId = messageId,
                                        text = choice.delta.content
                                    )
                                )
                            }
                            // Finish reason
                            choice?.finishReason != null -> {
                                trySend(
                                    HermesEvent.Completed(
                                        messageId = messageId,
                                        finishReason = choice.finishReason
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse SSE event: $data", e)
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    trySend(HermesEvent.Disconnected)
                    channel.close()
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    val errorMsg = t?.message ?: response?.message ?: "Stream connection failed"
                    trySend(HermesEvent.Error(errorMsg))
                    channel.close(t)
                }
            }

            val factory = EventSources.createFactory(okHttpClient)
            val eventSource = factory.newEventSource(httpRequest, listener)

            awaitClose {
                eventSource.cancel()
            }
        }

    override suspend fun createResponse(request: ResponseRequestDto): Result<ResponseDto> =
        runCatching {
            withContext(Dispatchers.IO) {
                val sessionId = secureStorage.getCurrentSessionId()
                val response = apiService.createResponse(request, sessionId)
                if (response.isSuccessful) {
                    response.body() ?: throw Exception("Empty response")
                } else {
                    throw Exception("Create response failed: ${response.code()} ${response.message()}")
                }
            }
        }

    override suspend fun getResponse(responseId: String): Result<ResponseDto> = runCatching {
        withContext(Dispatchers.IO) {
            val response = apiService.getResponse(responseId)
            if (response.isSuccessful) {
                response.body() ?: throw Exception("Empty response")
            } else {
                throw Exception("Get response failed: ${response.code()} ${response.message()}")
            }
        }
    }

    override suspend fun deleteResponse(responseId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val response = apiService.deleteResponse(responseId)
            if (!response.isSuccessful) {
                throw Exception("Delete response failed: ${response.code()} ${response.message()}")
            }
        }
    }

    override suspend fun createRun(request: RunRequestDto): Result<RunResponseDto> = runCatching {
        withContext(Dispatchers.IO) {
            val sessionId = secureStorage.getCurrentSessionId()
            val response = apiService.createRun(request, sessionId)
            if (response.isSuccessful) {
                response.body() ?: throw Exception("Empty response")
            } else {
                throw Exception("Create run failed: ${response.code()} ${response.message()}")
            }
        }
    }

    override fun streamRunEvents(runId: String): Flow<HermesEvent> = callbackFlow {
        // Subscribe to run events via SSE
        val baseUrl = getBaseUrl()
        val url = "${baseUrl}/v1/runs/$runId/events"

        val request = Request.Builder()
            .url(url)
            .get()
            .apply {
                val apiKey = secureStorage.getApiKey()
                if (!apiKey.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                try {
                    when (type) {
                        "progress" -> trySend(
                            HermesEvent.TaskProgress(
                                runId = runId,
                                progress = null,
                                status = data
                            )
                        )
                        "completed" -> {
                            trySend(HermesEvent.Completed(runId))
                            channel.close()
                        }
                        "error" -> {
                            trySend(HermesEvent.Error(data))
                            channel.close()
                        }
                        else -> {
                            trySend(HermesEvent.StatusUpdate(status = data))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse run event", e)
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                trySend(HermesEvent.Error(t?.message ?: "Run event stream failed"))
                channel.close(t)
            }

            override fun onClosed(eventSource: EventSource) {
                channel.close()
            }
        }

        val factory = EventSources.createFactory(okHttpClient)
        val eventSource = factory.newEventSource(request, listener)

        awaitClose { eventSource.cancel() }
    }

    override suspend fun cancelRun(runId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val response = apiService.cancelRun(runId)
            if (!response.isSuccessful) {
                throw Exception("Cancel run failed: ${response.code()} ${response.message()}")
            }
        }
    }

    override suspend fun uploadFile(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray
    ): Result<FileUploadResponseDto> = runCatching {
        withContext(Dispatchers.IO) {
            val filePart = MultipartBody.Part.createFormData(
                "file",
                fileName,
                fileBytes.toRequestBody(mimeType.toMediaType())
            )
            val purposePart = "chat".toRequestBody("text/plain".toMediaType())
            val response = apiService.uploadFile(filePart, purposePart)
            if (response.isSuccessful) {
                response.body() ?: throw Exception("Empty response")
            } else {
                throw Exception("Upload failed: ${response.code()} ${response.message()}")
            }
        }
    }

    override suspend fun approveToolCall(taskId: String, approvalId: String): Result<Unit> =
        runCatching {
            withContext(Dispatchers.IO) {
                val response = apiService.approveToolCall(taskId, approvalId)
                if (!response.isSuccessful) {
                    throw Exception("Approve failed: ${response.code()}")
                }
            }
        }

    override suspend fun rejectToolCall(taskId: String, approvalId: String): Result<Unit> =
        runCatching {
            withContext(Dispatchers.IO) {
                val response = apiService.rejectToolCall(taskId, approvalId)
                if (!response.isSuccessful) {
                    throw Exception("Reject failed: ${response.code()}")
                }
            }
        }

    override fun isConnected(): Boolean = connected

    override fun updateBaseUrl(url: String) {
        secureStorage.setServerUrl(url)
        connected = false
    }
}
