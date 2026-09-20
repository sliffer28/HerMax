package com.hermes.client.hermes.api

/**
 * Normalized event model for the Hermes streaming protocol.
 * All streaming sources (SSE from /v1/chat/completions, run events, etc.)
 * are normalized into this sealed hierarchy before reaching the UI.
 */
sealed class HermesEvent {

    /** A chunk of text from the assistant's response */
    data class TextDelta(
        val messageId: String,
        val text: String
    ) : HermesEvent()

    /** The assistant's response is complete */
    data class Completed(
        val messageId: String,
        val finishReason: String? = null
    ) : HermesEvent()

    /** Agent started thinking / processing */
    data class ThinkingStarted(
        val messageId: String
    ) : HermesEvent()

    /** Agent thinking content delta */
    data class ThinkingDelta(
        val messageId: String,
        val text: String
    ) : HermesEvent()

    /** A tool execution has started */
    data class ToolStarted(
        val taskId: String,
        val toolName: String,
        val toolInput: String? = null
    ) : HermesEvent()

    /** A tool execution has finished */
    data class ToolFinished(
        val taskId: String,
        val toolName: String,
        val summary: String? = null
    ) : HermesEvent()

    /** Agent requests human approval before proceeding */
    data class ApprovalRequired(
        val taskId: String,
        val approvalId: String,
        val toolName: String,
        val description: String
    ) : HermesEvent()

    /** Agent status update (searching, reading, analyzing...) */
    data class StatusUpdate(
        val status: String,
        val detail: String? = null
    ) : HermesEvent()

    /** Task/run progress update */
    data class TaskProgress(
        val runId: String,
        val progress: Float?,
        val status: String
    ) : HermesEvent()

    /** An error occurred during streaming */
    data class Error(
        val message: String,
        val code: String? = null
    ) : HermesEvent()

    /** Stream connection opened */
    data object Connected : HermesEvent()

    /** Stream connection closed */
    data object Disconnected : HermesEvent()
}
