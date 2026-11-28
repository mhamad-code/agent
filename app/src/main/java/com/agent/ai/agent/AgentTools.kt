package com.agent.ai.agent

import com.agent.ai.providers.ToolSpec
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** The tool names the model may use, in one place. */
object AgentToolNames {
    const val ReadFile = "read_file"
    const val WriteFile = "write_file"
    const val EditFile = "edit_file"
    const val ListDir = "list_dir"
    const val Search = "search"
    const val RunCommand = "run_command"

    val all = listOf(ReadFile, WriteFile, EditFile, ListDir, Search, RunCommand)

    /** Reading never needs a tap; everything else does. */
    val automatic = setOf(ReadFile, ListDir, Search)
}

object AgentTools {

    const val MAX_STEPS = 25
    const val COMMAND_TIMEOUT_SECONDS = 120
    const val MAX_OUTPUT = 12_000

    /** The definitions sent to the provider, as OpenAI compatible JSON schemas. */
    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            name = AgentToolNames.ReadFile,
            description = "Read a text file inside the project. Returns the first 200 KB.",
            parameters = """
                {"type":"object","properties":{"path":{"type":"string","description":"Path inside the project"}},"required":["path"]}
            """.trimIndent(),
        ),
        ToolSpec(
            name = AgentToolNames.WriteFile,
            description = "Create or replace a file inside the project with the given content.",
            parameters = """
                {"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}
            """.trimIndent(),
        ),
        ToolSpec(
            name = AgentToolNames.EditFile,
            description = "Replace one exact string in a file. The old text must appear exactly once.",
            parameters = """
                {"type":"object","properties":{"path":{"type":"string"},"old":{"type":"string"},"new":{"type":"string"}},"required":["path","old","new"]}
            """.trimIndent(),
        ),
        ToolSpec(
            name = AgentToolNames.ListDir,
            description = "List a folder inside the project.",
            parameters = """
                {"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}
            """.trimIndent(),
        ),
        ToolSpec(
            name = AgentToolNames.Search,
            description = "Search the project's text files for a string.",
            parameters = """
                {"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}
            """.trimIndent(),
        ),
        ToolSpec(
            name = AgentToolNames.RunCommand,
            description = "Run a shell command in the project folder. Stops after 120 seconds.",
            parameters = """
                {"type":"object","properties":{"command":{"type":"string"},"timeout_seconds":{"type":"integer"}},"required":["command"]}
            """.trimIndent(),
        ),
    )

    /** One line the approval card shows instead of the model's own description. */
    fun summary(name: String, arguments: String): String {
        val args = parseObject(arguments)
        return when (name) {
            AgentToolNames.ReadFile, AgentToolNames.ListDir ->
                args.string("path").orEmpty().ifBlank { "." }

            AgentToolNames.WriteFile -> {
                val path = args.string("path").orEmpty()
                val size = args.string("content")?.length ?: 0
                "$path · $size characters"
            }

            AgentToolNames.EditFile -> args.string("path").orEmpty()
            AgentToolNames.Search -> args.string("query").orEmpty()
            AgentToolNames.RunCommand -> args.string("command").orEmpty()
            else -> name
        }
    }

    /** The exact text an edit will produce, as a small unified diff. */
    fun diffFor(name: String, arguments: String, sandbox: ProjectSandbox): String {
        if (name != AgentToolNames.EditFile && name != AgentToolNames.WriteFile) return ""
        val args = parseObject(arguments)
        val path = args.string("path").orEmpty()
        val before = when (name) {
            AgentToolNames.EditFile -> runCatching { sandbox.read(path) }.getOrDefault("")
            else -> runCatching { sandbox.read(path) }.getOrDefault("")
        }
        val after = when (name) {
            AgentToolNames.EditFile -> {
                val old = args.string("old").orEmpty()
                val new = args.string("new").orEmpty()
                if (old.isEmpty()) before else before.replaceFirst(old, new)
            }

            else -> args.string("content").orEmpty()
        }
        return unifiedDiff(before, after)
    }

    fun unifiedDiff(before: String, after: String): String {
        if (before == after) return ""
        val oldLines = before.lines()
        val newLines = after.lines()
        val builder = StringBuilder()
        var start = 0
        while (start < oldLines.size && start < newLines.size && oldLines[start] == newLines[start]) start++
        var endOld = oldLines.size
        var endNew = newLines.size
        while (endOld > start && endNew > start && oldLines[endOld - 1] == newLines[endNew - 1]) {
            endOld--
            endNew--
        }
        builder.appendLine("@@ line ${start + 1} @@")
        oldLines.subList(start, endOld).take(40).forEach { builder.appendLine("-$it") }
        if (endOld - start > 40) builder.appendLine("-…")
        newLines.subList(start, endNew).take(40).forEach { builder.appendLine("+$it") }
        if (endNew - start > 40) builder.appendLine("+…")
        return builder.toString().trimEnd().take(6_000)
    }

    fun parseObject(raw: String): JsonObject =
        runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/** What one tool run produced. */
