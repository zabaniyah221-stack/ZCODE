package com.zaba.zcode.core.plugins

/**
 * TodoExtractor — kumpulkan penanda TODO/FIXME/HACK/XXX dari kode (batch anti-sepi).
 * Kotlin murni (tanpa Python): scan regex per baris. Hasil dipakai dialog TOOLS:
 * tap item → gotoLine(n) (bridge CM6, 1 fungsi 3 pemakai — plan §3 F2).
 *
 * Catatan jujur: penanda di dalam string literal ikut terdeteksi (false positive
 * kosmetik) — fitur analisis warn-only, tidak memblokir apa pun.
 */
data class TodoItem(val line: Int, val tag: String, val text: String)

object TodoExtractor {

    private val REGEX = Regex("""(TODO|FIXME|HACK|XXX)\s*:?\s*(.*)""")

    fun extract(code: String): List<TodoItem> {
        val out = mutableListOf<TodoItem>()
        code.split('\n').forEachIndexed { idx, line ->
            val m = REGEX.find(line)
            if (m != null) {
                out.add(TodoItem(idx + 1, m.groupValues[1], m.groupValues[2].trim()))
            }
        }
        return out
    }
}
