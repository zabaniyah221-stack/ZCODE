package com.zaba.zcode.core.execution

import android.content.Context
import android.os.StatFs
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.zaba.zcode.core.logging.SemanticLog
import com.zaba.zcode.core.logging.SemanticLogKind
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
 *    - pip runtime: `zcode_pip.py` (legacy) / PackageEngineV2 (baru, SPEC-001)
 * 2. ProcessBuilder (desktop/sandbox/dev): `python3 -u <file>` subprocess nyata,
 *    dipakai untuk pengujian lokal & CI logic verification.
 *
 * Guards (tidak boleh dihapus):
 * - MAX_CODE_BYTES 512KB (S-18, F-07 off-by-9 fixed: no prelude injection — kode user
 *   dieksekusi apa adanya, tanpa patch stdin 9 baris di depan)
 * - MAX_OUTPUT_CHARS 256KB — cap log IN-MEMORY (batch run & layar Pip). Full output
 *   interactive TIDAK di-cap: disimpan ke disk via RunLogger (SPEC-001 §16).
 * - MAX_INTERACTIVE_QUEUE 10k, MAX_IMAGE_BYTES 8MB
 * - DEFAULT_TIMEOUT_MS 30s — HANYA untuk batch run (bukan interactive, SPEC-001 §17:
 *   interactive session TIDAK punya hard timeout)
 *
 * SPEC-001 Phase 0 (implemented di sini):
 * - interactive hard timeout DIHAPUS (waitForExit() menunggu sampai process selesai)
 * - explicit process lifecycle (SessionState) + run ID (RunId) per session
 * - output batching (OutputBatcher: 40ms / 2KB)
 * - stdout/stderr dipisah (stream "out"/"err") → disk log (RunLogger)
 * - storage metrics (freeStorageBytes) untuk storage guard
 */
object ExecutionEngine {
    const val MAX_CODE_BYTES = 512 * 1024 // 512 KB
    const val MAX_OUTPUT_CHARS = 256 * 1024 // 256 KB — cap log in-memory (bukan interactive disk log)
    const val DEFAULT_TIMEOUT_MS = 30_000L // batch timeout 30s — interactive TIDAK memakai ini
    const val MAX_INTERACTIVE_INACTIVITY_MS = 60_000L // (tidak dipakai sebagai killer; hanya info)
    const val MAX_INTERACTIVE_BYTES = 8192 // 8KB per send
    const val MAX_INTERACTIVE_QUEUE = 10000
    const val MAX_IMAGE_BYTES = 8 * 1024 * 1024 // 8 MB skip (target Fase 3: matplotlib inline)
    const val MIN_FREE_STORAGE_BYTES = 50L * 1024 * 1024 // storage guard terminal (50MB)

    /** Folder workspace (filesDir) — di-set oleh WorkspaceViewModel, jadi cwd script = workspace. */
    @Volatile
    var workspaceDirPath: String = ""

    /**
     * Jumlah session Chaquopy yang thread Python-nya MASIH HIDUP (fix 2026-08-12).
     *
     * Chaquopy berjalan in-process: `sendKill()` hanya menyalakan flag yang dibaca
     * BridgeStdin saat `input()`. Script yang sedang menunggu jaringan atau berputar
     * di loop TIDAK membaca flag itu, sehingga thread-nya tetap hidup walau user
     * menekan Back. Setiap tap ▶ Run berikutnya menambah thread baru — di HP RAM
     * kecil penumpukan ini berujung pada aplikasi dimatikan sistem.
     *
     * Counter ini membuat kondisi tersebut TERLIHAT (di breadcrumb & UI) alih-alih
     * senyap. Penghentian paksa sesungguhnya memerlukan proses Python terpisah;
     * itu pekerjaan terpisah yang belum dilakukan — jangan mengklaim sudah beres.
     */
    private val liveSessions = AtomicInteger(0)

    /** Berapa session Python yang masih berjalan (0 = bersih). */
    fun liveSessionCount(): Int = liveSessions.get()

    data class RunResult(
        val ok: Boolean,
        val stdout: String,
        val stderr: String,
        val timeout: Boolean,
        val images: List<String> = emptyList()
    )

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

    /** Storage guard: free bytes pada partisi tempat `path` berada. -1 bila gagal. */
    fun freeStorageBytes(path: File): Long = try {
        StatFs(path.absolutePath).availableBytes
    } catch (e: Exception) {
        -1L
    }

    // ------------------------------------------------------------------
    // Interactive session (PTY layer)
    // ------------------------------------------------------------------

    interface InteractiveSession {
        val runId: String
        val state: SessionState
        fun sendInput(line: String)
        fun sendCtrlC()
        fun sendKill()
        fun isAlive(): Boolean
        /** Menunggu sampai process selesai — TANPA hard timeout (SPEC-001 §17). */
        fun waitForExit(): Int
    }

