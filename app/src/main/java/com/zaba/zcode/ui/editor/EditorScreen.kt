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
import com.zaba.zcode.WorkspaceViewModel

/**
 * EditorScreen — WebView file:// + CodeMirror 6 bundled (offline-first, tanpa CDN).
 *
 * - True-black OLED #050806; gutter line numbers + tema dikonfigurasi deklaratif
 *   di bundle CM6 (editor-src/src/editor.js → assets/editor/codemirror.bundle.js).
 * - Bridge: addJavascriptInterface "ZCODE" — TANPA loopback HTTP (file:// murni,
 *   tanpa localhost/port), menghapus kelas bug F-01/S-27/C-50 dari Zabacode selamanya.
 * - Kontrak bridge JS identik dengan era Ace (setCode/getCode/insertText/undo/redo/
 *   duplicateRows/toggleCommentLines + onEditorReady handshake PR #5) — lihat
 *   docs/MIGRASI_CM6.md §3.
 * - ZMUX lesson: debounce resize 100ms di MainActivity agar prompt tidak loncat 4-5 baris.
 * - Font editor fix 14px (audit 2026-08; sebelumnya 12px) — di-set di bundle CM6.
 * - Jenis font (UI & editor) via bridge setFontFamily + @font-face injeksi (audit 2026-08).
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun EditorScreen(
    code: String,
    onCodeChange: (String) -> Unit,
    webViewRef: MutableState<WebView?> = remember { mutableStateOf(null) },
    vm: WorkspaceViewModel? = null // F1.7 & F1.8: untuk apply editor settings ke CM6 bridge
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
            applyEditorFontFamily(webViewRef.value, vm?.appFontFamily ?: "Monospace")
            webViewRef.value?.requestLayout()
            webViewRef.value?.invalidate()
        }
    }

    // F1.7 & F1.8: Apply editor settings (closeBrackets, highlightSelectionMatches) ke CM6 bridge.
    // Dipanggil setiap recomposition agar perubahan dari SettingsScreen langsung berefek.
    // Audit 2026-08: jenis font (UI & editor) ikut di-apply via setFontFamily.
    // BUG H — FIX 2026-08-13. WebView yang BELUM pernah navigasi melaporkan
    // url `about:blank` (URI opaque) dan bundle CM6-nya belum dimuat. Memanggil
    // evaluateJavascript pada keadaan itu tidak berguna, dan pola yang sama
    // pernah menyebabkan CRASH LOOP saat cold start di VSCodroid
    // ("guard folderFromUrl against opaque WebView URLs",
    // https://github.com/rmyndharis/VSCodroid). ZCODE memanggil blok ini pada
    // SETIAP recomposition — termasuk recomposition pertama sebelum
    // onPageFinished — sehingga jalur rawannya identik.
    //
    // runCatching dipakai karena kegagalan menerapkan preferensi kosmetik tidak
    // boleh pernah mematikan aplikasi; guard `typeof ==='function'` di sisi JS
    // sudah ada, ini melindungi sisi Kotlin-nya.
    webViewRef.value?.post {
        val wv = webViewRef.value ?: return@post
        val url = wv.url
        if (url.isNullOrBlank() || url == "about:blank") return@post
        runCatching {
            val closeBrackets = vm?.closeBracketsEnabled ?: true
            val highlightSelectionMatches = vm?.highlightSelectionMatchesEnabled ?: true
            wv.evaluateJavascript("if(typeof setCloseBrackets==='function')setCloseBrackets($closeBrackets);", null)
            wv.evaluateJavascript("if(typeof setHighlightSelectionMatches==='function')setHighlightSelectionMatches($highlightSelectionMatches);", null)
            // Gerbong A v1.0.19: lint gutter + whitespace guard + diagnostik.
            // Satu sumber kebenaran: vm.problems (Checker, debounce 800ms) —
            // VPP dan lint gutter membaca data yang sama. Guard typeof sisi
            // JS + runCatching sisi Kotlin (pola BUG H yang sudah teruji).
            val lintOn = vm?.lintGutterEnabled ?: true
            val wsOn = vm?.whitespaceGuardEnabled ?: false
            wv.evaluateJavascript("if(typeof setLintEnabled==='function')setLintEnabled($lintOn);", null)
            wv.evaluateJavascript("if(typeof setWhitespaceEnabled==='function')setWhitespaceEnabled($wsOn);", null)
            if (lintOn) {
                val diagJson = org.json.JSONArray().apply {
                    (vm?.problems ?: emptyList()).forEach { p ->
                        put(org.json.JSONObject().apply {
                            put("from_line", p.line)
                            p.column?.let { put("column", it) }
                            put("severity", when (p.severity) {
                                com.zaba.zcode.core.editor.Severity.ERROR -> "error"
                                com.zaba.zcode.core.editor.Severity.WARNING -> "warning"
                                else -> "info"
                            })
                            put("message", p.message)
                        })
                    }
                }.toString()
                wv.evaluateJavascript(
                    "if(typeof setDiagnostics==='function')setDiagnostics(${escapeJavaScriptString(diagJson)});",
                    null
                )
            }
            applyEditorFontFamily(wv, vm?.appFontFamily ?: "Monospace")
        }.onFailure {
            com.zaba.zcode.core.diagnostics.Breadcrumb.log("WEBVIEW_APPLY_FAIL", it.message ?: "")
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
                        // Editor memuat HTML/bundle/font milik APK dari file://,
                        // tetapi JavaScript-nya tidak boleh membaca file lain,
                        // content://, atau origin internet. blockNetworkLoads
                        // adalah lapis native di samping CSP connect-src 'none'.
                        allowFileAccess = true
                        allowContentAccess = false
                        allowFileAccessFromFileURLs = false
                        allowUniversalAccessFromFileURLs = false
                        blockNetworkLoads = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    isFocusable = true
                    isFocusableInTouchMode = true

                    // FIX (audit 2026-08): keyboard HANYA saat tap terkonfirmasi
                    // (jarak < touch slop & durasi singkat). Versi lama memanggil
                    // showSoftInput di ACTION_DOWN — setiap sentuhan (termasuk awal
                    // swipe buka drawer / scroll) dianggap "mau ngetik" → keyboard
                    // nongol saat swipe sidebar. Tap biasa tetap memunculkan keyboard.
                    var downX = 0f
                    var downY = 0f
                    var downTime = 0L
                    setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                                downTime = System.currentTimeMillis()
                            }
                            MotionEvent.ACTION_UP -> {
                                val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                                val isTap = Math.abs(event.x - downX) < slop &&
                                    Math.abs(event.y - downY) < slop &&
                                    System.currentTimeMillis() - downTime <
                                    android.view.ViewConfiguration.getLongPressTimeout()
                                if (isTap) {
                                    v.requestFocus()
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                    imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                                }
                            }
                        }
                        false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val trusted = isTrustedEditorUrl(request?.url?.toString())
                            if (!trusted) {
                                // Jangan log URL penuh: query dapat memuat data
                                // sensitif. Scheme cukup untuk diagnosis.
                                com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                                    "WEBVIEW_NAV_BLOCKED",
                                    request?.url?.scheme ?: "unknown"
                                )
                            }
                            return !trusted
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (!isTrustedEditorUrl(url)) return
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

/** Hanya dokumen editor bundled yang boleh hidup di WebView ber-EditorBridge. */
private fun isTrustedEditorUrl(url: String?): Boolean =
    url != null && url.startsWith("file:///android_asset/editor/")

