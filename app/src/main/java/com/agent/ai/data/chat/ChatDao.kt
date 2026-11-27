package com.agent.ai.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeConversation(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, rowid ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, rowid ASC")
    suspend fun messages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun message(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND parentId IS :parentId ORDER BY createdAt ASC")
    suspend fun childrenOf(conversationId: String, parentId: String?): List<MessageEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversation(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("UPDATE messages SET parts = :parts, text = :text, status = :status WHERE id = :id")
    suspend fun updateParts(id: String, parts: String, text: String, status: String)

    @Query("UPDATE messages SET text = :text, status = :status WHERE id = :id")
    suspend fun updateMessageText(id: String, text: String, status: String)

    @Query(
        "UPDATE messages SET status = :status, errorKind = :errorKind, errorDetail = :detail, " +
            "truncated = :truncated WHERE id = :id"
    )
    suspend fun finishMessage(
        id: String,
        status: String,
        errorKind: String?,
        detail: String?,
        truncated: Boolean,
    )

    @Query("UPDATE messages SET parentId = :parentId WHERE id = :id")
    suspend fun setParent(id: String, parentId: String?)

    @Query("UPDATE messages SET deleted = :deleted WHERE id IN (:ids)")
    suspend fun setDeleted(ids: List<String>, deleted: Boolean)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessagesById(ids: List<String>)

    @Query("UPDATE messages SET archived = :archived WHERE conversationId = :conversationId")
    suspend fun setArchivedForAll(conversationId: String, archived: Boolean)

    @Query("UPDATE messages SET archived = :archived WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<String>, archived: Boolean)

    @Query("SELECT conversationId FROM messages WHERE id = :id")
    suspend fun messageConversation(id: String): String?

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET title = :title, titleAuto = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameByUser(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun setAutoTitle(id: String, title: String)

    @Query("UPDATE conversations SET modelId = :modelId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setConversationModel(id: String, modelId: String, updatedAt: Long)

    @Query("UPDATE conversations SET activeLeafId = :leafId WHERE id = :id")
    suspend fun setActiveLeaf(id: String, leafId: String?)

    @Query("UPDATE conversations SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE conversations SET systemPrompt = :prompt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSystemPrompt(id: String, prompt: String?, updatedAt: Long)

    @Query("UPDATE conversations SET compaction = :record WHERE id = :id")
    suspend fun setCompaction(id: String, record: String?)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchConversation(id: String, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    // ----- Drafts --------------------------------------------------------------------

    @Query("SELECT * FROM drafts WHERE conversationId = :key")
    fun observeDraft(key: String): Flow<DraftEntity?>

    @Query("SELECT * FROM drafts WHERE conversationId = :key")
    suspend fun draft(key: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE conversationId = :key")
    suspend fun deleteDraft(key: String)

    // ----- Search --------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(row: MessageFtsEntity)

    @Query("DELETE FROM message_fts WHERE messageId = :messageId")
    suspend fun deleteFts(messageId: String)

    @Query(
        "SELECT m.id AS messageId, m.conversationId AS conversationId, m.text AS text, " +
            "m.role AS role, c.title AS title, m.createdAt AS createdAt " +
            "FROM message_fts f JOIN messages m ON m.id = f.messageId " +
            "JOIN conversations c ON c.id = m.conversationId " +
            "WHERE message_fts MATCH :query AND m.deleted = 0 " +
            "ORDER BY m.createdAt DESC LIMIT 60"
    )
    suspend fun search(query: String): List<SearchHit>

    // ----- Models --------------------------------------------------------------------

    @Query("SELECT * FROM models ORDER BY free DESC, name ASC")
    fun observeModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun model(id: String): ModelEntity?

    @Query("SELECT * FROM models ORDER BY free DESC, name ASC")
    suspend fun cachedModels(): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModels(models: List<ModelEntity>)

    @Query("DELETE FROM models")
    suspend fun clearModels()

    @Transaction
    suspend fun replaceModels(models: List<ModelEntity>) {
        clearModels()
        upsertModels(models)
    }

    // ----- Memory --------------------------------------------------------------------

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observeMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    suspend fun memories(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("DELETE FROM memories")
    suspend fun clearMemories()

    // ----- Slash commands ------------------------------------------------------------

    @Query("SELECT * FROM slash_commands ORDER BY name ASC")
    fun observeCommands(): Flow<List<SlashCommandEntity>>

    @Query("SELECT * FROM slash_commands ORDER BY name ASC")
    suspend fun commands(): List<SlashCommandEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCommand(command: SlashCommandEntity)

    @Query("DELETE FROM slash_commands WHERE id = :id")
    suspend fun deleteCommand(id: String)
}
