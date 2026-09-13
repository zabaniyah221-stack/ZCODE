import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.withContext

/**
 * Spike Fase 0 rev2: tunggu bridge SIAP (poll getCode sampai valid),
 * baru round-trip setCode -> getCode -> bandingkan.
 */
fun main() = application {
    val windowState = rememberWindowState()
    var kcefReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            KCEF.init(builder = {
                installDir(File("kcef-bundle"))
                progress { onDownloading { println("[SPIKE] KCEF download $it%") } }
            })
        }
        kcefReady = true
        println("[SPIKE] KCEF_INIT_OK")
    }

    Window(onCloseRequest = ::exitApplication, state = windowState, title = "ZCODE Spike") {
        if (!kcefReady) return@Window
        val page = File("zcode-www/index.html")
        val state = rememberWebViewState("file://${page.absolutePath}")
        val navigator = rememberWebViewNavigator()
        var done by remember { mutableStateOf(false) }
        WebView(state, Modifier.fillMaxSize(), navigator = navigator)
        LaunchedEffect(state.loadingState) {
            if (done) return@LaunchedEffect
            // Poll: bridge siap kalau getCode mengembalikan string (bukan error)
            var ready = false
            for (i in 1..20) {
                var got: String? = null
                navigator.evaluateJavaScript("(typeof getCode==='function')?'BRIDGE_OK':'NO_BRIDGE'") { got = it.toString() }
                delay(1000)
                if (got != null && "BRIDGE_OK" in got!!) { ready = true; break }
            }
            println("[SPIKE] BRIDGE_READY=$ready")
            if (!ready) { done = true; return@LaunchedEffect }
            navigator.evaluateJavaScript("setCode('print(42)')") {}
            delay(1500)
            navigator.evaluateJavaScript("getCode()") { got ->
                val s = got.toString()
                val pass = "print(42)" in s
                println("[SPIKE] ROUNDTRIP_RESULT=$s")
                println("[SPIKE] ROUNDTRIP_PASS=$pass")
            }
            done = true
        }
    }
}
