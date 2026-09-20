package com.hermes.client.data.provider

import com.hermes.client.data.security.SecureStorage
import com.hermes.client.domain.model.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Direct OpenAI provider implementation.
 */
class OpenAIProvider(
    okHttpClient: OkHttpClient,
    json: Json,
    private val secureStorage: SecureStorage
) : BaseOpenAICompatibleProvider(okHttpClient, json) {

    override val providerType: AIProviderType = AIProviderType.OPENAI

    override fun getBaseUrl(): String = "https://api.openai.com/v1"

    override fun getAuthHeaders(): Map<String, String> {
        val config = secureStorage.getProviderConfig(AIProviderType.OPENAI)
        val headers = mutableMapOf("Authorization" to "Bearer ${config.apiKey.orEmpty()}")
        if (!config.organizationId.isNullOrBlank()) {
            headers["OpenAI-Organization"] = config.organizationId
        }
        return headers
    }

    override fun getCapabilities(): ModelCapabilities = ModelCapabilities(
        streaming = true,
        reasoning = true,
        responseEffortLevels = listOf(ResponseEffortLevel.LOW, ResponseEffortLevel.MEDIUM, ResponseEffortLevel.HIGH),
        vision = true,
        tools = true
    )

    override fun getDefaultModelId(): String = "gpt-4o-mini"
}
