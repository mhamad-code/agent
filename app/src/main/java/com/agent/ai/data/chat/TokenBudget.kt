package com.agent.ai.data.chat

import com.agent.ai.providers.Role
import com.agent.ai.providers.WireMessage

/**
 * Keeps a request inside a token budget without a tokenizer: four characters per token is
 * the usual rough ratio for English, and it errs on the safe side for code.
 *
 * The system prompt and the first message are always kept, then the newest messages are
 * added back until the budget is spent, so the oldest history is dropped first.
 */
object TokenBudget {

    fun estimate(text: String): Int = (text.length + 3) / 4 + 1

    fun total(messages: List<WireMessage>): Int =
        messages.sumOf { estimate(it.content) } + messages.size * 3

    fun trim(messages: List<WireMessage>, maxTokens: Int): List<WireMessage> {
        if (messages.isEmpty()) return messages
        if (total(messages) <= maxTokens) return messages

        val system = messages.filter { it.role == Role.System }
        val rest = messages.filterNot { it.role == Role.System }
        if (rest.isEmpty()) return system

        val first = rest.first()
        val tail = rest.drop(1)

        var used = total(system + first)
        val kept = ArrayDeque<WireMessage>()
        for (message in tail.asReversed()) {
            val cost = estimate(message.content) + 3
            // The newest message is always kept, even if it alone exceeds the budget.
            if (used + cost > maxTokens && kept.isNotEmpty()) break
            kept.addFirst(message)
            used += cost
        }
        return system + first + kept
    }
}

/** Where the context window went, for the ring and its sheet. */
data class ContextBreakdown(
    val system: Int = 0,
    val memory: Int = 0,
    val messages: Int = 0,
    val attachments: Int = 0,
) {
    val total: Int get() = system + memory + messages + attachments
}

/**
 * Context accounting and compaction planning. Estimating is always available; a reported
 * usage from the provider replaces the estimate when it is larger.
 */
object ContextBudget {

    /** The default window when a model reports no context length. */
    const val FALLBACK_WINDOW = 8_192

    fun estimate(text: String): Int = TokenBudget.estimate(text)

    fun tokens(parts: List<MessagePart>): Int = parts.sumOf { part ->
        when (part) {
            is MessagePart.Text -> estimate(part.text) + 4
            is MessagePart.Reasoning -> estimate(part.text) / 2
            is MessagePart.Image -> 850
            is MessagePart.File -> estimate(part.extracted) + 20
        }
    }

    fun breakdown(
        systemPrompt: String?,
        memory: List<String>,
        visible: List<MessageEntity>,
    ): ContextBreakdown {
        val system = if (systemPrompt.isNullOrBlank()) 0 else estimate(systemPrompt) + 4
        val memoryTokens = memory.sumOf { estimate(it) + 3 }
        var messages = 0
        var attachments = 0
        visible.forEach { message ->
            val parts = message.partsOrText()
            messages += parts.filterIsInstance<MessagePart.Text>().sumOf { estimate(it.text) + 4 }
            attachments += parts.filterNot { it is MessagePart.Text }.sumOf { part ->
                when (part) {
                    is MessagePart.Image -> 850
                    is MessagePart.File -> estimate(part.extracted)
                    else -> 0
                }
            }
        }
        return ContextBreakdown(system, memoryTokens, messages, attachments)
    }

    fun percent(used: Int, window: Int): Int {
        if (window <= 0) return 0
        return ((used.toLong() * 100) / window).toInt().coerceIn(0, 100)
    }

    /** True at or above 80 percent, the point where the ring changes and the sheet warns. */
    fun almostFull(percent: Int): Boolean = percent >= 80

    /** How many messages the summary should replace, newest kept first. */
    fun messagesToSummarise(visible: List<MessageEntity>, keepRecentTokens: Int): Int {
        var used = 0
        var keep = 0
        for (message in visible.asReversed()) {
            if (message.summary) break
            val cost = tokens(message.partsOrText())
            if (used + cost > keepRecentTokens && keep > 0) break
            used += cost
            keep++
        }
        return (visible.size - keep).coerceAtLeast(0)
    }
}
