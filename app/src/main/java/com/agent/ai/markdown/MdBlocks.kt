package com.agent.ai.markdown

/** Alignment markers from a table's delimiter row. */
enum class MdAlign { None, Left, Center, Right }

/** One item of a list. [level] is the nesting depth, zero for a top-level item. */
data class MdListItem(
    val level: Int,
    val text: String,
    val task: Boolean? = null,
    val number: Int? = null,
)

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock

    data class Paragraph(val text: String) : MdBlock

    data class Code(val code: String, val language: String, val closed: Boolean) : MdBlock

    data class Table(
        val header: List<String>,
        val alignments: List<MdAlign>,
        val rows: List<List<String>>,
        val complete: Boolean,
    ) : MdBlock

    data class Bullets(val items: List<MdListItem>) : MdBlock

    data class Ordered(val items: List<MdListItem>) : MdBlock

    data class Quote(val lines: List<String>) : MdBlock

    data object Rule : MdBlock

    data class Image(val alt: String, val url: String) : MdBlock
}

/**
 * Turns Markdown into blocks. It is deliberately forgiving: a reply is streamed, so a
 * fence that was not closed yet, a table cut in the middle of a row or a half written
 * bold marker must still render as something sensible instead of raw symbols.
 */
object MdBlocks {

    fun parse(text: String, streaming: Boolean = false): List<MdBlock> {
        if (text.isBlank()) return emptyList()
        val lines = text.split('\n')
        val blocks = mutableListOf<MdBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> i++

                isFenceStart(trimmed) -> {
                    val fence = trimmed.take(3)
                    val language = normalizeLanguage(trimmed.removePrefix(fence).trim())
                    val code = StringBuilder()
                    var closed = false
                    i++
                    while (i < lines.size) {
                        val current = lines[i]
                        if (current.trimStart().startsWith(fence)) {
                            closed = true
                            i++
                            break
                        }
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(current)
                        i++
                    }
                    blocks += MdBlock.Code(code.toString(), language, closed)
                }

                isRule(trimmed) -> {
                    blocks += MdBlock.Rule
                    i++
                }

                headingLevel(trimmed) > 0 -> {
                    val level = headingLevel(trimmed)
                    blocks += MdBlock.Heading(
                        level = level.coerceAtMost(4),
                        text = trimmed.dropWhile { it == '#' }.trim(),
                    )
                    i++
                }

                isTableStart(lines, i) -> {
                    val header = splitRow(lines[i]).map { it.trim() }
                    val alignments = alignmentsOf(lines[i + 1], header.size)
                    val rows = mutableListOf<List<String>>()
                    var j = i + 2
                    var complete = false
                    while (j < lines.size) {
                        val row = lines[j]
                        if (row.isBlank() || !looksLikeRow(row)) {
                            complete = true
                            break
                        }
                        rows += normalizeCells(splitRow(row), header.size)
                        j++
                    }
                    blocks += MdBlock.Table(header, alignments, rows, complete)
                    i = j
                }

                trimmed.startsWith(">") -> {
                    val quote = mutableListOf<String>()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        quote += lines[i].trimStart().removePrefix(">").removePrefix(" ").trimEnd()
                        i++
                    }
                    blocks += MdBlock.Quote(quote)
                }

                markerOf(lines[i]) != null -> {
                    val firstMarker = markerOf(lines[i])!!
                    val ordered = firstMarker.number != null
                    val items = mutableListOf<MdListItem>()
                    var j = i
                    var index = firstMarker.number ?: 0
                    while (j < lines.size) {
                        val marker = markerOf(lines[j]) ?: break
                        val isOrdered = marker.number != null
                        if (isOrdered != ordered) break
                        val content = lines[j].trimStart()
                            .let { if (isOrdered) it.substringAfter(marker.text) else it.substring(marker.text.length) }
                            .trimStart()
                        val (task, body) = taskOf(content)
                        items += MdListItem(
                            level = marker.indent / 2,
                            // A nested item keeps its own number, so the renderer can print it.
                            text = body.trim(),
                            task = task,
                            number = if (isOrdered) index else null,
                        )
                        if (isOrdered) index++
                        j++
                    }
                    blocks += if (ordered) MdBlock.Ordered(items) else MdBlock.Bullets(items)
                    i = j
                }

