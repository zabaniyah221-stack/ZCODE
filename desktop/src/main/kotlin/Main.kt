import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.RTextScrollPane
import java.io.File

/**
 * Lokasi zcode_run.py. Pola: dev → resources/jar.
 * Temuan 14 Sep: path "src/main/python" mati di versi installed.
 */
private fun resolveRunnerFile(): File {
    val dev = File("src/main/python/zcode_run.py")
    if (dev.isFile()) return dev
    val alt = File("src/main/resources/zcode_run.py")
    if (alt.isFile()) return alt
    val dir = java.nio.file.Files.createTempDirectory("zcode-app").toFile()
    val loader = object {}::class.java
    val src = loader.getResourceAsStream("/zcode_run.py")
    val dst = File(dir, "zcode_run.py")
    if (src != null) dst.outputStream().use { src.copyTo(it) }
    return dst
}

private val runnerFile: File by lazy { resolveRunnerFile() }

// GitHub Dark (palet ZCODE) — default sesuai keputusan NOTEZ v0.2.0.
private val BG = Color(0xFF0D1117)
private val SURFACE = Color(0xFF161B22)
private val ACCENT = Color(0xFF58A6FF)
private val TEXT = Color(0xFFC9D1D9)

/**
 * Drawer output kanan. Top-level agar AnimatedVisibility tanpa receiver
 * scope (konflik overload ColumnScope saat dipanggil dalam Column —
 * 3x CI merah, 14 Sep). Sibling kanan editor.
 */
@Composable
private fun DrawerPanel(
    modifier: Modifier,
    visible: Boolean,
    lines: List<String>,
    listState: LazyListState,
    onClose: () -> Unit
) {
    // Blink output (16 Sep): slide 150ms resize sibling SwingPanel = ras
    // repaint AWT. Tampil instan: kalau blink hilang = resize-race,
    // kalau tetap = peer-creation pertama. Jangan kembalikan slide
    // sebelum akar dipastikan.
    if (!visible) return
    Column(modifier.fillMaxHeight().background(Color(0xEB0A0E14))) {
        Column(Modifier.width(420.dp).fillMaxHeight()
            .background(Color(0xEB0A0E14))) {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Text("  Output", color = Color(0xFF8B949E), fontSize = 11.sp,
                    modifier = Modifier.weight(1f))
                Text("tutup ✕", color = ACCENT, fontSize = 11.sp,
                    modifier = Modifier.clickable { onClose() }
                        .padding(end = 8.dp))
            }
            Row(Modifier.fillMaxSize()) {
                LazyColumn(state = listState,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
                    items(lines) { line ->
                        Text(line, color = TEXT, fontSize = 12.sp)
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.fillMaxHeight().padding(end = 4.dp),
                    adapter = rememberScrollbarAdapter(listState)
                )
            }
        }
    }
}

