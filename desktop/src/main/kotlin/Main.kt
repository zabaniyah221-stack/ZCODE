import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.DisposableEffect
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
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.web.WebContent
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewFactoryParam
import com.multiplatform.webview.web.WebViewNavigator
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
    // UI minimal Fase fokus (14 Sep): sidebar + tombol non-Run DIHAPUS sementara.
    // Fungsi buka/simpan/tab dipertahankan untuk nanti; shortcut Ctrl+S/Ctrl+O
    // ikut dimatikan karena tiap shortcut wajib punya tombol.
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
    // JsBridge F5 (temuan 14 Sep): F5 di dalam CEF native tak sampai ke
    // dispatcher AWT Compose → JS keydown panggil balik via callNative.
    val jsBridge = rememberWebViewJsBridge(navigator)

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
            // Timer per tahap (temuan 14 Sep: Run lama, biang belum tahu).
            val t0 = System.currentTimeMillis()
            var code = ""
            navigator.evaluateJavaScript("getCode()") { code = it.toString() }
            // Tunggu callback JS (poll sederhana, cukup untuk v0.0.1)
            withContext(Dispatchers.IO) {
                var n = 0
                while (code.isEmpty() && n++ < 50) Thread.sleep(100)
            }
            val tGet = System.currentTimeMillis() - t0
            // Guard temuan 14 Sep: editor kosong/belum siap jangan dieksekusi sunyi.
            if (code.isBlank()) {
                outputText = "(editor kosong atau belum siap — tunggu status bridge OK)"
                outputOpen = true
                logLine = "[ERR] Run dibatalkan: editor kosong (ambil ${tGet}ms)"
                println("[RUN-TIME] batal get=${tGet}ms")
                return@launch
            }
            val t1 = System.currentTimeMillis()
            val res = withContext(Dispatchers.IO) {
                // Simpan kembali ke file asal bila ada, else workspace_tmp.py
                val target = currentPath?.let { java.io.File(it) } ?: File("workspace_tmp.py")
                target.writeText(code)
                currentFile = target.name
                Runner.run(target, File("src/main/python"))
            }
            val tPy = System.currentTimeMillis() - t1
            val tTot = System.currentTimeMillis() - t0
            outputText = res.output.ifBlank { "(tanpa output)" }
            outputOpen = true // auto-show saat Run pertama (keputusan UI/UX)
            val first = res.output.lineSequence().take(5).joinToString(" | ")
            logLine = if (res.exitCode == 0) "[OK] exit 0 · ambil ${tGet}ms · py ${tPy}ms · total ${tTot}ms · $first"
                      else "[ERR] exit ${res.exitCode} · ambil ${tGet}ms · py ${tPy}ms · $first"
            println("[RUN] exit=${res.exitCode} get=${tGet}ms py=${tPy}ms total=${tTot}ms out=${res.output.take(200)}")
        }
    }

    // Registrasi handler JsBridge ZcodeRun — di sini (setelah doRun) agar
    // forward-reference local fun tidak unresolved. Dipanggil dari JS keydown F5.
    LaunchedEffect(jsBridge) {
        jsBridge.register(object : IJsMessageHandler {
            override fun methodName() = "ZcodeRun"
            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                doRun()
            }
        })
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
        // F5 global level AWT (temuan 14 Sep): saat fokus di editor native CEF,
        // key event tak sampai ke onKeyEvent Compose → tangkap di dispatcher.
        // F5 = shortcut tombol Run (jalan utama ada) → sah.
        DisposableEffect(Unit) {
            val mgr = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
            val d = java.awt.KeyEventDispatcher { e ->
                if (e.id == java.awt.event.KeyEvent.KEY_PRESSED &&
                    e.keyCode == java.awt.event.KeyEvent.VK_F5) {
                    doRun(); true
                } else false
            }
            mgr.addKeyEventDispatcher(d)
            onDispose { mgr.removeKeyEventDispatcher(d) }
        }
        MaterialTheme(colors = androidx.compose.material.darkColors(
            primary = ACCENT, surface = SURFACE, background = BG, onSurface = TEXT
        )) {
            Column(Modifier.fillMaxSize().background(BG)
                .onKeyEvent {
                    if (it.key == Key.F5) { doRun(); true }
                    else false
                }) {
                // Toolbar minimal: hanya Run (+ judul). Buka/Simpan kembali
                // saat UI lengkap (aturan: shortcut wajib punya tombol).
                Row(Modifier.fillMaxWidth().height(48.dp).background(SURFACE),
                    verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { doRun() }, Modifier.padding(start = 8.dp)) {
                        Text("▶ Run (F5)")
                    }
                    Text("  ZCODE Desktop v0.0.1", fontSize = fontSize.sp, color = TEXT)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
                Divider(color = Color(0xFF30363D))
                // Fase fokus: TANPA sidebar — langsung editor.
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
                                webViewJsBridge = jsBridge,
                                onCreated = fun(b: KCEFBrowser) { cefBrowser = b },
                                onDispose = fun(_: KCEFBrowser) { cefBrowser = null },
                                // Factory sendiri: defaultWebViewFactory crash
                                // (createContext null saat race init, 14 Sep).
                                // Kita tak butuh custom UA → tanpa requestContext.
                                factory = fun(param: WebViewFactoryParam): KCEFBrowser {
                                    val url = (param.state.content as? WebContent.Url)?.url
                                        ?: KCEFBrowser.BLANK_URI
                                    return param.client.createBrowser(
                                        url, param.rendering, param.transparent)
                                })
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
                                    // F5 dari dalam halaman → callNative ZcodeRun.
                                    // preventDefault agar CEF tak reload.
                                    try {
                                        navigator.evaluateJavaScript(
                                            "(function(){if(window.__zcodeF5)return;window.__zcodeF5=true;" +
                                            "document.addEventListener('keydown',function(e){" +
                                            "if(e.key==='F5'){e.preventDefault();" +
                                            "if(window.kmpJsBridge&&window.kmpJsBridge.callNative)" +
                                            "{window.kmpJsBridge.callNative('ZcodeRun','{}',null);}}" +
                                            "},true);})()") {}
                                    } catch (_: Exception) { }
                                } else {
                                    logLine = "[ERR] bridge editor tak siap"
                                }
                            }
                        }
                    }
                }
                // Panel output: LazyColumn per baris (temuan 14 Sep: scroll parent
                // loyo — issue lib #123 outer-steals-scroll, atasi sendiri) +
                // auto ke baris terakhir agar traceback tak terpotong.
                val outLines = remember(outputText) { outputText.lines() }
                val outList = rememberLazyListState()
                LaunchedEffect(outLines.size, outputOpen) {
                    try {
                        if (outLines.isNotEmpty()) outList.scrollToItem(outLines.lastIndex)
                    } catch (_: Exception) { }
                }
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
                        LazyColumn(state = outList,
                            modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            items(outLines) { line ->
                                Text(line, color = TEXT, fontSize = 12.sp)
                            }
                        }
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
