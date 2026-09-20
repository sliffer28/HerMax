package com.hermes.client.data.provider

import com.hermes.client.data.security.SecureStorage
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
 * Direct Anthropic Claude provider.
 */
class AnthropicProvider(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val secureStorage: SecureStorage
) : AIProvider {

    override val providerType: AIProviderType = AIProviderType.ANTHROPIC

    private fun getApiKey(): String? =
        secureStorage.getProviderConfig(AIProviderType.ANTHROPIC).apiKey?.takeIf { it.isNotBlank() }

    override fun getCapabilities(): ModelCapabilities = ModelCapabilities(
        streaming = true,
        reasoning = true,
        thinkingLevels = listOf(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH),
        vision = true,
        tools = false
    )

    override suspend fun getModels(): Result<List<Model>> = runCatching {
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey() ?: throw IOException("Anthropic API key not configured")
            val url = "https://api.anthropic.com/v1/models"

            val request = Request.Builder()
                .url(url)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .get()
                .build()

            val response = runCatching { okHttpClient.newCall(request).execute() }.getOrNull()

            if (response != null && response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray
                if (!data.isNullOrEmpty()) {
                    return@withContext data.mapNotNull { el ->
                        val obj = el.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val displayName = obj["display_name"]?.jsonPrimitive?.contentOrNull ?: id
                        Model(
                            id = id,
                            name = displayName,
                            provider = "Anthropic",
                            isAvailable = true
                        )
                    }
                }
            }

            // Fallback to standard Claude catalog
            listOf(
                Model(id = "claude-3-5-sonnet-latest", name = "Claude 3.5 Sonnet", provider = "Anthropic"),
                Model(id = "claude-3-5-haiku-latest", name = "Claude 3.5 Haiku", provider = "Anthropic"),
                Model(id = "claude-3-opus-latest", name = "Claude 3 Opus", provider = "Anthropic")
            )
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
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            trySend(HermesEvent.Error("Anthropic API key is missing. Please configure it in Settings."))
            close(IOException("Missing API key"))
            return@callbackFlow
        }

        val selectedModel = model ?: "claude-3-5-sonnet-latest"
        val url = "https://api.anthropic.com/v1/messages"

        val messagesArray = buildJsonArray {
            history.filter { it.id != "streaming" && it.status != MessageStatus.ERROR }.forEach { msg ->
                add(buildJsonObject {
                    put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                    val contentElement = if (msg.role == MessageRole.USER && msg.attachments.isNotEmpty()) {
                        buildAnthropicContent(msg.content, msg.attachments)
                    } else {
                        JsonPrimitive(msg.content)
                    }
                    put("content", contentElement)
                })
            }
            add(buildJsonObject {
                put("role", "user")
                put("content", buildAnthropicContent(message, attachments))
            })
        }

        val requestBodyJson = buildJsonObject {
            put("model", selectedModel)
            put("max_tokens", 4096)
            put("stream", true)
            settings?.systemPrompt?.takeIf { it.isNotBlank() }?.let { put("system", it) }
            put("messages", messagesArray)
            settings?.temperature?.let { put("temperature", it) }
        }

        val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(requestBody)
            .build()

        val messageId = "msg_${System.currentTimeMillis()}"

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                trySend(HermesEvent.Connected)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val root = json.parseToJsonElement(data).jsonObject
                    val eventType = root["type"]?.jsonPrimitive?.contentOrNull ?: type

                    when (eventType) {
                        "content_block_delta" -> {
                            val delta = root["delta"]?.jsonObject
                            val deltaType = delta?.get("type")?.jsonPrimitive?.contentOrNull
                            if (deltaType == "thinking_delta") {
                                val thinking = delta["thinking"]?.jsonPrimitive?.contentOrNull
                                if (!thinking.isNullOrEmpty()) {
                                    trySend(HermesEvent.ThinkingDelta(messageId, thinking))
                                }
                            } else {
                                val text = delta?.get("text")?.jsonPrimitive?.contentOrNull
                                if (!text.isNullOrEmpty()) {
                                    trySend(HermesEvent.TextDelta(messageId, text))
                                }
                            }
                        }
                        "message_stop" -> {
                            trySend(HermesEvent.Completed(messageId))
                        }
                    }
                } catch (_: Exception) {
                }
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(HermesEvent.Completed(messageId))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorMsg = when {
                    response?.code == 401 -> "Authentication failed: Invalid Anthropic API key."
                    response?.code == 429 -> "Anthropic rate limit exceeded."
                    response != null -> "Anthropic error (${response.code}): ${response.message}"
                    t != null -> t.message ?: "Anthropic connection failed"
                    else -> "Connection error"
                }
                trySend(HermesEvent.Error(errorMsg, code = response?.code?.toString()))
                close(t ?: IOException(errorMsg))
            }
        }

        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(httpRequest, listener)
        awaitClose { eventSource.cancel() }
    }

    override suspend fun testConnection(): Result<ConnectionTestResult> = runCatching {
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey()
            if (apiKey.isNullOrBlank()) {
                return@withContext ConnectionTestResult.Failure("API key is not configured.", isAuthError = true)
            }

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/models")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val count = root["data"]?.jsonArray?.size ?: 3
                ConnectionTestResult.Success(modelsFound = count)
            } else if (response.code == 401) {
                ConnectionTestResult.Failure("Authentication failed: Invalid Anthropic API key.", isAuthError = true)
            } else {
                ConnectionTestResult.Failure("HTTP ${response.code}: ${response.message}")
            }
        }
    }

    private fun buildAnthropicContent(text: String, attachments: List<Attachment>): JsonElement {
        if (attachments.isEmpty()) {
            return JsonPrimitive(text)
        }
        return buildJsonArray {
            if (text.isNotBlank()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            }
            for (att in attachments) {
                if (FileAttachmentHelper.isImageMime(att.mimeType)) {
                    FileAttachmentHelper.readBase64ForVision(null, att)?.let { (mime, b64) ->
                        add(buildJsonObject {
                            put("type", "image")
                            put("source", buildJsonObject {
                                put("type", "base64")
                                put("media_type", mime)
                                put("data", b64)
                            })
                        })
                    }
                } else if (FileAttachmentHelper.isPdf(att.mimeType, att.fileName)) {
                    FileAttachmentHelper.readRawBase64(null, att)?.let { (_, b64) ->
                        add(buildJsonObject {
                            put("type", "document")
                            put("source", buildJsonObject {
                                put("type", "base64")
                                put("media_type", "application/pdf")
                                put("data", b64)
                            })
                        })
                    }
                }
            }
        }
    }
}
