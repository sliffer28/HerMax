package com.hermes.client.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── Entities ───────────────────────────────────────────────────────

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedModel: String?,
    val selectedProvider: String?,
    val agentId: String?,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val sessionId: String,
    val lastMessage: String? = null
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // USER, ASSISTANT, SYSTEM
    val content: String,
    val timestamp: Long,
    val status: String, // SENDING, SENT, STREAMING, COMPLETED, ERROR
    val model: String?,
    val attachmentsJson: String?, // JSON serialized attachments
    val toolCallsJson: String?   // JSON serialized tool calls
)

@Entity(tableName = "server_profiles")
data class ServerProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val authType: String, // NONE, BEARER_TOKEN, API_KEY, BASIC, JWT
    val isActive: Boolean = false,
    val lastConnectionState: String = "UNKNOWN"
)

// ─── DAOs ───────────────────────────────────────────────────────────

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessage(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int
}

@Dao
interface ServerProfileDao {

    @Query("SELECT * FROM server_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ServerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ServerProfileEntity)

    @Update
    suspend fun updateProfile(profile: ServerProfileEntity)

    @Query("DELETE FROM server_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("UPDATE server_profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE server_profiles SET isActive = 1 WHERE id = :id")
    suspend fun activateProfile(id: String)

    @Transaction
    suspend fun setActiveProfile(id: String) {
        deactivateAll()
        activateProfile(id)
    }
}

// ─── Database ───────────────────────────────────────────────────────

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ServerProfileEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class HermesDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun serverProfileDao(): ServerProfileDao
}
