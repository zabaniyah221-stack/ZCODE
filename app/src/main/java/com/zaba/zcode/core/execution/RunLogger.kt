package com.zaba.zcode.core.execution

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * RunId — identifier unik per run (SPEC-001 Phase 0: "Add run IDs").
 * Format: run_<epochMs>_<counterHex>. Aman untuk nama file.
 */
object RunId {
    private val counter = AtomicLong(0)

    fun newId(prefix: String = "run"): String {
        val n = counter.incrementAndGet()
        val rand = Integer.toHexString((Math.random() * 0xFFFF).toInt()).padStart(4, '0')
        return "${prefix}_${System.currentTimeMillis()}_${n}_$rand"
    }
}

/**
 * RunLogger — full output ke disk (SPEC-001 §16, Phase 0).
 *
 * Setiap run menulis <filesDir>/logs/runs/<run-id>.log berisi:
 * stdout/stderr dengan penanda stream + timestamp, process state, dan exit code.
 * UI memory TIDAK bergantung pada log ini — log adalah rekaman lengkap.
 * Disk penuh / I/O error → error callback (storage guard di UI).
 */
class RunLogger(
    private val logFile: File,
    private val onWriteError: (String) -> Unit = {}
) {
    private val lock = Any()
    private val tsFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var writer: BufferedWriter? = null
    @Volatile
    var bytesWritten: Long = 0
        private set

    fun start(header: String = "") {
        synchronized(lock) {
            try {
                logFile.parentFile?.mkdirs()
                writer = BufferedWriter(FileWriter(logFile, false), 8192)
                if (header.isNotEmpty()) writeLocked("sys", header)
            } catch (e: Exception) {
                onWriteError("Log disk gagal dibuka: ${e.message}")
            }
        }
    }

    fun append(stream: String, text: String) {
        if (text.isEmpty()) return
        synchronized(lock) {
            try {
                writeLocked(stream, text)
            } catch (e: Exception) {
                onWriteError("Log disk gagal ditulis: ${e.message}")
            }
        }
    }

    fun writeExit(state: SessionState, code: Int) {
        synchronized(lock) {
            try {
                writeLocked("sys", "--- process ${state.name} (exit code $code) ---")
            } catch (e: Exception) {
                onWriteError("Log disk gagal menulis exit: ${e.message}")
            }
        }
    }

    private fun writeLocked(stream: String, text: String) {
        val w = writer ?: return
        val ts = tsFormat.format(Date())
        for (line in text.split("\n")) {
            if (line.isEmpty()) continue
            w.append("[$ts][$stream] ").append(line).append("\n")
        }
        w.flush()
        bytesWritten += text.length.toLong()
    }

    fun close() {
        synchronized(lock) {
            try {
                writer?.flush()
                writer?.close()
            } catch (e: Exception) {
                // abaikan saat tutup
            }
            writer = null
        }
    }
}
