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
 * Custom OpenAI-compatible provider supporting any user-configured endpoint
 * (Local LM Studio, vLLM, Ollama, custom proxy, private server, etc.).
 */
class CustomProvider(
    okHttpClient: OkHttpClient,
    json: Json,
    private val secureStorage: SecureStorage
) : BaseOpenAICompatibleProvider(okHttpClient, json) {

    override val providerType: AIProviderType = AIProviderType.CUSTOM

    override fun getBaseUrl(): String {
        val config = secureStorage.getProviderConfig(AIProviderType.CUSTOM)
        val raw = config.baseUrl.orEmpty()
        return normalizeOpenAiBaseUrl(raw)
    }

    override fun getAuthHeaders(): Map<String, String> {
        val config = secureStorage.getProviderConfig(AIProviderType.CUSTOM)
        val headers = mutableMapOf<String, String>()
        val apiKey = config.apiKey?.trim().orEmpty()
        if (apiKey.isNotBlank()) {
            headers["Authorization"] = "Bearer $apiKey"
        }
        val org = config.organizationId?.trim().orEmpty()
        if (org.isNotBlank()) {
            headers["OpenAI-Organization"] = org
        }
        return headers
    }

    override suspend fun getModels(): Result<List<Model>> = runCatching {
        withContext(Dispatchers.IO) {
            val config = secureStorage.getProviderConfig(AIProviderType.CUSTOM)
            val base = getBaseUrl()
            if (base.isBlank()) {
                throw IOException("Server URL is required. Please configure Custom Provider in Settings.")
            }

            val providerName = config.customName?.ifBlank { "Custom Provider" } ?: "Custom Provider"

            // 1. Attempt model discovery via GET /models
            val discovery = super.getModels()
            if (discovery.isSuccess && discovery.getOrThrow().isNotEmpty()) {
                val list = discovery.getOrThrow().map { it.copy(provider = providerName) }.toMutableList()
                // If user also defined a manual model ID, ensure it appears at top
                config.manualModelId?.trim()?.takeIf { it.isNotBlank() }?.let { manualId ->
                    if (list.none { it.id == manualId }) {
                        list.add(0, Model(
                            id = manualId,
                            name = "$manualId (Manual)",
                            provider = providerName,
                            isAvailable = true
                        ))
                    }
                }
                return@withContext list
            }

            // 2. If model discovery failed or was empty, check for manual model entry
            val manualId = config.manualModelId?.trim().orEmpty()
            if (manualId.isNotBlank()) {
                return@withContext listOf(
                    Model(
                        id = manualId,
                        name = manualId,
                        provider = providerName,
                        isAvailable = true
                    )
                )
            }

            // 3. Neither discovery worked nor manual model exists
            val discoveryError = discovery.exceptionOrNull()?.message
            throw IOException(
                discoveryError?.let { "Model discovery failed: $it. You can specify a manual model ID in AI Providers." }
                    ?: "No models found at $base/models. Please enter a manual model ID in AI Providers."
            )
        }
    }

    override suspend fun testConnection(): Result<ConnectionTestResult> = runCatching {
        withContext(Dispatchers.IO) {
            val config = secureStorage.getProviderConfig(AIProviderType.CUSTOM)
            val base = getBaseUrl()
            if (base.isBlank()) {
                return@withContext ConnectionTestResult.Failure("Server URL is required.")
            }

            val url = "$base/models"
            val requestBuilder = Request.Builder().url(url).get()
            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            try {
                val response = okHttpClient.newCall(requestBuilder.build()).execute()
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
                        message = "Authentication failed (HTTP ${response.code}). Check your API key.",
                        isAuthError = true
                    )
                } else if (response.code == 404 || response.code == 405) {
                    // Endpoint might not support /models
                    val manualId = config.manualModelId?.trim().orEmpty()
                    if (manualId.isNotBlank()) {
                        ConnectionTestResult.Success(modelsFound = 1)
                    } else {
                        ConnectionTestResult.Failure(
                            message = "Server reached (HTTP ${response.code}), but /models is unavailable. Please enter a manual model ID."
                        )
                    }
                } else {
                    ConnectionTestResult.Failure("Server returned HTTP ${response.code}: ${response.message}")
                }
            } catch (e: Exception) {
                ConnectionTestResult.Failure(
                    message = "Unable to reach server at $base. Check URL and network connection."
                )
            }
        }
    }

    override fun getDefaultModelId(): String {
        val config = secureStorage.getProviderConfig(AIProviderType.CUSTOM)
        return config.manualModelId?.trim()?.takeIf { it.isNotBlank() } ?: "default"
    }

    override fun getCapabilities(): ModelCapabilities = ModelCapabilities(
        streaming = true,
        reasoning = true,
        vision = true,
        tools = false
    )
}
