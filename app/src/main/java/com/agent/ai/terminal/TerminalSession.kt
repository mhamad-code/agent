package com.agent.ai.terminal

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one live terminal of the process. It outlives rotation and leaving the screen, and
 * the foreground service keeps the process alive while the user is in another app.
 */
object TerminalSession {

    data class Ui(
        val running: Boolean = false,
        val backendId: String = DeviceShellBackend.ID,
        val backendLabel: String = "Device shell",
        val support: TerminalSupport? = null,
        val starting: Boolean = false,
        val lastError: String? = null,
        val exitCode: Int? = null,
        val workspace: String = "",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycle = SessionLifecycle()
    private var backend: TerminalBackend? = null
    private val screen = AnsiScreen()
    private val _snapshot = MutableStateFlow(screen.snapshot())
    val snapshot: StateFlow<AnsiScreen.Snapshot> = _snapshot.asStateFlow()

    private val _state = MutableStateFlow(Ui())
    val state: StateFlow<Ui> = _state.asStateFlow()

    /** True once the user asked for Linux; the proot backend is preferred when it can run. */
    var preferLinux: Boolean = true

    fun backends(context: Context): List<TerminalBackend> =
        listOf(ProotBackend(context), DeviceShellBackend(context))

    fun selected(context: Context): TerminalBackend =
        if (preferLinux) ProotBackend(context) else DeviceShellBackend(context)

    /** Checks the device without starting anything, so the screen can explain itself. */
    suspend fun inspect(context: Context): TerminalSupport {
        val spec = spec(context)
        val proot = ProotBackend(context)
        val device = DeviceShellBackend(context)
        val prootSupport = proot.support(spec)
        val deviceSupport = device.support(spec)
        val chosen = if (prootSupport.supported) prootSupport else deviceSupport
        _state.value = _state.value.copy(
            support = chosen,
            workspace = spec.workspace.absolutePath,
        )
        return chosen
    }

    suspend fun start(context: Context, forceDeviceShell: Boolean = false) {
        if (_state.value.starting) return
        if (backend?.isRunning() == true) return
        _state.value = _state.value.copy(starting = true, lastError = null)
        val spec = spec(context)
        val candidates = if (forceDeviceShell) {
            listOf(DeviceShellBackend(context))
        } else {
            listOf(ProotBackend(context), DeviceShellBackend(context))
        }
        screen.clear()
        _snapshot.value = screen.snapshot()
        val started = lifecycle.start(candidates, spec)
        val chosen = started.getOrNull()
        if (chosen == null) {
            _state.value = _state.value.copy(
                starting = false,
                running = false,
                lastError = started.exceptionOrNull()?.message
                    ?: "No shell could be started on this device.",
            )
            return
        }
        backend = chosen
        _state.value = _state.value.copy(
            running = true,
            starting = false,
            backendId = chosen.id,
            backendLabel = chosen.label,
            workspace = spec.workspace.absolutePath,
        )
        collect(chosen)
        TerminalSessionService.start(context)
    }

    private fun collect(candidate: TerminalBackend) {
        scope.launch {
            candidate.output.collect { chunk ->
                screen.feed(chunk)
                _snapshot.value = screen.snapshot()
            }
        }
        scope.launch {
            candidate.exitCode.collect { code ->
                if (code != null) {
                    _state.value = _state.value.copy(running = false, exitCode = code)
                    TerminalSessionService.stop(AgentAppContext.get())
                }
            }
        }
        scope.launch {
            candidate.lastError.collect { error ->
                if (error != null) _state.value = _state.value.copy(lastError = error)
            }
        }
    }

    fun write(text: String) {
        backend?.send(text)
    }

    fun resize(columns: Int, rows: Int) {
        backend?.resize(columns, rows)
    }

    fun clear() {
        screen.clear()
        _snapshot.value = screen.snapshot()
    }

    fun stop(context: Context) {
        lifecycle.stop()
        backend = null
        _state.value = _state.value.copy(running = false)
        TerminalSessionService.stop(context)
    }

    fun spec(context: Context): SessionSpec = SessionSpec(
        workspace = LinuxRuntime.workspace(context),
        home = LinuxRuntime.home(context),
        columns = 80,
        rows = 24,
    )

    /** Sends a key sequence such as an arrow or Tab straight to the process. */
    fun key(sequence: String) = write(sequence)

    val keys = listOf(
        "Esc" to "\u001b",
        "Tab" to "\t",
        "Ctrl+C" to "\u0003",
        "Ctrl+D" to "\u0004",
        "Up" to "\u001b[A",
        "Down" to "\u001b[B",
        "Right" to "\u001b[C",
        "Left" to "\u001b[D",
        "|" to "|",
        "~" to "~",
        "/" to "/",
        "-" to "-",
    )
}

/** The application context, so the session can stop the service without a screen. */
object AgentAppContext {
    private var context: Context? = null

    fun set(context: Context) {
        this.context = context.applicationContext
    }

    fun get(): Context = requireNotNull(context) { "The application context is not set yet" }
}
