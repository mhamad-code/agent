package com.agent.ai.terminal

/**
 * A tiny terminal screen: lines of styled runs, a cursor, scrollback and the handful of
 * escape sequences a shell actually emits. No third-party terminal library is used, so
 * there is no extra license to comply with.
 */
class AnsiScreen(private val scrollbackLimit: Int = 2_000) {

    data class Run(val text: String, val fg: Int? = null, val bg: Int? = null, val bold: Boolean = false)

    data class Line(val runs: List<Run>)

    private val lines = ArrayDeque<MutableList<Run>>()
    private var current = mutableListOf<Run>()
    private var cursorColumn = 0
    private var cursorAttribute = Attributes()
    private var pending = StringBuilder()
    private var escape = StringBuilder()
    private var inEscape = false
    private var inCsi = false
    private var inOsc = false

    data class Attributes(val fg: Int? = null, val bg: Int? = null, val bold: Boolean = false)

    /** The visible screen with the cursor marked, ready for Compose. */
    fun snapshot(): Snapshot {
        flushPending()
        val all = lines.map { it.toList() } + listOf(current.toList())
        return Snapshot(
            lines = all,
            cursorLine = all.size - 1,
            cursorColumn = cursorColumn,
            cursorAttribute = cursorAttribute,
        )
    }

    data class Snapshot(
        val lines: List<List<Run>>,
        val cursorLine: Int,
        val cursorColumn: Int,
        val cursorAttribute: Attributes,
    )

    fun clear() {
        lines.clear()
        current = mutableListOf()
        cursorColumn = 0
        pending = StringBuilder()
        escape = StringBuilder()
        inEscape = false
        inCsi = false
        inOsc = false
    }

    fun feed(chunk: String) {
        for (char in chunk) {
            when {
                inOsc -> if (char == '\u0007' || char == '\u001b') inOsc = false
                inCsi -> {
                    if (char in '@'..'~') {
                        inCsi = false
                        inEscape = false
                        applyCsi(escape.toString(), char)
                        escape = StringBuilder()
                    } else {
                        escape.append(char)
                    }
                }

                inEscape -> when (char) {
                    '[' -> {
                        inCsi = true
                        escape = StringBuilder()
                    }

                    ']' -> {
                        inOsc = true
                        inEscape = false
                    }

                    '(' , ')' , '#' -> {
                        // Charset selection and DEC line attributes: one more byte follows.
                    }

                    else -> inEscape = false
                }

                char == '\u001b' -> inEscape = true
                char == '\n' -> newLine()
                // A shell redraws the line after a carriage return: progress bars and
                // prompts depend on the old text being replaced, not appended to.
                char == '\r' -> {
                    flushPending()
                    current = mutableListOf()
                    cursorColumn = 0
                }

                char == '\b' -> backspace()
                char == '\t' -> append((8 - cursorColumn % 8).coerceAtLeast(1).let { " ".repeat(it) })
                char.code < 32 -> Unit
                else -> append(char.toString())
            }
        }
    }

    private fun backspace() {
        flushPending()
        if (cursorColumn == 0) return
        val last = current.lastOrNull() ?: return
        if (last.text.length > 1) {
            current[current.lastIndex] = last.copy(text = last.text.dropLast(1))
        } else {
            current.removeAt(current.lastIndex)
        }
        cursorColumn--
    }

    private fun append(text: String) {
        if (text.isEmpty()) return
        pending.append(text)
        cursorColumn += text.length
    }

    private fun flushPending() {
        if (pending.isEmpty()) return
        val text = pending.toString()
        pending = StringBuilder()
        if (current.isNotEmpty()) {
            val last = current.last()
            if (last.fg == cursorAttribute.fg && last.bg == cursorAttribute.bg && last.bold == cursorAttribute.bold) {
                current[current.lastIndex] = last.copy(text = last.text + text)
                return
            }
        }
        current.add(
            Run(
                text = text,
                fg = cursorAttribute.fg,
                bg = cursorAttribute.bg,
                bold = cursorAttribute.bold,
            ),
        )
    }

