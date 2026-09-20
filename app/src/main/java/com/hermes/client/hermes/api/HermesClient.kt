package com.hermes.client.hermes.api

import com.hermes.client.hermes.models.*
import kotlinx.coroutines.flow.Flow

/**
 * Core interface for communicating with the Hermes agentic server.
 * All Hermes API interactions go through this interface, enabling
 * adapter-based architecture (REST, WebSocket, mock, etc.)
 */
interface HermesClient {

    /** Test connectivity and retrieve server info */
    suspend fun getServerInfo(): Result<ServerInfoDto>

    /** GET /v1/models — basic model discovery */
    suspend fun getModels(): Result<List<ModelDto>>

    /** GET /api/model/options — Hermes-aware provider/model inventory */
    suspend fun getModelOptions(): Result<ModelOptionsResponseDto>

    /** POST /v1/chat/completions — non-streaming chat */
    suspend fun chatCompletion(request: ChatCompletionRequestDto): Result<ChatCompletionResponseDto>

    /** POST /v1/chat/completions with stream=true — streaming chat via SSE */
    fun streamChatCompletion(request: ChatCompletionRequestDto): Flow<HermesEvent>

    /** POST /v1/responses — server-side conversation state */
    suspend fun createResponse(request: ResponseRequestDto): Result<ResponseDto>

    /** GET /v1/responses/{id} */
    suspend fun getResponse(responseId: String): Result<ResponseDto>

    /** DELETE /v1/responses/{id} */
    suspend fun deleteResponse(responseId: String): Result<Unit>

    /** POST /v1/runs — long-running agent operations */
    suspend fun createRun(request: RunRequestDto): Result<RunResponseDto>

    /** Stream events from a running agent task */
    fun streamRunEvents(runId: String): Flow<HermesEvent>

    /** Cancel a running task */
    suspend fun cancelRun(runId: String): Result<Unit>

    /** Upload a file to Hermes */
    suspend fun uploadFile(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray
    ): Result<FileUploadResponseDto>

    /** Approve a tool call */
    suspend fun approveToolCall(
        taskId: String,
        approvalId: String
    ): Result<Unit>

    /** Reject a tool call */
    suspend fun rejectToolCall(
        taskId: String,
        approvalId: String
    ): Result<Unit>

    /** Check if the client is currently connected */
    fun isConnected(): Boolean

    /** Update the base URL (when user changes server config) */
    fun updateBaseUrl(url: String)
}
