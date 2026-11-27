package com.agent.ai.data.chat

import androidx.annotation.StringRes
import com.agent.ai.R

/**
 * The built-in slash commands. Only the ones that work without project tools are enabled;
 * the rest are listed as disabled, with the reason, so nothing looks broken.
 */
data class BuiltInCommand(
    val name: String,
    @StringRes val description: Int,
    val enabled: Boolean,
    @StringRes val reason: Int? = null,
)

object SlashCommandCatalog {

    val commands: List<BuiltInCommand> = listOf(
        BuiltInCommand("/compact", R.string.command_compact_description, enabled = true),
        BuiltInCommand("/new", R.string.command_new_description, enabled = true),
        BuiltInCommand("/init", R.string.command_init_description, false, R.string.command_needs_tools),
        BuiltInCommand("/plan", R.string.command_plan_description, false, R.string.command_needs_tools),
        BuiltInCommand("/review", R.string.command_review_description, false, R.string.command_needs_tools),
        BuiltInCommand("/test", R.string.command_test_description, false, R.string.command_needs_tools),
    )

    /** `{{name}}` placeholders in a custom command's prompt, in order of appearance. */
    fun variables(prompt: String): List<String> {
        val out = LinkedHashSet<String>()
        Regex("\\{\\{\\s*([\\p{L}\\p{N}_-]+)\\s*}}").findAll(prompt).forEach { match ->
            out.add(match.groupValues[1])
        }
        return out.toList()
    }

    fun fill(prompt: String, values: Map<String, String>): String =
        Regex("\\{\\{\\s*([\\p{L}\\p{N}_-]+)\\s*}}").replace(prompt) { match ->
            values[match.groupValues[1]]?.takeIf { it.isNotBlank() } ?: match.value
        }

    /** The commands the composer offers for [input], which must start with a slash. */
    fun matching(
        input: String,
        custom: List<SlashCommandEntity>,
    ): List<SlashCommandEntry> {
        if (!input.startsWith("/")) return emptyList()
        val prefix = input.substringBefore(' ')
        val needle = prefix.lowercase()
        val builtIns = commands
            .filter { it.name.startsWith(needle) }
            .map {
                SlashCommandEntry(
                    name = it.name,
                    description = it.description,
                    enabled = it.enabled,
                    reason = it.reason,
                    custom = null,
                )
            }
        val mine = custom
            .filter { "/${it.name.lowercase()}".startsWith(needle) }
            .map {
                SlashCommandEntry(
                    name = "/${it.name}",
                    description = 0,
                    enabled = true,
                    reason = null,
                    custom = it,
                )
            }
        return builtIns + mine
    }
}

/** One row of the slash menu. A custom command carries its stored row. */
data class SlashCommandEntry(
    val name: String,
    @StringRes val description: Int,
    val enabled: Boolean,
    @StringRes val reason: Int?,
    val custom: SlashCommandEntity?,
)
