package com.agent.ai.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The title fallback and the cleaning of a model written title. */
class TitleFallbackTest {

    @Test
    fun `a short first line is used as it is`() {
        assertEquals("Fix the login bug", ChatRepository.fallbackTitle("Fix the login bug"))
    }

    @Test
    fun `a long first line is cut at forty characters`() {
        val title = ChatRepository.fallbackTitle("a".repeat(80))
        assertEquals(41, title.length) // forty characters and the ellipsis
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun `the title comes from the first non blank line`() {
        assertEquals("Hello there", ChatRepository.fallbackTitle("\n\n  Hello there\nsecond line"))
        assertEquals("", ChatRepository.fallbackTitle("   \n  "))
    }

    @Test
    fun `quotes and trailing punctuation are removed from a model title`() {
        assertEquals("Kotlin chat refactor", ChatRepository.cleanTitle("\"Kotlin chat refactor.\""))
        assertEquals("Plan a trip", ChatRepository.cleanTitle("  `Plan a trip:`  "))
        assertEquals("", ChatRepository.cleanTitle("   "))
    }

    @Test
    fun `a model title is capped at eight words`() {
        val title = ChatRepository.cleanTitle("one two three four five six seven eight nine ten")
        assertEquals("one two three four five six seven eight", title)
    }

    @Test
    fun `an unknown error kind does not crash the chat`() {
        assertNull(ChatRepository.kindOf("SomethingElse"))
        assertNull(ChatRepository.kindOf(null))
        assertEquals("NoInternet", ChatRepository.kindOf("NoInternet")?.name)
    }

    @Test
    fun `the full text query drops punctuation and requires every term`() {
        assertEquals("\"kotlin\"* AND \"chat\"*", ChatRepository.ftsQuery("Kotlin, chat!"))
        assertNull(ChatRepository.ftsQuery("a ? !"))
        assertNull(ChatRepository.ftsQuery("   "))
    }
}
