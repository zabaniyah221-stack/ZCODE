package com.zaba.zcode.core.execution

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * ExecutionEngine — eksekusi Python asli untuk ZCODE.
 *
 * Fase 1: PTY interaktif — spawn `python3 -u <file>` (unbuffered), streaming output
 * real-time, input dikirim langsung ke stdin saat Enter (ketik langsung di terminal,
 * TANPA kotak stdin terpisah). Ctrl+C mengirim SIGINT asli (KeyboardInterrupt),
 * bukan destroyForcibly/SIGKILL.
 *
 * Catatan jujur (Fase 1): di Android, `python3` tersedia setelah runtime Chaquopy
 * di-embed (target Fase 3 / device build). Di sandbox/desktop, perintah ini berjalan
 * langsung. Arsitektur sengaja dibuat proses-basis agar swap ke Chaquopy mudah.
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

    data class RunResult(
        val ok: Boolean,
        val stdout: String,
        val stderr: String,
        val timeout: Boolean,
        val images: List<String> = emptyList()
    )

    data class OutputChunk(val stream: String, val text: String)

    /** Sesi interaktif: bungkus Process dengan I/O streaming + SIGINT asli. */
    class InteractiveSession(val process: Process) {
        val stdout: InputStream = process.inputStream
        val stderr: InputStream = process.errorStream
        val stdin: OutputStream = process.outputStream

        fun sendInput(text: String) {
            if (!process.isAlive) return
            try {
                stdin.write(text.toByteArray(Charsets.UTF_8))
                stdin.flush()
            } catch (e: Exception) {
                // proses sudah mati / pipe tertutup — abaikan
            }
        }

        /**
         * Ctrl+C asli: kirim SIGINT ke PID proses python (KeyboardInterrupt).
         * Fallback: SIGTERM via destroy() bila pid tidak tersedia.
         */
        fun sendCtrlC() {
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

        /** Paksa hentikan (SIGKILL) — dipakai saat keluar terminal. */
        fun sendKill() {
            try {
                process.destroyForcibly()
            } catch (e: Exception) {
                // abaikan
            }
        }

        fun isAlive(): Boolean = process.isAlive
    }

    /** Spawn proses python unbuffered untuk file script (interactive PTY). */
    fun startInteractiveSession(file: File): InteractiveSession {
        val pb = ProcessBuilder("python3", "-u", file.absolutePath)
        pb.redirectErrorStream(true) // stdout+stderr satu aliran, urutan traceback utuh
        return InteractiveSession(pb.start())
    }

    /** Validasi nama package pip (anti shell injection). */
    fun isSafePackageName(name: String): Boolean =
        name.isNotBlank() && name.length <= 200 &&
            Regex("^[A-Za-z0-9_\\-\\[\\]=.<>!]+$").matches(name)

    /** Spawn proses pip install dengan streaming log (Settings → Pip). */
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

    /** Run batch terisolasi (dipakai untuk eksekusi non-interaktif). */
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
