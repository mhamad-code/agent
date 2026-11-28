package com.agent.ai.updates

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One published release, as the GitHub releases API lists it. */
data class ReleaseSummary(
    val tag: String,
    val name: String,
    val publishedAt: String,
    val notes: String,
)

object ReleaseListParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): List<ReleaseSummary> {
        val array = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull()
            ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val tag = obj.str("tag_name") ?: return@mapNotNull null
            ReleaseSummary(
                tag = tag,
                name = obj.str("name") ?: tag,
                publishedAt = obj.str("published_at").orEmpty(),
                notes = obj.str("body").orEmpty(),
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
