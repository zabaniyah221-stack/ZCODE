import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fase 1 — Workbench v0.0.1.
 * Toolbar (Run + F5) · sidebar toggle (Ctrl+B) · editor CM6 · status bar.
 * Scope §2 RENCANA V002: tanpa command palette, tanpa PTY, tanpa packaging.
 */

// GitHub Dark (palet ZCODE) — default sesuai keputusan NOTEZ v0.2.0.
private val BG = Color(0xFF0D1117)
private val SURFACE = Color(0xFF161B22)
private val ACCENT = Color(0xFF58A6FF)
private val TEXT = Color(0xFFC9D1D9)

fun main() = application {
    val windowState = rememberWindowState()
    var kcefReady by remember { mutableStateOf(false) }
    var sidebarOpen by remember { mutableStateOf(true) }
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(14) }
    // Muat preferensi (file config desktop, bukan SharedPreferences)
    LaunchedEffect(Unit) {
        val saved: Int? = withContext(Dispatchers.IO) {
            try {
                val cfg = File(System.getProperty("user.home"),
                    ".config/zcode-desktop/settings.properties")
                if (cfg.isFile()) {
                    val p = java.util.Properties()
                    cfg.inputStream().use(p::load)
                    p.getProperty("ui.fontSize")?.toIntOrNull()
                } else null
            } catch (_: Exception) { null }
        }
        if (saved != null) fontSize = saved
    }
    fun saveSettings() {
        try {
            val dir = File(System.getProperty("user.home"), ".config/zcode-desktop")
            dir.mkdirs()
            val p = java.util.Properties()
            p.setProperty("ui.fontSize", fontSize.toString())
            File(dir, "settings.properties").outputStream().use { p.store(it, null) }
        } catch (_: Exception) { }
    }
    val navigator = rememberWebViewNavigator()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Handle browser native (lift ke sini supaya terlihat dari Box editor):
    // fokus Compose SAJA tidak sampai ke Chromium.
    var cefBrowser by remember { mutableStateOf<KCEFBrowser?>(null) }

    var logLine by remember { mutableStateOf("[>] ZCODE Desktop v0.0.1-desktop — siap") }
    var runCount by remember { mutableStateOf(0) }
    var outputText by remember { mutableStateOf("") }
    var outputOpen by remember { mutableStateOf(false) }
    // Tab per file: nama → isi. Statistik: 1 tab = 1 file.
    var openFiles by remember { mutableStateOf(mapOf("workspace_tmp.py" to "")) }
    var currentFile by remember { mutableStateOf("workspace_tmp.py") }
    var currentPath by remember { mutableStateOf<String?>(null) }

    fun showInEditor(text: String) {
        scope.launch {
            delay(500)
            val esc = text.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\n", "\\n")
            navigator.evaluateJavaScript("setCode('$esc')") {}
        }
    }

    fun openFileDialog() {
        val dlg = java.awt.FileDialog(null as java.awt.Frame?, "Buka file Python", java.awt.FileDialog.LOAD)
        dlg.isVisible = true
        if (dlg.file != null) {
            val f = java.io.File(dlg.directory, dlg.file)
            try {
                val text = f.readText()
                openFiles = openFiles + (f.name to text)
                currentFile = f.name
                currentPath = f.absolutePath
                showInEditor(text)
                logLine = "[OK] dibuka ${f.name}"
            } catch (_: Exception) {
                logLine = "[ERR] gagal buka ${f.name}"
            }
        }
    }

    fun switchTab(name: String) {
        currentFile = name
        showInEditor(openFiles[name].orEmpty())
    }

    /** Ctrl+S: simpan isi editor ke file asal (atau workspace_tmp.py). */
    fun saveCurrent() {
        scope.launch {
            var code = ""
            var n = 0
            while (code.isEmpty() && n++ < 50) {
                val d = kotlinx.coroutines.CompletableDeferred<String>()
                navigator.evaluateJavaScript("getCode()") { d.complete(it.toString()) }
                code = d.await()
                if (code.isEmpty()) delay(100)
            }
            val target = currentPath?.let { java.io.File(it) } ?: File("workspace_tmp.py")
            withContext(Dispatchers.IO) { target.writeText(code) }
            openFiles = openFiles + (target.name to code)
            currentFile = target.name
            logLine = "[OK] tersimpan ${target.name}"
        }
    }
    var pyInfo by remember { mutableStateOf("python3 …") }

    fun doRun() {
        runCount++
        logLine = "[>] menjalankan…"
        scope.launch {
            var code = ""
            navigator.evaluateJavaScript("getCode()") { code = it.toString() }
            // Tunggu callback JS (poll sederhana, cukup untuk v0.0.1)
            withContext(Dispatchers.IO) {
                var n = 0
                while (code.isEmpty() && n++ < 50) Thread.sleep(100)
            }
            // Guard temuan 14 Sep: editor kosong/belum siap jangan dieksekusi sunyi.
            if (code.isBlank()) {
                outputText = "(editor kosong atau belum siap — tunggu status bridge OK)"
                outputOpen = true
                logLine = "[ERR] Run dibatalkan: editor kosong"
                return@launch
            }
            val res = withContext(Dispatchers.IO) {
                // Simpan kembali ke file asal bila ada, else workspace_tmp.py
                val target = currentPath?.let { java.io.File(it) } ?: File("workspace_tmp.py")
                target.writeText(code)
                currentFile = target.name
                Runner.run(target, File("src/main/python"))
            }
            outputText = res.output.ifBlank { "(tanpa output)" }
            outputOpen = true // auto-show saat Run pertama (keputusan UI/UX)
            val first = res.output.lineSequence().take(5).joinToString(" | ")
            logLine = if (res.exitCode == 0) "[OK] exit 0 · $first"
                      else "[ERR] exit ${res.exitCode} · $first"
            println("[RUN] exit=${res.exitCode} out=${res.output.take(200)}")
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            KCEF.init(builder = { installDir(File("kcef-bundle")) })
        }
        kcefReady = true
        println("[SPIKE] KCEF_INIT_OK")
        pyInfo = Runner.pythonInfo()
    }

    Window(onCloseRequest = ::exitApplication, state = windowState, title = "ZCODE Desktop") {
        MaterialTheme(colors = androidx.compose.material.darkColors(
            primary = ACCENT, surface = SURFACE, background = BG, onSurface = TEXT
        )) {
            Column(Modifier.fillMaxSize().background(BG)
                .onKeyEvent {
                    if (it.key == Key.F5) { doRun(); true }
                    else if (it.isCtrlPressed && it.key == Key.S) { saveCurrent(); true }
                    else if (it.isCtrlPressed && it.key == Key.O) { openFileDialog(); true }
                    else false
                }) {
                // Toolbar: jalan utama tiap aksi (shortcut = jalan pintasnya)
                Row(Modifier.fillMaxWidth().height(48.dp).background(SURFACE),
                    verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { doRun() }, Modifier.padding(start = 8.dp)) {
                        Text("▶ Run (F5)")
                    }
                    Button(onClick = { openFileDialog() }, Modifier.padding(start = 4.dp)) {
                        Text("Buka (Ctrl+O)")
                    }
                    Button(onClick = { saveCurrent() }, Modifier.padding(start = 4.dp)) {
                        Text("Simpan (Ctrl+S)")
                    }
                    Text("  ZCODE Desktop v0.0.1", fontSize = fontSize.sp, color = TEXT)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
                Divider(color = Color(0xFF30363D))
                Row(Modifier.weight(1f)) {
                    // Sidebar: jalan utama = klik ☰ (belum ada shortcut; jangan klaim Ctrl+B)
                    if (sidebarOpen) {
                        Column(Modifier.width(180.dp).fillMaxHeight().background(SURFACE).padding(8.dp)) {
                            Text("Files", Modifier.fillMaxWidth().clickable { openFileDialog() }.padding(6.dp),
                                fontSize = fontSize.sp, color = TEXT)
                            // Jalan utama sama dengan tombol toolbar (tanpa duplikat mati).
                            Text("Settings", Modifier.fillMaxWidth().clickable { showSettings = true }.padding(6.dp),
                                fontSize = fontSize.sp, color = TEXT)
                            Text("About", Modifier.fillMaxWidth().clickable { showAbout = true }.padding(6.dp),
                                fontSize = fontSize.sp, color = TEXT)
                            // Belum Fase 1: ngaku, jangan mati sunyi.
                            listOf("Packages", "Samples").forEach {
                                Text(it, Modifier.fillMaxWidth().clickable {
                                    logLine = "[i] $it belum tersedia di Fase 1"
                                }.padding(6.dp),
                                    fontSize = fontSize.sp, color = Color(0xFF8B949E))
                            }
                        }
                    }
                    // Editor CM6 (bundle SAMA persis) + breadcrumb dasar
                    Column(Modifier.weight(1f)) {
                        // Breadcrumb dasar = path file aktif
                    Text("  ${currentPath ?: currentFile}", fontSize = 11.sp, color = Color(0xFF8B949E),
                            modifier = Modifier.fillMaxWidth().background(SURFACE).padding(4.dp),
                            maxLines = 1)
                        // Bar tab per file (Ctrl+Tab menyusul)
                        Row(modifier = Modifier.fillMaxWidth().background(SURFACE)) {
                            openFiles.keys.forEach { name ->
                                val sel = name == currentFile
                                Text(" $name ", color = if (sel) ACCENT else TEXT,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable { switchTab(name) }
                                        .background(if (sel) BG else SURFACE)
                                        .padding(6.dp))
                            }
                        }
                        // Fokus klik (temuan 14 Sep): klik mouse TIDAK memindahkan
                        // fokus ke Chromium (Tab bisa). Tiap Press: fokus Compose
                        // + browser.setFocus(true) native, TANPA consume supaya
                        // klik tetap sampai ke CEF (posisi kursor) + keyboard masuk.
                        val editorFocus = remember { FocusRequester() }
                        Box(Modifier.weight(1f)
                            .focusRequester(editorFocus)
                            .focusable()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        if (ev.type == PointerEventType.Press) {
                                            try { editorFocus.requestFocus() }
                                            catch (_: Exception) { }
                                            try { cefBrowser?.setFocus(true) }
                                            catch (_: Exception) { }
                                        }
                                    }
                                }
                            }) {
                        if (!kcefReady) {
                            Text("Initializing KCEF…", Modifier.align(Alignment.Center), color = TEXT)
                        } else {
                            val page = File("zcode-www/index.html")
                            val state = rememberWebViewState("file://${page.absolutePath}")
                            var done by remember { mutableStateOf(false) }
                            WebView(state, Modifier.fillMaxSize(), navigator = navigator,
                                onCreated = fun(b: KCEFBrowser) { cefBrowser = b },
                                onDispose = fun(_: KCEFBrowser) { cefBrowser = null })
                            // Indikator load TIDAK boleh overlay di atas WebView:
                            // view yang muncul/hilang mencuri fokus keyboard
                            // (temuan 14 Sep: ketikan mati setelah 2-3 huruf).
                            // Status cukup di log + status bar.
                            LaunchedEffect(state.loadingState) {
                                if (done) return@LaunchedEffect
                                logLine = "[>] memuat editor…"
                                var ready = false
                                for (i in 1..20) {
                                    try {
                                        var got: String? = null
                                        navigator.evaluateJavaScript("(typeof getCode==='function')?'BRIDGE_OK':'NO_BRIDGE'") { got = it.toString() }
                                        delay(1000)
                                        if (got != null && "BRIDGE_OK" in got!!) { ready = true; break }
                                    } catch (_: Exception) {
                                        delay(1000)
                                    }
                                }
                                println("[SPIKE] BRIDGE_READY=$ready")
                                done = true
                                if (ready) {
                                    logLine = "[OK] editor siap"
                                    // Kembalikan fokus ke editor CM6
                                    try {
                                        navigator.evaluateJavaScript(
                                            "document.querySelector('.cm-content')?.focus()") {}
                                    } catch (_: Exception) { }
                                } else {
                                    logLine = "[ERR] bridge editor tak siap"
                                }
                            }
                        }
                        }
                    }
                }
                // Dialog Settings — window eksplisit (temuan 14 Sep: tanpa judul/ukuran
                // jadi "Untitled" + area putih). Font editor ikut bundle — limit Fase 1.
                if (showSettings) {
                    androidx.compose.ui.window.Dialog(
                        onCloseRequest = { showSettings = false },
                        title = "Pengaturan ZCODE",
                        state = androidx.compose.ui.window.rememberDialogState(size = androidx.compose.ui.unit.DpSize(380.dp, 300.dp))
                    ) {
                        Column(Modifier.fillMaxSize().background(SURFACE).padding(16.dp)) {
                            Text("Settings", color = TEXT, fontSize = 15.sp)
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)) {
                                Text("Font UI: $fontSize", color = TEXT, fontSize = 12.sp,
                                    modifier = Modifier.padding(end = 8.dp))
                                Button(onClick = {
                                    if (fontSize > 10) { fontSize--; saveSettings() }
                                }) { Text("−") }
                                Button(onClick = {
                                    if (fontSize < 20) { fontSize++; saveSettings() }
                                }, modifier = Modifier.padding(start = 4.dp)) { Text("+") }
                            }
                            Text("Font editor ikut bundle (14px).",
                                color = Color(0xFF8B949E), fontSize = 11.sp)
                            Button(onClick = { showSettings = false },
                                modifier = Modifier.padding(top = 8.dp)) { Text("Tutup") }
                        }
                    }
                }
                // Dialog About (versi + GPLv3, scope §2)
                if (showAbout) {
                    androidx.compose.ui.window.Dialog(
                        onCloseRequest = { showAbout = false },
                        title = "Tentang ZCODE",
                        state = androidx.compose.ui.window.rememberDialogState(size = androidx.compose.ui.unit.DpSize(400.dp, 260.dp))
                    ) {
                        Column(Modifier.fillMaxSize().background(SURFACE).padding(16.dp)) {
                            Text("ZCODE Desktop v0.0.1-desktop", color = TEXT, fontSize = 15.sp)
                            Text("IDE Python • offline-first • gratis", color = TEXT, fontSize = 12.sp)
                            Text("Lisensi GPLv3 • github.com/zabaniyah221-stack/ZCODE",
                                color = Color(0xFF8B949E), fontSize = 11.sp)
                            Button(onClick = { showAbout = false }, Modifier.padding(top = 8.dp)) {
                                Text("Tutup")
                            }
                        }
                    }
                }
                // Panel output (auto-show saat Run; toggle via klik status)
                if (outputOpen) {
                    Column(Modifier.fillMaxWidth().height(140.dp).background(Color(0xFF0A0E14))) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                            Text("  Output", color = Color(0xFF8B949E), fontSize = 11.sp,
                                modifier = Modifier.weight(1f))
                            Text("tutup ✕", color = ACCENT, fontSize = 11.sp,
                                modifier = Modifier.clickable { outputOpen = false }
                                    .padding(end = 8.dp))
                        }
                        Text(outputText, color = TEXT, fontSize = 12.sp,
                            modifier = Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp))
                    }
                    Divider(color = Color(0xFF30363D))
                }
                // Status bar: interpreter + versi (keputusan UI/UX §2)
                Row(Modifier.fillMaxWidth().height(26.dp).background(SURFACE),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("  $pyInfo  ·  run#$runCount  ·  $logLine",
                        fontSize = 11.sp, color = TEXT, maxLines = 1)
                }
            }
        }
    }
}
