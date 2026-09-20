package com.hermes.client.hermes.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Chat Completions ───────────────────────────────────────────────

@Serializable
data class ChatCompletionRequestDto(
    val model: String = "hermes-agent",
    val messages: List<ChatMessageDto>,
    val stream: Boolean = false,
    val provider: String? = null,
    @SerialName("model_options") val modelOptions: Map<String, String>? = null,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class ChatMessageDto(
    val role: String, // "user", "assistant", "system"
    val content: String,
    val name: String? = null
)

@Serializable
data class ChatCompletionResponseDto(
    val id: String = "",
    @SerialName("object") val objectType: String = "chat.completion",
    val created: Long = 0,
    val model: String = "",
    val choices: List<ChoiceDto> = emptyList(),
    val usage: UsageDto? = null
)

@Serializable
data class ChoiceDto(
    val index: Int = 0,
    val message: ChatMessageDto? = null,
    val delta: DeltaDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class DeltaDto(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

// ─── SSE Streaming Chunk ────────────────────────────────────────────

@Serializable
data class StreamChunkDto(
    val id: String = "",
    @SerialName("object") val objectType: String = "",
    val created: Long = 0,
    val model: String = "",
    val choices: List<ChoiceDto> = emptyList()
)

// ─── Responses API ──────────────────────────────────────────────────

@Serializable
data class ResponseRequestDto(
    val model: String = "hermes-agent",
    val input: String,
    val conversation: String? = null,
    val store: Boolean = true,
    val provider: String? = null,
    @SerialName("model_options") val modelOptions: Map<String, String>? = null
)

@Serializable
data class ResponseDto(
    val id: String = "",
    @SerialName("object") val objectType: String = "",
    val status: String = "",
    val output: List<ResponseOutputDto>? = null,
    val model: String = "",
    val usage: UsageDto? = null
)

@Serializable
data class ResponseOutputDto(
    val type: String = "",
    val text: String? = null,
    val content: String? = null
)

// ─── Models ─────────────────────────────────────────────────────────

@Serializable
data class ModelsResponseDto(
    @SerialName("object") val objectType: String = "list",
    val data: List<ModelDto> = emptyList()
)

@Serializable
data class ModelDto(
    val id: String = "",
    @SerialName("object") val objectType: String = "model",
    val created: Long = 0,
    @SerialName("owned_by") val ownedBy: String = ""
)

@Serializable
data class ModelOptionsResponseDto(
    val providers: List<ProviderDto>? = null,
    val models: List<ModelOptionDto>? = null,
    val options: Map<String, String>? = null
)

@Serializable
data class ProviderDto(
    val id: String = "",
    val name: String = "",
    val models: List<ModelOptionDto>? = null,
    val enabled: Boolean = true
)

@Serializable
data class ModelOptionDto(
    val id: String = "",
    val name: String = "",
    val provider: String = "",
    @SerialName("context_window") val contextWindow: Int? = null,
    val capabilities: List<String>? = null,
    val available: Boolean = true
)

// ─── Runs API ───────────────────────────────────────────────────────

@Serializable
data class RunRequestDto(
    val input: String,
    @SerialName("session_id") val sessionId: String? = null,
    val model: String? = null,
    val provider: String? = null,
    @SerialName("model_options") val modelOptions: Map<String, String>? = null
)

@Serializable
data class RunResponseDto(
    val id: String = "",
    @SerialName("run_id") val runId: String = "",
    val status: String = "",
    @SerialName("session_id") val sessionId: String? = null
)

// ─── Server Info ────────────────────────────────────────────────────

@Serializable
data class ServerInfoDto(
    val version: String = "",
    val api: String = "",
    val models: Int = 0,
    val agents: Int = 0,
    val capabilities: ServerCapabilitiesDto? = null
)

@Serializable
data class ServerCapabilitiesDto(
    val streaming: Boolean = true,
    val models: Boolean = true,
    val agents: Boolean = true,
    val tools: Boolean = true,
    @SerialName("file_uploads") val fileUploads: Boolean = false,
    val tasks: Boolean = true,
    val approvals: Boolean = true,
    val runs: Boolean = true,
    val responses: Boolean = true
)

// ─── Files ──────────────────────────────────────────────────────────

@Serializable
data class FileUploadResponseDto(
    val id: String = "",
    val filename: String = "",
    val bytes: Long = 0,
    val purpose: String = ""
)
