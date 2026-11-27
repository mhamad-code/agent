package com.agent.ai.data.chat

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * A small PDF text extractor, with no dependency.
 *
 * It reads every content stream (compressed with FlateDecode or stored raw) and collects
 * the strings of the text showing operators: `Tj`, `TJ`, `'` and `"`. That is enough for
 * ordinary documents; a scan without a text layer yields nothing, and the caller says so.
 */
object PdfTextExtractor {

    private const val MaxExtracted = 400_000

    fun extract(bytes: ByteArray): String {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val out = StringBuilder()
        var cursor = 0
        while (cursor < raw.length && out.length < MaxExtracted) {
            val start = raw.indexOf("stream", cursor)
            if (start < 0) break
            var dataStart = start + "stream".length
            if (dataStart < raw.length && raw[dataStart] == '\r') dataStart++
            if (dataStart < raw.length && raw[dataStart] == '\n') dataStart++
            val end = raw.indexOf("endstream", dataStart)
            if (end < 0) break
            val chunk = raw.substring(dataStart, end)
            val compressed = raw.substring((start - 400).coerceAtLeast(0), start).contains("FlateDecode")
            val content = if (compressed) inflate(chunk) else chunk
            if (content != null) {
                out.append(extractOperators(content)).append('\n')
            }
            cursor = end + "endstream".length
        }
        return tidy(out.toString())
    }

    private fun inflate(chunk: String): String? {
        val bytes = chunk.toByteArray(Charsets.ISO_8859_1)
        val inflater = Inflater()
        return try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var guard = 0
            while (!inflater.finished() && guard++ < 512) {
                val read = inflater.inflate(buffer)
                if (read == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buffer, 0, read)
                }
                if (out.size() > MaxExtracted) break
            }
            String(out.toByteArray(), Charsets.ISO_8859_1)
        } catch (e: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    /** Collects the strings passed to the text operators, in reading order. */
    private fun extractOperators(content: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < content.length) {
            val open = content.indexOf('(', index)
            if (open < 0) break
            val (value, next) = readLiteral(content, open)
            index = next
            val operator = content.substring(next, (next + 3).coerceAtMost(content.length)).trimStart()
            val isText = operator.startsWith("Tj") || operator.startsWith("TJ") ||
                operator.startsWith("'") || operator.startsWith("\"")
            // Inside an array the string is followed by a number then `] TJ`, which the
            // `TJ` check below catches through the closing bracket search.
            val nearArray = content.substring(next, (next + 8).coerceAtMost(content.length)).contains(']')
            if (isText) {
                out.append(value)
            } else if (nearArray && content.substring(next, (next + 24).coerceAtMost(content.length)).contains("TJ")) {
                out.append(value)
            }
        }
        return out.toString()
    }

    /** A PDF literal string, with escapes and balanced parentheses. */
    private fun readLiteral(content: String, open: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var depth = 0
        var i = open
        while (i < content.length) {
            val c = content[i]
            when {
                c == '\\' && i + 1 < content.length -> {
                    val next = content[i + 1]
                    when (next) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        '(', ')', '\\' -> sb.append(next)
                        in '0'..'7' -> {
                            var octal = ""
                            var j = i + 1
                            while (j < content.length && octal.length < 3 && content[j] in '0'..'7') {
                                octal += content[j]
                                j++
                            }
                            sb.append(octal.toInt(8).toChar())
                            i = j - 1
                        }

                        else -> sb.append(next)
                    }
                    i += 2
                }

                c == '(' -> {
                    depth++
                    if (depth > 1) sb.append(c)
                    i++
                }

                c == ')' -> {
                    depth--
                    i++
                    if (depth <= 0) return decode(sb.toString()) to i
                }

                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return decode(sb.toString()) to content.length
    }

    /** UTF-16BE with a byte order mark, otherwise Latin-1 (PDFDocEncoding is close). */
    private fun decode(raw: String): String {
        if (raw.length >= 2 && raw[0] == '\u00FE' && raw[1] == '\u00FF') {
            val bytes = raw.substring(2).toByteArray(Charsets.ISO_8859_1)
            return String(bytes, Charsets.UTF_16BE)
        }
        return raw
    }

    private fun tidy(text: String): String =
        text.replace("\r", "\n")
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
}