    private fun newLine() {
        flushPending()
        val line = current
        // A line that only holds spaces is dropped: shells pad with them on clear.
        if (line.joinToString("") { it.text }.isNotBlank() || line.isEmpty()) {
            lines.addLast(line)
            while (lines.size > scrollbackLimit) lines.removeFirst()
        }
        current = mutableListOf()
        cursorColumn = 0
    }

    private fun applyCsi(raw: String, final: Char) {
        // Private modes (?25l and friends) are accepted and ignored.
        val privateMode = raw.startsWith("?")
        val body = raw.removePrefix("?").removePrefix("!")
        val params = body.split(';').mapNotNull { it.toIntOrNull() }
        when (final) {
            'm' -> {
                // The text before a style change belongs to the old attributes.
                flushPending()
                applySgr(if (params.isEmpty()) listOf(0) else params)
            }
            'K' -> when (params.firstOrNull() ?: 0) {
                1 -> cursorColumn = 0
                2 -> {
                    pending = StringBuilder()
                    current.clear()
                    cursorColumn = 0
                }
            }

            'J' -> {
                when (params.firstOrNull() ?: 0) {
                    2, 3 -> clear()
                    else -> {
                        flushPending()
                        current = mutableListOf()
                        cursorColumn = 0
                    }
                }
            }

            'H', 'f' -> {
                // A full cursor screen would need a cell grid; a shell prompt rarely moves up.
                val row = params.firstOrNull() ?: 1
                if (row <= 1) {
                    flushPending()
                    current = mutableListOf()
                    cursorColumn = (params.getOrNull(1) ?: 1) - 1
                }
            }

            'G' -> cursorColumn = ((params.firstOrNull() ?: 1) - 1).coerceAtLeast(0)
            'C' -> append(" ".repeat(params.firstOrNull() ?: 1))
            'B' -> repeat(params.firstOrNull() ?: 1) { newLine() }
            'D' -> cursorColumn = (cursorColumn - (params.firstOrNull() ?: 1)).coerceAtLeast(0)
            'A' -> Unit
            'h', 'l' -> if (privateMode) Unit
            else -> Unit
        }
    }

    private fun applySgr(params: List<Int>) {
        var index = 0
        while (index < params.size) {
            when (val code = params[index]) {
                0 -> cursorAttribute = Attributes()
                1 -> cursorAttribute = cursorAttribute.copy(bold = true)
                22 -> cursorAttribute = cursorAttribute.copy(bold = false)
                39 -> cursorAttribute = cursorAttribute.copy(fg = null)
                49 -> cursorAttribute = cursorAttribute.copy(bg = null)
                in 30..37 -> cursorAttribute = cursorAttribute.copy(fg = code - 30)
                in 90..97 -> cursorAttribute = cursorAttribute.copy(fg = code - 90 + 8)
                in 40..47 -> cursorAttribute = cursorAttribute.copy(bg = code - 40)
                in 100..107 -> cursorAttribute = cursorAttribute.copy(bg = code - 100 + 8)
                38, 48 -> {
                    // 38;5;n uses the 256 colour palette; 38;2;r;g;b is true colour.
                    if (params.getOrNull(index + 1) == 5) {
                        val n = params.getOrNull(index + 2) ?: 7
                        val color = if (n < 16) n else 16 + (n - 16) % 216
                        cursorAttribute = if (code == 38) {
                            cursorAttribute.copy(fg = color)
                        } else {
                            cursorAttribute.copy(bg = color)
                        }
                        index += 2
                    } else if (params.getOrNull(index + 1) == 2) {
                        index += 4
                    }
                }
            }
            index++
        }
    }

    companion object {
        /** The 16 ANSI colours as ARGB values, readable on the dark surface. */
        val palette = intArrayOf(
            0xFF1B1D1F.toInt(), 0xFFE06C75.toInt(), 0xFF98C379.toInt(), 0xFFE5C07B.toInt(),
            0xFF61AFEF.toInt(), 0xFFC678DD.toInt(), 0xFF56B6C2.toInt(), 0xFFABB2BF.toInt(),
            0xFF5C6370.toInt(), 0xFFFF7B86.toInt(), 0xFFA9E08C.toInt(), 0xFFF0CD8C.toInt(),
            0xFF7CC0F5.toInt(), 0xFFD79CEB.toInt(), 0xFF6DD3DE.toInt(), 0xFFF2F4F8.toInt(),
        )
    }
}
