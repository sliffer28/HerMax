package com.hermes.client.data.provider

import com.hermes.client.data.security.SecureStorage
import com.hermes.client.domain.model.AIProviderType
import com.hermes.client.domain.provider.AIProvider
import com.hermes.client.domain.repository.HermesRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Registry providing unified access to all AIProvider instances.
 */
@Singleton
class AIProviderRegistry @Inject constructor(
    @Named("cloudOkHttpClient") private val cloudOkHttpClient: OkHttpClient,
    private val json: Json,
    private val secureStorage: SecureStorage,
    private val hermesRepository: HermesRepository
) {

    private val providers = mutableMapOf<AIProviderType, AIProvider>()

    init {
        providers[AIProviderType.HERMES_AGENT] = HermesProvider(hermesRepository)
        providers[AIProviderType.GOOGLE_AI] = GoogleAIProvider(cloudOkHttpClient, json, secureStorage)
        providers[AIProviderType.OPENROUTER] = OpenRouterProvider(cloudOkHttpClient, json, secureStorage)
        providers[AIProviderType.ANTHROPIC] = AnthropicProvider(cloudOkHttpClient, json, secureStorage)
        providers[AIProviderType.OPENAI] = OpenAIProvider(cloudOkHttpClient, json, secureStorage)
        providers[AIProviderType.OLLAMA] = OllamaProvider(cloudOkHttpClient, json, secureStorage)
        providers[AIProviderType.OLLAMA_CLOUD] = OllamaCloudProvider(cloudOkHttpClient, json, secureStorage)
        providers[AIProviderType.NVIDIA] = NvidiaProvider(cloudOkHttpClient, json, secureStorage)
        providers[AIProviderType.CUSTOM] = CustomProvider(cloudOkHttpClient, json, secureStorage)
    }

    fun getProvider(type: AIProviderType): AIProvider {
        return providers[type] ?: providers[AIProviderType.HERMES_AGENT]!!
    }

    fun getAllProviders(): List<AIProvider> = providers.values.toList()

    fun isConfigured(type: AIProviderType): Boolean {
        val config = secureStorage.getProviderConfig(type)
        return when (type) {
            AIProviderType.HERMES_AGENT -> true
            AIProviderType.OLLAMA -> true
            AIProviderType.CUSTOM -> !config.baseUrl.isNullOrBlank()
            else -> !config.apiKey.isNullOrBlank()
        }
    }
}
