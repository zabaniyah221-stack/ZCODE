package com.zaba.zcode.core.execution

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * OutputBatcher — batching output terminal (SPEC-001 §14).
 *
 * Target: flush interval 32–50ms ATAU buffer 2–4KB, mana yang lebih dulu.
 * AI streaming (token per token) → satu UI update per batch, bukan per token.
 *
 * Threading: single consumer thread (urutan output TERJAGA — tidak out-of-order),
 * append() dari thread mana pun aman (queue thread-safe).
 */
class OutputBatcher(
    private val onBatch: (stream: String, text: String) -> Unit,
    private val maxBytes: Int = 2048,
    private val flushIntervalMs: Long = 40
) {
    private data class Chunk(val stream: String, val text: String)

    private val queue = ArrayBlockingQueue<Chunk>(32768)
    private val buffers = mutableMapOf<String, StringBuilder>()
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
                            val buf = buffers.getOrPut(chunk.stream) { StringBuilder() }
                            buf.append(chunk.text)
                            if (buf.length >= maxBytes) flushLocked()
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
        buffers.forEach { (stream, buf) ->
            if (buf.isNotEmpty()) {
                val text = buf.toString()
                buf.clear()
                onBatch(stream, text)
            }
        }
        buffers.entries.removeIf { it.value.isEmpty() }
    }
}
