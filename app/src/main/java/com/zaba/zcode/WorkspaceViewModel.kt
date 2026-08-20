package com.zaba.zcode

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.chaquo.python.Python
import com.zaba.zcode.core.editor.Checker
import com.zaba.zcode.core.editor.Problem
import com.zaba.zcode.core.editor.Severity
import com.zaba.zcode.core.execution.ExecutionEngine
import com.zaba.zcode.core.files.FileManager
import com.zaba.zcode.core.files.Paths
import com.zaba.zcode.core.plugins.PluginHost
import com.zaba.zcode.core.plugins.PluginRegistry
import com.zaba.zcode.core.plugins.PluginRunner
import com.zaba.zcode.core.plugins.Snippet
import com.zaba.zcode.ui.theme.ZcodeThemeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * WorkspaceViewModel — pusat state workspace ZCODE (satu sumber kebenaran, DRY).
 * - CRUD file via FileManager (filesDir internal, anti traversal, 512KB guard)
 * - Persistensi: isi file tersimpan otomatis tiap perubahan + daftar tab & file aktif
 *   disimpan di SharedPreferences (pulih walau app di-swipe dari Recent Apps)
 * - Diagnostik sintaksis real-time (debounce 800ms, cancel job lama → tanpa race)
 */
/** Seed main.py lama (tanpa tip swipe) — dipakai migrasi exact-match audit 2026-08. */
private const val LEGACY_SEED_MAIN_PY = "# Welcome to ZCODE\nprint(\"Hello, ZCODE!\")\n"

/** Seed main.py baru: sapaan + tip swipe drawer (drawer kini swipe-only). */
private const val SEED_MAIN_PY =
    "# Welcome to ZCODE\n" +
        "# Tip: swipe dari pinggir kiri layar untuk membuka menu\n" +
        "print(\"Hello, ZCODE!\")\n"

class WorkspaceViewModel(app: Application) : AndroidViewModel(app) {