                imageOnly(trimmed) != null -> {
                    val (alt, url) = imageOnly(trimmed)!!
                    blocks += MdBlock.Image(alt, url)
                    i++
                }

                else -> {
                    val paragraph = StringBuilder()
                    while (i < lines.size) {
                        val candidate = lines[i]
                        if (candidate.isBlank() || startsBlock(lines, i)) break
                        if (paragraph.isNotEmpty()) paragraph.append(' ')
                        paragraph.append(candidate.trim())
                        i++
                    }
                    blocks += MdBlock.Paragraph(paragraph.toString())
                }
            }
        }

        // While streaming, a marker that is still being typed is held back instead of
        // flashing "**" or an empty link at the end of the reply.
        if (!streaming) return blocks
        return blocks.map { block ->
            when (block) {
                is MdBlock.Paragraph -> block.copy(text = TextStreamTail.trim(block.text))
                is MdBlock.Heading -> block.copy(text = TextStreamTail.trim(block.text))
                else -> block
            }
        }
    }

    private fun startsBlock(lines: List<String>, index: Int): Boolean {
        val trimmed = lines[index].trim()
        return isFenceStart(trimmed) ||
            isRule(trimmed) ||
            headingLevel(trimmed) > 0 ||
            trimmed.startsWith(">") ||
            markerOf(lines[index]) != null ||
            imageOnly(trimmed) != null ||
            isTableStart(lines, index)
    }

    private fun normalizeLanguage(raw: String): String =
        raw.substringBefore(' ').substringBefore(',').trim().lowercase()

    fun isFenceStart(trimmed: String): Boolean =
        trimmed.startsWith("```") || trimmed.startsWith("~~~")

    private fun isRule(trimmed: String): Boolean {
        if (trimmed.length < 3) return false
        val first = trimmed.first()
        if (first != '-' && first != '*' && first != '_') return false
        return trimmed.all { it == first || it == ' ' } && trimmed.count { it == first } >= 3
    }

    private fun headingLevel(trimmed: String): Int {
        val hashes = trimmed.takeWhile { it == '#' }.length
        if (hashes == 0 || hashes > 6) return 0
        if (trimmed.length > hashes && trimmed[hashes] != ' ') return 0
        return hashes
    }

    private fun looksLikeRow(line: String): Boolean = line.contains('|')

    private fun isTableStart(lines: List<String>, index: Int): Boolean {
        if (index + 1 >= lines.size) return false
        if (!looksLikeRow(lines[index])) return false
        return isDelimiterRow(lines[index + 1])
    }

    private fun isDelimiterRow(line: String): Boolean {
        val cells = splitRow(line)
        if (cells.isEmpty()) return false
        return cells.all { cell ->
            val value = cell.trim()
            value.isNotEmpty() &&
                value.all { it == '-' || it == ':' } &&
                value.count { it == '-' } >= 1
        }
    }

    private fun alignmentsOf(delimiter: String, columns: Int): List<MdAlign> {
        val cells = splitRow(delimiter).map { cell ->
            val value = cell.trim()
            when {
                value.startsWith(":") && value.endsWith(":") -> MdAlign.Center
                value.startsWith(":") -> MdAlign.Left
                value.endsWith(":") -> MdAlign.Right
                else -> MdAlign.None
            }
        }
        return List(columns) { index -> cells.getOrElse(index) { MdAlign.None } }
    }

    private fun normalizeCells(cells: List<String>, columns: Int): List<String> = List(columns) { index ->
        cells.getOrElse(index) { "" }.trim()
    }

    /** Splits a table row on pipes that are not escaped and not inside inline code. */
    fun splitRow(line: String): List<String> {
        var value = line.trim()
        if (value.startsWith("|")) value = value.substring(1)
        if (value.endsWith("|") && !value.endsWith("\\|")) value = value.dropLast(1)

        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inCode = false
        var i = 0
        while (i < value.length) {
            val char = value[i]
            when {
                char == '\\' && i + 1 < value.length && value[i + 1] == '|' -> {
                    current.append('|')
                    i += 2
                    continue
                }

                char == '`' -> {
                    inCode = !inCode
                    current.append(char)
                }

                char == '|' && !inCode -> {
                    cells += current.toString()
                    current.clear()
                }

                else -> current.append(char)
            }
            i++
        }
        cells += current.toString()
        return cells
    }

    /** The list marker at the start of a line: its raw text and its number when ordered. */
    private fun markerOf(line: String): Marker? {
        val indent = line.takeWhile { it == ' ' }.length
        val trimmed = line.trimStart()
        if (trimmed.length < 3) return null
        val bullet = trimmed.first()
        if ((bullet == '-' || bullet == '*' || bullet == '+') && trimmed[1] == ' ') {
            return Marker(indent, trimmed.take(1), null)
        }
        val digits = trimmed.takeWhile { it.isDigit() }
        if (digits.isNotEmpty() && digits.length <= 9) {
            val rest = trimmed.drop(digits.length)
            if (rest.startsWith(". ") || rest.startsWith(") ")) {
                return Marker(indent, digits + rest.take(1), digits.toIntOrNull())
            }
        }
        return null
    }

    private fun taskOf(content: String): Pair<Boolean?, String> {
        val trimmed = content.trimStart()
        if (trimmed.startsWith("[ ]")) return true to trimmed.removePrefix("[ ]")
        if (trimmed.startsWith("[x]") || trimmed.startsWith("[X]")) {
            return false to trimmed.drop(3)
        }
        return null to content
    }

    private fun imageOnly(trimmed: String): Pair<String, String>? {
        if (!trimmed.startsWith("![") || !trimmed.endsWith(")")) return null
        val close = trimmed.indexOf("](")
        if (close < 0) return null
        val alt = trimmed.substring(2, close)
        val url = trimmed.substring(close + 2, trimmed.length - 1)
        if (url.contains(' ') || url.isEmpty()) return null
        return alt to url
    }

    private data class Marker(val indent: Int, val text: String, val number: Int?)
}

