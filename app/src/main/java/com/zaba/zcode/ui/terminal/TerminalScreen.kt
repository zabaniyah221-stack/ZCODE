package com.zaba.zcode.ui.terminal

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaba.zcode.core.execution.ExecutionEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.zaba.zcode.ui.theme.ZcodeThemeType
import com.zaba.zcode.ui.theme.getTerminalPalette

/**
 * TerminalScreen — layer Terminal full-screen (pindah layer, bukan panel).
 * - Ketik langsung di terminal: TextField transparan 1dp mengikat keyboard Android,
 *   Enter mengirim baris ke stdin (tanpa tombol Send) — dual backend:
 *   Chaquopy in-process (Android) / python3 subprocess (desktop).
 * - Ctrl+C: deterministik untuk input() yang nge-blok (flag interrupt), best-effort
 *   interrupt thread worker untuk loop CPU.
 * - Back di pojok kiri atas; proses dibersihkan saat keluar.
 * - Output di-cap MAX_OUTPUT_CHARS (S-18).
 */
@Composable
fun TerminalScreen(
    filename: String,
    filesDir: File,
    context: Context,
    onBack: () -> Unit,
    showPythonIndicator: Boolean = true, // F2.4: toggle indikator cold-start Python
    terminalOutputLimit: Int = 65536, // F2.2: Ring Buffer limit
    themeType: ZcodeThemeType = ZcodeThemeType.RETRO // F2.8: follow active theme
) {
    var terminalText by remember { mutableStateOf("ZCODE Terminal — Running $filename\n" + "-".repeat(40) + "\n") }
    var inputVal by remember { mutableStateOf(TextFieldValue("")) }
    var session by remember { mutableStateOf<ExecutionEngine.InteractiveSession?>(null) }
    // F1.2 (PERF_PASS B,F): indikator cold-start Python. Di ARMv7 Python.start()
    // bisa 1-3 dtk; tanpa ini layar terlihat kosong/diam seolah tap Run telat.
    var startingPython by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // F2.2: Coalesce scroll variables
    var lastScrollTime by remember { mutableStateOf(0L) }
    var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun append(text: String) {
        val combined = terminalText + text
        terminalText = if (combined.length > terminalOutputLimit) {
            "\n…[output truncated: >$terminalOutputLimit chars]…\n" +
                combined.takeLast(terminalOutputLimit)
        } else {
            combined
        }

        val isUserScrollingUp = scrollState.value < scrollState.maxValue - 120
        if (!isUserScrollingUp) {
            val now = System.currentTimeMillis()
            if (now - lastScrollTime > 120) {
                lastScrollTime = now
                scope.launch {
                    scrollState.scrollTo(scrollState.maxValue)
                }
            } else {
                scrollJob?.cancel()
                scrollJob = scope.launch {
                    delay(120)
                    scrollState.scrollTo(scrollState.maxValue)
                }
            }
        }
    }

    // Jalankan proses saat terminal dibuka (callback datang dari thread background)
    LaunchedEffect(filename) {
        val targetFile = File(filesDir, filename)
        if (!targetFile.exists()) {
            append("\nError: File $filename not found!\n")
            startingPython = false
            return@LaunchedEffect
        }
        // F1.2 + F2.4: tampilkan status cold-start SEBELUM memanggil startInteractiveSession
        // (Python.start() yang berat berjalan di dalamnya). Layar tidak lagi kosong.
        // F2.4: indikator bisa dimatikan user via Settings.
        if (showPythonIndicator) append("\u2699 Menyalakan Python\u2026\n")
        // Beri satu frame agar pesan tergambar sebelum pemanggilan sinkron yang berat.
        withContext(Dispatchers.Main) { kotlinx.coroutines.yield() }
        val activeSession = ExecutionEngine.startInteractiveSession(
            context = context,
            file = targetFile,
            onOutput = { chunk -> scope.launch { append(chunk) } },
            onExit = { code ->
                startingPython = false
                scope.launch { append("\n\nProcess finished with exit code $code\n") }
            }
        )
        session = activeSession
        startingPython = false
        withContext(Dispatchers.IO) {
            activeSession.waitForExit()
        }
    }

    // Fokus otomatis + bersihkan proses saat keluar
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    DisposableEffect(Unit) {
        onDispose {
            session?.sendKill()
        }
    }

    val blinkTransition = rememberInfiniteTransition(label = "cursor")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "cursorAlpha"
    )

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back di pojok kiri atas (keputusan tim)
                    Text(
                        "◀ Back",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable {
                                session?.sendCtrlC()
                                onBack()
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Terminal: $filename",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        },
        bottomBar = {
            // bottom bar ramping (permintaan user): padding vertikal & tombol diperkecil
            Surface(color = Color(0xFF1E1F29)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            session?.sendCtrlC()
                            append("^C\nProcess Interrupted\n")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
                    ) {
                        Text("Ctrl+C", fontSize = 11.sp, color = Color.White)
                    }
                    Text(
                        "Tap terminal untuk mengetik langsung",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF050806)) // terminal SELALU true-black (keputusan tim)
                .clickable { focusRequester.requestFocus() }
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                if (startingPython && showPythonIndicator) {
                    // F1.2 + F2.4: indikator tak menghalangi; user paham app sedang bekerja.
                    // F2.4: bisa dimatikan via Settings.
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Menyalakan Python\u2026",
                            color = Color(0xFF8A9BB0),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF39FF14)
                        )
                    }
                }
                Column {
                    val palette = getTerminalPalette(themeType)
                    val annotatedText = parseAnsiToAnnotatedString(terminalText, palette)
                    Text(
                        text = annotatedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp, // font size 12 (keputusan tim)
                        lineHeight = 16.sp
                    )
                    // Baris input aktif + kursor blok berkedip
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = inputVal.text,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(14.dp)
                                .alpha(blinkAlpha)
                                .background(Color(0xFF39FF14))
                        )
                    }
                }
            }

            // TextField transparan 1dp: pengikat keyboard virtual (ketik langsung di terminal)
            TextField(
                value = inputVal,
                onValueChange = { inputVal = it },
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(focusRequester),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.Transparent
                ),
                textStyle = TextStyle(fontSize = 12.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val line = inputVal.text
                        append(line + "\n")
                        session?.sendInput(line + "\n")
                        inputVal = TextFieldValue("")
                        focusRequester.requestFocus()
                    }
                )
            )
        }
    }


