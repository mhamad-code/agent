package com.agent.ai.data.chat

/**
 * Pure tree operations on the message list. No Android and no database, so the whole
 * edit / regenerate / switch / delete behaviour is unit tested.
 *
 * A branch is a chain of messages linked by `parentId`. Siblings are the versions of the
 * same position in the conversation: an edit or a regenerate adds a sibling.
 */
object MessageTree {

    /** The rows of one branch, root first, excluding deleted and archived rows. */
    fun path(all: List<MessageEntity>, leafId: String?): List<MessageEntity> {
        if (leafId == null) return emptyList()
        val byId = all.associateBy { it.id }
        val out = ArrayList<MessageEntity>()
        var cursor = byId[leafId]
        var guard = 0
        while (cursor != null && guard++ < 10_000) {
            if (!cursor.deleted && !cursor.archived) out.add(cursor)
            cursor = cursor.parentId?.let(byId::get)
        }
        out.reverse()
        return out
    }

    /** The deepest child of [id] (or [id] itself when it has none). */
    fun leafOf(all: List<MessageEntity>, id: String): MessageEntity {
        val byParent = all.groupBy { it.parentId }
        var current = all.first { it.id == id }
        var guard = 0
        while (guard++ < 10_000) {
            val children = byParent[current.id].orEmpty().filterNot { it.deleted }
            val next = children.maxByOrNull { it.createdAt } ?: return current
            if (next.id == current.id) return current
            current = next
        }
        return current
    }

    /** Other versions at the same position, oldest first, excluding deleted rows. */
    fun siblings(all: List<MessageEntity>, message: MessageEntity): List<MessageEntity> =
        all.filter {
            it.parentId == message.parentId &&
                it.conversationId == message.conversationId &&
                !it.deleted
        }.sortedBy { it.createdAt }

    /** `index` is 1-based, `count` includes every version. Null when there is only one. */
    fun branchPosition(all: List<MessageEntity>, message: MessageEntity): Pair<Int, Int>? {
        val versions = siblings(all, message)
        if (versions.size < 2) return null
        val index = versions.indexOfFirst { it.id == message.id }
        if (index < 0) return null
        return (index + 1) to versions.size
    }

    /** The message and every descendant of it, on every branch. */
    fun subtreeIds(all: List<MessageEntity>, id: String): Set<String> {
        val byParent = all.groupBy { it.parentId }
        val out = LinkedHashSet<String>()
        val stack = ArrayDeque<String>().apply { add(id) }
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (!out.add(current)) continue
            byParent[current].orEmpty().forEach { stack.add(it.id) }
        }
        return out
    }

    /** The active leaf after everything from [id] onward is removed. */
    fun leafAfterDelete(all: List<MessageEntity>, id: String): String? {
        val message = all.firstOrNull { it.id == id } ?: return null
        return message.parentId
    }

    /** Siblings that are still alive next to [id], used after a delete. */
    fun survivingSibling(all: List<MessageEntity>, id: String): MessageEntity? {
        val message = all.firstOrNull { it.id == id } ?: return null
        return siblings(all, message).lastOrNull { it.id != id }
    }
}
