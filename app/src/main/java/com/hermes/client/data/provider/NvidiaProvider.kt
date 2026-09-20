package com.hermes.client.data.provider

import com.hermes.client.data.security.SecureStorage
import com.hermes.client.domain.model.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Direct NVIDIA NIM / API provider implementation.
 */
class NvidiaProvider(
    okHttpClient: OkHttpClient,
    json: Json,
    private val secureStorage: SecureStorage
) : BaseOpenAICompatibleProvider(okHttpClient, json) {

    override val providerType: AIProviderType = AIProviderType.NVIDIA

    override fun getBaseUrl(): String {
        val configured = secureStorage.getProviderConfig(AIProviderType.NVIDIA).baseUrl
        return if (!configured.isNullOrBlank()) {
            configured.trimEnd('/')
        } else {
            "https://integrate.api.nvidia.com/v1"
        }
    }

    override fun getAuthHeaders(): Map<String, String> {
        val apiKey = secureStorage.getProviderConfig(AIProviderType.NVIDIA).apiKey.orEmpty()
        return mapOf("Authorization" to "Bearer $apiKey")
    }

    override fun getCapabilities(): ModelCapabilities = ModelCapabilities(
        streaming = true,
        reasoning = true,
        vision = true,
        tools = false
    )

    override fun getDefaultModelId(): String = "meta/llama-3.1-70b-instruct"
}
