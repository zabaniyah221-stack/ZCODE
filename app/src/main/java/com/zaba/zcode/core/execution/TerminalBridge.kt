package com.zaba.zcode.core.execution

import com.chaquo.python.PyObject
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * TerminalBridge — jembatan Java ↔ Python (Chaquopy) untuk eksekusi in-process.
 *
 * Python memanggil metode publik kelas ini (proxy otomatis Chaquopy):
 * - write(s)        → output script (print/traceback) dialirkan ke Terminal UI
 * - readLine()      → blokir sampai Kotlin mengirim satu baris (Enter di terminal);
 *                     kembalikan null bila dibatalkan
 * - isInterrupted() → flag Ctrl+C
 * - workspaceDir()  → folder filesDir (cwd script = workspace)
 * - onExit(code, tb)→ script selesai (dengan traceback bila error) — hanya sekali
 * - setWorkerThread → best-effort interrupt thread worker
 *
 * Ctrl+C (deterministik): interrupt() → BridgeStdin di Python melihat flag dan
 * melempar KeyboardInterrupt (script yang nge-blok di input() pasti terputus).
 * Best-effort: coba interrupt() thread worker bila Chaquopy menyediakannya.
 */
class TerminalBridge(
    private val onOutput: (String) -> Unit,
    private val onExit: (code: Int) -> Unit
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

    val isExited: Boolean get() = exited
    val exitCode: Int get() = exitCodeValue

    // ---------- dipanggil dari Python (proxy) ----------

    fun write(s: String) {
        onOutput(s)
    }

    fun readLine(): String? {
        while (!interrupted) {
            val line = inputQueue.poll(250, TimeUnit.MILLISECONDS)
            if (line != null) return line
        }
        return null
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
            onOutput(traceback)
        }
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
        // best-effort: KeyboardInterrupt di thread worker bila API tersedia
        try {
            workerThread?.callAttr("interrupt")
        } catch (e: Exception) {
            // API tidak tersedia — flag sudah cukup untuk input() yang nge-blok
        }
    }

    /** Fallback error dari sisi Kotlin (start Python gagal, dll). */
    fun abort(message: String) {
        onOutput(message)
        onExit(1, null)
    }
}
