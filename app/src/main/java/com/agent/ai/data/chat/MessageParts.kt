package com.agent.ai.data.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A message is a list of parts, stored as one JSON column. Reasoning is kept as its own
 * part and is never sent back to the model; attachments keep a pointer to a private file.
 */
sealed interface MessagePart {
    val type: String

    data class Text(val text: String) : MessagePart {
        override val type: String get() = TYPE_TEXT
    }

    data class Reasoning(val text: String, val seconds: Int = 0) : MessagePart {
        override val type: String get() = TYPE_REASONING
    }

    data class Image(
        val path: String,
        val name: String,
        val mime: String,
        val width: Int = 0,
        val height: Int = 0,
    ) : MessagePart {
        override val type: String get() = TYPE_IMAGE
    }

    data class File(
        val path: String,
        val name: String,
        val mime: String,
        val size: Long,
        val extracted: String = "",
    ) : MessagePart {
        override val type: String get() = TYPE_FILE
    }

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_REASONING = "reasoning"
        const val TYPE_IMAGE = "image"
        const val TYPE_FILE = "file"
    }
}

/** JSON encoding for [MessagePart]. Hand written, so no serializer plugin is needed. */
object MessageParts {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(parts: List<MessagePart>): String = buildJsonArray {
        parts.forEach { part ->
            add(
                when (part) {
                    is MessagePart.Text -> buildJsonObject {
                        put("type", part.type)
                        put("text", part.text)
                    }

                    is MessagePart.Reasoning -> buildJsonObject {
                        put("type", part.type)
                        put("text", part.text)
                        put("seconds", part.seconds)
                    }

                    is MessagePart.Image -> buildJsonObject {
                        put("type", part.type)
                        put("path", part.path)
                        put("name", part.name)
                        put("mime", part.mime)
                        put("width", part.width)
                        put("height", part.height)
                    }

                    is MessagePart.File -> buildJsonObject {
                        put("type", part.type)
                        put("path", part.path)
                        put("name", part.name)
                        put("mime", part.mime)
                        put("size", part.size)
                        put("extracted", part.extracted)
                    }
                }
            )
        }
    }.toString()

    fun decode(raw: String, fallbackText: String = ""): List<MessagePart> {
        if (raw.isBlank()) {
            return if (fallbackText.isEmpty()) emptyList() else listOf(MessagePart.Text(fallbackText))
        }
        val array = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull()
            ?: return listOf(MessagePart.Text(fallbackText))
        val parts = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            when (obj.str("type")) {
                MessagePart.TYPE_TEXT -> obj.str("text")?.let { MessagePart.Text(it) }
                MessagePart.TYPE_REASONING -> obj.str("text")?.let {
                    MessagePart.Reasoning(it, obj.int("seconds"))
                }

                MessagePart.TYPE_IMAGE -> MessagePart.Image(
                    path = obj.str("path").orEmpty(),
                    name = obj.str("name").orEmpty(),
                    mime = obj.str("mime") ?: "image/jpeg",
                    width = obj.int("width"),
                    height = obj.int("height"),
                )

                MessagePart.TYPE_FILE -> MessagePart.File(
                    path = obj.str("path").orEmpty(),
                    name = obj.str("name").orEmpty(),
                    mime = obj.str("mime").orEmpty(),
                    size = obj.int("size").toLong(),
                    extracted = obj.str("extracted").orEmpty(),
                )

                else -> null
            }
        }
        return parts.ifEmpty {
            if (fallbackText.isEmpty()) emptyList() else listOf(MessagePart.Text(fallbackText))
        }
    }

    /** The text the model and the list show: text parts only. */
    fun text(parts: List<MessagePart>): String =
        parts.filterIsInstance<MessagePart.Text>().joinToString("\n\n") { it.text }.trim()

    fun reasoning(parts: List<MessagePart>): String =
        parts.filterIsInstance<MessagePart.Reasoning>().joinToString("\n\n") { it.text }.trim()

    fun images(parts: List<MessagePart>): List<MessagePart.Image> =
        parts.filterIsInstance<MessagePart.Image>()

    fun files(parts: List<MessagePart>): List<MessagePart.File> =
        parts.filterIsInstance<MessagePart.File>()

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
}

/** The parts of a row, with the version 1 text column as the fallback. */
fun MessageEntity.partsOrText(): List<MessagePart> =
    MessageParts.decode(parts, text)
