package com.agent.ai.terminal

import android.content.Context
import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A shell driven through ProcessBuilder. Both backends share the reading, resizing and
 * stopping logic; only the command line differs.
 */
abstract class ProcessTerminalBackend(protected val context: Context) : TerminalBackend {

    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 1_024)
    override val output: SharedFlow<String> = _output.asSharedFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    override val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var process: Process? = null
    private var reader: Thread? = null
    private var columns = 80
    private var rows = 24

    protected abstract fun command(spec: SessionSpec): List<String>

    protected open fun environment(spec: SessionSpec): Map<String, String> = mapOf(
        "HOME" to spec.home.absolutePath,
        "TMPDIR" to spec.home.absolutePath,
        "TERM" to "xterm-256color",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "LANG" to "C.UTF-8",
    )

    protected open fun workingDirectory(spec: SessionSpec): File = spec.workspace

    override suspend fun start(spec: SessionSpec): Result<Unit> = runCatching {
        if (isRunning()) return@runCatching
        columns = spec.columns
        rows = spec.rows
        val builder = ProcessBuilder(command(spec))
            .directory(workingDirectory(spec))
            .redirectErrorStream(true)
        builder.environment().clear()
        builder.environment().putAll(environment(spec))
        val started = builder.start()
        process = started
        _exitCode.value = null
        _lastError.value = null
        reader = Thread {
            val buffer = ByteArray(4_096)
            val decoder = Charsets.UTF_8.newDecoder()
            try {
                started.inputStream.use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        val text = decoder.decode(java.nio.ByteBuffer.wrap(buffer, 0, read)).toString()
                        _output.tryEmit(text)
                    }
                }
            } catch (t: Throwable) {
                _lastError.value = t.message ?: t::class.java.simpleName
            }
            val code = runCatching { started.waitFor() }.getOrDefault(-1)
            _exitCode.value = code
            process = null
        }.apply {
            name = "terminal-reader"
            isDaemon = true
            start()
        }
        // A shell without a prompt looks dead; ask it for one.
        send("stty cols $columns rows $rows 2>/dev/null; export TERM=xterm-256color; cd \"${spec.workspace.absolutePath}\" 2>/dev/null; echo ready\n")
    }

    override fun send(input: String) {
        val current = process ?: return
        runCatching {
            current.outputStream.write(input.toByteArray())
            current.outputStream.flush()
        }.onFailure { _lastError.value = it.message }
    }

    override fun resize(columns: Int, rows: Int) {
        this.columns = columns
        this.rows = rows
        send("stty cols $columns rows $rows 2>/dev/null\n")
    }

    override fun stop() {
        val current = process ?: return
        runCatching { current.outputStream.write("exit\n".toByteArray()) }
        com.agent.ai.util.Processes.kill(current)
        process = null
    }

    override fun isRunning(): Boolean = process?.let { !com.agent.ai.util.Processes.exited(it) } == true
}

/**
 * The device shell. It runs everywhere Android runs, inside the app's own sandbox, and is
 * the fallback when no Linux environment is installed. It cannot install Linux packages,
 * and the screen says so.
 */
class DeviceShellBackend(context: Context) : ProcessTerminalBackend(context) {

    override val id: String = ID
    override val label: String = "Device shell"

    companion object {
        const val ID = "device-shell"
    }

    override fun command(spec: SessionSpec): List<String> = listOf("/system/bin/sh", "-i")

    override fun environment(spec: SessionSpec): Map<String, String> = mapOf(
        "HOME" to spec.home.absolutePath,
        "TMPDIR" to spec.home.absolutePath,
        "TERM" to "xterm-256color",
        "PATH" to "/system/bin:/system/xbin:/vendor/bin:/usr/bin:/bin",
        "PS1" to "$ ",
    )

    override suspend fun support(spec: SessionSpec): TerminalSupport {
        val shell = File("/system/bin/sh")
        val checks = mutableListOf(
            SupportCheck("/system/bin/sh exists", shell.exists(), shell.absolutePath),
            SupportCheck("The shell runs", canRun(), "echo from a test process"),
            SupportCheck(
                "A Linux environment is installed",
                LinuxRuntime.isProotInstalled(context),
                "Without it, packages cannot be installed",
            ),
        )
        return TerminalSupport(
            supported = shell.exists() && checks[1].passed,
            summary = if (shell.exists() && checks[1].passed) {
                "The device shell is available. Linux packages need a proot environment."
            } else {
                "This device does not expose a shell the app can start."
            },
            checks = checks,
            details = listOf(
                "CPU: ${Build.SUPPORTED_ABIS.joinToString()}",
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "Workspace: ${spec.workspace.absolutePath}",
            ),
        )
    }

    private fun canRun(): Boolean = runCatching {
        val process = ProcessBuilder("/system/bin/sh", "-c", "echo ok").start()
        val finished = com.agent.ai.util.Processes.awaitExit(process, 3)
        val text = process.inputStream.bufferedReader().readText().trim()
        if (!finished) com.agent.ai.util.Processes.kill(process)
        finished && text == "ok"
    }.getOrDefault(false)
}

/**
 * proot: a user space chroot, so a real Alpine userland runs without root. The binary is
 * GPLv2 and is not bundled in the APK; the app looks for it under `files/linux/bin/proot`.
 */
class ProotBackend(context: Context) : ProcessTerminalBackend(context) {

    override val id: String = "proot"
    override val label: String = "Alpine in proot"

    override fun command(spec: SessionSpec): List<String> {
        val proot = LinuxRuntime.prootBinary(context).absolutePath
        val rootfs = LinuxRuntime.rootfs(context).absolutePath
        return listOf(
            proot,
            "-0",
            "-r", rootfs,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${spec.home.absolutePath}:/root",
            "-b", "${spec.workspace.absolutePath}:/workspace",
            "-w", "/workspace",
            "/bin/sh", "-i",
        )
    }

    override fun environment(spec: SessionSpec): Map<String, String> = mapOf(
        "HOME" to "/root",
        "TERM" to "xterm-256color",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "LANG" to "C.UTF-8",
        // The seccomp filter proot uses is not allowed on every Android build.
        "PROOT_NO_SECCOMP" to "1",
        "PROOT_TMP_DIR" to context.cacheDir.absolutePath,
    )

    override fun workingDirectory(spec: SessionSpec): File = spec.workspace

    override suspend fun support(spec: SessionSpec): TerminalSupport {
        val proot = LinuxRuntime.prootBinary(context)
        val rootfs = LinuxRuntime.rootfs(context)
        val arch = LinuxRuntime.arch()
        val checks = listOf(
            SupportCheck("CPU type is supported", arch != null, arch ?: "unknown"),
            SupportCheck("The proot binary is installed", proot.isFile, proot.absolutePath),
            SupportCheck("The proot binary is executable", proot.canExecute(), "chmod 755"),
            SupportCheck("The root file system is installed", File(rootfs, "bin/sh").isFile, rootfs.absolutePath),
        )
        val supported = checks.all { it.passed }
        return TerminalSupport(
            supported = supported,
            summary = if (supported) {
                "Alpine runs in proot, with /workspace mapped to the project folder."
            } else {
                checks.first { !it.passed }.name + ". " +
                    "Install the Linux environment, or use the device shell."
            },
            checks = checks,
            details = listOf(
                "Path inside the session: /workspace",
                "proot is GPLv2: its source is at https://proot.gitlab.io/proot/",
            ),
        )
    }
}
