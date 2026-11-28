package com.agent.ai.terminal

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Where a session runs. The working directory is always the project workspace. */
data class SessionSpec(
    val workspace: File,
    val home: File,
    val columns: Int,
    val rows: Int,
)

/**
 * The result of every check the terminal runs before it starts. Nothing is assumed about
 * the device: an unsupported device gets a sentence it can act on, and chat keeps working.
 */
data class TerminalSupport(
    val supported: Boolean,
    val summary: String,
    val checks: List<SupportCheck> = emptyList(),
    val details: List<String> = emptyList(),
)

data class SupportCheck(
    val name: String,
    val passed: Boolean,
    val detail: String,
)

/**
 * One shell session. A proot based backend and the device shell implement this, so another
 * runtime can replace them without touching the screens.
 */
interface TerminalBackend {
    val id: String
    val label: String

    /** Whether this backend can run here, with the reason when it cannot. */
    suspend fun support(spec: SessionSpec): TerminalSupport

    /** Starts the process. Returns a failure instead of throwing. */
    suspend fun start(spec: SessionSpec): Result<Unit>

    /** Text typed by the user, sent to the process as is. */
    fun send(input: String)

    /** The terminal changed size; the shell is told with stty. */
    fun resize(columns: Int, rows: Int)

    fun stop()

    fun isRunning(): Boolean

    /** Raw output, already decoded. The parser turns it into a screen. */
    val output: Flow<String>

    /** The last exit code, when the process ended. */
    val exitCode: StateFlow<Int?>

    /** A readable description of the last failure, for the Diagnostics screen. */
    val lastError: StateFlow<String?>
}
