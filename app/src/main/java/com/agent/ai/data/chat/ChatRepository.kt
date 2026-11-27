package com.agent.ai.data.chat

import com.agent.ai.providers.ChatRequest
import com.agent.ai.providers.KiloGatewayProvider
import com.agent.ai.providers.Provider
import com.agent.ai.providers.ProviderError
import com.agent.ai.providers.ProviderErrorKind
import com.agent.ai.providers.ProviderException
import com.agent.ai.providers.Role
import com.agent.ai.providers.WireMessage
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * All chat persistence and the multi-step operations that touch more than one row:
 * appending a message, switching a branch, deleting a subtree, compacting the context.
 *
 * The message tree and the token accounting are pure functions in [MessageTree] and
 * [ContextBudget]; this class only reads and writes rows.
 */
class ChatRepository(
    private val dao: ChatDao,
    private val provider: Provider,
) {

    fun conversations(): Flow<List<ConversationEntity>> = dao.observeConversations()

    fun messages(conversationId: String): Flow<List<MessageEntity>> =
        dao.observeMessages(conversationId)

    fun models(): Flow<List<ModelEntity>> = dao.observeModels()

    fun memories(): Flow<List<MemoryEntity>> = dao.observeMemories()

    fun commands(): Flow<List<SlashCommandEntity>> = dao.observeCommands()

    fun draft(key: String): Flow<DraftEntity?> = dao.observeDraft(key)

    // ----- Conversations --------------------------------------------------------------

    suspend fun createConversation(
        modelId: String,
        title: String,
        autoTitle: Boolean = true,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertConversation(
            ConversationEntity(
                id = id,
                title = title.ifBlank { Untitled },
                modelId = modelId,
                createdAt = now,
                updatedAt = now,
                titleAuto = autoTitle,
            )
        )
        return id
    }

    suspend fun conversation(id: String): ConversationEntity? = dao.conversation(id)

    suspend fun allMessages(conversationId: String): List<MessageEntity> =
        dao.messages(conversationId)

    suspend fun renameByUser(conversationId: String, title: String) {
        dao.renameByUser(conversationId, title.ifBlank { Untitled }, System.currentTimeMillis())
    }

    suspend fun renameAuto(conversationId: String, title: String) {
        dao.setAutoTitle(conversationId, title)
    }

    suspend fun setPinned(conversationId: String, pinned: Boolean) {
        dao.setPinned(conversationId, pinned, System.currentTimeMillis())
    }

    suspend fun setConversationPrompt(conversationId: String, prompt: String?) {
        dao.setSystemPrompt(conversationId, prompt?.takeIf { it.isNotBlank() }, System.currentTimeMillis())
    }

    suspend fun setModel(conversationId: String, modelId: String) {
        dao.setConversationModel(conversationId, modelId, System.currentTimeMillis())
    }

    suspend fun deleteConversation(conversationId: String): List<MessagePart> {
        val parts = dao.messages(conversationId).flatMap { it.partsOrText() }
        dao.deleteConversation(conversationId)
        dao.deleteDraft(conversationId)
        return parts
    }

    suspend fun setActiveLeaf(conversationId: String, leafId: String?) {
        dao.setActiveLeaf(conversationId, leafId)
    }

    // ----- Messages -------------------------------------------------------------------

    suspend fun appendMessage(
        conversationId: String,
        parentId: String?,
        role: Role,
        parts: List<MessagePart>,
        status: String,
        modelId: String,
        summary: Boolean = false,
        makeActive: Boolean = true,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val row = MessageEntity(
            id = id,
            conversationId = conversationId,
            parentId = parentId,
            role = role.wire,
            text = MessageParts.text(parts),
            parts = MessageParts.encode(parts),
            status = status,
            modelId = modelId,
            createdAt = now,
            summary = summary,
        )
        dao.upsertMessage(row)
        syncFts(row)
        if (makeActive) dao.setActiveLeaf(conversationId, id)
        dao.touchConversation(conversationId, now)
        return id
    }

    suspend fun message(id: String): MessageEntity? = dao.message(id)

    suspend fun updateParts(
        messageId: String,
        parts: List<MessagePart>,
        status: String,
    ) {
        dao.updateParts(messageId, MessageParts.encode(parts), MessageParts.text(parts), status)
        dao.message(messageId)?.let { syncFts(it) }
    }

    suspend fun finish(
        messageId: String,
        parts: List<MessagePart>,
        status: String,
        errorKind: String? = null,
        detail: String? = null,
        truncated: Boolean = false,
    ) {
        updateParts(messageId, parts, status)
        dao.finishMessage(messageId, status, errorKind, detail, truncated)
        dao.messageConversation(messageId)?.let {
            dao.touchConversation(it, System.currentTimeMillis())
        }
    }

    /** Soft delete: the rows stay for the undo window, then [purge] removes them. */
    suspend fun softDelete(conversationId: String, messageId: String): Set<String> {
        val all = dao.messages(conversationId)
        val ids = MessageTree.subtreeIds(all, messageId)
        dao.setDeleted(ids.toList(), true)
        return ids
    }

    suspend fun restoreMessages(ids: Collection<String>) {
        if (ids.isNotEmpty()) dao.setDeleted(ids.toList(), false)
    }

    suspend fun purge(ids: Collection<String>) {
        if (ids.isEmpty()) return
        ids.forEach { dao.deleteFts(it) }
        dao.deleteMessagesById(ids.toList())
    }

    /** The next active leaf after removing [deletedIds] from the visible branch. */
    suspend fun activeLeafAfterDelete(conversationId: String, removed: Set<String>): String? {
        val all = dao.messages(conversationId)
        val alive = all.filterNot { it.id in removed || it.deleted }
        val current = dao.conversation(conversationId)?.activeLeafId
        if (current != null && current !in removed) {
            val leaf = MessageTree.leafOf(all, current)
            if (leaf.id !in removed) return leaf.id
        }
        return alive.maxByOrNull { it.createdAt }?.id
    }

    // ----- Branch switching -----------------------------------------------------------

    /** Points the conversation at the branch that contains [siblingId]. */
    suspend fun switchToSibling(conversationId: String, siblingId: String): String? {
        val all = dao.messages(conversationId)
        val target = all.firstOrNull { it.id == siblingId } ?: return null
        val leaf = MessageTree.leafOf(all, target.id)
        val leafId = if (leaf.deleted || leaf.archived) target.id else leaf.id
        dao.setActiveLeaf(conversationId, leafId)
        return leafId
    }

    // ----- Search ---------------------------------------------------------------------

    suspend fun search(query: String): List<SearchHit> {
        val expression = ftsQuery(query) ?: return emptyList()
        return runCatching { dao.search(expression) }.getOrElse { emptyList() }
    }

    // ----- Model list -----------------------------------------------------------------

    suspend fun refreshModels(): Result<Int> = try {
        val models = provider.listModels()
        val now = System.currentTimeMillis()
        dao.replaceModels(
            models.map { model ->
                ModelEntity(
                    id = model.id,
                    name = model.name,
                    free = model.free,
                    contextLength = model.contextLength,
                    description = model.description,
                    vision = model.capabilities.vision,
                    reasoning = model.capabilities.reasoning,
                    toolCalling = model.capabilities.toolCalling,
                    updatedAt = now,
                )
            }
        )
        Result.success(models.size)
    } catch (e: ProviderException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun cachedModels(): List<ModelEntity> = dao.cachedModels()

    // ----- Requests -------------------------------------------------------------------

    /**
     * The visible branch as it is sent to the model, oldest first. The system prompt is
     * the global one, then the conversation's own, then the memory block; attachments
     * travel as text, and images only when the model can read them.
     */
    suspend fun history(
        conversationId: String,
        leafId: String?,
        systemPrompt: String?,
        memory: List<String>,
        allowImages: Boolean,
        budget: Int = HISTORY_BUDGET,
    ): List<WireMessage> = withContext(Dispatchers.IO) {
        val visible = MessageTree.path(dao.messages(conversationId), leafId)
        val system = buildString {
            append(systemPrompt?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PROMPT)
            if (memory.isNotEmpty()) {
                append("\n\nThings to remember about the user:\n")
                memory.forEach { append("- ").append(it).append('\n') }
            }
        }.trim()

        val messages = visible.mapNotNull { message ->
            val parts = message.partsOrText()
            val role = when (message.role) {
                Role.User.wire -> Role.User
                Role.Assistant.wire -> Role.Assistant
                else -> null
            } ?: return@mapNotNull null

            val text = buildString {
                append(MessageParts.text(parts))
                MessageParts.files(parts).forEach { file ->
                    if (file.extracted.isNotBlank()) {
                        append("\n\n[File: ").append(file.name).append("]\n")
                        append(file.extracted)
                    }
                }
            }.trim()
            if (text.isBlank() && parts.none { it is MessagePart.Image }) return@mapNotNull null

            val images = if (allowImages) {
                MessageParts.images(parts).mapNotNull { image ->
                    runCatching {
                        val bytes = java.io.File(image.path).readBytes()
                        "data:${image.mime};base64," +
                            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    }.getOrNull()
                }
            } else {
                emptyList()
            }
            WireMessage(role, text, images)
        }

        TokenBudget.trim(
            listOf(WireMessage(Role.System, system)) + messages,
            budget,
        )
    }

    /**
     * A three to six word title from the same model. Falls back to the first 40 characters
     * of the first message, which is what [fallbackTitle] returns.
     */
    suspend fun autoTitle(
        conversationId: String,
        modelId: String,
        firstUserText: String,
        allowAuto: Boolean,
    ): String? {
        val conversation = dao.conversation(conversationId) ?: return null
        if (!conversation.titleAuto || !allowAuto) return null
        val fallback = fallbackTitle(firstUserText)
        val title = runCatching {
            provider.complete(
                ChatRequest(
                    model = modelId,
                    messages = listOf(
                        WireMessage(
                            Role.System,
                            "Write a title for this conversation in three to six words. " +
                                "Reply with the title only, no quotes, no punctuation at the end.",
                        ),
                        WireMessage(Role.User, firstUserText.take(2000)),
                    ),
                    maxTokens = 24,
                    temperature = 0.3,
                )
            )
        }.getOrNull()?.let(::cleanTitle)?.takeIf { it.isNotBlank() } ?: fallback
        if (title.isBlank()) return null
        if (dao.conversation(conversationId)?.titleAuto == false) return null
        dao.setAutoTitle(conversationId, title)
        return title
    }

    // ----- Compaction -----------------------------------------------------------------

    /**
     * Replaces the older part of the branch with one summary message. The originals are
     * archived, not deleted, and [restoreCompaction] puts them back.
     */
    suspend fun compact(
        conversationId: String,
        modelId: String,
        keepRecentTokens: Int,
    ): Boolean {
        val conversation = dao.conversation(conversationId) ?: return false
        val all = dao.messages(conversationId)
        val visible = MessageTree.path(all, conversation.activeLeafId)
        if (visible.size < 3) return false
        val split = ContextBudget.messagesToSummarise(visible, keepRecentTokens)
        if (split <= 0) return false

        val older = visible.take(split)
        val keep = visible.drop(split)
        if (keep.isEmpty()) return false

        val transcript = older.joinToString("\n\n") { message ->
            val name = if (message.role == Role.User.wire) "User" else "Assistant"
            "$name: ${MessageParts.text(message.partsOrText()).take(4000)}"
        }
        val summary = runCatching {
            provider.complete(
                ChatRequest(
                    model = modelId,
                    messages = listOf(
                        WireMessage(
                            Role.System,
                            "Summarise the conversation so it can continue. Keep every decision, " +
                                "name, number and open question. Write in the third person, at most " +
                                "250 words. Reply with the summary only.",
                        ),
                        WireMessage(Role.User, transcript),
                    ),
                    maxTokens = 500,
                    temperature = 0.2,
                )
            )
        }.getOrNull()?.trim()
        if (summary.isNullOrBlank()) return false

        val summaryId = appendMessage(
            conversationId = conversationId,
            parentId = null,
            role = Role.Assistant,
            parts = listOf(MessagePart.Text("Summary of the earlier conversation:\n\n$summary")),
            status = MessageStatus.DONE,
            modelId = modelId,
            summary = true,
            makeActive = false,
        )
        val firstKept = keep.first()
        val record = CompactionRecord(
            summaryId = summaryId,
            firstKeptId = firstKept.id,
            originalParentId = firstKept.parentId,
            archivedIds = older.map { it.id },
            previousLeafId = conversation.activeLeafId,
        )
        dao.setParent(firstKept.id, summaryId)
        dao.setArchived(record.archivedIds, true)
        dao.setCompaction(conversationId, record.encode())
        dao.setActiveLeaf(conversationId, keep.last().id)
        return true
    }

    /** Undoes the last compaction, putting the archived messages back where they were. */
    suspend fun restoreCompaction(conversationId: String): Boolean {
        val conversation = dao.conversation(conversationId) ?: return false
        val record = conversation.compaction?.let(CompactionRecord::decode) ?: return false
        dao.setArchived(record.archivedIds, false)
        dao.setParent(record.firstKeptId, record.originalParentId)
        dao.setDeleted(listOf(record.summaryId), true)
        dao.deleteMessagesById(listOf(record.summaryId))
        dao.deleteFts(record.summaryId)
        dao.setCompaction(conversationId, null)
        dao.setActiveLeaf(conversationId, record.previousLeafId ?: record.firstKeptId)
        return true
    }

    // ----- Drafts ---------------------------------------------------------------------

    suspend fun saveDraft(key: String, text: String) {
        if (text.isBlank()) {
            dao.deleteDraft(key)
        } else {
            dao.upsertDraft(DraftEntity(key, text, System.currentTimeMillis()))
        }
    }

    suspend fun draftOnce(key: String): String? = dao.draft(key)?.text

    // ----- Memory ---------------------------------------------------------------------

    suspend fun addMemory(text: String): String {
        val id = UUID.randomUUID().toString()
        dao.upsertMemory(MemoryEntity(id, text.trim(), "general", System.currentTimeMillis()))
        return id
    }

    suspend fun updateMemory(id: String, text: String) {
        val existing = dao.memories().firstOrNull { it.id == id } ?: return
        dao.upsertMemory(existing.copy(text = text.trim()))
    }

    suspend fun deleteMemory(id: String) {
        dao.deleteMemory(id)
    }

    suspend fun clearMemories() {
        dao.clearMemories()
    }

    /** Newest first, capped by [maxTokens] so memory never crowds out the conversation. */
    suspend fun memoryForPrompt(enabled: Boolean, maxTokens: Int = MEMORY_TOKEN_CAP): List<String> {
        if (!enabled) return emptyList()
        val out = ArrayList<String>()
        var used = 0
        dao.memories().forEach { memory ->
            val cost = TokenBudget.estimate(memory.text) + 3
            if (used + cost > maxTokens) return@forEach
            out.add(memory.text)
            used += cost
        }
        return out
    }

    // ----- Slash commands -------------------------------------------------------------

    suspend fun saveCommand(id: String?, name: String, prompt: String, description: String): String {
        val commandId = id ?: UUID.randomUUID().toString()
        dao.upsertCommand(
            SlashCommandEntity(
                id = commandId,
                name = name.trim().removePrefix("/").trim(),
                prompt = prompt,
                description = description,
            )
        )
        return commandId
    }

    suspend fun deleteCommand(id: String) {
        dao.deleteCommand(id)
    }

    suspend fun commandList(): List<SlashCommandEntity> = dao.commands()

    // ----- Errors ---------------------------------------------------------------------

    private suspend fun syncFts(row: MessageEntity) {
        dao.deleteFts(row.id)
        if (row.text.isNotBlank() && !row.deleted) {
            dao.insertFts(
                MessageFtsEntity(
                    messageId = row.id,
                    conversationId = row.conversationId,
                    text = row.text,
                )
            )
        }
    }

    companion object {
        const val Untitled = "New chat"

        /** Token budget for one request, well inside the free models' context windows. */
        const val HISTORY_BUDGET = 6_000

        /** How much memory is added to the system prompt. */
        const val MEMORY_TOKEN_CAP = 500

        const val DEFAULT_SYSTEM_PROMPT =
            "You are Agent, a concise and helpful assistant. Answer in English. " +
                "Use Markdown for structure, including tables and fenced code blocks with a language name."

        fun defaultModelId(): String = KiloGatewayProvider.AUTO_FREE_MODEL

        fun titleFrom(text: String): String =
            text.lineSequence().firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.take(48)
                ?.trimEnd()
                .orEmpty()

        /** The first 40 characters of the first message, the title fallback. */
        fun fallbackTitle(text: String): String {
            val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (line.length <= 40) return line
            val cut = line.take(40).trimEnd()
            return "$cut…"
        }

        /** Strips quotes and trailing punctuation from a model-written title. */
        fun cleanTitle(raw: String): String = raw
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .trim()
            .trim('"', '\'', '`', '“', '”', '‘', '’')
            .trimEnd('.', ':', ';', '!')
            .split(Regex("\\s+"))
            .take(8)
            .joinToString(" ")
            .take(60)

        fun errorKindOf(error: ProviderError): String = error.kind.name

        fun kindOf(name: String?): ProviderErrorKind? =
            name?.let { runCatching { ProviderErrorKind.valueOf(it) }.getOrNull() }

        /** Turns what the user typed into an FTS4 query; null when nothing is left. */
        fun ftsQuery(input: String): String? {
            val terms = input.lowercase()
                .split(Regex("[^\\p{L}\\p{N}_]+"))
                .filter { it.isNotBlank() && it.length > 1 }
            if (terms.isEmpty()) return null
            return terms.joinToString(" AND ") { "\"$it\"*" }
        }
    }
}

/** What a compaction replaced, so it can be undone exactly. */
data class CompactionRecord(
    val summaryId: String,
    val firstKeptId: String,
    val originalParentId: String?,
    val archivedIds: List<String>,
    val previousLeafId: String?,
) {
    fun encode(): String = buildJsonObject {
        put("summaryId", summaryId)
        put("firstKeptId", firstKeptId)
        originalParentId?.let { put("originalParentId", it) }
        previousLeafId?.let { put("previousLeafId", it) }
        putJsonArray("archivedIds") { archivedIds.forEach { add(JsonPrimitive(it)) } }
    }.toString()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(raw: String): CompactionRecord? {
            val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
                ?: return null
            val summaryId = obj.str("summaryId") ?: return null
            val firstKeptId = obj.str("firstKeptId") ?: return null
            val archived = (obj["archivedIds"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.content }
            return CompactionRecord(
                summaryId = summaryId,
                firstKeptId = firstKeptId,
                originalParentId = obj.str("originalParentId"),
                archivedIds = archived,
                previousLeafId = obj.str("previousLeafId"),
            )
        }

        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.content
    }
}
