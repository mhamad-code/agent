package com.agent.ai.markdown

/** Inline Markdown that can appear inside a paragraph, heading, cell, quote or list item. */
sealed interface MdInline {
    data class Text(val text: String) : MdInline

    data class Bold(val children: List<MdInline>) : MdInline

    data class Italic(val children: List<MdInline>) : MdInline

    data class BoldItalic(val children: List<MdInline>) : MdInline

    data class Strike(val children: List<MdInline>) : MdInline

    data class Code(val text: String) : MdInline

    data class Link(val children: List<MdInline>, val url: String) : MdInline

    /** An image is shown as its alt text and link; it is never fetched automatically. */
    data class Image(val alt: String, val url: String) : MdInline

    data class LineBreak(val hard: Boolean) : MdInline
}

/**
 * Recursive inline parser. It is text-only (no Compose types) so it can be unit tested and
 * so finished blocks can be cached as plain data.
 */
object MdInlineParser {

    fun parse(text: String): List<MdInline> {
        val out = mutableListOf<MdInline>()
        val buffer = StringBuilder()
        var i = 0

        fun flush() {
            if (buffer.isNotEmpty()) {
                out += MdInline.Text(buffer.toString())
                buffer.clear()
            }
        }

        while (i < text.length) {
            val char = text[i]
            when {
                char == '\\' && i + 1 < text.length -> {
                    buffer.append(text[i + 1])
                    i += 2
                }

                char == '`' -> {
                    val close = text.indexOf('`', startIndex = i + 1)
                    if (close > i + 1) {
                        flush()
                        out += MdInline.Code(text.substring(i + 1, close))
                        i = close + 1
                    } else {
                        buffer.append(char)
                        i++
                    }
                }

                char == '!' && i + 1 < text.length && text[i + 1] == '[' -> {
                    val parsed = parseLinkOrImage(text, i + 2)
                    if (parsed != null) {
                        flush()
                        out += MdInline.Image(parsed.first, parsed.second)
                        i = parsed.third
                    } else {
                        buffer.append(char)
                        i++
                    }
                }

                char == '[' -> {
                    val parsed = parseLinkOrImage(text, i + 1)
                    if (parsed != null) {
                        flush()
                        out += MdInline.Link(parse(parsed.first), parsed.second)
                        i = parsed.third
                    } else {
                        buffer.append(char)
                        i++
                    }
                }

                startsWith(text, i, "***") || startsWith(text, i, "___") -> {
                    val marker = text.substring(i, i + 3)
                    val close = text.indexOf(marker, startIndex = i + 3)
                    if (close > i + 3) {
                        flush()
                        out += MdInline.BoldItalic(parse(text.substring(i + 3, close)))
                        i = close + 3
                    } else {
                        buffer.append(marker)
                        i += 3
                    }
                }

                startsWith(text, i, "**") || startsWith(text, i, "__") -> {
                    val marker = text.substring(i, i + 2)
                    val close = text.indexOf(marker, startIndex = i + 2)
                    if (close > i + 2) {
                        flush()
                        out += MdInline.Bold(parse(text.substring(i + 2, close)))
                        i = close + 2
                    } else {
                        buffer.append(marker)
                        i += 2
                    }
                }

                startsWith(text, i, "~~") -> {
                    val close = text.indexOf("~~", startIndex = i + 2)
                    if (close > i + 2) {
                        flush()
                        out += MdInline.Strike(parse(text.substring(i + 2, close)))
                        i = close + 2
                    } else {
                        buffer.append("~~")
                        i += 2
                    }
                }

                char == '*' || char == '_' -> {
                    val close = findClosingEmphasis(text, i, char)
                    if (close > i + 1) {
                        flush()
                        out += MdInline.Italic(parse(text.substring(i + 1, close)))
                        i = close + 1
                    } else {
                        buffer.append(char)
                        i++
                    }
                }

                else -> {
                    buffer.append(char)
                    i++
                }
            }
        }
        flush()
        return out
    }

    /** `[label](url)` or `![alt](url)`, starting just after the opening bracket. */
    private fun parseLinkOrImage(text: String, labelStart: Int): Triple<String, String, Int>? {
        val labelEnd = text.indexOf(']', startIndex = labelStart)
        if (labelEnd < 0) return null
        if (labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') return null
        val urlEnd = text.indexOf(')', startIndex = labelEnd + 2)
        if (urlEnd < 0) return null
        val url = text.substring(labelEnd + 2, urlEnd).trim()
        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
        return Triple(text.substring(labelStart, labelEnd), url, urlEnd + 1)
    }

    private fun findClosingEmphasis(text: String, open: Int, marker: Char): Int {
        var i = open + 1
        while (i < text.length) {
            if (text[i] == marker) {
                // Do not close on the first character of a "**" pair.
                if (i + 1 < text.length && text[i + 1] == marker) {
                    i += 2
                    continue
                }
                if (i == open + 1) return -1
                return i
            }
            if (text[i] == '\n') return -1
            i++
        }
        return -1
    }

    private fun startsWith(text: String, index: Int, marker: String): Boolean =
        text.regionMatches(index, marker, 0, marker.length)
}
