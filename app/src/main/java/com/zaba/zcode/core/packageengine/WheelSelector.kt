package com.zaba.zcode.core.packageengine

import android.content.Context
import com.zaba.zcode.core.files.Paths
import java.io.File

/**
 * WheelSelector — prioritas wheel (SPEC-001 §7 Wheel Installation Rules).
 *
 * Urutan prioritas:
 *   1. ZCODE tested wheel
 *   2. Chaquopy Android wheel
 *   3. universal pure-Python wheel
 *   4. approved experimental wheel
 *
 * Pemilihan sebenarnya terjadi di Python (package_runtime.resolve) memakai
 * packaging.tags; kelas ini menyediakan label + daftar wheel lokal (cache)
 * untuk UI dan laporan.
 */
class WheelSelector(private val context: Context) {

    fun priorityLabel(priority: Int): String = when (priority) {
        1 -> "ZCODE tested"
        2 -> "Chaquopy Android"
        3 -> "Universal pure-Python"
        4 -> "Experimental"
        else -> "Unknown"
    }

    fun priorityIcon(priority: Int): String = when (priority) {
        1 -> "✅"
        2 -> "🤖"
        3 -> "🐍"
        4 -> "🧪"
        else -> "❓"
    }

    /** Wheel lokal di cache python-env/wheels (offline reuse + ZCODE wheel source). */
    fun localWheels(): List<File> {
        val dir = Paths.pythonWheels(context)
        return dir.listFiles()?.filter { it.isFile && it.name.endsWith(".whl") }?.sortedBy { it.name } ?: emptyList()
    }

    fun cacheSizeBytes(): Long {
        val dir = Paths.pythonWheels(context)
        return dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }
}
