package com.agent.ai.util

import java.io.InputStream

/**
 * Process helpers that work on API 24. `waitFor(timeout)`, `isAlive` and `destroyForcibly`
 * arrived in API 26, so the exit state is polled instead of assumed.
 */
object Processes {

    fun exited(process: Process): Boolean = runCatching { process.exitValue() }.isSuccess

    fun exitCode(process: Process): Int = runCatching { process.exitValue() }.getOrDefault(-1)

    /** Waits up to [seconds] for the process to end. Returns true when it did. */
    fun awaitExit(process: Process, seconds: Long): Boolean {
        val deadline = System.currentTimeMillis() + seconds * 1_000L
        while (System.currentTimeMillis() < deadline) {
            if (exited(process)) return true
            runCatching { Thread.sleep(25) }
        }
        return exited(process)
    }

    /** Ends the process and waits briefly, then destroys it again if it ignored the first. */
    fun kill(process: Process) {
        runCatching { process.destroy() }
        if (awaitExit(process, 2)) return
        runCatching { process.destroy() }
    }

    /** Skips bytes without `skipNBytes`, which needs a newer API than the app supports. */
    fun skip(input: InputStream, count: Long) {
        var left = count
        val buffer = ByteArray(4_096)
        while (left > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read < 0) return
            left -= read
        }
    }
}
