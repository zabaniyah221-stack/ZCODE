package com.zaba.zcode.core.packageengine

import com.zaba.zcode.core.logging.SemanticLogKind
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Bridge per-operasi untuk progress dan cooperative cancellation resolver.
 *
 * Object Kotlin dipass sebagai argumen Chaquopy; Python memanggil emit() dan
 * isCancelled() lewat proxy Java normal. Tidak ada state static selain counter
 * ID, sehingga callback tidak dapat pindah ke operasi lain.
 */
class ResolveOperationBridge(
    private val onProgress: (
        displayText: String,
        rawEvent: String,
        diagnosticsWorthKeeping: Boolean,
        kind: SemanticLogKind
    ) -> Unit
) {
    val operationId: Long = nextId.incrementAndGet()

    @Volatile
    private var cancelRequested = false

    fun isCancelled(): Boolean = cancelRequested

    fun cancel() {
        cancelRequested = true
    }

    /** Dipanggil Python. Event rusak tidak boleh menggagalkan resolusi. */
    fun emit(eventJson: String) {
        val raw = eventJson.take(MAX_RAW_EVENT_CHARS)
        var eventStage = "progress"
        val display = try {
            val event = JSONObject(raw)
            val stage = event.optString("stage", "progress")
            eventStage = stage
            val pkg = event.optString("package").ifBlank { "package" }
            val source = event.optString("source")
            val attempt = event.optInt("attempt", 0)
            val maxAttempts = event.optInt("max_attempts", 0)
            val detail = event.optString("detail")
            when (stage) {
                "package_begin" -> "Menganalisis $pkg…"
                "package_chosen" -> "Terpilih $detail [${source.ifBlank { "cache" }}]"
                "package_unavailable" -> "$pkg: tidak ada kandidat kompatibel"
                "http_begin" -> "$pkg: membaca $source${attemptLabel(attempt, maxAttempts)}…"
                "http_retry" -> "$pkg: $source gagal ($detail), mencoba lagi${attemptLabel(attempt + 1, maxAttempts)}"
                "http_ok" -> "$pkg: $source selesai ($detail)"
                "http_fail" -> "$pkg: $source gagal ($detail)"
                // 404 probe sumber = alur normal (toko tidak menjual paket
                // ini), bukan error. Keputusan user 2026-08-17: label polos.
                "target_not_found" -> "$pkg: TARGET NOT FOUND [$source]"
                "cancelled" -> "Pembatalan diterima; merapikan operasi…"
                else -> listOf(pkg, source, detail).filter { it.isNotBlank() }.joinToString(": ")
            }
        } catch (_: Exception) {
            "Progress resolver"
        }
        // `http_ok` sangat panas (matplotlib nyata = 156 raw event). UI sudah
        // melihat begin lalu package_chosen; menampilkan setiap sukses hanya
        // membuat ratusan coroutine scroll dan I/O Diagnostics di ARMv7.
        if (eventStage != "http_ok") {
            val diagnostic = eventStage in DIAGNOSTIC_STAGES
            runCatching { onProgress(display, raw, diagnostic, kindFor(eventStage)) }
        }
    }

    private fun kindFor(stage: String): SemanticLogKind = when (stage) {
        "package_begin", "http_begin" -> SemanticLogKind.WAIT
        "http_retry", "http_fail", "package_unavailable" -> SemanticLogKind.WARN
        "cancelled" -> SemanticLogKind.STOP
        "package_chosen", "target_not_found" -> SemanticLogKind.INFO
        else -> SemanticLogKind.RAW
    }

    private fun attemptLabel(attempt: Int, max: Int): String =
        if (attempt > 0 && max > 0) " ($attempt/$max)" else ""

    companion object {
        private val nextId = AtomicLong(0)
        // `target_not_found` SENGAJA tidak diagnostic: 404 probe adalah alur
        // normal (±90 event per sesi UAT — dulu membanjiri breadcrumb sebagai
        // "http_fail" palsu). Konsol tetap menampilkannya; jejak sumber final
        // tiap paket sudah tercatat lewat `package_chosen`.
        private val DIAGNOSTIC_STAGES = setOf(
            "http_retry", "http_fail", "cancelled", "package_chosen"
        )
        private const val MAX_RAW_EVENT_CHARS = 600
    }
}
