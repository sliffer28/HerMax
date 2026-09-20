package com.hermes.client.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents an AI provider (Hermes, OpenRouter, Google, Anthropic, OpenAI, Ollama, NVIDIA)
 */
@Serializable
enum class AIProviderType(
    val displayName: String,
    val iconRes: String, // Material icon name
    val requiresApiKey: Boolean = true,
    val supportsStreaming: Boolean = true,
    val supportsThinking: Boolean = false,
    val supportsResponseEffort: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false,
    val isEnabled: Boolean = false // Default to disabled
) {
    HERMES_AGENT("Hermes Agent", "dns", requiresApiKey = false, supportsStreaming = true, supportsThinking = false, supportsResponseEffort = false, supportsVision = true, supportsTools = true, isEnabled = true),
    OPENROUTER("OpenRouter", "cloud", supportsStreaming = true, supportsThinking = true, supportsResponseEffort = false, supportsVision = true, supportsTools = true),
    GOOGLE_AI("Google AI Studio", "smart_toy", supportsStreaming = true, supportsThinking = true, supportsResponseEffort = false, supportsVision = true, supportsTools = true),
    ANTHROPIC("Anthropic / Claude", "psychology", supportsStreaming = true, supportsThinking = true, supportsResponseEffort = false, supportsVision = true, supportsTools = true),
    OPENAI("OpenAI", "smart_toy", supportsStreaming = true, supportsThinking = false, supportsResponseEffort = true, supportsVision = true, supportsTools = true),
    OLLAMA("Ollama Local", "memory", requiresApiKey = false, supportsStreaming = true, supportsThinking = false, supportsResponseEffort = false, supportsVision = true, supportsTools = true),
    OLLAMA_CLOUD("Ollama Cloud", "cloud", requiresApiKey = true, supportsStreaming = true, supportsThinking = false, supportsResponseEffort = false, supportsVision = true, supportsTools = true),
    NVIDIA("NVIDIA", "memory", supportsStreaming = true, supportsThinking = false, supportsResponseEffort = false, supportsVision = true, supportsTools = true),
    CUSTOM("Custom Provider", "tune", requiresApiKey = false, supportsStreaming = true, supportsThinking = false, supportsResponseEffort = false, supportsVision = true, supportsTools = true)
}

/**
 * Thinking/Reasoning levels supported by some models
 */
@Serializable
enum class ThinkingLevel(val displayName: String, val apiValue: String?) {
    OFF("Off", null),
    LOW("Low", "low"),
    MEDIUM("Medium", "medium"),
    HIGH("High", "high")
}

/**
 * Response effort levels (OpenAI specific)
 */
@Serializable
enum class ResponseEffortLevel(val displayName: String, val apiValue: String?) {
    LOW("Low", "low"),
    MEDIUM("Medium", "medium"),
    HIGH("High", "high")
}

/**
 * Model capabilities metadata
 */
@Serializable
data class ModelCapabilities(
    val streaming: Boolean = true,
    val reasoning: Boolean = false,
    val thinkingLevels: List<ThinkingLevel> = emptyList(),
    val vision: Boolean = false,
    val tools: Boolean = false,
    val maxContext: Int? = null,
    val responseEffortLevels: List<ResponseEffortLevel> = emptyList()
) {
    fun supportsThinking(): Boolean = reasoning && thinkingLevels.isNotEmpty()
    fun supportsResponseEffort(): Boolean = responseEffortLevels.isNotEmpty()
}

/**
 * Unified model representation across all providers
 */
@Serializable
data class UnifiedModel(
    val id: String,
    val name: String,
    val provider: AIProviderType,
    val providerModelId: String, // The actual model ID used by the provider API
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val contextWindow: Int? = null,
    val isAvailable: Boolean = true,
    val description: String = "",
    val pricing: ModelPricing? = null
)

@Serializable
data class ModelPricing(
    val inputPricePer1k: Double? = null, // USD per 1K tokens
    val outputPricePer1k: Double? = null,
    val currency: String = "USD"
)

/**
 * Settings for a specific model conversation
 */
@Serializable
data class ModelSettings(
    val provider: AIProviderType,
    val modelId: String,
    val providerModelId: String,
    val thinkingEnabled: Boolean = false,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    val responseEffort: ResponseEffortLevel? = null,
    val temperature: Double = 0.7,
    val maxTokens: Int? = null,
    val systemPrompt: String? = null
) {
    fun toModelOptionsMap(): Map<String, String> {
        val options = mutableMapOf<String, String>()
        if (thinkingEnabled && thinkingLevel != ThinkingLevel.OFF) {
            options["thinking"] = thinkingLevel.apiValue!!
        }
        responseEffort?.let { options["response_effort"] = it.apiValue!! }
        return options
    }
}

/**
 * Provider configuration stored by the user
 */
@Serializable
data class ProviderConfig(
    val provider: AIProviderType,
    val isEnabled: Boolean = false,
    val apiKey: String? = null, // Encrypted in storage
    val baseUrl: String? = null, // For Ollama, custom endpoints
    val organizationId: String? = null, // For OpenAI
    val lastTested: Long? = null,
    val lastTestSuccess: Boolean? = null,
    val discoveredModels: List<UnifiedModel> = emptyList(),
    val customName: String? = null, // For Custom Provider
    val manualModelId: String? = null // For Custom Provider manual model entry
)

/**
 * Normalizes OpenAI-compatible base URLs safely.
 * Examples:
 *  "https://example.com" -> "https://example.com/v1"
 *  "https://example.com/v1" -> "https://example.com/v1"
 *  "http://192.168.1.50:11434" -> "http://192.168.1.50:11434/v1"
 *  "http://192.168.1.50:11434/v1/" -> "http://192.168.1.50:11434/v1"
 * Prevents double-paths like /v1/v1.
 */
fun normalizeOpenAiBaseUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isBlank()) return ""
    val withScheme = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
        "http://$trimmed"
    } else {
        trimmed
    }
    return if (withScheme.endsWith("/v1", ignoreCase = true)) {
        withScheme
    } else {
        "$withScheme/v1"
    }
}

/**
 * Result of a provider connection test
 */
@Serializable
sealed class ConnectionTestResult {
    data class Success(val modelsFound: Int = 0) : ConnectionTestResult()
    data class Failure(val message: String, val isAuthError: Boolean = false) : ConnectionTestResult()
}