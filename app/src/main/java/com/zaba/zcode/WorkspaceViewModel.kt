package com.zaba.zcode

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.zaba.zcode.core.editor.Checker
import com.zaba.zcode.core.files.FileManager
import com.zaba.zcode.core.files.Paths
import com.zaba.zcode.core.plugins.PluginHost
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

    private val fileDrafts = mutableMapOf<String, String>()

    init {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        loadSavedWorkspace()
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
        validateSyntaxDebounced(activeCode)
        persistWorkspaceState()
    }

    // ------------------------------------------------------------------
    // Navigasi file
    // ------------------------------------------------------------------

    fun selectFile(filename: String) {
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
        prefs.edit().clear().apply()
        loadSavedWorkspace()
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}
