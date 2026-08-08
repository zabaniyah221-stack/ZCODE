package com.zaba.zcode.ui.editor

import android.webkit.WebSettings
import android.webkit.WebView
import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

/**
 * EditorScreen — WebView file:// + Ace 1.44.0 bundled (offline-first, tanpa CDN).
 *
 * - True-black OLED #050806; gutter line numbers 40px dikonfigurasi di Ace (index.html).
 * - Bridge: addJavascriptInterface "ZCODE" — TANPA loopback HTTP (file:// murni,
 *   tanpa localhost/port), menghapus kelas bug F-01/S-27/C-50 dari Zabacode selamanya.
 * - ZMUX lesson: debounce resize 100ms di MainActivity agar prompt tidak loncat 4-5 baris.
 * - Font editor 12px (keputusan tim) — di-set di index.html.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun EditorScreen(
    code: String,
    onCodeChange: (String) -> Unit,
    webViewRef: MutableState<WebView?> = remember { mutableStateOf(null) }
) {
    // Menghindari stale state capture pada factory blok AndroidView
    val bridge = androidx.compose.runtime.remember {
        EditorBridge(
            onChange = { onCodeChange(it) },
            onReady = {}
        )
    }

    // Perbarui callback dan nilai code pada bridge setiap kali recomposition terjadi
    bridge.onChange = onCodeChange
    bridge.onReady = {
        webViewRef.value?.post {
            webViewRef.value?.evaluateJavascript("setCode(${escapeJavaScriptString(code)});", null)
            webViewRef.value?.requestLayout()
            webViewRef.value?.invalidate()
        }
    }

    Surface(color = Color(0xFF050806), modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    isFocusable = true
                    isFocusableInTouchMode = true

                    setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            v.requestFocus()
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                        }
                        false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript("setCode(${escapeJavaScriptString(code)});", null)
                            view?.post {
                                view.requestLayout()
                                view.invalidate()
                            }
                        }
                    }

                    addJavascriptInterface(bridge, "ZCODE")
                    loadUrl("file:///android_asset/editor/index.html")
                    webViewRef.value = this
                }
            },
            update = { webView ->
                // setCode dipicu on-demand dari WorkbenchScreen (pindah tab / plugin transform)
                // agar kursor tidak melompat sembarangan saat user mengetik.
            }
        )
    }
}

/** Escape string ke JS string literal yang aman (baris baru, kutip, backslash, unicode). */
fun escapeJavaScriptString(value: String): String {
    val builder = StringBuilder()
    builder.append("\"")
    for (char in value) {
        when (char) {
            '\\' -> builder.append("\\\\")
            '"' -> builder.append("\\\"")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            else -> {
                if (char.code < 32 || char.code > 126) {
                    builder.append(String.format("\\u%04x", char.code))
                } else {
                    builder.append(char)
                }
            }
        }
    }
    builder.append("\"")
    return builder.toString()
}

class EditorBridge(
    var onChange: (String) -> Unit,
    var onReady: () -> Unit = {}
) {
    @android.webkit.JavascriptInterface
    fun onCodeChange(code: String) {
        onChange(code)
    }

    @android.webkit.JavascriptInterface
    fun onEditorReady() {
        onReady()
    }

    @android.webkit.JavascriptInterface
    fun getCode(): String = ""
}
