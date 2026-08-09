package com.zaba.zcode.core.samples

/**
 * SampleLibrary — katalog SAMPLES ZCODE (redesign 2026-08, FASE E).
 *
 * Desain (hasil diskusi, bukan tebakan):
 * - Struktur 2 level ala Pydroid: kategori → item. Hanya kategori yang DIJAMIN
 *   jalan di Chaquopy + terminal interaktif ZCODE: Basics (pure Python, input()
 *   didukung penuh TerminalBridge), Numpy (pip-installable), Web (stdlib urllib).
 * - Kategori GUI native Pydroid (Kivy/Pygame/Tkinter/Qt, Tensorflow dsb.) sengaja
 *   TIDAK dimasukkan — arsitektur Chaquopy ZCODE tidak punya surface GUI; sample
 *   yang crash saat pertama dicoba = UX terburuk. Alternatif GUI lewat "App Mode"
 *   (Flask+WebView) tercatat di docs/RENCANA_UPDATE_2026_08.md (batch berikutnya).
 * - Kode sample hidup sebagai file .py asli di assets/samples/ — BUKAN string
 *   Kotlin — supaya test_zcode_fase3 bisa py_compile semuanya (sample rusak
 *   syntax = test merah otomatis; rule #2: meticulous).
 */

data class SampleEntry(
    val id: String,
    val title: String,
    val description: String,
    /** path relatif di assets/, mis. "samples/hello_world.py" */
    val assetPath: String,
)

data class SampleCategory(
    val id: String,
    val title: String,
    val description: String,
    val samples: List<SampleEntry>,
)

object SampleLibrary {

    val categories: List<SampleCategory> = listOf(
        SampleCategory(
            "basics", "Basics",
            "Dasar-dasar Python — tanpa install apapun, langsung Run",
            listOf(
                SampleEntry(
                    "hello_world", "Hello World",
                    "Sapaan klasik pertama kali nulis Python",
                    "samples/hello_world.py"
                ),
                SampleEntry(
                    "text_input", "Text Input",
                    "Baca ketikan user pakai input() — interaktif di terminal ZCODE",
                    "samples/text_input.py"
                ),
                SampleEntry(
                    "simple_math", "Simple Math",
                    "Operasi matematika sederhana + akar kuadrat",
                    "samples/simple_math.py"
                ),
                SampleEntry(
                    "functions_quadratic", "Functions",
                    "Pemecah persamaan kuadrat pakai fungsi (dukung bilangan kompleks)",
                    "samples/functions_quadratic.py"
                ),
                SampleEntry(
                    "for_loop_factorial", "For Loop",
                    "Hitung faktorial angka pakai perulangan for",
                    "samples/for_loop_factorial.py"
                ),
                SampleEntry(
                    "while_loop_guess", "While Loop",
                    "Game Tebak Angka 1-100 — while loop + input() 🎯",
                    "samples/while_loop_guess.py"
                ),
                SampleEntry(
                    "generators_squares", "Generators",
                    "Deretan kuadrat tanpa menampung list — pakai yield",
                    "samples/generators_squares.py"
                ),
                SampleEntry(
                    "dictionaries_db", "Dictionaries",
                    "Database key-value mini pakai dict Python",
                    "samples/dictionaries_db.py"
                ),
            )
        ),
        SampleCategory(
            "numpy", "Numpy",
            "Komputasi array ilmiah — butuh: install numpy di INSTALL MODULES dulu",
            listOf(
                SampleEntry(
                    "numpy_basics", "Array Basics",
                    "Bikin array & operasi vektor — butuh: install numpy dulu",
                    "samples/numpy_basics.py"
                ),
                SampleEntry(
                    "numpy_stats", "Quick Stats",
                    "Mean, median, standar deviasi sekejap — butuh: install numpy dulu",
                    "samples/numpy_stats.py"
                ),
            )
        ),
        SampleCategory(
            "web", "Web",
            "Ngobrol sama internet — butuh: koneksi internet (stdlib, tanpa install)",
            listOf(
                SampleEntry(
                    "web_fetch_json", "Fetch JSON",
                    "Ambil data JSON dari API publik pakai urllib bawaan Python — butuh internet",
                    "samples/web_fetch_json.py"
                ),
            )
        ),
    )

    fun findSample(id: String): SampleEntry? =
        categories.flatMap { it.samples }.firstOrNull { it.id == id }
}
