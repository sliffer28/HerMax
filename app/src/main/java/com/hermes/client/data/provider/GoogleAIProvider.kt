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
 * Direct Google AI Studio (Gemini) provider talking straight to:
 * https://generativelanguage.googleapis.com/v1beta/
 */
class GoogleAIProvider(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val secureStorage: SecureStorage
) : AIProvider {

    override val providerType: AIProviderType = AIProviderType.GOOGLE_AI

    private fun getApiKey(): String? =
        secureStorage.getProviderConfig(AIProviderType.GOOGLE_AI).apiKey?.takeIf { it.isNotBlank() }

    override fun getCapabilities(): ModelCapabilities = ModelCapabilities(
        streaming = true,
        reasoning = true,
        thinkingLevels = listOf(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH),
        vision = true,
        tools = false
    )

    override suspend fun getModels(): Result<List<Model>> = runCatching {
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey() ?: throw IOException("Google AI Studio API key not configured")
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"

            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val error = response.body?.string().orEmpty()
                throw IOException("Failed to load Gemini models (${response.code}): $error")
            }

            val body = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(body).jsonObject
            val modelsArray = root["models"]?.jsonArray ?: JsonArray(emptyList())

            modelsArray.mapNotNull { element ->
                val obj = element.jsonObject
                val rawName = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val supportedMethods = obj["supportedGenerationMethods"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                // Only include generative chat models
                if (!supportedMethods.contains("generateContent")) return@mapNotNull null

                val modelId = rawName.removePrefix("models/")
                val displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull ?: modelId
                val contextWindow = obj["inputTokenLimit"]?.jsonPrimitive?.intOrNull

                val isReasoning = modelId.contains("thinking", ignoreCase = true) ||
                        modelId.contains("gemini-2.0", ignoreCase = true)

                Model(
                    id = modelId,
                    name = displayName,
                    provider = "Google AI Studio",
                    contextWindow = contextWindow,
                    capabilities = if (isReasoning) listOf("reasoning", "streaming") else listOf("streaming"),
                    isAvailable = true
                )
            }.sortedWith(compareByDescending<Model> { it.id.contains("flash") || it.id.contains("pro") }.thenBy { it.name })
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
            trySend(HermesEvent.Error("Google AI Studio API key is missing. Please configure it in Settings."))
            close(IOException("Missing API key"))
            return@callbackFlow
        }

        val rawModel = model ?: "gemini-1.5-flash"
        val cleanModel = rawModel.removePrefix("models/")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:streamGenerateContent?alt=sse&key=$apiKey"

        val contentsArray = buildJsonArray {
            // History messages
            history.filter { it.id != "streaming" && it.status != MessageStatus.ERROR }.forEach { msg ->
                add(buildJsonObject {
                    // Gemini uses "user" and "model" roles
                    put("role", if (msg.role == MessageRole.USER) "user" else "model")
                    put("parts", if (msg.role == MessageRole.USER && msg.attachments.isNotEmpty()) {
                        buildGeminiParts(msg.content, msg.attachments)
                    } else {
                        buildJsonArray {
                            add(buildJsonObject { put("text", msg.content) })
                        }
                    })
                })
            }
            // Latest user message
            add(buildJsonObject {
                put("role", "user")
                put("parts", buildGeminiParts(message, attachments))
            })
        }

        val requestBodyJson = buildJsonObject {
            put("contents", contentsArray)
            // System instructions if provided
            settings?.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            }
            // Generation config
            put("generationConfig", buildJsonObject {
                settings?.temperature?.let { put("temperature", it) }
                // Thinking budget if reasoning model
                if (settings?.thinkingEnabled == true && settings.thinkingLevel != ThinkingLevel.OFF) {
                    val budget = when (settings.thinkingLevel) {
                        ThinkingLevel.LOW -> 1024
                        ThinkingLevel.MEDIUM -> 4096
                        ThinkingLevel.HIGH -> 8192
                        ThinkingLevel.OFF -> 0
                    }
                    put("thinkingConfig", buildJsonObject {
                        put("thinkingBudget", budget)
                    })
                }
            })
        }

        val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
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
                    val candidates = root["candidates"]?.jsonArray
                    val firstCandidate = candidates?.firstOrNull()?.jsonObject
                    val content = firstCandidate?.get("content")?.jsonObject
                    val parts = content?.get("parts")?.jsonArray

                    parts?.forEach { partEl ->
                        val partObj = partEl.jsonObject
                        val isThought = partObj["thought"]?.jsonPrimitive?.booleanOrNull ?: false
                        val text = partObj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()

                        if (text.isNotEmpty()) {
                            if (isThought) {
                                trySend(HermesEvent.ThinkingDelta(messageId, text))
                            } else {
                                trySend(HermesEvent.TextDelta(messageId, text))
                            }
                        }
                    }

                    val finishReason = firstCandidate?.get("finishReason")?.jsonPrimitive?.contentOrNull
                    if (finishReason != null && finishReason != "STOP" && finishReason.isNotBlank()) {
                        trySend(HermesEvent.Completed(messageId, finishReason))
                    }
                } catch (_: Exception) {
                }
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(HermesEvent.Completed(messageId))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorMsg = when {
                    response?.code == 400 || response?.code == 403 ->
                        "Authentication failed: Google AI Studio API key is invalid."
                    response?.code == 429 ->
                        "Google AI Studio quota exceeded. Please check your rate limits."
                    response != null ->
                        "Google AI error (${response.code}): ${response.message}"
                    t != null ->
                        t.message ?: "Google AI connection failed"
                    else ->
                        "Connection error"
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

            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val models = root["models"]?.jsonArray?.size ?: 0
                ConnectionTestResult.Success(modelsFound = models)
            } else if (response.code == 400 || response.code == 403) {
                ConnectionTestResult.Failure(
                    message = "Authentication failed: Google AI Studio API key is invalid.",
                    isAuthError = true
                )
            } else {
                ConnectionTestResult.Failure("HTTP ${response.code}: ${response.message}")
            }
        }
    }

    private fun buildGeminiParts(text: String, attachments: List<Attachment>): JsonArray {
        return buildJsonArray {
            if (text.isNotBlank()) {
                add(buildJsonObject {
                    put("text", text)
                })
            }
            for (att in attachments) {
                if (FileAttachmentHelper.isImageMime(att.mimeType)) {
                    FileAttachmentHelper.readBase64ForVision(null, att)?.let { (mime, b64) ->
                        add(buildJsonObject {
                            put("inline_data", buildJsonObject {
                                put("mime_type", mime)
                                put("data", b64)
                            })
                        })
                    }
                } else if (FileAttachmentHelper.isPdf(att.mimeType, att.fileName)) {
                    FileAttachmentHelper.readRawBase64(null, att)?.let { (_, b64) ->
                        add(buildJsonObject {
                            put("inline_data", buildJsonObject {
                                put("mime_type", "application/pdf")
                                put("data", b64)
                            })
                        })
                    }
                }
            }
        }
    }
}
