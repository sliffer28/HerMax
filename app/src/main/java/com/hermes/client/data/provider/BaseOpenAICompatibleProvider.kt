package com.hermes.client.data.provider

import com.hermes.client.domain.model.*
import com.hermes.client.domain.provider.AIProvider
import com.hermes.client.hermes.api.HermesEvent
import com.hermes.client.util.FileAttachmentHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException

/**
 * Base provider implementation for OpenAI-compatible APIs (OpenAI, OpenRouter, Ollama, NVIDIA).
 */
abstract class BaseOpenAICompatibleProvider(
    protected val okHttpClient: OkHttpClient,
    protected val json: Json
) : AIProvider {

    abstract fun getBaseUrl(): String
    abstract fun getAuthHeaders(): Map<String, String>

    override suspend fun getModels(): Result<List<Model>> = runCatching {
        withContext(Dispatchers.IO) {
            val url = "${getBaseUrl().trimEnd('/')}/models"
            val requestBuilder = Request.Builder().url(url).get()
            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                throw IOException("Failed to fetch models (${response.code}): $errorBody")
            }

            val bodyString = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(bodyString).jsonObject
            val dataArray = root["data"]?.jsonArray ?: JsonArray(emptyList())

            dataArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: id
                val contextWindow = obj["context_length"]?.jsonPrimitive?.intOrNull
                    ?: obj["context_window"]?.jsonPrimitive?.intOrNull

                Model(
                    id = id,
                    name = name,
                    provider = providerType.displayName,
                    contextWindow = contextWindow,
                    isAvailable = true
                )
            }.sortedBy { it.name }
        }
    }

    override fun streamMessage(
        conversationId: String,
        message: String,
        model: String?,
        history: List<Message>,
        settings: ModelSettings?,
        attachments: List<Attachment>
    ): Flow<HermesEvent> = callbackFlow {
        val selectedModel = model ?: getDefaultModelId()
        val url = "${getBaseUrl().trimEnd('/')}/chat/completions"

        val messagesArray = buildJsonArray {
            // Optional system prompt
            settings?.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
                add(buildJsonObject {
                    put("role", "system")
                    put("content", prompt)
                })
            }
            // Conversation history
            history.filter { it.id != "streaming" && it.status != MessageStatus.ERROR }.forEach { msg ->
                add(buildJsonObject {
                    put("role", when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.SYSTEM -> "system"
                    })
                    val contentElement = if (msg.role == MessageRole.USER && msg.attachments.isNotEmpty()) {
                        buildMessageContent(msg.content, msg.attachments)
                    } else {
                        JsonPrimitive(msg.content)
                    }
                    put("content", contentElement)
                })
            }
            // Latest user message
            add(buildJsonObject {
                put("role", "user")
                put("content", buildMessageContent(message, attachments))
            })
        }

        val requestBodyJson = buildJsonObject {
            put("model", selectedModel)
            put("messages", messagesArray)
            put("stream", true)
            settings?.temperature?.let { put("temperature", it) }
            // Response effort if supported
            settings?.responseEffort?.let { effort ->
                put("reasoning_effort", effort.apiValue ?: "medium")
            }
        }

        val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)

        getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        val messageId = "msg_${System.currentTimeMillis()}"

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                trySend(HermesEvent.Connected)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    trySend(HermesEvent.Completed(messageId))
                    return
                }

                try {
                    val root = json.parseToJsonElement(data).jsonObject
                    val choices = root["choices"]?.jsonArray
                    val firstChoice = choices?.firstOrNull()?.jsonObject
                    val delta = firstChoice?.get("delta")?.jsonObject

                    // DeepSeek/Qwen reasoning delta
                    val reasoningContent = delta?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
                        ?: delta?.get("reasoning")?.jsonPrimitive?.contentOrNull
                    if (!reasoningContent.isNullOrEmpty()) {
                        trySend(HermesEvent.ThinkingDelta(messageId, reasoningContent))
                    }

                    // Text content delta
                    val content = delta?.get("content")?.jsonPrimitive?.contentOrNull
                    if (!content.isNullOrEmpty()) {
                        trySend(HermesEvent.TextDelta(messageId, content))
                    }

                    val finishReason = firstChoice?.get("finish_reason")?.jsonPrimitive?.contentOrNull
                    if (finishReason != null && finishReason != "null" && finishReason.isNotBlank()) {
                        trySend(HermesEvent.Completed(messageId, finishReason))
                    }
                } catch (e: Exception) {
                    // Ignore transient malformed SSE line
                }
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(HermesEvent.Completed(messageId))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorMsg = when {
                    response?.code == 401 || response?.code == 403 ->
                        "Authentication failed: Invalid API key for ${providerType.displayName}."
                    response?.code == 429 ->
                        "Rate limit or quota exceeded for ${providerType.displayName}."
                    response != null ->
                        "Provider error (${response.code}): ${response.message}"
                    t != null ->
                        t.message ?: "Connection failed"
                    else ->
                        "Unknown connection error"
                }
                trySend(HermesEvent.Error(errorMsg, code = response?.code?.toString()))
                close(t ?: IOException(errorMsg))
            }
        }

        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(requestBuilder.build(), listener)
        awaitClose { eventSource.cancel() }
    }

    override suspend fun testConnection(): Result<ConnectionTestResult> = runCatching {
        withContext(Dispatchers.IO) {
            val url = "${getBaseUrl().trimEnd('/')}/models"
            val requestBuilder = Request.Builder().url(url).get()
            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(bodyString).jsonObject
                val count = root["data"]?.jsonArray?.size ?: 0
                ConnectionTestResult.Success(modelsFound = count)
            } else if (response.code == 401 || response.code == 403) {
                ConnectionTestResult.Failure(
                    message = "Authentication failed (HTTP ${response.code}). Please check your API key.",
                    isAuthError = true
                )
            } else {
                ConnectionTestResult.Failure(
                    message = "Server returned HTTP ${response.code}: ${response.message}"
                )
            }
        }
    }

    protected open fun buildMessageContent(
        msgText: String,
        msgAttachments: List<Attachment>
    ): JsonElement {
        val imageAttachments = msgAttachments.filter { FileAttachmentHelper.isImageMime(it.mimeType) }
        if (imageAttachments.isEmpty()) {
            return JsonPrimitive(msgText)
        }
        return buildJsonArray {
            if (msgText.isNotBlank()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", msgText)
                })
            }
            for (img in imageAttachments) {
                val base64Pair = FileAttachmentHelper.readBase64ForVision(null, img)
                if (base64Pair != null) {
                    val (mime, b64) = base64Pair
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject {
                            put("url", "data:$mime;base64,$b64")
                        })
                    })
                }
            }
        }
    }

    open fun getDefaultModelId(): String = "gpt-4o"
}
