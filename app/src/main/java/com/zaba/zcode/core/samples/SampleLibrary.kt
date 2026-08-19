package com.zaba.zcode.core.samples

/**
 * SampleLibrary — katalog SAMPLES ZCODE (redesign 2026-08, FASE E).
 *
 * Desain (hasil diskusi, bukan tebakan):
 * - Struktur 2 level ala Pydroid: kategori tujuan → item. NumPy/Matplotlib punya
 *   jalur sendiri; paket lain dikelompokkan menurut pekerjaan user, bukan
 *   dilempar ke keranjang "Paket Populer".
 * - Hanya contoh stdlib atau paket yang punya jalur kompatibilitas nyata yang
 *   ditampilkan. Status/batas yang belum DEVICE VERIFIED harus disebut jujur.
 * - Kategori GUI native Pydroid (Kivy/Pygame/Tkinter/Qt, TensorFlow dsb.) sengaja
 *   TIDAK dimasukkan — arsitektur Chaquopy ZCODE belum punya surface/runtime-nya.
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
    /**
     * requiresPackage (v1.0.19, Gerbong B): canonical name paket pip yang
     * WAJIB aktif supaya sample ini jalan. Kosong = pure stdlib.
     * Dipakai SamplesScreen untuk dialog jujur "butuh X, instal dulu?"
     * SEBELUM file dibuat — sample yang crash saat pertama dicoba adalah
     * UX terburuk (alasan historis kategori GUI ditolak). Jembatan inilah
     * yang membuka gerbong konten: sample paket pip kini aman ditambah.
     */
    val requiresPackage: List<String> = emptyList(),
    /**
     * A7 (v1.0.19): asset pendamping yang ditulis ke workspace dgn NAMA
     * TETAP saat sample dibuka (mis. modul helper yang di-import file
     * utama). Tidak menimpa file yang sudah ada.
     */
    val companionAssets: List<String> = emptyList(),
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
            "Dasar Python — tanpa install apa pun, langsung Run",
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
                SampleEntry(
                    "try_except", "Try / Except",
                    "Menangkap error tanpa bikin program mati — pelajaran wajib!",
                    "samples/try_except.py"
                ),
                SampleEntry(
                    "classes_oop", "Classes (OOP)",
                    "Cetak biru objek: class Siswa + method + __repr__",
                    "samples/classes_oop.py"
                ),
                SampleEntry(
                    "file_io", "File I/O",
                    "Tulis & baca file — hasilnya muncul di file manager ZCODE",
                    "samples/file_io.py"
                ),
                SampleEntry(
                    "json_data", "JSON",
                    "dict ↔ JSON: bahasa universal pertukaran data",
                    "samples/json_data.py"
                ),
                SampleEntry(
                    "datetime_random", "Datetime & Random",
                    "Tanggal, selisih hari, dadu, undian — dua modul paling kepake",
                    "samples/datetime_random.py"
                )
            )
        ),
        SampleCategory(
            "numpy", "NumPy",
            "Array dan komputasi numerik — butuh install numpy",
            listOf(
                SampleEntry(
                    "numpy_basics", "Array Basics",
                    "Bikin array dan operasi vektor",
                    "samples/numpy_basics.py",
                    requiresPackage = listOf("numpy")
                ),
                SampleEntry(
                    "numpy_stats", "Quick Stats",
                    "Mean, median, dan standar deviasi",
                    "samples/numpy_stats.py",
                    requiresPackage = listOf("numpy")
                ),
                SampleEntry(
                    "numpy_slicing", "Indexing & Slicing",
                    "Pilih baris, kolom, blok, dan data dengan kondisi",
                    "samples/numpy_slicing.py",
                    requiresPackage = listOf("numpy")
                )
            )
        ),
        SampleCategory(
            "matplotlib", "Matplotlib",
            "Grafik disimpan sebagai PNG lewat backend Agg — tanpa GUI desktop",
            listOf(
                SampleEntry(
                    "matplotlib_chart", "Bar Chart",
                    "Buat bar chart PNG dari data sederhana",
                    "samples/matplotlib_chart.py",
                    requiresPackage = listOf("matplotlib")
                ),
                SampleEntry(
                    "matplotlib_subplots", "Subplots",
                    "Dua grafik dalam satu file PNG",
                    "samples/matplotlib_subplots.py",
                    requiresPackage = listOf("matplotlib")
                )
            )
        ),
        SampleCategory(
            "web_api", "Web & API",
            "Ambil data internet atau bedah HTML; contoh network selalu memakai timeout",
            listOf(
                SampleEntry(
                    "web_fetch_json", "urllib — Fetch JSON",
                    "Ambil JSON API dengan modul bawaan Python",
                    "samples/web_fetch_json.py"
                ),
                SampleEntry(
                    "requests_api", "Requests — API",
                    "Ambil data GitHub API dengan timeout",
                    "samples/requests_api.py",
                    requiresPackage = listOf("requests")
                ),
                SampleEntry(
                    "httpx_api", "HTTPX — API",
                    "HTTP client modern dengan timeout dan error handling",
                    "samples/httpx_api.py",
                    requiresPackage = listOf("httpx")
                ),
                SampleEntry(
                    "beautifulsoup_links", "Beautiful Soup — HTML",
                    "Ambil judul dan link dari HTML offline",
                    "samples/beautifulsoup_links.py",
                    requiresPackage = listOf("beautifulsoup4")
                )
            )
        ),
        SampleCategory(
            "office", "File & Office",
            "Buat dokumen Word, Excel, dan PowerPoint langsung dari HP",
            listOf(
                SampleEntry(
                    "openpyxl_excel", "openpyxl — Excel",
                    "Bikin file .xlsx berisi data dan rumus",
                    "samples/openpyxl_excel.py",
                    requiresPackage = listOf("openpyxl")
                ),
                SampleEntry(
                    "docx_laporan", "python-docx — Word",
                    "Bikin laporan .docx bertabel",
                    "samples/docx_laporan.py",
                    requiresPackage = listOf("python-docx")
                ),
                SampleEntry(
                    "pptx_presentasi", "python-pptx — PowerPoint",
                    "Bikin presentasi .pptx dua slide",
                    "samples/pptx_presentasi.py",
                    requiresPackage = listOf("python-pptx")
                )
            )
        ),
        SampleCategory(
            "database", "Database",
            "Data persisten: SQLite bawaan atau dokumen JSON dengan TinyDB",
            listOf(
                SampleEntry(
                    "sqlite_catatan", "SQLite — Catatan Persisten",
                    "Database bawaan Python; data awet setelah app ditutup",
                    "samples/sqlite_catatan.py"
                ),
                SampleEntry(
                    "tinydb_catatan", "TinyDB — Catatan JSON",
                    "Insert, upsert, query, dan simpan ke file JSON",
                    "samples/tinydb_catatan.py",
                    requiresPackage = listOf("tinydb")
                )
            )
        ),
        SampleCategory(
            "data_math", "Data & Matematika",
            "Olah tabel dan rumus dengan paket yang sudah teruji",
            listOf(
                SampleEntry(
                    "pandas_nilai", "pandas — Tabel Nilai",
                    "Rata-rata dan ranking ala spreadsheet",
                    "samples/pandas_nilai.py",
                    requiresPackage = listOf("pandas")
                ),
                SampleEntry(
                    "sympy_aljabar", "SymPy — Aljabar",
                    "Ekspansi, faktor, akar, dan turunan simbolik",
                    "samples/sympy_aljabar.py",
                    requiresPackage = listOf("sympy")
                )
            )
        ),
        SampleCategory(
            "image_qr", "Gambar & QR",
            "Buat dan olah gambar tanpa surface GUI desktop",
            listOf(
                SampleEntry(
                    "pillow_image", "Pillow — Gambar",
                    "Generate file PNG dari kode",
                    "samples/pillow_image.py",
                    requiresPackage = listOf("pillow")
                ),
                SampleEntry(
                    "qr_generator", "qrcode — QR Generator",
                    "PNG bila Pillow aktif, fallback SVG",
                    "samples/qr_generator.py",
                    requiresPackage = listOf("qrcode")
                )
            )
        ),
        SampleCategory(
            "security", "Security",
            "Contoh pembelajaran kriptografi dan autentikasi; bukan template produksi",
            listOf(
                SampleEntry(
                    "crypto_pesan", "cryptography — Enkripsi",
                    "Fernet — ARMv7 import-verified, belum device-verified",
                    "samples/crypto_pesan.py",
                    requiresPackage = listOf("cryptography")
                ),
                SampleEntry(
                    "pyotp_2fa", "PyOTP — Kode 2FA",
                    "Buat dan verifikasi secret TOTP demo secara offline",
                    "samples/pyotp_2fa.py",
                    requiresPackage = listOf("pyotp")
                )
            )
        ),
        SampleCategory(
            "utilities", "Terminal & Utilities",
            "Output terminal, progress, dan konfigurasi yang aman",
            listOf(
                SampleEntry(
                    "rich_table", "Rich — Tabel Warna",
                    "Tabel dan warna di terminal",
                    "samples/rich_table.py",
                    requiresPackage = listOf("rich")
                ),
                SampleEntry(
                    "tqdm_progress", "tqdm — Progress Bar",
                    "Progress bar satu baris di loop",
                    "samples/tqdm_progress.py",
                    requiresPackage = listOf("tqdm")
                ),
                SampleEntry(
                    "pyyaml_config", "PyYAML — Konfigurasi",
                    "Baca dengan safe_load lalu tulis YAML baru",
                    "samples/pyyaml_config.py",
                    requiresPackage = listOf("pyyaml")
                )
            )
        ),
        SampleCategory(
            "projects", "Project Mini",
            "Contoh multi-file untuk memahami struktur project",
            listOf(
                SampleEntry(
                    "project_mini", "Project Mini (2 file)",
                    "Main mengimpor modul helper di sebelahnya",
                    "samples/project_mini.py",
                    companionAssets = listOf("samples/helper_util.py")
                )
            )
        )
    )

    fun findSample(id: String): SampleEntry? =
        categories.flatMap { it.samples }.firstOrNull { it.id == id }
}
