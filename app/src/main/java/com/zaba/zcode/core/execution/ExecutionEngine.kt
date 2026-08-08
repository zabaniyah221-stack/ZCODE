package com.zaba.zcode.core.execution

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * ExecutionEngine — eksekusi Python untuk ZCODE, dual-backend.
 *
 * 1. Chaquopy (Android / perangkat): Python 3.11 in-process via `zcode_runner.py`.
 *    - ketik langsung: input() membaca baris dari queue Kotlin (Enter → stdin, tanpa
 *      tombol Send — keputusan tim)
 *    - Ctrl+C: flag interrupt → KeyboardInterrupt deterministik untuk script yang
 *      nge-blok di input(); best-effort interrupt() thread worker
 *    - pip runtime: `zcode_pip.py` (pip_main in-process) → log streaming
 * 2. ProcessBuilder (desktop/sandbox/dev): `python3 -u <file>` subprocess nyata,
 *    dipakai untuk pengujian lokal & CI logic verification.
 *
 * Guards (tidak boleh dihapus):
 * - MAX_CODE_BYTES 512KB (S-18, F-07 off-by-9 fixed: no prelude injection — kode user
 *   dieksekusi apa adanya, tanpa patch stdin 9 baris di depan)
 * - MAX_OUTPUT_CHARS 256KB, MAX_INTERACTIVE_QUEUE 10k
 * - timeout 30s (batch), interactive 120s lifetime / 60s inactivity / 8KB per send
 */
object ExecutionEngine {
    const val MAX_CODE_BYTES = 512 * 1024 // 512 KB
    const val MAX_OUTPUT_CHARS = 256 * 1024 // 256 KB
    const val DEFAULT_TIMEOUT_MS = 30_000L // batch timeout 30s
    const val MAX_INTERACTIVE_DURATION_MS = 120_000L
    const val MAX_INTERACTIVE_INACTIVITY_MS = 60_000L
    const val MAX_INTERACTIVE_BYTES = 8192 // 8KB per send
    const val MAX_INTERACTIVE_QUEUE = 10000
    const val MAX_IMAGE_BYTES = 8 * 1024 * 1024 // 8 MB skip (target Fase 3: matplotlib inline)

    /** Folder workspace (filesDir) — di-set oleh WorkspaceViewModel, jadi cwd script = workspace. */
    @Volatile
    var workspaceDirPath: String = ""

    data class RunResult(
        val ok: Boolean,
        val stdout: String,
        val stderr: String,
        val timeout: Boolean,
        val images: List<String> = emptyList()
    )

    data class OutputChunk(val stream: String, val text: String)

    // ------------------------------------------------------------------
    // Backend selection
    // ------------------------------------------------------------------

    /** Deteksi runtime Chaquopy (ada di APK Android; tidak ada di desktop JVM). */
    fun isChaquopyAvailable(): Boolean = try {
        Class.forName("com.chaquo.python.Python")
        true
    } catch (e: Throwable) {
        false
    }

    fun describeBackend(): String =
        if (isChaquopyAvailable()) "Python 3.11 (Chaquopy in-process)" else "python3 subprocess"

    // ------------------------------------------------------------------
    // Interactive session (PTY layer)
    // ------------------------------------------------------------------

    interface InteractiveSession {
        fun sendInput(line: String)
        fun sendCtrlC()
        fun sendKill()
        fun isAlive(): Boolean
        fun waitForExit(): Int
    }

    fun startInteractiveSession(
        context: Context?,
        file: File,
        onOutput: (String) -> Unit,
        onExit: (Int) -> Unit
    ): InteractiveSession {
        // TEST A: hanya backend subprocess (Chaquopy dinonaktifkan)
        val pb = ProcessBuilder("python3", "-u", file.absolutePath)
        pb.redirectErrorStream(true)
        return ProcessSession(pb.start(), onOutput, onExit)
    }

    // ------------------------------------------------------------------
    // Backend: subprocess python3 (desktop / dev)
    // ------------------------------------------------------------------

