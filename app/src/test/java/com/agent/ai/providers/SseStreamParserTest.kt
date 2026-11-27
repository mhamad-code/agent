package com.agent.ai.providers

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recorded shape of a Kilo Gateway stream, with the awkward lines included. */
class SseStreamParserTest {

    private val sample = """
        data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk","created":1,"model":"kilo-auto/free","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}

        : keep-alive

        data: {"id":"chatcmpl-abc123","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":null}]}

        data: {"id":"chatcmpl-abc123","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

        data: {"id":"chatcmpl-abc123","choices":[],"usage":{"prompt_tokens":12,"completion_tokens":2,"total_tokens":14}}

        data: [DONE]
    """.trimIndent()

    @Test
    fun `parses text deltas and stops at the done marker`() {
        val events = mutableListOf<ProviderEvent>()
        var done = false

        sample.lineSequence().forEach { line ->
            val parsed = SseStreamParser.parse(line)
            events += parsed.events
            if (parsed.done) done = true
        }

        assertEquals(
            listOf("Hello", " world"),
            events.filterIsInstance<ProviderEvent.TextDelta>().map { it.text },
        )
        assertTrue(done)
    }

    @Test
    fun `keep-alive, blank and event lines are ignored`() {
        listOf("", "   ", ": ping", "event: message", "id: 42", "retry: 1000").forEach { line ->
            val parsed = SseStreamParser.parse(line)
            assertTrue(parsed.events.isEmpty())
            assertFalse(parsed.done)
            assertFalse(parsed.broken)
        }
    }

    @Test
    fun `a broken line is reported and skipped`() {
        val parsed = SseStreamParser.parse("""data: {"choices": [""")
        assertTrue(parsed.broken)
        assertTrue(parsed.events.isEmpty())
        assertFalse(parsed.done)

        // And the stream keeps going after it.
        val next = SseStreamParser.parse(
            """data: {"choices":[{"index":0,"delta":{"content":"still here"},"finish_reason":null}]}"""
        )
        assertEquals("still here", (next.events.single() as ProviderEvent.TextDelta).text)
    }

    @Test
    fun `an error in the middle of the stream becomes a failure`() {
        val parsed = SseStreamParser.parse(
            """data: {"error":{"message":"Provider disconnected","code":502},"choices":[{"index":0,"delta":{"content":""},"finish_reason":"error"}]}"""
        )
        val failure = parsed.events.filterIsInstance<ProviderEvent.Failed>().single()
        assertEquals(ProviderErrorKind.ServerError, failure.error.kind)
        assertEquals(502, failure.error.statusCode)
        assertEquals("Provider disconnected", failure.error.detail)
    }

    @Test
    fun `a rate limit error is retryable`() {
        val parsed = SseStreamParser.parse(
            """data: {"error":{"message":"Rate limit exceeded","code":429},"choices":[]}"""
        )
        val failure = parsed.events.filterIsInstance<ProviderEvent.Failed>().single()
        assertEquals(ProviderErrorKind.RateLimited, failure.error.kind)
        assertTrue(failure.error.retryable)
    }

    @Test
    fun `tool calls are parsed`() {
        val parsed = SseStreamParser.parse(
            """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"id":"call_1","function":{"name":"read_file","arguments":"{\"path\":\"a\"}"}}]},"finish_reason":null}]}"""
        )
        val call = parsed.events.filterIsInstance<ProviderEvent.ToolCall>().single()
        assertEquals("read_file", call.name)
        assertEquals("call_1", call.id)
    }

    @Test
    fun `cancelling the collector stops consuming the stream`() = runTest {
        val consumed = mutableListOf<String>()

        val job = launch {
            flow {
                for (line in sample.lineSequence()) {
                    emit(line)
                    delay(100)
                }
            }.collect { line ->
                SseStreamParser.parse(line).events.forEach { event ->
                    if (event is ProviderEvent.TextDelta) consumed += event.text
                }
            }
        }

        advanceTimeBy(150)
        job.cancel()
        advanceUntilIdle()

        assertEquals(listOf("Hello"), consumed)
    }
}
