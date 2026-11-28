package com.agent.ai.data.chat

import com.agent.ai.providers.Role
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Conversation export. Markdown follows the active branch by default (or every branch
 * when asked); JSON always contains everything, including all branches.
 */
object ChatExporter {

    fun markdown(
        conversation: ConversationEntity,
        all: List<MessageEntity>,
        allBranches: Boolean,
    ): String {
        val rows = if (allBranches) {
            all.filterNot { it.deleted }.sortedBy { it.createdAt }
        } else {
            MessageTree.path(all, conversation.activeLeafId)
        }
        return buildString {
            append("# ").append(conversation.title).append("\n\n")
            append("_Exported from Agent on this device._\n\n")
            rows.forEach { message ->
                val role = when (message.role) {
                    Role.User.wire -> "You"
                    Role.Assistant.wire -> "Agent"
                    else -> "System"
                }
                append("## ").append(role)
                if (message.summary) append(" · summary")
                append("\n\n")
                MessageParts.text(message.partsOrText()).let { text ->
                    if (text.isNotBlank()) append(text).append("\n\n")
                }
                MessageParts.files(message.partsOrText()).forEach { file ->
                    if (file.extracted.isNotBlank()) {
                        append("**File: ").append(file.name).append("**\n\n")
                        append("```\n").append(file.extracted).append("\n```\n\n")
                    }
                }
                MessageParts.images(message.partsOrText()).forEach { image ->
                    append("![Image](").append(image.name).append(")\n\n")
                }
            }
        }.trimEnd() + "\n"
    }

    fun json(
        conversation: ConversationEntity,
        all: List<MessageEntity>,
    ): String {
        val rows = buildJsonArray {
            all.sortedBy { it.createdAt }.forEach { message ->
                add(
                    buildJsonObject {
                        put("id", message.id)
                        put("parentId", message.parentId)
                        put("role", message.role)
                        put("text", message.text)
                        put("status", message.status)
                        put("modelId", message.modelId)
                        put("createdAt", message.createdAt)
                        put("summary", message.summary)
                        put("deleted", message.deleted)
                        put("parts", partsJson(message))
                    }
                )
            }
        }
        return buildJsonObject {
            put("id", conversation.id)
            put("title", conversation.title)
            put("modelId", conversation.modelId)
            put("activeLeafId", conversation.activeLeafId)
            put("systemPrompt", conversation.systemPrompt)
            put("createdAt", conversation.createdAt)
            put("updatedAt", conversation.updatedAt)
            put("messages", rows)
        }.toString()
    }

    private fun partsJson(message: MessageEntity): JsonArray = buildJsonArray {
        message.partsOrText().forEach { part ->
            add(
                buildJsonObject {
                    put("type", part.type)
                    when (part) {
                        is MessagePart.Text -> put("text", part.text)
                        is MessagePart.Reasoning -> {
                            put("text", part.text)
                            put("seconds", part.seconds)
                        }

                        is MessagePart.Image -> {
                            put("name", part.name)
                            put("mime", part.mime)
                        }

                        is MessagePart.File -> {
                            put("name", part.name)
                            put("mime", part.mime)
                            put("size", part.size)
                            put("extracted", part.extracted)
                        }

                        is MessagePart.ToolCall -> {
                            put("id", part.id)
                            put("name", part.name)
                            put("arguments", part.arguments)
                            put("status", part.status)
                            put("output", part.output)
                        }
                    }
                }
            )
        }
    }
}