data class ToolOutcome(
    val ok: Boolean,
    val output: String,
    val diff: String = "",
    /** Where the original content was saved, so the change can be reverted. */
    val backupPath: String? = null,
)

/**
 * Runs the tools. Output is cut in the middle, so both the beginning and the end reach the
 * model, and a command is killed when it runs past its timeout or the user stops it.
 */
class ToolExecutor(
    private val sandbox: ProjectSandbox,
    private val backups: File,
    private val command: CommandRunner = CommandRunner(),
) {

    suspend fun run(
        name: String,
        arguments: String,
        /** Called with the output so far while a command is still running. */
        onLiveOutput: (String) -> Unit = {},
    ): ToolOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val args = AgentTools.parseObject(arguments)
            when (name) {
                AgentToolNames.ReadFile -> {
                    val path = args.string("path") ?: error("path is required")
                    ToolOutcome(true, cut(sandbox.read(path)))
                }

                AgentToolNames.ListDir -> {
                    val path = args.string("path") ?: "."
                    val entries = sandbox.list(path)
                    val text = entries.joinToString("\n") { entry ->
                        val suffix = if (entry.directory) "/" else " · ${entry.sizeBytes} B"
                        entry.name + suffix
                    }.ifBlank { "(empty folder)" }
                    ToolOutcome(true, cut(text))
                }

                AgentToolNames.Search -> {
                    val query = args.string("query") ?: error("query is required")
                    val hits = sandbox.search(query)
                    val text = hits.joinToString("\n") { "${it.path}:${it.line}: ${it.text}" }
                        .ifBlank { "No match for \"$query\"" }
                    ToolOutcome(true, cut(text))
                }

                AgentToolNames.WriteFile -> {
                    val path = args.string("path") ?: error("path is required")
                    val content = args.string("content").orEmpty()
                    val target = sandbox.resolve(path)
                    val backup = backup(target)
                    sandbox.write(path, content)
                    ToolOutcome(
                        ok = true,
                        output = "Wrote ${sandbox.relative(target)} (${content.length} characters)",
                        diff = AgentTools.unifiedDiff("", content),
                        backupPath = backup,
                    )
                }

                AgentToolNames.EditFile -> {
                    val path = args.string("path") ?: error("path is required")
                    val old = args.string("old") ?: error("old is required")
                    val new = args.string("new") ?: error("new is required")
                    val target = sandbox.resolve(path)
                    val before = sandbox.read(path)
                    val backup = backup(target)
                    sandbox.edit(path, old, new)
                    ToolOutcome(
                        ok = true,
                        output = "Edited ${sandbox.relative(target)}",
                        diff = AgentTools.unifiedDiff(before, sandbox.read(path)),
                        backupPath = backup,
                    )
                }

                AgentToolNames.RunCommand -> {
                    val line = args.string("command") ?: error("command is required")
                    val timeout = (args["timeout_seconds"] as? JsonPrimitive)?.content
                        ?.toIntOrNull() ?: AgentTools.COMMAND_TIMEOUT_SECONDS
                    val result = command.run(
                        directory = sandbox.root,
                        command = line,
                        timeoutSeconds = timeout.coerceIn(1, 600),
                        onOutput = { text -> onLiveOutput(cut(text)) },
                    )
                    ToolOutcome(result.ok, cut(result.output))
                }

                else -> ToolOutcome(false, "Unknown tool: $name")
            }
        }.getOrElse { error ->
            ToolOutcome(false, "The tool failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    private fun backup(target: File): String? {
        if (!target.isFile) return null
        backups.mkdirs()
        val file = File(backups, "${System.currentTimeMillis()}-${target.name}")
        runCatching { target.copyTo(file, overwrite = true) }
        return file.absolutePath
    }

    private fun cut(text: String): String {
        if (text.length <= AgentTools.MAX_OUTPUT) return text
        val keep = AgentTools.MAX_OUTPUT / 2
        val removed = text.length - keep * 2
        return text.take(keep) +
            "\n\n… $removed characters removed …\n\n" +
            text.takeLast(keep)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/** A shell command in the project folder, with live output, a timeout and cancellation. */
class CommandRunner {

    data class Result(val ok: Boolean, val output: String)

    private val _live = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val live: SharedFlow<String> = _live.asSharedFlow()

    private var process: Process? = null

    fun cancel() {
        process?.let { com.agent.ai.util.Processes.kill(it) }
        process = null
    }

    suspend fun run(
        directory: File,
        command: String,
        timeoutSeconds: Int,
        onOutput: (String) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
            val builder = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(directory)
                .redirectErrorStream(true)
            val started = builder.start()
            process = started
            val output = StringBuilder()
            val reader = Thread {
                runCatching {
                    started.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            appendLimited(output, line)
                            _live.tryEmit(line)
                            onOutput(output.toString())
                        }
                    }
                }
            }.apply { isDaemon = true; start() }
            val finished = com.agent.ai.util.Processes.awaitExit(started, timeoutSeconds.toLong())
            if (!finished) {
                com.agent.ai.util.Processes.kill(started)
            }
            reader.join(1_000)
            process = null
            val text = output.toString().trim().ifBlank { "(no output)" }
            Result(
                ok = finished && com.agent.ai.util.Processes.exitCode(started) == 0,
                output = if (finished) {
                    text
                } else {
                    "$text\n\nThe command was stopped after $timeoutSeconds seconds."
                },
            )
        }

    @Synchronized
    private fun appendLimited(builder: StringBuilder, line: String) {
        if (builder.length > 60_000) return
        builder.appendLine(line)
    }
}

