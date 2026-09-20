package com.hermes.client.hermes.api

import com.hermes.client.hermes.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit service interface for the Hermes API.
 * Maps to the real Hermes HTTP endpoints.
 */
interface HermesApiService {

    // ─── Chat Completions (OpenAI-compatible) ───────────────────────

    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Body request: ChatCompletionRequestDto,
        @Header("X-Hermes-Session-Id") sessionId: String? = null,
        @Header("X-Hermes-Session-Key") sessionKey: String? = null
    ): Response<ChatCompletionResponseDto>

    // ─── Responses API ──────────────────────────────────────────────

    @POST("v1/responses")
    suspend fun createResponse(
        @Body request: ResponseRequestDto,
        @Header("X-Hermes-Session-Id") sessionId: String? = null
    ): Response<ResponseDto>

    @GET("v1/responses/{id}")
    suspend fun getResponse(
        @Path("id") responseId: String
    ): Response<ResponseDto>

    @DELETE("v1/responses/{id}")
    suspend fun deleteResponse(
        @Path("id") responseId: String
    ): Response<Unit>

    // ─── Models ─────────────────────────────────────────────────────

    @GET("v1/models")
    suspend fun getModels(): Response<ModelsResponseDto>

    @GET("api/model/options")
    suspend fun getModelOptions(): Response<ModelOptionsResponseDto>

    // ─── Runs API ───────────────────────────────────────────────────

    @POST("v1/runs")
    suspend fun createRun(
        @Body request: RunRequestDto,
        @Header("X-Hermes-Session-Id") sessionId: String? = null
    ): Response<RunResponseDto>

    @DELETE("v1/runs/{id}")
    suspend fun cancelRun(
        @Path("id") runId: String
    ): Response<Unit>

    // ─── Sessions API ───────────────────────────────────────────────

    @POST("api/sessions/{session_id}/chat")
    suspend fun sessionChat(
        @Path("session_id") sessionId: String,
        @Body request: ChatCompletionRequestDto
    ): Response<ChatCompletionResponseDto>

    // ─── Tool Approvals ─────────────────────────────────────────────

    @POST("v1/runs/{task_id}/approve/{approval_id}")
    suspend fun approveToolCall(
        @Path("task_id") taskId: String,
        @Path("approval_id") approvalId: String
    ): Response<Unit>

    @POST("v1/runs/{task_id}/reject/{approval_id}")
    suspend fun rejectToolCall(
        @Path("task_id") taskId: String,
        @Path("approval_id") approvalId: String
    ): Response<Unit>

    // ─── Files ──────────────────────────────────────────────────────

    @Multipart
    @POST("v1/files")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("purpose") purpose: RequestBody
    ): Response<FileUploadResponseDto>
}
