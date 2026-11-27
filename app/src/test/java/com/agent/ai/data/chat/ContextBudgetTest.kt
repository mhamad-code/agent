package com.agent.ai.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Token accounting, the 80 percent warning and how much a compaction replaces. */
class ContextBudgetTest {

    private fun message(id: String, text: String, summary: Boolean = false) = MessageEntity(
        id = id,
        conversationId = "c1",
        parentId = null,
        role = "assistant",
        text = text,
        status = MessageStatus.DONE,
        modelId = "m",
        createdAt = 1,
        summary = summary,
    )

    @Test
    fun `four characters per token, rounded up`() {
        assertEquals(1, ContextBudget.estimate(""))
        assertEquals(2, ContextBudget.estimate("abcd"))
        assertEquals(3, ContextBudget.estimate("abcde"))
        assertEquals(251, ContextBudget.estimate("a".repeat(1000)))
    }

    @Test
    fun `the breakdown splits system, memory, messages and attachments`() {
        val image = MessageEntity(
            id = "img",
            conversationId = "c1",
            parentId = null,
            role = "user",
            text = "",
            parts = MessageParts.encode(
                listOf(
                    MessagePart.Text("look"),
                    MessagePart.Image("/tmp/a.jpg", "a.jpg", "image/jpeg"),
                )
            ),
            status = MessageStatus.DONE,
            modelId = "m",
            createdAt = 1,
        )
        val breakdown = ContextBudget.breakdown(
            systemPrompt = "a".repeat(400),
            memory = listOf("b".repeat(40)),
            visible = listOf(message("a", "c".repeat(400)), image),
        )
        assertTrue(breakdown.system > 0)
        assertTrue(breakdown.memory > 0)
        assertTrue(breakdown.messages > 0)
        assertEquals(850, breakdown.attachments)
        assertEquals(
            breakdown.system + breakdown.memory + breakdown.messages + breakdown.attachments,
            breakdown.total,
        )
    }

    @Test
    fun `the ring warns at eighty percent, not only by colour`() {
        assertEquals(50, ContextBudget.percent(4_000, 8_000))
        assertEquals(100, ContextBudget.percent(9_000, 8_000))
        assertEquals(0, ContextBudget.percent(10, 0))
        assertFalse(ContextBudget.almostFull(79))
        assertTrue(ContextBudget.almostFull(80))
        assertTrue(ContextBudget.almostFull(100))
    }

    @Test
    fun `compaction keeps the newest messages and summarises the rest`() {
        val visible = (1..20).map { message("m$it", "x".repeat(400)) }
        val split = ContextBudget.messagesToSummarise(visible, keepRecentTokens = 500)
        assertTrue(split > 0)
        assertTrue(split < visible.size)
        // About 105 tokens per message, so four fit in 500 tokens.
        assertEquals(4, visible.size - split)
    }

    @Test
    fun `a second compaction folds the previous summary into the new one`() {
        val visible = listOf(
            message("m1", "x".repeat(4000)),
            message("s", "summary", summary = true),
            message("m2", "y".repeat(80)),
        )
        val split = ContextBudget.messagesToSummarise(visible, keepRecentTokens = 1_000)
        // The newest message is kept; the old summary and the message before it are replaced.
        assertEquals(2, split)
    }
}