fun parseAnsiToAnnotatedString(text: String, palette: com.zaba.zcode.ui.theme.TerminalPalette): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    val len = text.length

    var currentFg: Color? = null
    var currentBg: Color? = null
    var bold = false

    fun getSpanStyle(): SpanStyle {
        return SpanStyle(
            color = currentFg ?: palette.foreground,
            background = currentBg ?: Color.Transparent,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }

    var styleStartIndex = 0

    while (i < len) {
        if (i + 1 < len && text[i] == '\u001B' && text[i + 1] == '[') {
            val currentStyle = getSpanStyle()
            builder.addStyle(currentStyle, styleStartIndex, builder.length)

            i += 2
            val startCode = i
            while (i < len && text[i] != 'm') {
                i++
            }
            if (i < len && text[i] == 'm') {
                val codeSeq = text.substring(startCode, i)
                i++ // Skip 'm'
                val codes = codeSeq.split(';').mapNotNull { it.toIntOrNull() }
                if (codes.isEmpty()) {
                    currentFg = null
                    currentBg = null
                    bold = false
                } else {
                    var idx = 0
                    while (idx < codes.size) {
                        val c = codes[idx]
                        when (c) {
                            0 -> {
                                currentFg = null
                                currentBg = null
                                bold = false
                            }
                            1 -> bold = true
                            22 -> bold = false
                            in 30..37 -> {
                                currentFg = palette.ansiColors[c - 30]
                            }
                            39 -> currentFg = null
                            in 40..47 -> {
                                currentBg = palette.ansiColors[c - 40]
                            }
                            49 -> currentBg = null
                            in 90..97 -> {
                                currentFg = palette.ansiColors[c - 90 + 8]
                            }
                            in 100..107 -> {
                                currentBg = palette.ansiColors[c - 100 + 8]
                            }
                        }
                        idx++
                    }
                }
            }
            styleStartIndex = builder.length
            continue
        }

        builder.append(text[i])
        i++
    }

    val finalStyle = getSpanStyle()
    builder.addStyle(finalStyle, styleStartIndex, builder.length)

    return builder.toAnnotatedString()
}
}
