package com.hermes.client.data.provider

import com.hermes.client.data.security.SecureStorage
import com.hermes.client.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Direct Ollama Cloud provider connecting to https://ollama.com/v1 using an API key.
 */
class OllamaCloudProvider(
    okHttpClient: OkHttpClient,
    json: Json,
    private val secureStorage: SecureStorage
) : BaseOpenAICompatibleProvider(okHttpClient, json) {

    override val providerType: AIProviderType = AIProviderType.OLLAMA_CLOUD

    override fun getBaseUrl(): String {
        val configured = secureStorage.getProviderConfig(AIProviderType.OLLAMA_CLOUD).baseUrl
        return if (!configured.isNullOrBlank()) {
            normalizeOpenAiBaseUrl(configured)
        } else {
            "https://ollama.com/v1"
        }
    }

    override fun getAuthHeaders(): Map<String, String> {
        val apiKey = secureStorage.getProviderConfig(AIProviderType.OLLAMA_CLOUD).apiKey.orEmpty().trim()
        return if (apiKey.isNotBlank()) {
            mapOf("Authorization" to "Bearer $apiKey")
        } else {
            emptyMap()
        }
    }

    override suspend fun getModels(): Result<List<Model>> = runCatching {
        withContext(Dispatchers.IO) {
            val apiKey = secureStorage.getProviderConfig(AIProviderType.OLLAMA_CLOUD).apiKey.orEmpty().trim()
            if (apiKey.isBlank()) {
                throw IOException("Ollama Cloud API key is required. Please configure it in Settings.")
            }

            // 1. Try standard OpenAI-compatible /v1/models endpoint
            val discoveryResult = super.getModels()
            if (discoveryResult.isSuccess && discoveryResult.getOrThrow().isNotEmpty()) {
                return@withContext discoveryResult.getOrThrow()
            }

            // 2. Fallback: try Ollama native /api/tags endpoint with Bearer auth
            val rawHost = getBaseUrl().removeSuffix("/v1").trimEnd('/')
            val tagsUrl = "$rawHost/api/tags"
            val request = Request.Builder()
                .url(tagsUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val modelsArray = root["models"]?.jsonArray ?: JsonArray(emptyList())

                val parsed = modelsArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    Model(
                        id = name,
                        name = name,
                        provider = "Ollama Cloud",
                        capabilities = listOf("streaming"),
                        isAvailable = true
                    )
                }.sortedBy { it.name }

                if (parsed.isNotEmpty()) {
                    return@withContext parsed
                }
            }

            // 3. Fallback popular cloud models if endpoint listing is not supported
            listOf(
                Model(id = "llama3.3", name = "Llama 3.3 (70B)", provider = "Ollama Cloud", isAvailable = true),
                Model(id = "llama3.2", name = "Llama 3.2 (3B)", provider = "Ollama Cloud", isAvailable = true),
                Model(id = "deepseek-r1", name = "DeepSeek R1", provider = "Ollama Cloud", isAvailable = true),
                Model(id = "qwen2.5:72b", name = "Qwen 2.5 (72B)", provider = "Ollama Cloud", isAvailable = true),
                Model(id = "mistral-small", name = "Mistral Small", provider = "Ollama Cloud", isAvailable = true),
                Model(id = "phi4", name = "Phi 4 (14B)", provider = "Ollama Cloud", isAvailable = true),
                Model(id = "gemma2:27b", name = "Gemma 2 (27B)", provider = "Ollama Cloud", isAvailable = true)
            )
        }
    }

    override suspend fun testConnection(): Result<ConnectionTestResult> = runCatching {
        withContext(Dispatchers.IO) {
            val apiKey = secureStorage.getProviderConfig(AIProviderType.OLLAMA_CLOUD).apiKey.orEmpty().trim()
            if (apiKey.isBlank()) {
                return@withContext ConnectionTestResult.Failure(
                    message = "Ollama Cloud API key is required. Get yours at ollama.com/settings/keys",
                    isAuthError = true
                )
            }

            val url = "${getBaseUrl()}/models"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    val count = try {
                        val root = json.parseToJsonElement(bodyString).jsonObject
                        root["data"]?.jsonArray?.size ?: 0
                    } catch (_: Exception) {
                        0
                    }
                    ConnectionTestResult.Success(modelsFound = count)
                } else if (response.code == 401 || response.code == 403) {
                    ConnectionTestResult.Failure(
                        message = "Invalid API key: Authentication failed with Ollama Cloud (HTTP ${response.code}).",
                        isAuthError = true
                    )
                } else {
                    // Try /api/tags fallback for validation
                    val tagsUrl = "${getBaseUrl().removeSuffix("/v1")}/api/tags"
                    val tagsReq = Request.Builder()
                        .url(tagsUrl)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .get()
                        .build()
                    val tagsResp = okHttpClient.newCall(tagsReq).execute()
                    if (tagsResp.isSuccessful) {
                        ConnectionTestResult.Success(modelsFound = 1)
                    } else {
                        ConnectionTestResult.Failure(
                            message = "Ollama Cloud returned HTTP ${response.code}: ${response.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                ConnectionTestResult.Failure(
                    message = "Unable to reach Ollama Cloud (${getBaseUrl()}). Check your network connection."
                )
            }
        }
    }

    override fun getDefaultModelId(): String = "llama3.3"

    override fun getCapabilities(): ModelCapabilities = ModelCapabilities(
        streaming = true,
        reasoning = true,
        vision = true,
        tools = false
    )
}
