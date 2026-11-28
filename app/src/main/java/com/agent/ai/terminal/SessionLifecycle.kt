package com.agent.ai.terminal

/**
 * Chooses the first backend that reports itself supported, starts it and stops it again.
 * Keeping this separate from the screen makes the whole lifecycle testable with a fake
 * backend, which is how the behaviour is verified without a device.
 */
class SessionLifecycle {

    var backend: TerminalBackend? = null
        private set

    /** The reasons the earlier candidates were skipped, in order. */
    var skipped: List<String> = emptyList()
        private set

    val isRunning: Boolean get() = backend?.isRunning() == true

    suspend fun start(candidates: List<TerminalBackend>, spec: SessionSpec): Result<TerminalBackend> {
        val reasons = mutableListOf<String>()
        for (candidate in candidates) {
            val support = candidate.support(spec)
            if (!support.supported) {
                reasons += "${candidate.label}: ${support.summary}"
                continue
            }
            val started = candidate.start(spec)
            if (started.isFailure) {
                reasons += "${candidate.label}: ${started.exceptionOrNull()?.message.orEmpty()}"
                continue
            }
            skipped = reasons
            backend = candidate
            return Result.success(candidate)
        }
        skipped = reasons
        return Result.failure(
            IllegalStateException(reasons.firstOrNull() ?: "No shell could be started on this device."),
        )
    }

    fun stop() {
        backend?.stop()
        backend = null
    }
}
