package com.hermes.client.data.provider

import com.hermes.client.data.security.SecureStorage
import com.hermes.client.domain.model.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Direct OpenRouter provider implementation.
 */
class OpenRouterProvider(
    okHttpClient: OkHttpClient,
    json: Json,
    private val secureStorage: SecureStorage
) : BaseOpenAICompatibleProvider(okHttpClient, json) {

    override val providerType: AIProviderType = AIProviderType.OPENROUTER

    override fun getBaseUrl(): String = "https://openrouter.ai/api/v1"

    override fun getAuthHeaders(): Map<String, String> {
        val apiKey = secureStorage.getProviderConfig(AIProviderType.OPENROUTER).apiKey.orEmpty()
        return mapOf(
            "Authorization" to "Bearer $apiKey",
            "HTTP-Referer" to "https://github.com/hermes-client",
            "X-Title" to "Hermes AI Client"
        )
    }

    override fun getCapabilities(): ModelCapabilities = ModelCapabilities(
        streaming = true,
        reasoning = true,
        thinkingLevels = listOf(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH),
        vision = true,
        tools = true
    )

    override fun getDefaultModelId(): String = "google/gemini-2.0-flash-exp:free"
}
