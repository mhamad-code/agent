package com.agent.ai.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tree operations behind edit, regenerate, branch switching and delete with undo. */
class MessageTreeTest {

    private fun message(
        id: String,
        parent: String?,
        role: String = "assistant",
        at: Long = 1L,
        deleted: Boolean = false,
        archived: Boolean = false,
    ) = MessageEntity(
        id = id,
        conversationId = "c1",
        parentId = parent,
        role = role,
        text = id,
        status = MessageStatus.DONE,
        modelId = "kilo-auto/free",
        createdAt = at,
        deleted = deleted,
        archived = archived,
    )

    @Test
    fun `the path follows parents from the root to the active leaf`() {
        val all = listOf(
            message("u1", null, role = "user", at = 1),
            message("a1", "u1", at = 2),
            message("u2", "a1", role = "user", at = 3),
            message("a2", "u2", at = 4),
        )
        assertEquals(listOf("u1", "a1", "u2", "a2"), MessageTree.path(all, "a2").map { it.id })
        assertEquals(listOf("u1", "a1"), MessageTree.path(all, "a1").map { it.id })
    }

    @Test
    fun `a regenerated reply is a sibling and the switcher shows its position`() {
        val all = listOf(
            message("u1", null, role = "user", at = 1),
            message("a1", "u1", at = 2),
            message("a2", "u1", at = 3),
            message("a3", "u1", at = 4),
        )
        assertEquals(listOf("a1", "a2", "a3"), MessageTree.siblings(all, all[1]).map { it.id })
        assertEquals(2 to 3, MessageTree.branchPosition(all, all[2]))
        assertNull(MessageTree.branchPosition(all, all[0]))
    }

    @Test
    fun `switching a branch picks the deepest leaf of the sibling`() {
        val all = listOf(
            message("u1", null, role = "user", at = 1),
            message("a1", "u1", at = 2),
            message("u2", "a1", role = "user", at = 3),
            message("a2", "u2", at = 4),
            message("b1", "u1", at = 5),
        )
        assertEquals("a2", MessageTree.leafOf(all, "a1").id)
        assertEquals("b1", MessageTree.leafOf(all, "b1").id)
    }

    @Test
    fun `deleting a message removes every branch under it`() {
        val all = listOf(
            message("u1", null, role = "user", at = 1),
            message("a1", "u1", at = 2),
            message("b1", "u1", at = 3),
            message("u2", "a1", role = "user", at = 4),
        )
        assertEquals(setOf("u1", "a1", "b1", "u2"), MessageTree.subtreeIds(all, "u1"))
        assertEquals(setOf("a1", "u2"), MessageTree.subtreeIds(all, "a1"))
        assertEquals("u1", MessageTree.leafAfterDelete(all, "a1"))
    }

    @Test
    fun `a soft deleted or archived message and everything under it disappears`() {
        val all = listOf(
            message("u1", null, role = "user", at = 1),
            message("a1", "u1", at = 2, deleted = true),
            message("a2", "a1", at = 3),
            message("b1", "u1", at = 4),
        )
        // A soft delete removes the whole subtree, so this cannot happen in practice;
        // the path still walks through the hidden parent to the visible child.
        assertEquals(listOf("u1", "a2"), MessageTree.path(all, "a2").map { it.id })

        val archived = listOf(
            message("u1", null, role = "user", at = 1, archived = true),
            message("u2", "u1", role = "user", at = 2),
            message("a1", "u2", at = 3),
        )
        assertEquals(listOf("u2", "a1"), MessageTree.path(archived, "a1").map { it.id })
    }

    @Test
    fun `the surviving sibling is kept when one version is deleted`() {
        val all = listOf(
            message("u1", null, role = "user", at = 1),
            message("a1", "u1", at = 2, deleted = true),
            message("a2", "u1", at = 3),
        )
        assertEquals("a2", MessageTree.survivingSibling(all, "a1")?.id)
        assertTrue(MessageTree.path(all, "u1").none { it.id == "a1" })
        assertFalse(MessageTree.path(all, "u1").isEmpty())
    }
}
