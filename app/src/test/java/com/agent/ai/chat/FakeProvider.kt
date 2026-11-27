package com.agent.ai.chat

import com.agent.ai.providers.ChatRequest
import com.agent.ai.providers.ModelCapabilities
import com.agent.ai.providers.ModelInfo
import com.agent.ai.providers.Provider
import com.agent.ai.providers.ProviderError
import com.agent.ai.providers.ProviderErrorKind
import com.agent.ai.providers.ProviderEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.io.IOException

/**
 * A provider with no network, for chat tests. It can stream, fail, stop early and report
 * reasoning, so every branch of the chat can be exercised without a request.
 */
class FakeProvider(
    private val reply: String = "Hello from the fake provider.",
    private val reasoning: String = "",
    private val failWith: ProviderError? = null,
    private val failAfter: Int = 0,
    private val stopAfterChunks: Int = 0,
    private val models: List<ModelInfo> = listOf(
        ModelInfo(
            id = "kilo-auto/free",
            name = "Auto Free",
            free = true,
            contextLength = 256_000,
            capabilities = ModelCapabilities(vision = true, reasoning = true),
        ),
    ),
    private val chunkSize: Int = 6,
    private val title: String = "A short title",
) : Provider {

    override val id: String = "fake"
    override val displayName: String = "Fake"

    var cancelled: Boolean = false
        private set

    var lastRequest: ChatRequest? = null
        private set

    /** Every request in order: the reply, then the title, then a compaction summary. */
    val requests: MutableList<ChatRequest> = mutableListOf()

    override suspend fun listModels(): List<ModelInfo> = models

    override fun chat(request: ChatRequest): Flow<ProviderEvent> = flow {
        lastRequest = request
        requests += request

        if (failWith != null && failAfter == 0) {
            emit(ProviderEvent.Failed(failWith))
            return@flow
        }

        if (reasoning.isNotEmpty()) {
            reasoning.chunked(chunkSize).forEach { chunk ->
                emit(ProviderEvent.ReasoningDelta(chunk))
            }
        }

        val chunks = reply.chunked(chunkSize)
        chunks.forEachIndexed { index, chunk ->
            emit(ProviderEvent.TextDelta(chunk))
            if (failWith != null && index + 1 == failAfter) {
                emit(ProviderEvent.Failed(failWith))
                return@flow
            }
            if (stopAfterChunks > 0 && index + 1 == stopAfterChunks) {
                emit(ProviderEvent.Finished(null))
                return@flow
            }
        }
        emit(ProviderEvent.Finished(null))
    }

    override suspend fun complete(request: ChatRequest): String {
        requests += request
        return title
    }

    override fun cancel() {
        cancelled = true
    }

    companion object {
        fun offline(): FakeProvider = FakeProvider(
            failWith = ProviderError(ProviderErrorKind.NoInternet, "offline"),
        )

        fun rateLimited(): FakeProvider = FakeProvider(
            failWith = ProviderError(ProviderErrorKind.RateLimited, "429", statusCode = 429),
        )

        fun empty(): FakeProvider = FakeProvider(models = emptyList())
    }
}

/** A provider whose stream is already complete; handy for flow assertions. */
object AlwaysEmptyProvider : Provider {
    override val id: String = "empty"
    override val displayName: String = "Empty"
    override suspend fun listModels(): List<ModelInfo> = emptyList()
    override fun chat(request: ChatRequest): Flow<ProviderEvent> = flowOf(ProviderEvent.Finished(null))
    override suspend fun complete(request: ChatRequest): String = ""
    override fun cancel() = Unit
}

/** Thrown by providers when the device is offline, for error mapping tests. */
class OfflineException : IOException("no internet")
