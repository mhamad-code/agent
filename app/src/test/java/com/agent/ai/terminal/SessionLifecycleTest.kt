package com.agent.ai.terminal

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A backend that only remembers what the lifecycle asked it to do. */
private class FakeBackend(
    override val id: String,
    override val label: String,
    private val supported: Boolean,
    private val failStart: Boolean = false,
) : TerminalBackend {

    var started = 0
    var stopped = 0
    var running = false
    val sent = mutableListOf<String>()
    var resized: Pair<Int, Int>? = null

    override suspend fun support(spec: SessionSpec): TerminalSupport = TerminalSupport(
        supported = supported,
        summary = if (supported) "$label is available" else "$label is not available",
    )

    override suspend fun start(spec: SessionSpec): Result<Unit> {
        started++
        if (failStart) return Result.failure(IllegalStateException("start failed"))
        running = true
        return Result.success(Unit)
    }

    override fun send(input: String) {
        sent += input
    }

    override fun resize(columns: Int, rows: Int) {
        resized = columns to rows
    }

    override fun stop() {
        stopped++
        running = false
    }

    override fun isRunning(): Boolean = running

    override val output: Flow<String> = emptyFlow()
    override val exitCode = MutableStateFlow<Int?>(null)
    override val lastError = MutableStateFlow<String?>(null)
}

class SessionLifecycleTest {

    private val spec = SessionSpec(
        workspace = File("build/tmp/workspace"),
        home = File("build/tmp/home"),
        columns = 80,
        rows = 24,
    )

    @Test
    fun `the first supported backend is started and the unsupported one is recorded`() = runTest {
        val unsupported = FakeBackend("proot", "Alpine in proot", supported = false)
        val device = FakeBackend("device-shell", "Device shell", supported = true)
        val lifecycle = SessionLifecycle()

        val result = lifecycle.start(listOf(unsupported, device), spec)

        assertTrue(result.isSuccess)
        assertEquals("device-shell", result.getOrNull()?.id)
        assertEquals(0, unsupported.started)
        assertEquals(1, device.started)
        assertTrue(lifecycle.isRunning)
        assertEquals(1, lifecycle.skipped.size)
    }

    @Test
    fun `a failed start falls through to the next backend`() = runTest {
        val broken = FakeBackend("proot", "Alpine in proot", supported = true, failStart = true)
        val device = FakeBackend("device-shell", "Device shell", supported = true)
        val lifecycle = SessionLifecycle()

        val result = lifecycle.start(listOf(broken, device), spec)

        assertEquals("device-shell", result.getOrNull()?.id)
        assertEquals(1, broken.started)
        assertEquals(1, device.started)
    }

    @Test
    fun `when nothing is supported the reason is returned and nothing runs`() = runTest {
        val lifecycle = SessionLifecycle()

        val result = lifecycle.start(
            listOf(FakeBackend("proot", "Alpine in proot", supported = false)),
            spec,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("not available"))
        assertFalse(lifecycle.isRunning)
    }

    @Test
    fun `stop ends the running session and a second stop is harmless`() = runTest {
        val device = FakeBackend("device-shell", "Device shell", supported = true)
        val lifecycle = SessionLifecycle()
        lifecycle.start(listOf(device), spec)

        lifecycle.stop()
        lifecycle.stop()

        assertNull(lifecycle.backend)
        assertFalse(lifecycle.isRunning)
        assertEquals(1, device.stopped)
    }

    @Test
    fun `input and resize reach the running backend`() = runTest {
        val device = FakeBackend("device-shell", "Device shell", supported = true)
        val lifecycle = SessionLifecycle()
        val started = lifecycle.start(listOf(device), spec).getOrNull()
        assertNotNull(started)

        started?.send("ls\n")
        started?.resize(120, 40)

        assertEquals(listOf("ls\n"), device.sent)
        assertEquals(120 to 40, device.resized)
    }

    /** The shared flow type is part of the contract, so the fake keeps it honest. */
    @Test
    fun `a fake backend exposes empty flows before it starts`() {
        val fake = FakeBackend("x", "X", supported = true)
        assertNotNull(fake.output)
        assertNotNull(fake.exitCode)
        assertNull(fake.exitCode.value)
    }
}
