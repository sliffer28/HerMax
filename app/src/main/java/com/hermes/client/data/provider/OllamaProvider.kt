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
 * Direct Ollama provider connecting to local network Ollama instance.
 */
class OllamaProvider(
    okHttpClient: OkHttpClient,
    json: Json,
    private val secureStorage: SecureStorage
) : BaseOpenAICompatibleProvider(okHttpClient, json) {

    override val providerType: AIProviderType = AIProviderType.OLLAMA

    private fun getRawServerUrl(): String {
        val configured = secureStorage.getProviderConfig(AIProviderType.OLLAMA).baseUrl
        return if (!configured.isNullOrBlank()) {
            configured.trimEnd('/')
        } else {
            "http://192.168.1.22:11434"
        }
    }

    override fun getBaseUrl(): String = "${getRawServerUrl()}/v1"

    override fun getAuthHeaders(): Map<String, String> = emptyMap()

    override suspend fun getModels(): Result<List<Model>> = runCatching {
        withContext(Dispatchers.IO) {
            val url = "${getRawServerUrl()}/api/tags"
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("Ollama returned HTTP ${response.code}: ${response.message}")
            }

            val body = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(body).jsonObject
            val modelsArray = root["models"]?.jsonArray ?: JsonArray(emptyList())

            modelsArray.mapNotNull { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val sizeBytes = obj["size"]?.jsonPrimitive?.longOrNull
                val sizeDesc = if (sizeBytes != null) " (${sizeBytes / (1024 * 1024 * 1024)}GB)" else ""

                Model(
                    id = name,
                    name = "$name$sizeDesc",
                    provider = "Ollama",
                    capabilities = listOf("streaming"),
                    isAvailable = true
                )
            }.sortedBy { it.name }
        }
    }

    override suspend fun testConnection(): Result<ConnectionTestResult> = runCatching {
        withContext(Dispatchers.IO) {
            val serverUrl = getRawServerUrl()
            val url = "$serverUrl/api/tags"
            val request = Request.Builder().url(url).get().build()

            try {
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val count = root["models"]?.jsonArray?.size ?: 0
                    ConnectionTestResult.Success(modelsFound = count)
                } else {
                    ConnectionTestResult.Failure("Ollama server returned HTTP ${response.code}: ${response.message}")
                }
            } catch (e: Exception) {
                ConnectionTestResult.Failure(
                    "Could not reach Ollama at $serverUrl. Ensure Ollama is running and your phone can reach your PC on local Wi-Fi."
                )
            }
        }
    }

    override fun getCapabilities(): ModelCapabilities = ModelCapabilities(
        streaming = true,
        reasoning = true,
        vision = true,
        tools = false
    )

    override fun getDefaultModelId(): String = "llama3:latest"
}
