package com.agent.ai.data.chat

import com.agent.ai.providers.Role
import com.agent.ai.providers.WireMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBudgetTest {

    private fun user(text: String) = WireMessage(Role.User, text)
    private fun assistant(text: String) = WireMessage(Role.Assistant, text)

    @Test
    fun `keeps the system prompt, the first message and the newest messages`() {
        val system = WireMessage(Role.System, "system prompt")
        val first = user("first question")
        val middle = (1..40).map { assistant("old reply number $it " + "x".repeat(200)) }
        val last = user("latest question")

        val trimmed = TokenBudget.trim(listOf(system) + first + middle + last, maxTokens = 400)

        assertEquals(system, trimmed.first())
        assertEquals(first, trimmed[1])
        assertEquals(last, trimmed.last())
        assertTrue(trimmed.size < middle.size + 3)
    }

    @Test
    fun `a short history is returned unchanged`() {
        val messages = listOf(
            WireMessage(Role.System, "system prompt"),
            user("hi"),
            assistant("hello"),
        )
        assertEquals(messages, TokenBudget.trim(messages, maxTokens = 4_000))
    }

    @Test
    fun `the newest message is kept even when it alone is over budget`() {
        val messages = listOf(
            WireMessage(Role.System, "system"),
            user("first"),
            user("y".repeat(40_000)),
        )
        val trimmed = TokenBudget.trim(messages, maxTokens = 100)
        assertEquals("y".repeat(40_000), trimmed.last().content)
        assertEquals(3, trimmed.size)
    }
}
