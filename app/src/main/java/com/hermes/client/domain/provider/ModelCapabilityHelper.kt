package com.hermes.client.domain.provider

import com.hermes.client.domain.model.AIProviderType
import java.util.Locale

object ModelCapabilityHelper {

    /**
     * Determines whether the given model under the specified provider supports vision / image input.
     */
    fun isVisionSupported(providerType: AIProviderType, modelId: String?): Boolean {
        val model = (modelId ?: "").lowercase(Locale.ROOT)

        return when (providerType) {
            AIProviderType.GOOGLE_AI -> {
                // All modern Gemini models support vision
                true
            }
            AIProviderType.ANTHROPIC -> {
                // All Claude 3 and 3.5 models support vision
                model.contains("claude-3") || model.isBlank()
            }
            AIProviderType.OPENAI -> {
                if (model.isBlank()) return true // default is gpt-4o-mini
                model.contains("gpt-4o") ||
                        model.contains("gpt-4-turbo") ||
                        model.contains("vision") ||
                        model.contains("o1") ||
                        model.contains("chatgpt-4o")
            }
            AIProviderType.OPENROUTER -> {
                if (model.isBlank()) return true // default is gemini-2.0-flash-exp:free
                model.contains("vision") ||
                        model.contains("gemini") ||
                        model.contains("claude-3") ||
                        model.contains("gpt-4o") ||
                        model.contains("gpt-4-turbo") ||
                        model.contains("llama-3.2-11b-vision") ||
                        model.contains("llama-3.2-90b-vision") ||
                        model.contains("qwen-vl") ||
                        model.contains("qwen2-vl") ||
                        model.contains("llava") ||
                        model.contains("pixtral")
            }
            AIProviderType.OLLAMA, AIProviderType.OLLAMA_CLOUD -> {
                model.contains("llava") ||
                        model.contains("vision") ||
                        model.contains("moondream") ||
                        model.contains("minicpm-v") ||
                        model.contains("qwen2-vl") ||
                        model.contains("llama3.2-vision") ||
                        model.contains("bakllava")
            }
            AIProviderType.NVIDIA -> {
                model.contains("vision") ||
                        model.contains("vl") ||
                        model.contains("neva") ||
                        model.contains("llama-3.2-11b-vision") ||
                        model.contains("llama-3.2-90b-vision")
            }
            AIProviderType.CUSTOM -> {
                // For custom providers, check if model ID suggests vision or permit if vision keyword present
                model.contains("vision") ||
                        model.contains("llava") ||
                        model.contains("vl") ||
                        model.contains("gpt-4o") ||
                        model.contains("gemini") ||
                        model.contains("claude")
            }
            AIProviderType.HERMES_AGENT -> {
                true
            }
        }
    }

    /**
     * Determines whether the given model/provider supports direct native PDF document input.
     */
    fun supportsDirectPdf(providerType: AIProviderType, modelId: String?): Boolean {
        return when (providerType) {
            AIProviderType.GOOGLE_AI -> true // Gemini natively ingests PDF inline_data
            AIProviderType.ANTHROPIC -> true // Claude 3 natively ingests PDF document blocks
            else -> false
        }
    }

    /**
     * Human-friendly message when a model cannot process an image.
     */
    fun getUnsupportedVisionMessage(providerType: AIProviderType, modelId: String?): String {
        val name = modelId?.ifBlank { null } ?: providerType.displayName
        return "Model \"$name\" does not support image input. Please switch to a vision model (e.g. GPT-4o, Claude 3.5 Sonnet, or Gemini 1.5/2.0)."
    }
}