    /**
     * Mulai interactive session.
     * onOutput(stream, text): stream = "out" | "err" | "sys".
     * logger: opsional RunLogger untuk disk-backed full log; TIDAK memengaruhi UI.
     */
    fun startInteractiveSession(
        context: Context?,
        file: File,
        runId: String = RunId.newId("run"),
        logger: RunLogger? = null,
        onOutput: (stream: String, text: String) -> Unit,
        onExit: (code: Int) -> Unit,
        onState: (SessionState) -> Unit = {}
    ): InteractiveSession {
        return if (context != null && isChaquopyAvailable()) {
            ChaquopySession(context, file, runId, logger, onOutput, onExit, onState)
        } else {
            // Backend desktop/dev: spawn subprocess python3 (bukan ProcessSession(file))
            val pb = ProcessBuilder("python3", "-u", file.absolutePath)
            pb.redirectErrorStream(false) // stdout/stderr terpisah (SPEC-001)
            ProcessSession(pb.start(), runId, logger, onOutput, onExit, onState)
        }
    }

    // ------------------------------------------------------------------
    // Backend: subprocess python3 (desktop / dev)
    // ------------------------------------------------------------------

    private class ProcessSession(
        private val process: Process,
        override val runId: String,
        private val logger: RunLogger?,
        private val onOutput: (String, String) -> Unit,
        private val onExit: (Int) -> Unit,
        private val onState: (SessionState) -> Unit
    ) : InteractiveSession {
        private val exitValue = AtomicInteger(-1)
        private val done = CountDownLatch(1)
        @Volatile
        private var stateValue = SessionState.START
        override val state: SessionState get() = stateValue

        private fun setState(s: SessionState) {
            if (stateValue != s) {
                stateValue = s
                onState(s)
            }
        }

        init {
            // stdout reader
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8), 8192)
                    val batch = StringBuilder()
                    var c: Int
                    while (reader.read().also { c = it } != -1) {
                        batch.append(c.toChar())
                        if (batch.length >= 2048) {
                            emit("out", batch.toString())
                            batch.clear()
                        }
                    }
                    if (batch.isNotEmpty()) emit("out", batch.toString())
                    exitValue.set(process.waitFor())
                } catch (e: Exception) {
                    // abaikan — stdout thread yang menyelesaikan latch
                } finally {
                    finish()
                }
            }.start()
            // stderr reader — dipisah dari stdout (SPEC-001 Phase 0)
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8), 8192)
                    val batch = StringBuilder()
                    var c: Int
                    while (reader.read().also { c = it } != -1) {
                        batch.append(c.toChar())
                        if (batch.length >= 2048) {
                            emit("err", batch.toString())
                            batch.clear()
                        }
                    }
                    if (batch.isNotEmpty()) emit("err", batch.toString())
                } catch (e: Exception) {
                    // abaikan — stdout thread yang menyelesaikan latch
                }
            }.start()
        }

        private fun emit(stream: String, text: String) {
            setState(SessionState.RUNNING)
            onOutput(stream, text)
            logger?.append(stream, text)
        }

        private fun finish() {
            val code = exitValue.get()
            setState(if (code == 0) SessionState.EXITED else SessionState.FAILED)
            logger?.writeExit(state, code)
            logger?.close()
            onExit(code)
            done.countDown()
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
            setState(SessionState.INTERRUPTING)
            // `Process.pid()` butuh Java 9+ dan pernah gagal resolve di CI
            // (Unresolved reference: pid) — ambil via reflection agar kompilasi
            // aman di semua JDK; kalau gagal, fallback ke destroy().
            val pid = runCatching {
                val m = process.javaClass.getMethod("pid")
                (m.invoke(process) as Number).toLong()
            }.getOrNull()
            if (pid != null && pid > 0) {
                try {
                    val kill = ProcessBuilder("kill", "-INT", pid.toString())
                    kill.redirectErrorStream(true)
                    kill.start().waitFor()
                    return
                } catch (e: Exception) {
                    // fallback di bawah
                }
            }
            try {
                process.destroy()
            } catch (e: Exception) {
                // abaikan
            }
        }

        override fun sendKill() {
            setState(SessionState.STOPPING)
            try {
                process.destroyForcibly()
            } catch (e: Exception) {
                // abaikan
            }
        }

        override fun isAlive(): Boolean = process.isAlive

        override fun waitForExit(): Int {
            // SPEC-001 §17: TIDAK ada hard timeout interactive.
            done.await() // menunggu sampai process selesai (exit/Ctrl+C/Stop/error)
            return exitValue.get()
        }
    }

    // ------------------------------------------------------------------
    // Backend: Chaquopy in-process (Android)
    // ------------------------------------------------------------------

    private class ChaquopySession(
        context: Context,
        file: File,
        override val runId: String,
        private val logger: RunLogger?,
        private val onOutput: (String, String) -> Unit,
        private val onExit: (Int) -> Unit,
        private val onState: (SessionState) -> Unit
    ) : InteractiveSession {
        private val bridge = TerminalBridge(
            onOutput = onOutput,
            onExit = { code -> onExit(code) },
            onState = onState
        )
        private val exitValue = AtomicInteger(-1)
        private val done = CountDownLatch(1)
        @Volatile
        private var stateValue = SessionState.START
        override val state: SessionState get() = stateValue

        init {
            val appContext = context.applicationContext
            liveSessions.incrementAndGet()
            Thread {
                try {
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                        "PY_THREAD_BEGIN", "${file.name} live=${liveSessions.get()}"
                    )
                    if (!PythonRuntime.ensureStarted(appContext)) {
                        val why = PythonRuntime.failureMessage() ?: "runtime tidak tersedia"
                        bridge.abort("\nGagal menyalakan Python: $why\n")
                        return@Thread
                    }
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log("PY_MODULE_LOAD")
                    val runner = Python.getInstance().getModule("zcode_runner")
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log("SCRIPT_BEGIN", file.name)
                    runner.callAttr("run_script", bridge, file.absolutePath)
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log("SCRIPT_END", file.name)
                } catch (e: PyException) {
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log("PY_EXCEPTION", e.message ?: "")
                    bridge.abort("\nPython error: ${e.message}\n")
                } catch (e: Throwable) {
                    // Throwable (bukan Exception): OutOfMemoryError / StackOverflowError /
                    // UnsatisfiedLinkError sebelumnya LOLOS dari catch dan mematikan thread
                    // tanpa jejak — user hanya melihat aplikasi mati. Sekarang tercatat.
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                        "PY_THROWABLE", "${e.javaClass.simpleName}: ${e.message}"
                    )
                    bridge.abort("\nRuntime error: ${e.javaClass.simpleName}: ${e.message}\n")
                } finally {
                    exitValue.set(if (bridge.isExited) bridge.exitCode else -1)
                    stateValue = if (bridge.isExited) bridge.state else SessionState.FAILED
                    logger?.writeExit(stateValue, exitValue.get())
                    logger?.close()
                    val remaining = liveSessions.decrementAndGet()
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                        "PY_THREAD_END", "code=${exitValue.get()} live=$remaining"
                    )
                    done.countDown()
                }
            }.start()
        }

        override fun sendInput(line: String) {
            bridge.sendLine(line.take(MAX_INTERACTIVE_BYTES))
        }

        override fun sendCtrlC() {
            stateValue = SessionState.INTERRUPTING
            onState(stateValue)
            bridge.interrupt()
        }

        override fun sendKill() {
            stateValue = SessionState.STOPPING
            onState(stateValue)
            bridge.interrupt()
        }

        override fun isAlive(): Boolean = done.count > 0

        override fun waitForExit(): Int {
            // SPEC-001 §17: TIDAK ada hard timeout interactive.
            done.await()
            return exitValue.get()
        }
    }

    // ------------------------------------------------------------------
    // Pip (Settings → Pip) — LEGACY path; UI baru memakai PackageEngineV2.
    // ------------------------------------------------------------------

    /** Validasi nama package pip (anti shell injection). */
    fun isSafePackageName(name: String): Boolean =
        name.isNotBlank() && name.length <= 200 &&
            Regex("^[A-Za-z0-9_\\-\\[\\]=.<>!, ]+$").matches(name)

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
     * Pip install dengan streaming log real-time (LEGACY — dipakai dev/desktop
     * dan fallback; UI utama memakai PackageEngineV2 dengan verifikasi nyata).
     */
    fun startPipStream(
        context: Context?,
        packageName: String,
        onLog: (SemanticLog) -> Unit,
        onDone: (success: Boolean, exitCode: Int) -> Unit
    ): Boolean {
        if (!isSafePackageName(packageName)) return false
        return if (context != null && isChaquopyAvailable()) {
            val appContext = context.applicationContext
            Thread {
                try {
                    if (!PythonRuntime.ensureStarted(appContext)) {
                        onLog(SemanticLog(
                            "Python runtime tidak tersedia.",
                            SemanticLogKind.FAIL
                        ))
                        onDone(false, -1)
                        return@Thread
                    }
                    val bridge = TerminalBridge(
                        onOutput = { _, text ->
                            onLog(SemanticLog(text, SemanticLogKind.RAW))
                        },
                        onExit = { code -> onDone(code == 0, code) }
                    )
                    Python.getInstance()
                        .getModule("zcode_pip")
                        .callAttr("install_package", bridge, packageName)
                } catch (e: PyException) {
                    onLog(SemanticLog("Error: ${e.message}", SemanticLogKind.FAIL))
                    onDone(false, -1)
                } catch (e: Exception) {
                    onLog(SemanticLog("Error: ${e.message}", SemanticLogKind.FAIL))
                    onDone(false, -1)
                }
            }.start()
            true
        } else {
            val process = startPipProcess(packageName) ?: return false
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        onLog(SemanticLog(line ?: "", SemanticLogKind.RAW))
                    }
                    val exitCode = process.waitFor()
                    onDone(exitCode == 0, exitCode)
                } catch (e: Exception) {
                    onLog(SemanticLog("Error: ${e.message}", SemanticLogKind.FAIL))
                    onDone(false, -1)
                }
            }.start()
            true
        }
    }

    // ------------------------------------------------------------------
    // Batch run (non-interaktif; dipakai dev/test — boleh 30s timeout)
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
