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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import dev.datlag.kcef.KCEF
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
    var logLine by remember { mutableStateOf("[>] ZCODE Desktop v0.0.1-desktop — siap") }
    var runCount by remember { mutableStateOf(0) }
    var pyInfo by remember { mutableStateOf("python3 …") }
    var showAbout by remember { mutableStateOf(false) }
    val navigator = rememberWebViewNavigator()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

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
            val res = withContext(Dispatchers.IO) {
                val script = File("workspace_tmp.py")
                script.writeText(code)
                Runner.run(script, File("src/main/python"))
            }
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
                    if (it.key == Key.F5) { doRun(); true } else false
                }) {
                // Toolbar: Run + F5
                Row(Modifier.fillMaxWidth().height(48.dp).background(SURFACE),
                    verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { doRun() }, Modifier.padding(start = 8.dp)) {
                        Text("▶ Run (F5)")
                    }
                    Text("  ZCODE Desktop v0.0.1", fontSize = 13.sp, color = TEXT)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Button(onClick = { showAbout = true }, Modifier.padding(end = 8.dp)) {
                        Text("About")
                    }
                }
                Divider(color = Color(0xFF30363D))
                Row(Modifier.weight(1f)) {
                    // Sidebar toggle Ctrl+B — di sini via klik ☰ (shortcut global menyusul)
                    if (sidebarOpen) {
                        Column(Modifier.width(180.dp).fillMaxHeight().background(SURFACE).padding(8.dp)) {
                            listOf("Files", "Packages", "Samples", "Settings", "About").forEach {
                                Text(it, Modifier.fillMaxWidth().clickable { }.padding(6.dp),
                                    fontSize = 13.sp, color = TEXT)
                            }
                        }
                    }
                    // Editor CM6 (bundle SAMA persis) + breadcrumb dasar
                    Column(Modifier.weight(1f)) {
                        Text("  workspace_tmp.py", fontSize = 11.sp, color = Color(0xFF8B949E),
                            modifier = Modifier.fillMaxWidth().background(SURFACE).padding(4.dp),
                            maxLines = 1)
                        Box(Modifier.weight(1f)) {
                        if (!kcefReady) {
                            Text("Initializing KCEF…", Modifier.align(Alignment.Center), color = TEXT)
                        } else {
                            val page = File("zcode-www/index.html")
                            val state = rememberWebViewState("file://${page.absolutePath}")
                            var done by remember { mutableStateOf(false) }
                            WebView(state, Modifier.fillMaxSize(), navigator = navigator)
                            LaunchedEffect(state.loadingState) {
                                if (done) return@LaunchedEffect
                                var ready = false
                                for (i in 1..20) {
                                    var got: String? = null
                                    navigator.evaluateJavaScript("(typeof getCode==='function')?'BRIDGE_OK':'NO_BRIDGE'") { got = it.toString() }
                                    delay(1000)
                                    if (got != null && "BRIDGE_OK" in got!!) { ready = true; break }
                                }
                                println("[SPIKE] BRIDGE_READY=$ready")
                                done = true
                            }
                        }
                        }
                    }
                }
                // Dialog About (versi + GPLv3, scope §2)
                if (showAbout) {
                    androidx.compose.ui.window.Dialog(onCloseRequest = { showAbout = false }) {
                        Column(Modifier.background(SURFACE).padding(16.dp)) {
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
