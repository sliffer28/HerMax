package com.hermes.client.data.repository

import com.hermes.client.data.local.ConversationDao
import com.hermes.client.data.local.ConversationEntity
import com.hermes.client.data.local.MessageDao
import com.hermes.client.data.local.MessageEntity
import com.hermes.client.domain.model.*
import com.hermes.client.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ConversationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchConversations(query: String): Flow<List<Conversation>> {
        return conversationDao.searchConversations(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getConversation(id: String): Conversation? {
        return conversationDao.getConversation(id)?.toDomain()
    }

    override suspend fun createConversation(conversation: Conversation): Conversation {
        conversationDao.insertConversation(conversation.toEntity())
        return conversation
    }

    override suspend fun updateConversation(conversation: Conversation) {
        conversationDao.updateConversation(conversation.toEntity())
    }

    override suspend fun deleteConversation(id: String) {
        conversationDao.deleteConversation(id)
    }

    override fun getMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.getMessages(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMessage(id: String): Message? {
        return messageDao.getMessage(id)?.toDomain()
    }

    override suspend fun insertMessage(message: Message): Message {
        messageDao.insertMessage(message.toEntity())
        return message
    }

    override suspend fun updateMessage(message: Message) {
        messageDao.updateMessage(message.toEntity())
    }

    override suspend fun deleteMessage(id: String) {
        messageDao.deleteMessage(id)
    }

    override suspend fun clearAllConversations() {
        conversationDao.clearAll()
    }

    override fun searchMessages(query: String): Flow<List<Message>> {
        return messageDao.searchMessages(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // ─── Mappers ────────────────────────────────────────────────

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        selectedModel = selectedModel,
        selectedProvider = selectedProvider,
        agentId = agentId,
        isPinned = isPinned,
        isArchived = isArchived,
        sessionId = sessionId,
        lastMessage = lastMessage
    )

    private fun Conversation.toEntity() = ConversationEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        selectedModel = selectedModel,
        selectedProvider = selectedProvider,
        agentId = agentId,
        isPinned = isPinned,
        isArchived = isArchived,
        sessionId = sessionId,
        lastMessage = lastMessage
    )

    private fun MessageEntity.toDomain() = Message(
        id = id,
        conversationId = conversationId,
        role = MessageRole.valueOf(role),
        content = content,
        timestamp = timestamp,
        status = MessageStatus.valueOf(status),
        model = model,
        attachments = attachmentsJson?.let {
            runCatching { json.decodeFromString<List<Attachment>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
    )

    private fun Message.toEntity() = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role.name,
        content = content,
        timestamp = timestamp,
        status = status.name,
        model = model,
        attachmentsJson = if (attachments.isNotEmpty()) {
            runCatching { json.encodeToString(attachments) }.getOrNull()
        } else null,
        toolCallsJson = if (toolCalls.isNotEmpty()) {
            json.encodeToString(toolCalls.map { mapOf("name" to it.name, "status" to it.status.name) })
        } else null
    )
}
