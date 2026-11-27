package com.agent.ai.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The draft rule and the offline queue, before the note reaches the composer. */
class OfflineDraftTest {

    @Test
    fun `a blank draft is deleted instead of stored`() {
        assertFalse(DraftPolicy.shouldPersist(""))
        assertFalse(DraftPolicy.shouldPersist("   "))
        assertTrue(DraftPolicy.shouldPersist("hello"))
        assertEquals(500L, DraftPolicy.DelayMs)
    }

    @Test
    fun `sending without a connection queues the message`() {
        assertTrue(OfflinePolicy.shouldQueue(online = false))
        assertFalse(OfflinePolicy.shouldQueue(online = true))
    }

    @Test
    fun `the queue sends once when the connection returns`() {
        assertTrue(
            OfflinePolicy.shouldAutoSend(
                nowOnline = true,
                wasOnline = false,
                hasPending = true,
                alreadyAutoSent = false,
            )
        )
    }

    @Test
    fun `it never sends automatically twice`() {
        assertFalse(
            OfflinePolicy.shouldAutoSend(
                nowOnline = true,
                wasOnline = false,
                hasPending = true,
                alreadyAutoSent = true,
            )
        )
        assertFalse(
            OfflinePolicy.shouldAutoSend(
                nowOnline = false,
                wasOnline = true,
                hasPending = true,
                alreadyAutoSent = false,
            )
        )
        assertFalse(
            OfflinePolicy.shouldAutoSend(
                nowOnline = true,
                wasOnline = false,
                hasPending = false,
                alreadyAutoSent = false,
            )
        )
    }
}