/**
 * Drops an inline marker that is still open at the very end of a streaming reply, so the
 * user does not see `**` or `[` on its own while the rest arrives.
 */
internal object TextStreamTail {

    fun trim(text: String): String {
        var value = text.trimEnd()
        if (value.isEmpty()) return value

        // An unfinished image or link: "![alt](http://exa" or "[label]("
        val lastOpenBracket = value.lastIndexOf('[')
        if (lastOpenBracket >= 0) {
            val tail = value.substring(lastOpenBracket)
            if (!tail.contains(']') || (tail.contains("](") && !tail.endsWith(")"))) {
                value = value.substring(0, lastOpenBracket).trimEnd()
            }
        }

        // A marker is still being typed when the number of its pairs is odd.
        for (marker in listOf("**", "__", "~~")) {
            val pairs = Regex(Regex.escape(marker)).findAll(value).count()
            if (value.endsWith(marker) && pairs % 2 == 1) {
                return value.dropLast(marker.length).trimEnd()
            }
        }
        if (value.endsWith("`") && value.count { it == '`' } % 2 == 1) {
            return value.dropLast(1).trimEnd()
        }
        if ((value.endsWith("*") || value.endsWith("_")) &&
            !value.endsWith("**") &&
            value.count { it == value.last() } % 2 == 1
        ) {
            return value.dropLast(1).trimEnd()
        }
        return value.trimEnd()
    }
}