/** What the approval rules decide for one call. */
enum class ApprovalDecision { Automatic, Ask, AlwaysAsk }

object ApprovalPolicy {

    private val alwaysAskCommands = listOf(
        "git push",
        "git remote set-url",
        "rm -rf",
        "rm -r ",
        "rm -f ",
        "sudo",
        "curl -x post",
        "gh release",
        "npm publish",
        "git tag",
    )

    /**
     * Reading and searching run automatically. Writes, edits and commands ask, and the
     * short always-ask list can never be switched to automatic.
     */
    fun decide(
        tool: String,
        arguments: String,
        alwaysAllowed: Set<String>,
    ): ApprovalDecision {
        if (tool in AgentToolNames.automatic) return ApprovalDecision.Automatic
        if (tool == AgentToolNames.RunCommand) {
            val command = AgentTools.parseObject(arguments)
                .let { (it["command"] as? JsonPrimitive)?.content.orEmpty() }
                .lowercase()
            if (alwaysAskCommands.any { command.contains(it) }) return ApprovalDecision.AlwaysAsk
            if (command.contains("/..") || command.contains("~/") || command.contains(" /sdcard")) {
                return ApprovalDecision.AlwaysAsk
            }
        }
        if (tool in alwaysAllowed) return ApprovalDecision.Automatic
        return ApprovalDecision.Ask
    }

    fun isAlwaysAsk(tool: String, arguments: String): Boolean =
        decide(tool, arguments, emptySet()) == ApprovalDecision.AlwaysAsk
}
