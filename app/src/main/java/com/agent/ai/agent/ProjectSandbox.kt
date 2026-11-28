package com.agent.ai.agent

import java.io.File
import java.io.IOException

class SandboxException(message: String) : IOException(message)

/**
 * Every file the agent touches goes through here. A path is resolved and then checked
 * against the canonical project folder, so `..`, an absolute path or a symlink that points
 * outside the project is refused instead of followed.
 */
class ProjectSandbox(val root: File) {

    init {
        root.mkdirs()
    }

    fun resolve(path: String): File {
        val raw = path.trim().ifBlank { "." }
        val candidate = if (File(raw).isAbsolute) File(raw) else File(root, raw)
        val canonicalRoot = root.canonicalFile
        val canonical = candidate.canonicalFile
        val rootPath = canonicalRoot.path.trimEnd(File.separatorChar)
        if (canonical.path != rootPath &&
            !canonical.path.startsWith(rootPath + File.separatorChar)
        ) {
            throw SandboxException("That path is outside the project: $path")
        }
        if (canonical == canonicalRoot) return canonical

        // The parents are checked too, so a symlinked folder cannot lead outside later.
        // The walk stops at the project folder itself; its own parent is not part of the project.
        var parent = canonical.parentFile
        while (parent != null && parent.canonicalFile != canonicalRoot) {
            val parentCanonical = parent.canonicalFile
            if (parentCanonical.path != rootPath &&
                !parentCanonical.path.startsWith(rootPath + File.separatorChar)
            ) {
                throw SandboxException("That path leaves the project through a link: $path")
            }
            parent = parent.parentFile
        }
        if (parent == null) {
            throw SandboxException("That path is not inside the project: $path")
        }
        return canonical
    }

    fun relative(file: File): String {
        val rootPath = root.canonicalFile.path.trimEnd(File.separatorChar)
        val path = file.canonicalFile.path
        return path.removePrefix(rootPath).removePrefix(File.separator).ifBlank { "." }
    }

    data class Entry(val name: String, val directory: Boolean, val sizeBytes: Long)

    fun list(path: String): List<Entry> {
        val dir = resolve(path)
        if (!dir.isDirectory) throw SandboxException("Not a folder: $path")
        return dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { Entry(it.name, it.isDirectory, if (it.isFile) it.length() else 0L) }
            .orEmpty()
    }

    fun read(path: String, maxBytes: Int = 200_000): String {
        val file = resolve(path)
        if (!file.isFile) throw SandboxException("No such file: $path")
        if (file.length() > maxBytes) {
            val text = file.inputStream().use { input ->
                val buffer = ByteArray(maxBytes)
                var read = 0
                while (read < maxBytes) {
                    val step = input.read(buffer, read, maxBytes - read)
                    if (step <= 0) break
                    read += step
                }
                String(buffer, 0, read, Charsets.UTF_8)
            }
            return text + "\n… truncated: the file is ${file.length()} bytes"
        }
        return file.readText()
    }

    fun write(path: String, content: String): File {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    /** Replaces an exact string. A string that is missing or repeated is reported. */
    fun edit(path: String, old: String, new: String): String {
        val file = resolve(path)
        if (!file.isFile) throw SandboxException("No such file: $path")
        val text = file.readText()
        if (old.isEmpty()) throw SandboxException("The old string is empty")
        val first = text.indexOf(old)
        if (first < 0) throw SandboxException("That exact text is not in the file")
        val second = text.indexOf(old, first + old.length)
        if (second >= 0) {
            throw SandboxException("That text appears ${text.windowed(old.length).count { it == old }} times; make it unique")
        }
        file.writeText(text.replaceFirst(old, new))
        return file.readText()
    }

    data class Match(val path: String, val line: Int, val text: String)

    fun search(query: String, limit: Int = 60): List<Match> {
        if (query.isBlank()) return emptyList()
        val matches = mutableListOf<Match>()
        val files = mutableListOf<File>()
        root.walkTopDown()
            .onEnter { !it.name.startsWith(".") && it.name != "build" && it.name != "node_modules" }
            .filter { it.isFile && it.length() < 1_000_000 && it.extension !in BINARY }
            .forEach { files += it }
        for (file in files) {
            val lines = runCatching { file.readLines() }.getOrNull() ?: continue
            lines.forEachIndexed { index, line ->
                if (line.contains(query, ignoreCase = true)) {
                    matches += Match(relative(file), index + 1, line.trim().take(240))
                    if (matches.size >= limit) return matches
                }
            }
        }
        return matches
    }

    private companion object {
        val BINARY = setOf("png", "jpg", "jpeg", "gif", "webp", "zip", "apk", "so", "ttf", "jar")
    }
}
