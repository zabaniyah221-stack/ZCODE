package com.zaba.zcode.core.execution

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * ExecutionEngine — port of zabacode/core/executor.py (541 lines) with all guards (S-18, F-07)
 * Fase 0 skeleton: constants + guards, real ProcessBuilder in Fase 1
 *
 * Guards (must not be removed):
 * - MAX_CODE_BYTES 512KB (S-18, F-07 off-by-9 fixed: no SAFE_INPUT_PATCH injection)
 * - MAX_OUTPUT_CHARS 256KB
 * - MAX_INTERACTIVE_QUEUE 10k
 * - timeout 30s, PGID kill
 * - interactive: 120s lifetime, 60s inactivity, 8KB per send
 */
object ExecutionEngine {
    const val MAX_CODE_BYTES = 512 * 1024 // 512 KB
    const val MAX_OUTPUT_CHARS = 256 * 1024 // 256 KB
    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val MAX_INTERACTIVE_DURATION_MS = 120_000L
    const val MAX_INTERACTIVE_INACTIVITY_MS = 60_000L
    const val MAX_INTERACTIVE_BYTES = 8192 // 8KB per send
    const val MAX_INTERACTIVE_QUEUE = 10000
    const val MAX_IMAGE_BYTES = 8 * 1024 * 1024 // 8 MB skip

    data class RunResult(
        val ok: Boolean,
        val stdout: String,
        val stderr: String,
        val timeout: Boolean,
        val images: List<String> = emptyList()
    )

    data class OutputChunk(val stream: String, val text: String)

    /**
     * Fase 0: mock isolated run with guards — Fase 1 will be ProcessBuilder + withTimeout
     * No prelude injection — wrapper process will handle input() stub separately (Fix F-07)
     */
    suspend fun runIsolated(code: String, stdin: String = ""): RunResult {
        // Guard: code too large
        if (code.toByteArray(Charsets.UTF_8).size > MAX_CODE_BYTES) {
            return RunResult(false, "", "Source too large: >512KB", false)
        }
        // Guard: stdin too large
        if (stdin.toByteArray(Charsets.UTF_8).size > MAX_CODE_BYTES) {
            return RunResult(false, "", "Stdin too large", false)
        }
        // Fase 0 mock: just echo
        return RunResult(true, "Fase0 mock: ${code.take(100)}", "", false)
    }

    /**
     * Fase 0: mock interactive — Fase 1 will be real PTY via terminal-view + realpty.py
     * PTY: ketik langsung di terminal (no stdin field), supports input() directly (user request)
     */
    fun startInteractive(code: String): Flow<OutputChunk> = flow {
        if (code.toByteArray(Charsets.UTF_8).size > MAX_CODE_BYTES) {
            emit(OutputChunk("stderr", "Source too large"))
            return@flow
        }
        emit(OutputChunk("stdout", "PTY Fase0 mock — will be RealPtyProcess in Fase 1\n"))
        emit(OutputChunk("stdout", code.take(200)))
    }

    // Image collector — Fase 0 placeholder, Fase 1 will be FileObserver + baseline dedup + 8MB skip (B-13)
    fun collectNewImages(baseline: Set<String>): Pair<List<String>, Set<String>> = emptyList<String>() to baseline
}