fun main() {
    // Panel AWT default light-gray = sumber blink (temuan 15 Sep).
    // Set gelap sebelum komponen AWT pertama dibuat.
    try {
        javax.swing.UIManager.put("Panel.background", java.awt.Color(0x0D, 0x11, 0x17))
    } catch (_: Exception) { }
    // Splash launcher native: tampil detik pertama, tutup pas ready/Enter.
    // Hidup di EDT sendiri; application{} di bawah tetap memblokir sampai exit.
    try {
        showNativeSplash()
    } catch (_: Exception) { }
    application {
    // Posisi tengah eksplisit (15 Sep): jahit dengan splash 900x600 tengah —
    // dua-duanya center = tepat tindih, bukan splash tengah + ZCODE kanan.
    val windowState = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        size = androidx.compose.ui.unit.DpSize(900.dp, 600.dp))
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(14) }
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
    // Handle editor RSTA (migrasi irisan 2, 15 Sep): SwingPanel native,
    // getText/setText sinkron — tanpa bridge, poll, splash-wait.
    // SECURITY: setCode-escape JS dihapus (kelas injeksi hilang total).
    var rstaRef by remember { mutableStateOf<RSyntaxTextArea?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Splash: layar hitam + logo sampai editor siap. RSTA Swing itu
    // lightweight (bukan heavyweight CEF) → overlay Compose BISA menutupnya.
    var showSplash by remember { mutableStateOf(true) }
    val logoBmp: ImageBitmap? = remember {
        try {
            val bytes = object {}::class.java.getResourceAsStream("/zcode_logo.png")?.readBytes()
                ?: return@remember null
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (_: Exception) { null }
    }

    var logLine by remember { mutableStateOf("[>] ZCODE Desktop v0.0.1-desktop — siap") }
    var runCount by remember { mutableStateOf(0) }
    var outputText by remember { mutableStateOf("") }
    var outputOpen by remember { mutableStateOf(false) }
    val outLines = remember(outputText) { outputText.lines() }
    val outList = rememberLazyListState()
    var openFiles by remember { mutableStateOf(mapOf("workspace_tmp.py" to "")) }
    var currentFile by remember { mutableStateOf("workspace_tmp.py") }
    var currentPath by remember { mutableStateOf<String?>(null) }

    fun showInEditor(text: String) {
        try { rstaRef?.text = text } catch (_: Exception) { }
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
        // Simpan isi tab lama dulu agar tak hilang saat pindah.
        try {
            val cur = rstaRef?.text
            if (cur != null) openFiles = openFiles + (currentFile to cur)
        } catch (_: Exception) { }
        currentFile = name
        showInEditor(openFiles[name].orEmpty())
    }

    /** Simpan isi editor ke file asal (atau workspace_tmp.py). */
    fun saveCurrent() {
        scope.launch {
            val code = try { rstaRef?.text.orEmpty() } catch (_: Exception) { "" }
            val target = currentPath?.let { java.io.File(it) } ?: File("workspace_tmp.py")
            withContext(Dispatchers.IO) { target.writeText(code) }
            openFiles = openFiles + (target.name to code)
            currentFile = target.name
            logLine = "[OK] tersimpan ${target.name}"
        }
    }

    /** Tutup drawer output + kembalikan fokus ke editor RSTA. */
    fun closeOutput() {
        outputOpen = false
        try { rstaRef?.requestFocusInWindow() } catch (_: Exception) { }
    }
    var pyInfo by remember { mutableStateOf("python3 …") }

    fun doRun() {
        runCount++
        val area = rstaRef
        if (area == null) {
            outputText = "(tunggu editor siap)"
            outputOpen = true
            logLine = "[ERR] Run dibatalkan: editor belum siap"
            println("[RUN-TIME] batal rsta-belum-siap")
            return
        }
        logLine = "[>] menjalankan…"
        scope.launch {
            outputText = "[>] mengambil kode editor…"
            outputOpen = true
            val t0 = System.currentTimeMillis()
            val code = try { area.text } catch (_: Exception) { "" }
            val tGet = System.currentTimeMillis() - t0
            if (code.isBlank()) {
                outputText = "(editor kosong — ketik kode dulu)"
                outputOpen = true
                logLine = "[ERR] Run dibatalkan: editor kosong (ambil ${tGet}ms)"
                println("[RUN-TIME] batal get=${tGet}ms")
                return@launch
            }
            val t1 = System.currentTimeMillis()
            outputText = "[>] menjalankan python…"
            val res = withContext(Dispatchers.IO) {
                val target = currentPath?.let { java.io.File(it) } ?: run {
                    val dir = File(System.getProperty("user.home"), ".cache/zcode")
                    dir.mkdirs()
                    File(dir, "workspace_tmp.py")
                }
                target.writeText(code)
                currentFile = target.name
                Runner.run(target, runnerFile)
            }
            val tPy = System.currentTimeMillis() - t1
            val tTot = System.currentTimeMillis() - t0
            outputText = res.output.ifBlank { "(tanpa output)" }
            outputOpen = true
            logLine = if (res.exitCode == 0) "[OK] exit 0 · ambil ${tGet}ms · py ${tPy}ms · total ${tTot}ms"
                      else "[ERR] exit ${res.exitCode} · ambil ${tGet}ms · py ${tPy}ms"
            println("[RUN] exit=${res.exitCode} get=${tGet}ms py=${tPy}ms total=${tTot}ms out=${res.output.take(200)}")
        }
    }

    LaunchedEffect(Unit) {
        // RSTA instan: tanpa download 500MB, tanpa init CEF.
        pyInfo = withContext(Dispatchers.IO) { Runner.pythonInfo() }
        logLine = "[OK] editor siap (RSyntaxTextArea)"
        showSplash = false
        SplashGate.appReady.set(true)
        println("[SPIKE] RSTA_READY=true")
    }

    Window(onCloseRequest = ::exitApplication, state = windowState, title = "ZCODE") {
        DisposableEffect(Unit) {
            // Blink putih (temuan 15 Sep, burst capture: 29.5% white 1 frame
            // saat drawer dibuka): panel AWT perantara Compose (default
            // light-gray 238,238,238) ikut repaint saat SwingPanel resize.
            // Cat SEMUA container gelap, rekursif — warna sama dengan tema.
            fun catGelap(c: java.awt.Component, hitam: java.awt.Color) {
                try {
                    c.background = hitam
                    (c as? java.awt.Container)?.components?.forEach { catGelap(it, hitam) }
                } catch (_: Exception) { }
            }
            try {
                val win = java.awt.Window.getWindows()
                    .firstOrNull { (it as? java.awt.Frame)?.title == "ZCODE" }
                val hitam = java.awt.Color(0x0D, 0x11, 0x17)
                if (win != null) catGelap(win, hitam)
            } catch (_: Exception) { }
            // F5 global level AWT: shortcut tombol Run (jalan utama ada tombol).
            // Paket 15 Sep sore: +Ctrl+S simpan +Ctrl+O buka (pengganti button
            // yang dihapus — fungsi tetap punya jalan keyboard).
            val mgr = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
            val d = java.awt.KeyEventDispatcher { e ->
                if (e.id == java.awt.event.KeyEvent.KEY_PRESSED) {
                    val ctrl = e.isControlDown
                    when {
                        e.keyCode == java.awt.event.KeyEvent.VK_F5 -> { doRun(); true }
                        ctrl && e.keyCode == java.awt.event.KeyEvent.VK_S -> { saveCurrent(); true }
                        ctrl && e.keyCode == java.awt.event.KeyEvent.VK_O -> { openFileDialog(); true }
                        else -> false
                    }
                } else false
            }
            mgr.addKeyEventDispatcher(d)
            onDispose { mgr.removeKeyEventDispatcher(d) }
        }
        MaterialTheme(colors = androidx.compose.material.darkColors(
            primary = ACCENT, surface = SURFACE, background = BG, onSurface = TEXT
        )) {
            Box(Modifier.fillMaxSize().background(BG)) {
            Column(Modifier.fillMaxSize()
                .onKeyEvent {
                    if (it.key == Key.F5) { doRun(); true }
                    else false
                }) {
                // Paket 15 Sep sore: SEMUA button dihapus (fokus editor+output).
                // Jalan keyboard: F5 Run, Ctrl+S simpan, Ctrl+O buka (dispatcher AWT).
                Row(Modifier.fillMaxWidth().height(48.dp).background(SURFACE),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("  ZCODE", color = TEXT, fontSize = 14.sp,
                        modifier = Modifier.padding(start = 8.dp))
                }
                Divider(color = Color(0xFF30363D))
                LaunchedEffect(outLines.size, outputOpen) {
                    try {
                        if (outLines.isNotEmpty()) outList.scrollToItem(outLines.lastIndex)
                    } catch (_: Exception) { }
                }
                androidx.compose.foundation.layout.Row(Modifier.weight(1f)) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Text("  ${currentPath ?: currentFile}", fontSize = 11.sp, color = Color(0xFF8B949E),
                            modifier = Modifier.fillMaxWidth().background(SURFACE).padding(4.dp),
                            maxLines = 1)
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
                        // Editor RSTA via SwingPanel (irisan 4): wiring tema +
                        // completion + parser terpusat di EditorRsta.kt.
                        // Swing native: fokus klik otomatis, tanpa jembatan JS/CEF.
                        Box(Modifier.weight(1f).fillMaxWidth().background(BG)) {
                            SwingPanel(
                                factory = {
                                    newPythonEditor(
                                        fontSize,
                                        openFiles[currentFile].orEmpty()
                                    ) {
                                        rstaRef = it
                                        // Focus handoff saat splash native ditutup.
                                        SplashGate.focusEditor = {
                                            try {
                                                it.requestFocusInWindow()
                                            } catch (_: Exception) { }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { pane ->
                                    try {
                                        val sp = (pane as? RTextScrollPane)?.viewport?.view
                                            as? RSyntaxTextArea
                                        if (sp != null && sp.font.size != fontSize) {
                                            sp.font = java.awt.Font(
                                                java.awt.Font.MONOSPACED,
                                                java.awt.Font.PLAIN, fontSize)
                                        }
                                    } catch (_: Exception) { }
                                }
                            )
                        }
                    }
                    DrawerPanel(
                        Modifier.fillMaxHeight(),
                        outputOpen, outLines, outList, { closeOutput() })
                }
                Row(Modifier.fillMaxWidth().height(26.dp).background(SURFACE),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("  $pyInfo  ·  run#$runCount  ·  $logLine  ·  Ctrl+Spasi lengkap",
                        fontSize = 11.sp, color = TEXT, maxLines = 1)
                }
            }
            if (showSplash) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0D1117)),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (logoBmp != null) {
                            Image(logoBmp, contentDescription = "ZCODE",
                                modifier = Modifier.width(160.dp))
                        } else {
                            Text("{Z}", color = ACCENT, fontSize = 64.sp)
                        }
                        Text("memuat editor…", color = Color(0xFF8B949E),
                            fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            }
        }
    }
}
}
