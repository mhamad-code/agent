package com.agent.ai.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/** Who wrote a message. Tool messages carry one tool result back to the model. */
enum class Role(val wire: String) {
    System("system"),
    User("user"),
    Assistant("assistant"),
    Tool("tool"),
}

/** One function the model may call, in the OpenAI compatible shape. */
data class ToolSpec(val name: String, val description: String, val parameters: String)

/** A call the model made, ready to send back in the next request. */
data class WireToolCall(val id: String, val name: String, val arguments: String)

/**
 * One message in the wire format sent to the gateway. [images] holds `data:` URLs, so a
 * vision model receives the pixels while a text model never sees them.
 */
data class WireMessage(
    val role: Role,
    val content: String,
    val images: List<String> = emptyList(),
    val toolCalls: List<WireToolCall> = emptyList(),
    /** Set on a [Role.Tool] message: which call this answers. */
    val toolCallId: String? = null,
)

/** A ready to send chat request. */
data class ChatRequest(
    val model: String,
    val messages: List<WireMessage>,
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    /** Sent only when the model reports reasoning support. */
    val reasoning: Boolean = false,
    /** Offered only when the model reports tool support and the project is attached. */
    val tools: List<ToolSpec> = emptyList(),
)

/** What a model can do. Unknown or unreported means not supported. */
data class ModelCapabilities(
    val vision: Boolean = false,
    val reasoning: Boolean = false,
    val toolCalling: Boolean = false,
)

/** A model as reported by the provider. */
data class ModelInfo(
    val id: String,
    val name: String,
    val free: Boolean,
    val contextLength: Int,
    val description: String = "",
    val capabilities: ModelCapabilities = ModelCapabilities(),
)

data class Usage(val promptTokens: Int, val completionTokens: Int) {
    val total: Int get() = promptTokens + completionTokens
}

enum class ProviderErrorKind {
    NoInternet,
    Timeout,
    RateLimited,
    ServerError,
    ModelGone,
    Unknown,
}

/**
 * A failure that the chat can show to the user in plain words. [retryable] is true only for
 * failures worth retrying automatically: a rate limit or a server error that happened
 * before the first token arrived.
 */
data class ProviderError(
    val kind: ProviderErrorKind,
    val detail: String = "",
    val statusCode: Int? = null,
    val retryAfterSeconds: Int? = null,
) {
    val retryable: Boolean
        get() = kind == ProviderErrorKind.RateLimited ||
            kind == ProviderErrorKind.ServerError ||
            kind == ProviderErrorKind.Timeout
}

class ProviderException(val error: ProviderError) : Exception(error.detail.ifBlank { error.kind.name })

/** Everything a provider can emit while a reply streams in. */
sealed interface ProviderEvent {
    data class TextDelta(val text: String) : ProviderEvent

    /** Thinking tokens, kept as their own part and never sent back. */
    data class ReasoningDelta(val text: String) : ProviderEvent

    /** A fragment of a tool call; [index] joins the pieces of one call across chunks. */
    data class ToolCall(
        val id: String?,
        val name: String,
        val arguments: String,
        val index: Int = 0,
    ) : ProviderEvent

    /** [truncated] is true when the provider stopped at the token limit, not at the end. */
    data class Finished(val usage: Usage?, val truncated: Boolean = false) : ProviderEvent

    data class Failed(val error: ProviderError) : ProviderEvent
}

/**
 * One chat backend. The chat only talks to this interface, so the real gateway and a fake
 * provider for tests are interchangeable.
 */
interface Provider {
    val id: String
    val displayName: String

    /** Lists the models this provider can serve right now. Throws [ProviderException]. */
    suspend fun listModels(): List<ModelInfo>

    /**
     * Streams a reply. The flow ends after [ProviderEvent.Finished] or
     * [ProviderEvent.Failed]. Cancelling the collector cancels the network call.
     */
    fun chat(request: ChatRequest): Flow<ProviderEvent>

    /**
     * One short, non-streaming answer: a title, a compaction summary. The default
     * collects [chat], so any provider works without implementing it.
     */
    suspend fun complete(request: ChatRequest): String {
        val builder = StringBuilder()
        var failure: ProviderError? = null
        chat(request.copy(maxTokens = request.maxTokens ?: 256)).collect { event ->
            when (event) {
                is ProviderEvent.TextDelta -> builder.append(event.text)
                is ProviderEvent.Failed -> failure = event.error
                else -> Unit
            }
        }
        failure?.let { throw ProviderException(it) }
        return builder.toString()
    }

    /** Cancels the in-flight request, if any. Safe to call at any time. */
    fun cancel()
}