    private class ProcessSession(
        private val process: Process,
        private val onOutput: (String) -> Unit,
        private val onExit: (Int) -> Unit
    ) : InteractiveSession {
        private val exitValue = AtomicInteger(-1)
        private val done = CountDownLatch(1)

        init {
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                    val batch = StringBuilder()
                    var c: Int
                    while (reader.read().also { c = it } != -1) {
                        batch.append(c.toChar())
                        if (batch.length >= 256) {
                            val chunk = batch.toString()
                            batch.clear()
                            onOutput(chunk)
                        }
                    }
                    if (batch.isNotEmpty()) onOutput(batch.toString())
                    exitValue.set(process.waitFor())
                } catch (e: Exception) {
                    exitValue.set(-1)
                } finally {
                    onExit(exitValue.get())
                    done.countDown()
                }
            }.start()
        }

        override fun sendInput(line: String) {
            if (!process.isAlive) return
            try {
                val capped = line.take(MAX_INTERACTIVE_BYTES)
                process.outputStream.write(capped.toByteArray(Charsets.UTF_8))
                process.outputStream.flush()
            } catch (e: Exception) {
                // pipe tertutup / proses mati — abaikan
            }
        }

        override fun sendCtrlC() {
            if (!process.isAlive) return
            try {
                val pid = process.pid()
                if (pid > 0) {
                    val kill = ProcessBuilder("kill", "-INT", pid.toString())
                    kill.redirectErrorStream(true)
                    kill.start().waitFor()
                    return
                }
            } catch (e: Exception) {
                // fallback di bawah
            }
            try {
                process.destroy()
            } catch (e: Exception) {
                // abaikan
            }
        }

        override fun sendKill() {
            try {
                process.destroyForcibly()
            } catch (e: Exception) {
                // abaikan
            }
        }

        override fun isAlive(): Boolean = process.isAlive

        override fun waitForExit(): Int {
            done.await(MAX_INTERACTIVE_DURATION_MS, TimeUnit.MILLISECONDS)
            if (done.count > 0) {
                sendKill()
                done.await(5, TimeUnit.SECONDS)
            }
            return exitValue.get()
        }
    }

    // ------------------------------------------------------------------
    // Pip (Settings → Pip)
    // ------------------------------------------------------------------

    /** Validasi nama package pip (anti shell injection). */
    fun isSafePackageName(name: String): Boolean =
        name.isNotBlank() && name.length <= 200 &&
            Regex("^[A-Za-z0-9_\\-\\[\\]=.<>!]+$").matches(name)

    /** Spawn proses pip install (backend desktop). */
    fun startPipProcess(packageName: String): Process? {
        if (!isSafePackageName(packageName)) return null
        return try {
            val pb = ProcessBuilder("python3", "-m", "pip", "install", packageName)
            pb.redirectErrorStream(true)
            pb.start()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Pip install dengan streaming log real-time. Kembalikan false bila nama invalid.
     * `onDone(success, exitCode)` dipanggil tepat sekali setelah proses selesai.
     * Di Android memakai pip in-process Chaquopy; di desktop memakai subprocess.
     */
    fun startPipStream(
        context: Context?,
        packageName: String,
        onLog: (String) -> Unit,
        onDone: (success: Boolean, exitCode: Int) -> Unit
    ): Boolean {
        if (!isSafePackageName(packageName)) return false
        // TEST A: backend Chaquopy dinonaktifkan — hanya subprocess
        val process = startPipProcess(packageName) ?: return false
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    onLog(line + "\n")
                }
                val exitCode = process.waitFor()
                onDone(exitCode == 0, exitCode)
            } catch (e: Exception) {
                onLog("\n❌ Error: ${e.message}\n")
                onDone(false, -1)
            }
        }.start()
        return true
    }

    // ------------------------------------------------------------------
    // Batch run (non-interaktif; dipakai dev/test)
    // ------------------------------------------------------------------

    suspend fun runIsolated(code: String, stdin: String = ""): RunResult = withContext(Dispatchers.IO) {
        if (code.toByteArray(Charsets.UTF_8).size > MAX_CODE_BYTES) {
            return@withContext RunResult(false, "", "Source too large: >512KB", false)
        }
        if (stdin.toByteArray(Charsets.UTF_8).size > MAX_CODE_BYTES) {
            return@withContext RunResult(false, "", "Stdin too large", false)
        }

        val tmp = File.createTempFile("zcode_run_", ".py")
        try {
            tmp.writeText(code, Charsets.UTF_8)
            val pb = ProcessBuilder("python3", "-u", tmp.absolutePath)
            pb.redirectErrorStream(false)
            val process = pb.start()
            if (stdin.isNotEmpty()) {
                process.outputStream.write(stdin.toByteArray(Charsets.UTF_8))
                process.outputStream.flush()
            }
            process.outputStream.close()

            val outSb = StringBuilder()
            val errSb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            val errReader = BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8))
            val jobs = listOf(
                Thread {
                    reader.forEachLine { line ->
                        if (outSb.length < MAX_OUTPUT_CHARS) outSb.append(line).append('\n')
                    }
                },
                Thread {
                    errReader.forEachLine { line ->
                        if (errSb.length < MAX_OUTPUT_CHARS) errSb.append(line).append('\n')
                    }
                }
            )
            jobs.forEach { it.start() }

            val finished = withTimeoutOrNull(DEFAULT_TIMEOUT_MS) { process.waitFor() }
            jobs.forEach { it.join() }

            if (finished == null) {
                process.destroyForcibly()
                RunResult(false, outSb.toString(), errSb.toString() + "\nTimeout after 30s", true)
            } else {
                RunResult(process.exitValue() == 0, outSb.toString(), errSb.toString(), false)
            }
        } catch (e: Exception) {
            RunResult(false, "", "Execution error: ${e.message}", false)
        } finally {
            tmp.delete()
        }
    }

    /** Kolektor gambar (matplotlib inline) — target Fase 3, lihat README roadmap. */
    fun collectNewImages(baseline: Set<String>): Pair<List<String>, Set<String>> = emptyList<String>() to baseline
}
