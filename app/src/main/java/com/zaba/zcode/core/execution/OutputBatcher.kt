package com.zaba.zcode.core.execution

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * OutputBatcher — batching output terminal (SPEC-001 §14).
 *
 * Target: flush interval 32–50ms ATAU buffer 2–4KB, mana yang lebih dulu.
 * AI streaming (token per token) → satu UI update per batch, bukan per token.
 *
 * Threading: single consumer thread, append() dari thread mana pun aman
 * (queue thread-safe).
 *
 * URUTAN — pelajaran 2026-08-13. Versi lama menyimpan satu StringBuilder PER
 * STREAM lalu mem-flush dengan `buffers.forEach`. Akibatnya urutan hanya
 * terjaga DI DALAM satu stream; begitu "out" dan "sys" jatuh di jendela flush
 * yang sama, yang keluar duluan adalah stream yang map-nya lebih dulu dibuat —
 * bukan yang teksnya lebih dulu datang. Itulah sebab "Process finished with
 * exit code 0" muncul DI ATAS "Hello, ZCODE!" (dilaporkan user, v1.0.5).
 *
 * Sekarang buffer TUNGGAL dan berurutan: potongan disimpan apa adanya sesuai
 * kedatangan, dan batch dipecah tepat di titik pergantian stream. Urutan
 * kronologis di layar = urutan kedatangan, tanpa kecuali.
 */
class OutputBatcher(
    private val onBatch: (stream: String, text: String) -> Unit,
    private val maxBytes: Int = 2048,
    private val flushIntervalMs: Long = 40
) {
    private data class Chunk(val stream: String, val text: String)

    private val queue = ArrayBlockingQueue<Chunk>(32768)

    /**
     * Antrean tunggal yang menjaga urutan kedatangan. Potongan beruntun dengan
     * stream yang sama digabung saat flush supaya jumlah update UI tetap
     * sedikit — penggabungan TIDAK PERNAH melompati potongan stream lain.
     */
    private val pending = ArrayDeque<Chunk>()
    private var pendingBytes = 0
    private val lock = Any()
    @Volatile
    private var running = true
    private var thread: Thread? = null

    fun start() {
        if (thread != null) return
        // FIX 2026-08-12: `running` DULU hanya di-set true sekali saat objek dibuat.
        // Setelah close() (running=false), start() berikutnya membuat thread baru yang
        // loop-nya `while (running)` LANGSUNG berhenti — batcher hidup tapi tuli, dan
        // SEMUA output script dibuang diam-diam tanpa error. Reset di sini membuat
        // start() benar-benar berarti "mulai lagi".
        running = true
        thread = Thread {
            try {
                while (running) {
                    val chunk = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        synchronized(lock) {
                            pending.addLast(chunk)
                            pendingBytes += chunk.text.length
                            if (pendingBytes >= maxBytes) flushLocked()
                        }
                    } else {
                        synchronized(lock) { flushLocked() }
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                synchronized(lock) { flushLocked() }
            }
        }.apply {
            name = "zcode-output-batcher"
            isDaemon = true
            start()
        }
    }

    fun append(stream: String, text: String) {
        if (text.isEmpty()) return
        queue.offer(Chunk(stream, text))
    }

    /** Flush sisa antrian + hentikan thread batching (blokir sampai selesai). */
    fun close() {
        running = false
        thread?.interrupt()
        try {
            thread?.join(1000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
        synchronized(lock) { flushLocked() }
    }

    private fun flushLocked() {
        if (pending.isEmpty()) return
        // Gabung hanya potongan BERURUTAN yang stream-nya sama; begitu stream
        // berganti, batch dipotong. Dengan begitu satu update UI tetap murah
        // tanpa pernah menukar urutan antar-stream.
        var stream = pending.first().stream
        val sb = StringBuilder()
        while (pending.isNotEmpty()) {
            val c = pending.first()
            if (c.stream != stream) {
                onBatch(stream, sb.toString())
                sb.setLength(0)
                stream = c.stream
            }
            sb.append(c.text)
            pending.removeFirst()
        }
        if (sb.isNotEmpty()) onBatch(stream, sb.toString())
        pendingBytes = 0
    }

    /**
     * Tunggu sampai antrean benar-benar kosong tampil di layar.
     *
     * Dipakai sebelum menulis pesan penutup ("Process finished..."): tanpa ini
     * pesan itu bisa menyalip output yang masih menunggu jendela flush 40ms.
     * Mengembalikan false bila melewati [timeoutMs] — pemanggil tetap lanjut,
     * karena pesan exit yang terlambat lebih baik daripada tidak muncul.
     */
    fun drain(timeoutMs: Long = 1500): Boolean {
        val batas = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < batas) {
            val kosong = queue.isEmpty() && synchronized(lock) { pending.isEmpty() }
            if (kosong) return true
            try {
                Thread.sleep(5)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }
}