/**
 * Audit 2026-08: jenis font editor mengikuti pilihan user (Settings → UI & editor).
 * @font-face di-inject sekali (guard id), lalu bridge setFontFamily (compartment CM6)
 * mengganti keluarga font .cm-scroller — gutter ikut (inherit). Idempoten & aman
 * dipanggil tiap recomposition; no-op bila bundle belum siap (typeof guard).
 */
private fun applyEditorFontFamily(webView: android.webkit.WebView?, family: String) {
    webView ?: return
    webView.evaluateJavascript(FONT_FACE_JS, null)
    webView.evaluateJavascript(
        "if(typeof setFontFamily==='function')setFontFamily(\"${fontFamilyCss(family)}\");",
        null
    )
}

/** Map nama pilihan Settings → CSS font-family (fallback monospace selalu). */
private fun fontFamilyCss(family: String): String = when (family) {
    "JetBrains Mono" -> "'ZCodeJetBrainsMono', monospace"
    "Fira Code" -> "'ZCodeFiraCode', monospace"
    "Source Code Pro" -> "'ZCodeSourceCodePro', monospace"
    else -> "monospace"
}

/** @font-face untuk font bundel di assets/editor/fonts/ (offline-first, tanpa CDN). */
private const val FONT_FACE_JS =
    "(function(){if(document.getElementById('zcode-fontfaces'))return;" +
        "var s=document.createElement('style');s.id='zcode-fontfaces';" +
        "s.textContent=\"@font-face{font-family:'ZCodeJetBrainsMono';" +
        "src:url('fonts/jetbrains_mono.ttf')}" +
        "@font-face{font-family:'ZCodeFiraCode';" +
        "src:url('fonts/fira_code.ttf')}" +
        "@font-face{font-family:'ZCodeSourceCodePro';" +
        "src:url('fonts/source_code_pro.ttf')}\";" +
        "document.head.appendChild(s);})();"

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
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onChange(code)
        }
    }

    @android.webkit.JavascriptInterface
    fun onEditorReady() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onReady()
        }
    }

    @android.webkit.JavascriptInterface
    fun getCode(): String = ""
}
