package com.agent.ai.data.chat

/**
 * When a draft is written, and what counts as a draft. Kept apart from the view model so
 * the rule can be tested without Android.
 */
object DraftPolicy {

    /** A pause of half a second, or leaving the screen, writes the draft. */
    const val DelayMs = 500L

    /** A blank field means the draft is deleted, not stored as an empty string. */
    fun shouldPersist(text: String): Boolean = text.isNotBlank()
}

/**
 * The offline queue. A message typed without a connection is stored once, and it is sent
 * automatically exactly once when the connection returns. A second automatic attempt is
 * never made; only the user can ask again.
 */
object OfflinePolicy {

    fun shouldQueue(online: Boolean): Boolean = !online

    fun shouldAutoSend(
        nowOnline: Boolean,
        wasOnline: Boolean,
        hasPending: Boolean,
        alreadyAutoSent: Boolean,
    ): Boolean = nowOnline && !wasOnline && hasPending && !alreadyAutoSent

    /** After a manual send, the automatic one is no longer allowed. */
    fun afterManualSend(): Boolean = true
}
