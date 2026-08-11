package com.zaba.zcode.core.execution

import com.chaquo.python.PyObject
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * TerminalBridge — jembatan Java ↔ Python (Chaquopy) untuk eksekusi in-process.
 *
 * Python memanggil metode publik kelas ini (proxy otomatis Chaquopy):
 * - write(s, stream) → output script dialirkan ke Terminal UI; stream "out"/"err"
 *                      (SPEC-001 Phase 0: stdout/stderr dipisah untuk disk log)
 * - readLine()      → blokir sampai Kotlin mengirim satu baris (Enter di terminal);
 *                     kembalikan null bila dibatalkan
 * - waitingInput(b) → tandai session state WAITING_FOR_INPUT (input() nge-blok)
 * - isInterrupted() → flag Ctrl+C
 * - workspaceDir()  → folder filesDir (cwd script = workspace)
 * - onExit(code, tb)→ script selesai (dengan traceback bila error) — hanya sekali
 * - setWorkerThread → best-effort interrupt thread worker
 *
 * Ctrl+C (deterministik): interrupt() → BridgeStdin di Python melihat flag dan
 * melempar KeyboardInterrupt (script yang nge-blok di input() pasti terputus).
 */
class TerminalBridge(
    private val onOutput: (stream: String, text: String) -> Unit,
    private val onExit: (code: Int) -> Unit,
    private val onState: (SessionState) -> Unit = {}
) {
    private val inputQueue = ArrayBlockingQueue<String>(ExecutionEngine.MAX_INTERACTIVE_QUEUE)
    @Volatile
    private var interrupted = false
    @Volatile
    private var workerThread: PyObject? = null
    @Volatile
    private var exited = false
    @Volatile
    private var exitCodeValue = -1
    @Volatile
    private var stateValue = SessionState.START

    val isExited: Boolean get() = exited
    val exitCode: Int get() = exitCodeValue
    val state: SessionState get() = stateValue

    private fun setState(s: SessionState) {
        if (stateValue != s) {
            stateValue = s
            onState(s)
        }
    }

    // ---------- dipanggil dari Python (proxy) ----------

    /** Backward-compat: write(s) → stream "out". */
    fun write(s: String) = write(s, "out")

    fun write(s: String, stream: String) {
        if (s.isNotEmpty()) onOutput(stream, s)
    }

    fun readLine(): String? {
        while (!interrupted) {
            val line = inputQueue.poll(250, TimeUnit.MILLISECONDS)
            if (line != null) {
                setState(SessionState.RUNNING)
                return line
            }
        }
        return null
    }

    fun waitingInput(waiting: Boolean) {
        setState(if (waiting) SessionState.WAITING_FOR_INPUT else SessionState.RUNNING)
    }

    fun isInterrupted(): Boolean = interrupted

    fun workspaceDir(): String = ExecutionEngine.workspaceDirPath

    fun setWorkerThread(thread: PyObject?) {
        workerThread = thread
    }

    fun onExit(code: Int, traceback: String?) {
        if (exited) return
        exited = true
        exitCodeValue = code
        if (traceback != null && traceback.isNotBlank()) {
            onOutput("err", traceback)
        }
        setState(if (code == 0) SessionState.EXITED else SessionState.FAILED)
        onExit(code)
    }

    // ---------- dipanggil dari Kotlin (UI / session) ----------

    fun sendLine(line: String) {
        if (!interrupted) {
            inputQueue.offer(line, 1, TimeUnit.SECONDS)
        }
    }

    fun interrupt() {
        interrupted = true
        setState(SessionState.INTERRUPTING)
        // best-effort: KeyboardInterrupt di thread worker bila API tersedia
        try {
            workerThread?.callAttr("interrupt")
        } catch (e: Exception) {
            // API tidak tersedia — flag sudah cukup untuk input() yang nge-blok
        }
    }

    /** Fallback error dari sisi Kotlin (start Python gagal, dll). */
    fun abort(message: String) {
        onOutput("err", message)
        setState(SessionState.FAILED)
        onExit(1, null)
    }
}
