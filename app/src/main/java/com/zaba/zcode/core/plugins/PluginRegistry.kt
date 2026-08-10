package com.zaba.zcode.core.plugins

/**
 * PluginRegistry — katalog plugin ZCODE (batch anti-sepi, S1/S2/S7).
 *
 * Semantik toggle (PLAN_BATCH_ANTI_SEPI.md §1 S2 — lebih rapi dari Zabacode):
 *  - ACTION   → ON = tersedia di palette/akses cepat; OFF = disembunyikan.
 *               Tap baris di drawer SELALU = eksekusi manual.
 *  - BEHAVIOR → ON = jalan otomatis pada event (saat Run).
 * Tidak ada "aktivasi = eksekusi diam-diam" ala marketplace Zabacode.
 *
 * State enabled TIDAK disimpan di sini — satu sumber: SharedPreferences
 * (WorkspaceViewModel.pluginEnabled), anti kasus state-terbelah Zabacode.
 */
enum class PluginKind { ACTION, BEHAVIOR }

data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val kind: PluginKind,
    /** id di zcode_plugins.py bila eksekusi via PluginRunner; null = handler khusus Kotlin/JS. */
    val pythonId: String? = null,
    val enabledByDefault: Boolean = true,
)

object PluginRegistry {

    val plugins: List<PluginInfo> = listOf(
        // ---- Transform bawaan ZCODE (Kotlin, PluginHost — dipertahankan, S7) ----
        PluginInfo(
            "beautifier", "Beautifier Pro (Format Code)",
            "Spasi operator PEP-8; string & komentar tidak pernah disentuh",
            PluginKind.ACTION
        ),
        PluginInfo(
            "optimize_imports", "Optimize Auto-Imports",
            "Tambahkan import standar (os, sys, math, …) yang terpakai",
            PluginKind.ACTION
        ),
        PluginInfo(
            "duplicate_line", "Duplicate Active Line",
            "Gandakan baris/selection aktif ke bawah",
            PluginKind.ACTION
        ),
        PluginInfo(
            "toggle_comment", "Toggle Line Comment",
            "Comment/uncomment baris selection",
            PluginKind.ACTION
        ),

        // ---- Plugin baru: port ZABACODE via Python/Chaquopy (S7, provenance GPLv3) ----
        PluginInfo(
            "docstring_generator", "Smart Docstring Generator",
            "Docstring PEP-257 dari signature fungsi (Python AST)",
            PluginKind.ACTION, pythonId = "docstring_generator"
        ),
        PluginInfo(
            "type_hint_generator", "Type Hint Generator",
            "Infer anotasi tipe dari nilai default argumen (Python AST)",
            PluginKind.ACTION, pythonId = "type_hint_generator"
        ),
        PluginInfo(
            "find_duplicates", "Find Duplicate Lines",
            "Deteksi baris duplikat (DRY); injeksi komentar WARNING di atas file",
            PluginKind.ACTION, pythonId = "duplicate_line_detector"
        ),

        // ---- Plugin Kotlin murni baru ----
        PluginInfo(
            "todo_extractor", "TODO Extractor",
            "Kumpulkan TODO/FIXME/HACK — tap item untuk lompat ke baris",
            PluginKind.ACTION
        ),
        PluginInfo(
            "snippets", "Snippet Pack",
            "Template Flask / BS4 / AsyncIO / REST → jadi file baru",
            PluginKind.ACTION
        ),

        // ---- F1.9: Transform teks kecil (Kotlin/JS murni, tanpa pip) ----
        PluginInfo(
            "sort_lines", "Sort Lines",
            "Urutkan baris yang dipilih secara alfabetis",
            PluginKind.ACTION
        ),
        PluginInfo(
            "change_case", "Change Case",
            "Ubah UPPER / lower / Title Case pada teks yang dipilih",
            PluginKind.ACTION
        ),
        PluginInfo(
            "trim_now", "Trim Now",
            "Buang spasi akhir tiap baris secara manual (tanpa Run)",
            PluginKind.ACTION
        ),

        // ---- Behavior: otomatis saat Run (default OFF) ----
        PluginInfo(
            "auto_trim_on_run", "Auto Trim on Run",
            "Buang spasi di akhir baris sebelum eksekusi (file tidak diubah)",
            PluginKind.BEHAVIOR, enabledByDefault = false
        ),
    )

    fun byId(id: String): PluginInfo? = plugins.firstOrNull { it.id == id }

    /** Plugin ACTION yang sedang enabled — dipakai palette (semantik S2). */
    fun enabledActions(isEnabled: (String) -> Boolean): List<PluginInfo> =
        plugins.filter { it.kind == PluginKind.ACTION && isEnabled(it.id) }

    fun isBehaviorActive(id: String, isEnabled: (String) -> Boolean): Boolean =
        byId(id)?.let { it.kind == PluginKind.BEHAVIOR && isEnabled(it.id) } == true
}
