package com.hermes.client.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val selectedModel: String? = null,
    val selectedProvider: String? = null,
    val agentId: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val sessionId: String = "session_${UUID.randomUUID().toString().take(8)}",
    val messageCount: Int = 0,
    val lastMessage: String? = null
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val attachments: List<Attachment> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val model: String? = null,
    val isStreaming: Boolean = false
)

enum class MessageRole {
    USER, ASSISTANT, SYSTEM
}

enum class MessageStatus {
    SENDING, SENT, STREAMING, COMPLETED, ERROR, PENDING_OFFLINE
}

@Serializable
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String,
    val size: Long = 0,
    val uri: String? = null,
    val localPath: String? = null,
    val uploadProgress: Float = 0f,
    val isUploaded: Boolean = false,
    val error: String? = null
)

data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val input: String? = null,
    val output: String? = null,
    val status: ToolCallStatus = ToolCallStatus.RUNNING,
    val isExpanded: Boolean = false
)

enum class ToolCallStatus {
    RUNNING, COMPLETED, FAILED, APPROVAL_REQUIRED
}

data class Model(
    val id: String,
    val name: String,
    val provider: String = "",
    val contextWindow: Int? = null,
    val capabilities: List<String> = emptyList(),
    val isAvailable: Boolean = true
)

data class Agent(
    val id: String,
    val name: String,
    val description: String = "",
    val capabilities: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val model: String? = null
)

data class ServerInfo(
    val version: String = "",
    val apiVersion: String = "",
    val modelCount: Int = 0,
    val agentCount: Int = 0,
    val capabilities: ServerCapabilities = ServerCapabilities()
)

data class ServerCapabilities(
    val streaming: Boolean = true,
    val models: Boolean = true,
    val agents: Boolean = true,
    val tools: Boolean = true,
    val fileUploads: Boolean = false,
    val tasks: Boolean = true,
    val approvals: Boolean = true,
    val runs: Boolean = true,
    val responses: Boolean = true
)

data class Task(
    val id: String,
    val name: String,
    val status: TaskStatus = TaskStatus.RUNNING,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val progress: Float? = null,
    val agentId: String? = null,
    val model: String? = null,
    val lastActivity: String? = null,
    val result: String? = null,
    val error: String? = null,
    val sessionId: String? = null
)

enum class TaskStatus {
    QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, PAUSED
}

data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val authType: AuthType = AuthType.BEARER_TOKEN,
    val isActive: Boolean = false,
    val lastConnectionState: ConnectionState = ConnectionState.UNKNOWN
)

enum class AuthType {
    NONE, BEARER_TOKEN, API_KEY, BASIC, JWT
}

enum class ConnectionState {
    CONNECTED, DISCONNECTED, ERROR, UNKNOWN
}
