package com.agent.ai.markdown

/**
 * Keeps parsing cheap while tokens arrive.
 *
 * The reply is split at the last blank line that is not inside a code fence. Everything
 * before that point is finished and parsed once; only the growing tail is re-parsed on each
 * update. Block identity (and therefore the renderer's keys) stays stable because the
 * finished list only ever grows at the end.
 */
class IncrementalMarkdown {

    private var stableEnd = 0
    private var stable: List<MdBlock> = emptyList()
    private var tailSource: String? = null
    private var tail: List<MdBlock> = emptyList()

    fun reset() {
        stableEnd = 0
        stable = emptyList()
        tailSource = null
        tail = emptyList()
    }

    fun blocks(text: String): List<MdBlock> {
        if (stableEnd > text.length) reset()

        val boundary = stableBoundary(text, stableEnd)
        if (boundary > stableEnd) {
            stable = stable + MdBlocks.parse(text.substring(stableEnd, boundary), streaming = false)
            stableEnd = boundary
        }

        val remaining = text.substring(stableEnd)
        if (remaining != tailSource) {
            tailSource = remaining
            tail = MdBlocks.parse(remaining, streaming = true)
        }
        return if (tail.isEmpty()) stable else stable + tail
    }

    /** End offset of the last block that is certainly complete, or [from] when none is. */
    private fun stableBoundary(text: String, from: Int): Int {
        var index = from
        var boundary = from
        var fence: String? = null

        while (index < text.length) {
            val lineEnd = text.indexOf('\n', index).let { if (it < 0) text.length else it }
            val line = text.substring(index, lineEnd)
            val trimmed = line.trimStart()

            if (fence == null) {
                if (MdBlocks.isFenceStart(trimmed)) fence = trimmed.take(3)
            } else if (trimmed.startsWith(fence)) {
                fence = null
            }

            // A blank line outside a fence ends whatever block came before it.
            if (fence == null && line.isBlank() && index > from && lineEnd + 1 < text.length) {
                boundary = lineEnd + 1
            }
            index = lineEnd + 1
        }
        return boundary
    }
}