    private val filesDir: File = Paths.filesDir(app)
    private val prefs: android.content.SharedPreferences =
        app.getSharedPreferences("zcode_workspace", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var validationJob: Job? = null
    private var saveJob: Job? = null
    private var pendingSave = false

    // v1.0.18: default GITHUB_DARK (keputusan user 2026-08-15) — RETRO tetap
    // tersedia di cycle; pilihan tersimpan user lama tetap dihormati (baris
    // restore di bawah hanya fallback saat preferensi kosong/tak dikenal).
    var themeType by mutableStateOf(ZcodeThemeType.GITHUB_DARK)
    val openedFiles = mutableStateListOf<String>()
    var activeFile by mutableStateOf<String?>(null)
    var activeCode by mutableStateOf("")
    var syntaxError by mutableStateOf<String?>(null)
    var problems by mutableStateOf<List<Problem>>(emptyList())
        private set

    /** Symbol bar (QuickTools) di bawah editor — toggle user, persist di SharedPreferences. */
    var symbolBarEnabled by mutableStateOf(true)
        private set

    /** Gerbong A v1.0.19: lint gutter (merah di baris salah) — default ON. */
    var lintGutterEnabled by mutableStateOf(true)
        private set

    /** A3: tap traceback di terminal → lompat ke baris editor — default ON. */
    var tracebackJumpEnabled by mutableStateOf(true)
        private set

    /**
     * A3: baris tujuan yang menunggu editor siap. Terminal → navigasi balik →
     * WorkbenchScreen mengonsumsi ini SEKALI setelah editor ready (WebView
     * butuh waktu; gotoLine langsung saat navigasi = ditelan about:blank,
     * kelas BUG H). Nol = tidak ada yang pending.
     */
    var pendingGotoLine by mutableStateOf(0)

    fun requestGotoLine(fileName: String, line: Int) {
        // buka file yang benar dulu (traceback bisa menunjuk file lain di
        // workspace — multi-file import sudah jalan sejak dulu, A7).
        val ada = FileManager.listFiles(filesDir).any { it["name"] == fileName }
        if (fileName != activeFile && ada) selectFile(fileName)
        pendingGotoLine = line
    }

    /** A2: whitespace guard (trailing WS + campuran tab/spasi di gutter) —
     *  default OFF (keputusan user 2026-08-17: highlight bisa berisik). */
    var whitespaceGuardEnabled by mutableStateOf(false)
        private set

    /** F1.7: Auto-close brackets (CM6) — toggle user, persist di SharedPreferences. */
    var closeBracketsEnabled by mutableStateOf(true)
        private set

    /** F1.8: Selection match highlight (CM6) — toggle user, persist di SharedPreferences. */
    var highlightSelectionMatchesEnabled by mutableStateOf(true)
        private set

    /** F2.4: Toggle indikator "Menyalakan Python…" di terminal — persist di SharedPreferences. */
    var showPythonIndicator by mutableStateOf(true)
        private set

    /** F2.2: Batas output terminal (64KB, 256KB, 1MB). Default 64KB (65536 char). */
    var terminalOutputLimit by mutableStateOf(65536)
        private set

    /** Ukuran font TERMINAL saja (audit 2026-08; editor fix 14px di bundle CM6).
     *  Nama lama: editorFontSize. Key SharedPreferences tetap "editor_font_size"
     *  supaya preferensi user yang sudah tersimpan tidak hilang. */
    var terminalFontSize by mutableStateOf(14)
        private set

    /** Jenis font untuk UI & editor (BUKAN terminal — terminal tetap Monospace,
     *  keputusan audit 2026-08). Key SharedPreferences tetap "editor_font_family". */
    var appFontFamily by mutableStateOf("Monospace")
        private set

    /**
     * State enabled plugin (batch anti-sepi S2) — SATU sumber kebenaran di sini
     * (SharedPreferences), anti kasus state-terbelah Zabacode (backend in-memory
     * vs frontend localStorage).
     */
    var pluginFlags by mutableStateOf(
        PluginRegistry.plugins
            .associate { it.id to it.enabledByDefault }.toMutableMap()
    )
        private set

    private val fileDrafts = mutableMapOf<String, String>()

    /** Audit 2026-08: asal-usul file eksternal (nama file workspace → URI SAF).
     *  Dipakai menu Save (timpa file asli) & Save as (re-link). Persist di prefs. */
    private val externalOrigins = mutableMapOf<String, String>()

    private var lastClosed: Pair<String, Long>? = null

    init {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        // CATATAN: kode lama menghapus files/chaquopy/AssetFinder/requirements/pip
        // sudah DIHAPUS — pip 23.3.1 kini resmi di-bundle build-time (gradle chaquopy
        // pip{}), dan folder itu memuat file data pip yang sah (mis. cert bundle TLS).
        // backend eksekusi butuh cwd = folder workspace (plt.savefig / open() relatif)
        ExecutionEngine.workspaceDirPath = filesDir.absolutePath
        loadExternalOrigins()
        loadSavedWorkspace()
        loadPluginFlags()
        scope.launch(Dispatchers.IO) {
            preWarmPython()
        }
    }

    private fun preWarmPython() {
        try {
            // Lewat PythonRuntime (kunci global) — dulu pre-warm ini balapan dengan
            // thread Run bila user menekan ▶ sebelum pre-warm selesai (fix 2026-08-12).
            if (com.zaba.zcode.core.execution.PythonRuntime.ensureStarted(getApplication())) {
                Python.getInstance().getModule("zcode_runner")
                com.zaba.zcode.core.diagnostics.Breadcrumb.log("PREWARM_OK")
            }
        } catch (e: Throwable) {
            com.zaba.zcode.core.diagnostics.Breadcrumb.log("PREWARM_FAIL", e.message ?: "")
        }
    }

    private fun loadPluginFlags() {
        val m = pluginFlags.toMutableMap()
        PluginRegistry.plugins.forEach { p ->
            m[p.id] = prefs.getBoolean("plugin_enabled_${p.id}", p.enabledByDefault)
        }
        pluginFlags = m
    }

    fun isPluginEnabled(id: String): Boolean =
        pluginFlags[id]
            ?: (PluginRegistry.byId(id)?.enabledByDefault ?: false)

    fun setPluginEnabled(id: String, enabled: Boolean) {
        pluginFlags = pluginFlags.toMutableMap().apply { put(id, enabled) }
        prefs.edit().putBoolean("plugin_enabled_$id", enabled).apply()
    }

    /**
     * Eksekusi plugin transform Python (port ZABACODE) secara async.
     * Bila sukses & kode berubah → diterapkan ke file aktif. Callback di Main.
     */
    fun runPythonPlugin(pythonId: String, onDone: (Boolean, String) -> Unit) {
        val code = activeCode
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PluginRunner.run(getApplication(), pythonId, code)
            }
            if (result.ok && result.code != code) {
                updateCode(result.code)
            }
            onDone(result.ok, result.report)
        }
    }

    /**
     * Overload untuk plugin yang butuh parameter tambahan (go_to_definition, rename_symbol).
     * Param diteruskan ke PluginRunner.runWithParam.
     */
    fun runPythonPlugin(pythonId: String, param: String, onDone: (Boolean, String) -> Unit) {
        val code = activeCode
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PluginRunner.runWithParam(getApplication(), pythonId, code, param)
            }
            if (result.ok && result.code != code) {
                updateCode(result.code)
            }
            onDone(result.ok, result.report)
        }
    }

    /** Snippet Pack (S5): bikin file dari template lalu buka. Return nama file. */
    fun createFileFromSnippet(snippet: Snippet): String {
        val existing = FileManager.listFiles(filesDir).map { it["name"] as String }.toSet()
        var index = 1
        var newName = "snippet_${snippet.id}_$index.py"
        while (existing.contains(newName)) {
            index++
            newName = "snippet_${snippet.id}_$index.py"
        }
        FileManager.saveFile(filesDir, newName, snippet.code)
        selectFile(newName)
        return newName
    }

    /** 🔍 mode Find (S3): cari kata di file aktif → (line, konteks), maks 100 hasil. */
    fun findInActiveCode(query: String): List<Pair<Int, String>> {
        if (query.isBlank()) return emptyList()
        val out = mutableListOf<Pair<Int, String>>()
        activeCode.split('\n').forEachIndexed { idx, line ->
            if (out.size >= 100) return@forEachIndexed
            if (line.contains(query, ignoreCase = true)) {
                out.add((idx + 1) to line.trim())
            }
        }
        return out
    }

    /**
     * BEHAVIOR auto_trim_on_run (port perilaku auto_formatter Zabacode):
     * buang spasi akhir tiap baris SEBELUM eksekusi. Catatan jujur: Zabacode
     * juga mengubah buffer (setEditorValue) — file di sini ikut tersimpan rapi.
     */
    fun applyAutoTrimIfEnabled() {
        if (!isPluginEnabled("auto_trim_on_run")) return
        val trimmed = activeCode.split('\n').joinToString("\n") { it.trimEnd() }
        if (trimmed != activeCode) updateCode(trimmed)
    }

    // ------------------------------------------------------------------
    // Persistensi workspace (tab terbuka + file aktif)
    // ------------------------------------------------------------------

    private fun persistWorkspaceState() {
        try {
            val json = JSONObject()
            json.put("opened", JSONArray(openedFiles))
            json.put("active", activeFile ?: "")
            prefs.edit().putString("workspace", json.toString()).apply()
        } catch (e: Exception) {
            // gagal persist bukan bencana — file sudah tersimpan di disk
        }
    }

    private fun loadSavedWorkspace() {
        val available = FileManager.listFiles(filesDir)
        if (available.isEmpty()) {
            FileManager.saveFile(filesDir, "main.py", SEED_MAIN_PY)
        } else {
            // Audit 2026-08: migrasi seed lama yang BELUM disentuh user — tambah
            // tip swipe drawer. HANYA bila isi identik byte-per-byte dengan seed
            // lama; file yang sudah diedit user tidak boleh disentuh (hukum #1).
            val mainFile = File(filesDir, "main.py")
            if (mainFile.exists() && mainFile.readText() == LEGACY_SEED_MAIN_PY) {
                FileManager.saveFile(filesDir, "main.py", SEED_MAIN_PY)
            }
        }

        val saved = try {
            prefs.getString("workspace", null)?.let { JSONObject(it) }
        } catch (e: Exception) {
            null
        }

        val savedTabs = saved?.optJSONArray("opened")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()

        if (savedTabs.isNotEmpty()) {
            // hanya tab yang filenya masih ada di disk (file bisa dihapus di luar app)
            val validTabs = savedTabs.filter { File(filesDir, it).exists() }
            openedFiles.addAll(validTabs)
            activeFile = saved?.optString("active").takeIf { it in validTabs } ?: validTabs.firstOrNull()
        }

        if (openedFiles.isEmpty()) {
            openedFiles.add("main.py")
            activeFile = "main.py"
        }

        activeCode = activeFile?.let { FileManager.readFile(filesDir, it).getOrDefault("") } ?: ""
        symbolBarEnabled = prefs.getBoolean("symbol_bar", true)
        lintGutterEnabled = prefs.getBoolean("lint_gutter", true)
        whitespaceGuardEnabled = prefs.getBoolean("whitespace_guard", false)
        tracebackJumpEnabled = prefs.getBoolean("traceback_jump", true)
        closeBracketsEnabled = prefs.getBoolean("close_brackets", true)
        highlightSelectionMatchesEnabled = prefs.getBoolean("highlight_selection_matches", true)
        // F2.4: Load preferensi indikator Python (default ON)
        showPythonIndicator = prefs.getBoolean("show_python_indicator", true)
        terminalOutputLimit = prefs.getInt("terminal_output_limit", 65536)
        terminalFontSize = prefs.getInt("editor_font_size", 14)
        appFontFamily = prefs.getString("editor_font_family", "Monospace") ?: "Monospace"
        // F1.5: Load tema yang dipersist (default RETRO jika belum ada)
        prefs.getString("theme_type", null)?.let { saved ->
            themeType = ZcodeThemeType.values().firstOrNull { it.name == saved } ?: ZcodeThemeType.GITHUB_DARK
        }
        validateSyntaxDebounced(activeCode)
        persistWorkspaceState()
    }

    /** Toggle Symbol bar — disimpan supaya preferensi bertahan antar sesi.
     *  CATATAN: namanya BUKAN setSymbolBarEnabled — property var di atas tetap
     *  membangkitkan method JVM setSymbolBarEnabled(Z)V (walau private set),
     *  sehingga nama itu bentrok (platform declaration clash, CI compile error). */
    fun setSymbolBar(enabled: Boolean) {
        symbolBarEnabled = enabled
        prefs.edit().putBoolean("symbol_bar", enabled).apply()
    }

    /** Gerbong A: toggle lint gutter — persist antar sesi (pola setSymbolBar,
     *  nama tidak berakhiran Enabled: anti platform declaration clash). */
    fun setLintGutter(enabled: Boolean) {
        lintGutterEnabled = enabled
        prefs.edit().putBoolean("lint_gutter", enabled).apply()
    }

    /** A3: toggle traceback tap-to-jump — persist antar sesi. */
    fun setTracebackJump(enabled: Boolean) {
        tracebackJumpEnabled = enabled
        prefs.edit().putBoolean("traceback_jump", enabled).apply()
    }

    /** A2: toggle whitespace guard — persist antar sesi. */
    fun setWhitespaceGuard(enabled: Boolean) {
        whitespaceGuardEnabled = enabled
        prefs.edit().putBoolean("whitespace_guard", enabled).apply()
    }

    /** F1.7: Toggle auto-close brackets (CM6) — persist antar sesi. */
    fun setCloseBrackets(enabled: Boolean) {
        closeBracketsEnabled = enabled
        prefs.edit().putBoolean("close_brackets", enabled).apply()
    }

    /** F1.8: Toggle selection match highlight (CM6) — persist antar sesi. */
    fun setHighlightSelectionMatches(enabled: Boolean) {
        highlightSelectionMatchesEnabled = enabled
        prefs.edit().putBoolean("highlight_selection_matches", enabled).apply()
    }

    /** F2.4: Toggle indikator "Menyalakan Python…" — persist antar sesi.
     *  CATATAN: namanya BUKAN setShowPythonIndicatorEnabled — property var di atas tetap
     *  membangkitkan method JVM setShowPythonIndicator(Z)V (walau private set),
     *  sehingga nama itu bentrok (platform declaration clash, CI compile error). */
    fun setPythonIndicator(enabled: Boolean) {
        showPythonIndicator = enabled
        prefs.edit().putBoolean("show_python_indicator", enabled).apply()
    }

    fun setOutputLimit(limit: Int) {
        terminalOutputLimit = limit
        prefs.edit().putInt("terminal_output_limit", limit).apply()
    }

    fun setFontSize(size: Int) {
        terminalFontSize = size
        prefs.edit().putInt("editor_font_size", size).apply()
    }

    fun setFontFamily(family: String) {
        appFontFamily = family
        prefs.edit().putString("editor_font_family", family).apply()
    }

    /**
     * Cycle tema satu tombol (redesign 2026-08): tap-tap sampai cocok.
     * Urutan mengikuti enum ZcodeThemeType: RETRO → DRACULA → TOKYO_NIGHT → RETRO…
     * CATATAN JUJUR: pilihan tema belum dipersist antar-restart proses (perilaku
     * lama dipertahankan; tercatat di docs/RENCANA_UPDATE_2026_08.md §7).
     */
    fun cycleTheme() {
        val order = ZcodeThemeType.values()
        val next = (order.indexOf(themeType) + 1) % order.size
        themeType = order[next]
    }

    /**
     * F1.5: Pilih tema langsung (bukan cycle buta) — dipanggil dari SettingsScreen.
     * Tema dipersist antar-restart (berbeda dengan cycleTheme yang tidak persist).
     */
    fun setTheme(theme: ZcodeThemeType) {
        themeType = theme
        prefs.edit().putString("theme_type", theme.name).apply()
    }

    // ------------------------------------------------------------------
    // Navigasi file
    // ------------------------------------------------------------------

    fun selectFile(filename: String) {
        // guard anti double-trigger: long-press close lalu onClick re-add file yang baru ditutup
        val now = System.currentTimeMillis()
        val lc = lastClosed
        if (lc != null && lc.first == filename && now - lc.second < 400) return

        if (filename !in openedFiles) {
            openedFiles.add(filename)
        }
        flushSaveSync()
        activeFile = filename
        activeCode = FileManager.readFile(filesDir, filename).getOrDefault("")
        fileDrafts[filename] = activeCode
        validateSyntaxDebounced(activeCode)
        persistWorkspaceState()
    }

    /**
     * Flush used by ordinary lifecycle callbacks and by process rebirth.
     * Unlike the old Unit API, success means both current source and workspace
     * topology are durably committed. A failed save must never be followed by
     * killing the process.
     */
    fun flushSaveSync(verifyAllDrafts: Boolean = false): Boolean {
        saveJob?.cancel()
        val current = activeFile
        val draftsToSave = linkedMapOf<String, String>()
        if (verifyAllDrafts) {
            fileDrafts.forEach { (name, code) ->
                if (name in openedFiles) draftsToSave[name] = code
            }
            if (current != null) draftsToSave[current] = activeCode
        } else if (current != null && pendingSave) {
            draftsToSave[current] = activeCode
        }
        for ((name, code) in draftsToSave) {
            val result = FileManager.saveFile(filesDir, name, code)
            if (result.isFailure) {
                com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                    "WORKSPACE_FLUSH_FAIL",
                    "$name: ${result.exceptionOrNull()?.message ?: "save gagal"}"
                )
                return false
            }
        }
        if (draftsToSave.isNotEmpty()) {
            pendingSave = false
        }
        val json = try {
            JSONObject().apply {
                put("opened", JSONArray(openedFiles))
                put("active", activeFile ?: "")
            }.toString()
        } catch (e: Exception) {
            com.zaba.zcode.core.diagnostics.Breadcrumb.log("WORKSPACE_FLUSH_FAIL", e.message ?: "state gagal")
            return false
        }
        val committed = prefs.edit().putString("workspace", json).commit()
        if (committed) com.zaba.zcode.core.diagnostics.Breadcrumb.log("WORKSPACE_FLUSH_OK", activeFile ?: "-")
        else com.zaba.zcode.core.diagnostics.Breadcrumb.log("WORKSPACE_FLUSH_FAIL", "commit workspace gagal")
        return committed
    }

    fun updateCode(newCode: String) {
        if (newCode == activeCode) return
        activeCode = newCode
        val current = activeFile ?: return
        fileDrafts[current] = newCode

        pendingSave = true
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(600)
            withContext(Dispatchers.IO) {
                flushSaveSync()
            }
        }
        validateSyntaxDebounced(newCode)
    }

    /**
     * Callback editor membawa ID dokumen agar event WebView yang sempat antre
     * tidak menimpa file baru setelah user cepat berpindah tab.
     */
    fun updateCodeForFile(filename: String, newCode: String) {
        if (filename == activeFile) {
            updateCode(newCode)
            return
        }
        if (filename !in openedFiles) return
        if (fileDrafts[filename] == newCode) return
        fileDrafts[filename] = newCode
        scope.launch(Dispatchers.IO) {
            runCatching { FileManager.saveFile(filesDir, filename, newCode) }
        }
    }

    fun createNewFile() {
        val existing = FileManager.listFiles(filesDir).map { it["name"] as String }.toSet()
        var index = 1
        var newName = "untitled_$index.py"
        while (existing.contains(newName)) {
            index++
            newName = "untitled_$index.py"
        }
        FileManager.saveFile(filesDir, newName, "# New python script\n")
        selectFile(newName)
    }

    // ------------------------------------------------------------------
    // Import file dari file manager HP (SAF, ikon folder di topbar) —
    // keputusan redesign 2026-08: IMPORT COPY ke workspace internal
    // (file asli TIDAK diubah) + filter file teks.
    // ------------------------------------------------------------------

    /**
     * Baca file dari URI SAF → salin ke workspace → buka langsung di editor.
     * Rule #1 & #2 (honest + meticulous):
     * - Cap 512KB (guard FileManager) — file raksasa ditolak sopan, tidak OOM.
     * - Konten biner (ada NUL byte / UTF-8 rusak) → pesan jelas, bukan crash.
     * - Nama bentrok → suffix unik (main.py → main_2.py), file lama TIDAK ditimpa.
     * Return: (sukses, pesan untuk toast user).
     */
    fun importExternalFile(uri: Uri): Pair<Boolean, String> {
        val resolver = getApplication<Application>().contentResolver
        return try {
            val input = resolver.openInputStream(uri)
                ?: return false to "File tidak bisa dibaca."
            val bytes = input.use { readCapped(it, FileManager.MAX_FILE_BYTES) }
                ?: return false to "File terlalu besar (maks 512 KB)"
            if (bytes.isEmpty()) return false to "File kosong — tidak ada yang diimport"
            if (bytes.contains(0.toByte())) {
                return false to "File biner tidak dapat dibuka sebagai teks."
            }
            val text = String(bytes, Charsets.UTF_8)
            // UTF-8 decode Kotlin tidak melempar error tapi menyisipkan U+FFFD
            // untuk byte rusak — tolak agar source code tidak corrupt diam-diam.
            // Char(0xFFFD) eksplisit (bukan literal U+FFFD mentah di source) agar
            // tahan editor/tooling yang bisa merusak karakter replacement di file.
            if (text.contains('\uFFFD')) return false to "Encoding file bukan UTF-8."

            val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
            val finalName = uniqueFileName(displayName ?: "imported")
            FileManager.saveFile(filesDir, finalName, text)
            // Audit 2026-08: catat asal file eksternal + tahan izin tulis persisten
            // supaya menu SAVE bisa menimpa file asli tanpa minta izin lagi.
            try {
                resolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // izin non-persisten tetap cukup untuk sesi ini; Save akan sopan gagal
            }
            externalOrigins[finalName] = uri.toString()
            persistExternalOrigins()
            selectFile(finalName)
            true to "Diimport: $finalName"
        } catch (e: Exception) {
            false to "Gagal import: ${e.message ?: "error tidak dikenal"}"
        }
    }

    /** Baca stream dengan batas keras — return null bila melebihi max. */
    private fun readCapped(input: java.io.InputStream, max: Int): ByteArray? {
        val buffer = ByteArray(8192)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > max) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * Nama file unik & aman via FileManager.secureFilename. Bila nama mentah
     * ilegal (mis. "_secret.py", "catatan gue.txt") → fallback "imported".
     * Bentrok dengan file lama → "nama_N.py" (N mulai 2).
     */
    private fun uniqueFileName(requested: String): String {
        val secured = FileManager.secureFilename(requested)
            ?: FileManager.secureFilename("imported")
            ?: "imported.py"
        val existing = FileManager.listFiles(filesDir).map { it["name"] as String }.toSet()
        if (secured !in existing) return secured
        val stem = secured.removeSuffix(".py")
        var i = 2
        while ("${stem}_$i.py" in existing) i++
        return "${stem}_$i.py"
    }

    // ------------------------------------------------------------------
    // Audit 2026-08: menu File di topbar (Open / Save / Save as)
    // ------------------------------------------------------------------

    /** Apakah file aktif berasal dari file manager (punya file asli di device)? */
    fun hasExternalSource(): Boolean =
        activeFile?.let { externalOrigins.containsKey(it) } == true

    /**
     * SAVE: timpa file asli di device (asal import / Save as terakhir).
     * File internal workspace → pesan jujur: sudah tersimpan otomatis.
     */
    fun saveActiveToSource(): Pair<Boolean, String> {
        val name = activeFile ?: return false to "Tidak ada file aktif"
        val uriStr = externalOrigins[name]
            ?: return false to "File internal tersimpan otomatis di workspace."
        return try {
            val resolver = getApplication<Application>().contentResolver
            resolver.openOutputStream(Uri.parse(uriStr), "wt")?.use { out ->
                out.write(activeCode.toByteArray(Charsets.UTF_8))
            } ?: return false to "Gagal membuka stream tulis"
            true to "Disimpan ke file asli."
        } catch (e: SecurityException) {
            false to "Izin tulis dicabut Android — pakai Save as"
        } catch (e: Exception) {
            false to "Gagal save: ${e.message ?: "error tidak dikenal"}"
        }
    }

    /**
     * SAVE AS: tulis isi aktif ke URI pilihan user (SAF CreateDocument),
     * lalu link URI itu sebagai asal file — Save berikutnya menimpa ke sana.
     */
    fun saveActiveAs(uri: Uri): Pair<Boolean, String> {
        val name = activeFile ?: return false to "Tidak ada file aktif"
        return try {
            val resolver = getApplication<Application>().contentResolver
            resolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(activeCode.toByteArray(Charsets.UTF_8))
            } ?: return false to "Gagal membuka stream tulis"
            try {
                resolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // best-effort; tanpa persisten Save tetap jalan sesi ini
            }
            externalOrigins[name] = uri.toString()
            persistExternalOrigins()
            true to "Disimpan sebagai file device."
        } catch (e: Exception) {
            false to "Gagal save as: ${e.message ?: "error tidak dikenal"}"
        }
    }

    private fun persistExternalOrigins() {
        val o = JSONObject()
        externalOrigins.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("external_origins", o.toString()).apply()
    }

    private fun loadExternalOrigins() {
        try {
            val o = JSONObject(prefs.getString("external_origins", null) ?: "{}")
            o.keys().forEach { k -> externalOrigins[k] = o.optString(k) }
        } catch (e: Exception) {
            externalOrigins.clear()
        }
    }

    /**
     * SAMPLES (FASE E): bikin file dari sample di assets/samples/ lalu buka.
     * Return: (sukses, pesan) — pesan berisi nama file final bila sukses.
     */
    fun createSampleFromAsset(
        assetPath: String,
        sampleId: String,
        /**
         * A7 v1.0.19 (sample multi-file): asset pendamping yang ditulis
         * dengan NAMA TETAP (bukan uniqueFileName) — `import helper_x` di
         * file utama wajib menemukan `helper_x.py` persis. File pendamping
         * yang sudah ada TIDAK ditimpa (user mungkin sudah mengeditnya);
         * ditulis hanya bila belum ada. Multi-file import sendiri sudah
         * jalan sejak dulu (workspace di sys.path — zcode_runner line 137).
         */
        companionAssets: List<String> = emptyList()
    ): Pair<Boolean, String> {
        return try {
            val app = getApplication<Application>()
            for (companion in companionAssets) {
                val cName = companion.substringAfterLast('/')
                if (!java.io.File(filesDir, cName).exists()) {
                    val cCode = app.assets.open(companion)
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    FileManager.saveFile(filesDir, cName, cCode)
                }
            }
            val code = app.assets.open(assetPath)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val finalName = uniqueFileName(sampleId)
            FileManager.saveFile(filesDir, finalName, code)
            selectFile(finalName)
            val note = if (companionAssets.isEmpty()) ""
            else " (+${companionAssets.size} file pendamping)"
            true to "Sample kebuka: $finalName$note"
        } catch (e: Exception) {
            false to "Gagal buka sample: ${e.message ?: "asset hilang"}"
        }
    }

    fun closeFile(filename: String) {
        if (activeFile == filename) {
            flushSaveSync()
        }
        val idx = openedFiles.indexOf(filename)
        if (idx == -1) return
        openedFiles.removeAt(idx)
        fileDrafts.remove(filename)
        lastClosed = filename to System.currentTimeMillis()
        if (activeFile == filename) {
            if (openedFiles.isNotEmpty()) {
                val nextIdx = if (idx < openedFiles.size) idx else openedFiles.size - 1
                selectFile(openedFiles[nextIdx])
            } else {
                activeFile = null
                activeCode = ""
                syntaxError = null
            }
        }
        persistWorkspaceState()
    }

    fun renameFile(oldName: String, newName: String): Boolean {
        val securedOld = FileManager.secureFilename(oldName) ?: return false
        val securedNew = FileManager.secureFilename(newName) ?: return false
        val oldFile = File(filesDir, securedOld)
        val newFile = File(filesDir, securedNew)
        if (oldFile.exists() && !newFile.exists()) {
            val ok = oldFile.renameTo(newFile)
            if (ok) {
                val idx = openedFiles.indexOf(securedOld)
                if (idx != -1) openedFiles[idx] = securedNew
                if (activeFile == securedOld) activeFile = securedNew
                val draft = fileDrafts.remove(securedOld)
                if (draft != null) fileDrafts[securedNew] = draft
                externalOrigins.remove(securedOld)?.let { externalOrigins[securedNew] = it }
                persistExternalOrigins()
                persistWorkspaceState()
                return true
            }
        }
        return false
    }

    fun deleteFile(filename: String): Boolean {
        val secured = FileManager.secureFilename(filename) ?: return false
        val file = File(filesDir, secured)
        if (file.exists() && file.delete()) {
            externalOrigins.remove(secured)
            persistExternalOrigins()
            closeFile(secured)
            return true
        }
        return false
    }

    fun getAllFiles(): List<Map<String, Any>> = FileManager.listFiles(filesDir)

    // ------------------------------------------------------------------
    // Diagnostik sintaksis real-time (Fase 2)
    // ------------------------------------------------------------------

    private fun validateSyntaxDebounced(code: String) {
        validationJob?.cancel()
        validationJob = scope.launch {
            delay(800)
            withContext(Dispatchers.Default) {
                val list = Checker.checkSyntaxList(code)
                val err = if (list.isNotEmpty()) list.first().message else null
                withContext(Dispatchers.Main) {
                    problems = list
                    syntaxError = err
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Plugin transform (Fase 2) — dipicu dari Sidebar / Command Palette
    // ------------------------------------------------------------------

    fun beautifyActiveFile() {
        val beautified = PluginHost.beautify(activeCode)
        updateCode(beautified)
    }

    fun optimizeActiveImports() {
        runPythonPlugin("organize_imports") { _, _ -> }
    }

    fun clearAllDrafts() {
        openedFiles.forEach { FileManager.deleteFileIfExists(filesDir, it) }
        openedFiles.clear()
        fileDrafts.clear()
        externalOrigins.clear()
        persistExternalOrigins()
        activeFile = null
        activeCode = ""
        syntaxError = null
        // hanya hapus state workspace — preferensi UI (symbol_bar dsb.) tetap dipertahankan
        prefs.edit().remove("workspace").apply()
        loadSavedWorkspace()
    }

    override fun onCleared() {
        super.onCleared()
        flushSaveSync()
        scope.cancel()
    }
}
