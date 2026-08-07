package com.zaba.zcode.ui.editor

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * EditorScreen — Fase 0 WebView file:// + Ace 1.44.0 bundled (offline-first)
 * Dark OLED #050806, gutter 40dp, line numbers, no prelude injection
 * Bridge: addJavascriptInterface (no loopback, no port, no Content-Type bug)
 * Debounce resize 100ms is handled in MainActivity
 */
@Composable
fun EditorScreen(
    code: String,
    onCodeChange: (String) -> Unit
) {
    // Fase 0: placeholder Box showing dark OLED + gutter
    // Real WebView will be:
    // WebView with file:///android_asset/editor/index.html + ace.js 1.44.0
    // setJavaScriptEnabled true, addJavascriptInterface EditorBridge, WebViewClient block remote URL
    Surface(color = androidx.compose.ui.graphics.Color(0xFF050806), modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            // Gutter 40dp — line numbers
            Surface(color = androidx.compose.ui.graphics.Color(0xFF0A100D), modifier = Modifier.width(40.dp).fillMaxHeight()) {
                Column(Modifier.padding(8.dp)) {
                    (1..4).forEach { Text("$it", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF4D7A5A)) }
                }
            }
            // Editor area — in Fase 1 this will be AndroidView { WebView }
            Box(Modifier.weight(1f).padding(12.dp)) {
                Text(
                    code,
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color(0xFFB9F5C4)
                )
                // For Fase 0 skeleton, show WebView placeholder comment
                // AndroidView(factory = { ctx ->
                //   WebView(ctx).apply {
                //     settings.javaScriptEnabled = true
                //     addJavascriptInterface(EditorBridge(onCodeChange), "ZCODE")
                //     webViewClient = block remote
                //     loadUrl("file:///android_asset/editor/index.html")
                //   }
                // })
            }
        }
    }
}

// Bridge interface — no loopback token needed
class EditorBridge(private val onChange: (String) -> Unit) {
    @android.webkit.JavascriptInterface
    fun onCodeChange(code: String) { onChange(code) }

    @android.webkit.JavascriptInterface
    fun getCode(): String = ""
}
