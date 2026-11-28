package com.agent.ai.data.chat

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved conversation.
 *
 * Messages form a tree: [activeLeafId] points at the message whose path from the root is
 * the conversation the user sees and sends. Regenerating or editing a message adds a
 * sibling instead of replacing anything, so every version stays reachable.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val modelId: String,
    val activeLeafId: String? = null,
    /** False once the user typed a title, so the auto title never overwrites it. */
    val titleAuto: Boolean = true,
    val pinned: Boolean = false,
    /** Optional per-conversation instruction, added after the global one. */
    val systemPrompt: String? = null,
    /** JSON record of the last compaction, so the archived messages can be restored. */
    val compaction: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** How a message ended, so a stopped or failed reply is still readable after a restart. */
object MessageStatus {
    const val SENDING = "sending"
    const val STREAMING = "streaming"
    const val DONE = "done"
    const val FAILED = "failed"
    const val STOPPED = "stopped"

    /** Saved but not sent yet, because the device is offline. */
    const val WAITING = "waiting"
}

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversationId"), Index("parentId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    /** Null only for the first message of a branch. */
    val parentId: String? = null,
    val role: String,
    /** Flattened text of the text parts, kept for previews and search. */
    val text: String,
    /** JSON list of [MessagePart]s. Empty on rows migrated from version 1. */
    val parts: String = "",
    val status: String,
    val modelId: String,
    val createdAt: Long,
    /** [com.agent.ai.providers.ProviderErrorKind] name when [status] is failed. */
    val errorKind: String? = null,
    /** The provider's own words, shown inside the failed message. */
    val errorDetail: String? = null,
    /** Hidden from the visible path after a compaction, but still stored. */
    val archived: Boolean = false,
    /** A compaction summary, not something the model or the user wrote. */
    val summary: Boolean = false,
    /** The reply was cut off (token limit or a broken stream), so Continue is offered. */
    val truncated: Boolean = false,
    /** Soft delete: the row is removed for good only after the undo window. */
    val deleted: Boolean = false,
)

/** One row per conversation, plus one for the not-yet-created new chat. */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val conversationId: String,
    val text: String,
    val updatedAt: Long,
)

/** The model list cached in Room, so the picker works offline. */
@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val free: Boolean,
    val contextLength: Int,
    val description: String,
    val vision: Boolean = false,
    val reasoning: Boolean = false,
    val toolCalling: Boolean = false,
    val updatedAt: Long,
)

/** Local memory. Only the general scope exists at this stage. */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val text: String,
    val scope: String = "general",
    val createdAt: Long,
)

/** A user-defined slash command. The built-in ones live in code, not here. */
@Entity(tableName = "slash_commands")
data class SlashCommandEntity(
    @PrimaryKey val id: String,
    val name: String,
    val prompt: String,
    val description: String = "",
)

/** Full-text index over message text, for searching every conversation. */
@Fts4
@Entity(tableName = "message_fts")
data class MessageFtsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Int = 0,
    val messageId: String,
    val conversationId: String,
    val text: String,
)

/** Every tool call the agent made, for the Security and approvals screen. */
@Entity(tableName = "tool_activity")
data class ToolActivityEntity(
    @PrimaryKey val id: String,
    val at: Long,
    val tool: String,
    val input: String,
    val result: String,
    /** `automatic`, `once`, `always` or `rejected`. */
    val approvedBy: String,
    val ok: Boolean,
)

/** One search hit, joined with its conversation for the list. */
data class SearchHit(
    val messageId: String,
    val conversationId: String,
    val text: String,
    val role: String,
    val title: String,
    val createdAt: Long,
)
