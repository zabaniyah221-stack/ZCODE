package com.zaba.zcode

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.zaba.zcode.core.editor.Checker
import com.zaba.zcode.core.execution.ExecutionEngine
import com.zaba.zcode.core.files.FileManager
import com.zaba.zcode.core.files.Paths
import com.zaba.zcode.core.plugins.PluginHost
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
class WorkspaceViewModel(app: Application) : AndroidViewModel(app) {

    private val filesDir: File = Paths.filesDir(app)
    private val prefs: android.content.SharedPreferences =
        app.getSharedPreferences("zcode_workspace", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var validationJob: Job? = null

    var themeType by mutableStateOf(ZcodeThemeType.RETRO)
    val openedFiles = mutableStateListOf<String>()
    var activeFile by mutableStateOf<String?>(null)
    var activeCode by mutableStateOf("")
    var syntaxError by mutableStateOf<String?>(null)

    /** Symbol bar (QuickTools) di bawah editor — toggle user, persist di SharedPreferences. */
    var symbolBarEnabled by mutableStateOf(true)
        private set

    /**
     * State enabled plugin (batch anti-sepi S2) — SATU sumber kebenaran di sini
     * (SharedPreferences), anti kasus state-terbelah Zabacode (backend in-memory
     * vs frontend localStorage).
     */
    var pluginFlags by mutableStateOf(
        com.zaba.zcode.core.plugins.PluginRegistry.plugins
            .associate { it.id to it.enabledByDefault }.toMutableMap()
    )
        private set

    private val fileDrafts = mutableMapOf<String, String>()

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
        loadSavedWorkspace()
        loadPluginFlags()
    }

    private fun loadPluginFlags() {
        val m = pluginFlags.toMutableMap()
        com.zaba.zcode.core.plugins.PluginRegistry.plugins.forEach { p ->
            m[p.id] = prefs.getBoolean("plugin_enabled_${p.id}", p.enabledByDefault)
        }
        pluginFlags = m
    }

    fun isPluginEnabled(id: String): Boolean =
        pluginFlags[id]
            ?: (com.zaba.zcode.core.plugins.PluginRegistry.byId(id)?.enabledByDefault ?: false)

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
            FileManager.saveFile(filesDir, "main.py", "# Welcome to ZCODE\nprint(\"Hello, ZCODE!\")\n")
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
        activeFile = filename
        activeCode = FileManager.readFile(filesDir, filename).getOrDefault("")
        fileDrafts[filename] = activeCode
        validateSyntaxDebounced(activeCode)
        persistWorkspaceState()
    }

    fun updateCode(newCode: String) {
        if (newCode == activeCode) return
        activeCode = newCode
        val current = activeFile ?: return
        fileDrafts[current] = newCode
        FileManager.saveFile(filesDir, current, newCode)
        validateSyntaxDebounced(newCode)
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

    fun closeFile(filename: String) {
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
                val err = Checker.checkSyntax(code)
                withContext(Dispatchers.Main) {
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
        val optimized = PluginHost.optimizeImports(activeCode)
        updateCode(optimized)
    }

    fun clearAllDrafts() {
        openedFiles.forEach { FileManager.deleteFileIfExists(filesDir, it) }
        openedFiles.clear()
        fileDrafts.clear()
        activeFile = null
        activeCode = ""
        syntaxError = null
        // hanya hapus state workspace — preferensi UI (symbol_bar dsb.) tetap dipertahankan
        prefs.edit().remove("workspace").apply()
        loadSavedWorkspace()
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}
