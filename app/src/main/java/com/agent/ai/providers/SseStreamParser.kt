package com.agent.ai.providers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses one line of an OpenAI compatible event stream into [ProviderEvent]s.
 *
 * The gateway sends `data: {json}` per chunk, `data: [DONE]` at the end and keep-alive
 * comments that start with a colon. Anything that does not parse is reported as broken
 * and skipped, so one damaged line never kills a long reply.
 */
object SseStreamParser {

    data class Parsed(
        val events: List<ProviderEvent> = emptyList(),
        val done: Boolean = false,
        val broken: Boolean = false,
        /** The provider hit its output limit, so a Continue makes sense. */
        val truncated: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawLine: String): Parsed {
        val line = rawLine.trimEnd('\r')
        if (line.isBlank()) return Parsed()
        if (line.startsWith(":")) return Parsed() // keep-alive comment
        if (line.startsWith("event:") || line.startsWith("id:") || line.startsWith("retry:")) {
            return Parsed()
        }
        if (!line.startsWith("data:")) return Parsed(broken = true)

        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty()) return Parsed()
        if (payload == "[DONE]") return Parsed(done = true)

        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull()
        val obj = root as? JsonObject ?: return Parsed(broken = true)

        val events = mutableListOf<ProviderEvent>()
        val choice = obj.firstChoice()
        val delta = (choice?.get("delta") as? JsonObject)
        val finishReason = choice?.string("finish_reason")
        val errorObj = obj["error"] as? JsonObject

        if (errorObj != null) {
            // The gateway sends the code as a number, and some proxies send it as a string.
            val code = (errorObj["code"] as? JsonPrimitive)?.content?.toIntOrNull()
                ?: (obj["code"] as? JsonPrimitive)?.content?.toIntOrNull()
            events += ProviderEvent.Failed(
                ProviderError(
                    kind = if (code == 429) ProviderErrorKind.RateLimited else ProviderErrorKind.ServerError,
                    detail = errorObj.string("message").orEmpty(),
                    statusCode = code,
                )
            )
        } else {
            val text = delta?.string("content")
            if (!text.isNullOrEmpty()) events += ProviderEvent.TextDelta(text)

            // Providers name thinking tokens differently; both spellings are accepted.
            val reasoning = delta?.string("reasoning") ?: delta?.string("reasoning_content")
            if (!reasoning.isNullOrEmpty()) events += ProviderEvent.ReasoningDelta(reasoning)

            val toolCalls = delta?.get("tool_calls") as? JsonArray
            toolCalls?.forEach { element ->
                val tool = element as? JsonObject ?: return@forEach
                val function = tool["function"] as? JsonObject ?: return@forEach
                events += ProviderEvent.ToolCall(
                    id = tool.string("id"),
                    name = function.string("name").orEmpty(),
                    arguments = function.string("arguments").orEmpty(),
                )
            }
        }

        if (finishReason == "error" && events.none { it is ProviderEvent.Failed }) {
            events += ProviderEvent.Failed(
                ProviderError(
                    kind = ProviderErrorKind.ServerError,
                    detail = errorObj?.string("message").orEmpty(),
                )
            )
        }

        obj.usage()?.let { usage -> events += ProviderEvent.Finished(usage) }

        return Parsed(events = events, truncated = finishReason == "length")
    }

    /** The token counts the gateway reports at the end of a stream, when present. */
    fun JsonObject.usage(): Usage? {
        val usage = this["usage"] as? JsonObject ?: return null
        val prompt = (usage["prompt_tokens"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
        val completion = (usage["completion_tokens"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        return Usage(prompt, completion)
    }
}

/** The first entry of `choices`, when the chunk carries one (usage chunks carry none). */
internal fun JsonObject.firstChoice(): JsonObject? {
    val choices = this["choices"] as? JsonArray ?: return null
    return choices.firstOrNull() as? JsonObject
}

/** A string field, or null when the key is missing or holds a non string value. */
internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.stringValue()

internal fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.stringValue()

private fun JsonPrimitive.stringValue(): String? = if (isString) content else null
