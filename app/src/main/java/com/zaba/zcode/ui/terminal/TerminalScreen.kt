package com.zaba.zcode.ui.terminal

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import com.zaba.zcode.core.diagnostics.Breadcrumb
import com.zaba.zcode.core.execution.ExecutionEngine
import com.zaba.zcode.core.execution.OutputBatcher
import com.zaba.zcode.core.execution.RunId
import com.zaba.zcode.core.execution.RunLogger
import com.zaba.zcode.core.execution.SessionState
import com.zaba.zcode.core.execution.TerminalBuffer
import com.zaba.zcode.core.files.Paths
import com.zaba.zcode.core.packageengine.TelemetryStore
import kotlinx.coroutines.Job
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
 *
 * SPEC-001 Phase 0 + Phase 3 terminal (implemented):
 * - interactive hard timeout DIHAPUS (session selesai karena exit/Ctrl+C/Stop/error)
 * - run ID per session + disk-backed full log (filesDir/logs/runs/<run-id>.log)
 * - output batching (OutputBatcher: 40ms / 2KB) — AI streaming → 1 UI update/batch
 * - stdout/stderr dipisah (stream "out"/"err") di disk log
 * - session state eksplisit (START/RUNNING/WAITING_FOR_INPUT/INTERRUPTING/…)
 * - line-oriented buffer (TerminalBuffer) — bukan satu String raksasa
 * - incremental ANSI parser (AnsiLineCache per baris)
 * - virtualized renderer (LazyColumn — UI hanya menyusun baris yang terlihat)
 * - metrik: visible memory (chars), log bytes, free storage
 * - Export Log (SAF) — full log dari disk, bukan dari ring buffer
 *
 * In-memory cap: TerminalBuffer(maxLines) membatasi RAM (SPEC Rule 5:
 * full output ≠ full RAM). Full history di disk via RunLogger — TIDAK hilang.
 * Batas MAX_OUTPUT_CHARS (S-18) berlaku untuk log in-memory di layar Pip;
 * disk log interactive tidak di-cap.
 */
@Composable
fun TerminalScreen(
    filename: String,
    filesDir: File,
    context: Context,
    onBack: () -> Unit,
    showPythonIndicator: Boolean = true, // F2.4: toggle indikator cold-start Python
    terminalOutputLimit: Int = 65536, // F2.2: legacy ring-buffer chars (dipertahankan utk kompatibilitas param)
    themeType: ZcodeThemeType = ZcodeThemeType.RETRO, // F2.8: follow active theme
    // Audit 2026-08: ukuran font setting kini KHUSUS terminal (label UI "Ukuran
    // Font Terminal"); keluarga font terminal SELALU Monospace (console wajib
    // alignment) — jenis font pilihan user berlaku untuk UI & editor saja.
    terminalFontSize: Int = 14
) {
    val buffer = remember { TerminalBuffer(maxLines = 10_000) }
    val ansiCache = remember(themeType) { AnsiLineCache(getTerminalPalette(themeType)) }
    var inputVal by remember { mutableStateOf(TextFieldValue("")) }
    var session by remember { mutableStateOf<ExecutionEngine.InteractiveSession?>(null) }
    // F1.2 (PERF_PASS B,F): indikator cold-start Python. Di ARMv7 Python.start()
    // bisa 1-3 dtk; tanpa ini layar terlihat kosong/diam seolah tap Run telat.
    var startingPython by remember { mutableStateOf(true) }
    var sessionState by remember { mutableStateOf(SessionState.START) }
    var logBytes by remember { mutableStateOf(0L) }
    var memChars by remember { mutableLongStateOf(0L) }
    var runId by remember { mutableStateOf(RunId.newId("run")) }
    var logger by remember { mutableStateOf<RunLogger?>(null) }
    var logFilePath by remember { mutableStateOf<File?>(null) }
    val listState: LazyListState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var stickToBottom by remember { mutableStateOf(true) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    fun appendToTerminal(stream: String, text: String) {
        // 1) line-oriented buffer (RAM terbatas) — hanya baris baru di-parse
        buffer.append(text)
        memChars += text.length
        // 2) disk log lengkap (tidak terpotong) + telemetri — DIPINDAH KE THREAD IO.
        //    Sebelumnya keduanya berjalan di Main thread setiap batch (40ms):
        //    RunLogger.flush() 1x + TelemetryStore.saveLocked() 2x = ±75 tulis-file
        //    per detik di UI thread → ANR di eMMC lambat (fix 2026-08-12).
        val lg = logger
        scope.launch(Dispatchers.IO) {
            lg?.append(stream, text)
            TelemetryStore.recordPeak("terminal_memory_peak_chars", memChars)
            TelemetryStore.recordPeak("terminal_log_bytes", lg?.bytesWritten ?: 0L)
        }
        logBytes = logger?.bytesWritten ?: 0L

        // auto-scroll bila user masih di bawah (stickToBottom)
        if (stickToBottom) {
            val target = (buffer.lastLineIndex() - buffer.startOffset + 1).toInt().coerceAtLeast(0)
            scrollJob?.cancel()
            scrollJob = scope.launch {
                delay(16)
                listState.scrollToItem(target)
            }
        }
    }

    // Output batching (SPEC-001 §14) — thread consumer menjaga urutan.
    val batcher = remember {
        OutputBatcher(
            onBatch = { stream, text ->
                scope.launch { appendToTerminal(stream, text) }
            }
        )
    }
    // FIX 2026-08-12: `batcher.start()` DULU dipanggil telanjang di badan komposisi.
    // Itu melanggar kontrak Compose (side-effect harus di dalam effect handler):
    // badan composable bisa dijalankan berkali-kali / dibatalkan, sehingga thread
    // batcher bisa lahir yatim. Sekarang siklus hidupnya terikat komposisi.
    DisposableEffect(batcher) {
        batcher.start()
        onDispose { batcher.close() }
    }

    // Deteksi posisi scroll: di bawah → ikut output; scroll naik → jangan ganggu
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.layoutInfo.totalItemsCount }
            .collect { (first, total) ->
                stickToBottom = total == 0 || first >= total - 8
            }
    }

    // Export full log (SAF) — dari DISK, bukan buffer (SPEC-001 §16)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            try {
                val src = logFilePath
                if (src != null && src.exists()) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        src.inputStream().use { it.copyTo(out) }
                    }
                    scope.launch { appendToTerminal("sys", "\n✅ Log diekspor (${src.name}).\n") }
                } else {
                    scope.launch { appendToTerminal("sys", "\n⚠️ Belum ada log (proses belum jalan).\n") }
                }
            } catch (e: Exception) {
                scope.launch { appendToTerminal("sys", "\n❌ Export gagal: ${e.message}\n") }
            }
        }
    }

    // Jalankan proses saat terminal dibuka (callback datang dari thread background)
    LaunchedEffect(filename) {
        Breadcrumb.log("TERMINAL_EFFECT", filename)
        withContext(Dispatchers.IO) { TelemetryStore.increment("terminal_runs") }
        val targetFile = File(filesDir, filename)
        if (!targetFile.exists()) {
            Breadcrumb.log("FILE_MISSING", filename)
            appendToTerminal("err", "\nError: File $filename not found!\n")
            startingPython = false
            return@LaunchedEffect
        }
        // RunLogger: disk-backed full log (SPEC-001 §16)
        val rl = RunLogger(File(Paths.runLogsDir(context), "$runId.log"))
        withContext(Dispatchers.IO) { rl.start("ZCODE run $runId — $filename") }
        logger = rl
        logFilePath = File(Paths.runLogsDir(context), "$runId.log")
        Breadcrumb.log("LOGGER_OK", runId)
        // F1.2 + F2.4: tampilkan status cold-start SEBELUM memanggil startInteractiveSession
        if (showPythonIndicator) appendToTerminal("sys", "\u2699 Menyalakan Python\u2026\n")
        withContext(Dispatchers.Main) { kotlinx.coroutines.yield() }
        Breadcrumb.log("SESSION_START_CALL")
        val activeSession = ExecutionEngine.startInteractiveSession(
            context = context,
            file = targetFile,
            runId = runId,
            logger = rl,
            onOutput = { stream, chunk -> batcher.append(stream, chunk) },
            onExit = { code ->
                startingPython = false
                Breadcrumb.log("SESSION_EXIT", "code=$code state=${sessionState.name}")
                scope.launch {
                    appendToTerminal(
                        "sys",
                        "\n\nProcess finished with exit code $code (state: ${sessionState.name})\n"
                    )
                }
                rl.writeExit(sessionState, code)
                rl.close()
            },
            onState = { st -> sessionState = st }
        )
        session = activeSession
        startingPython = false
        Breadcrumb.log("SESSION_READY", runId)
        // waitForExit TANPA hard timeout (SPEC-001 §17) — menunggu sampai selesai
        withContext(Dispatchers.IO) {
            activeSession.waitForExit()
        }
    }

    // Fokus otomatis. FIX 2026-08-12: requestFocus() DULU dipanggil langsung di
    // LaunchedEffect — effect berjalan setelah komposisi tapi SEBELUM node ter-place,
    // sehingga bisa melempar "FocusRequester is not initialized" (crash saat layar
    // terminal baru dibuka). withFrameNanos menunda sampai satu frame terlewati,
    // dan runCatching memastikan kegagalan fokus tidak pernah mematikan aplikasi.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
            .onFailure { Breadcrumb.log("FOCUS_FAIL", it.message ?: "") }
    }
    DisposableEffect(Unit) {
        onDispose {
            // batcher ditutup oleh DisposableEffect(batcher) di atas.
            session?.sendKill()
            logger?.close()
        }
    }

    val blinkTransition = rememberInfiniteTransition(label = "cursor")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "cursorAlpha"
    )

    // Terminal selalu Monospace (keputusan audit 2026-08).
    val resolvedFontFamily = FontFamily.Monospace
    val fontSizeSp = terminalFontSize.sp
    val freeStorageMb = ExecutionEngine.freeStorageBytes(filesDir).let {
        if (it < 0) -1 else it / 1024 / 1024
    }
    val lineHeightSp = (terminalFontSize + 4).sp

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
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        stateLabel(sessionState),
                        color = stateColor(sessionState),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        bottomBar = {
            Surface(color = Color(0xFF1E1F29)) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                TelemetryStore.increment("terminal_interrupts")
                                session?.sendCtrlC()
                                appendToTerminal("sys", "^C\nProcess Interrupted\n")
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
                        Button(
                            onClick = { exportLauncher.launch("zcode_${runId}.log") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Text("Export Log", fontSize = 11.sp, color = Color.White)
                        }
                    }
                    // Metrik (SPEC-001 Phase 0 #8): memori tampilan, log disk, storage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "mem ${memChars / 1024}KB · ${buffer.lineCount} baris · log ${logBytes / 1024}KB · $runId",
                            color = Color.Gray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                        Text(
                            if (freeStorageMb < 0) "storage ?" else "storage ${freeStorageMb}MB",
                            color = if (freeStorageMb in 1 until 100) Color(0xFFFFB000) else Color.Gray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
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
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 0.dp)
        ) {
            if (startingPython && showPythonIndicator) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
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

            // Virtualized renderer: LazyColumn hanya menyusun baris yang terlihat
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val relCount = buffer.lineCount
                // FIX 2026-08-12: `key` DIHAPUS (dulu `key = { buffer.startOffset + it }`).
                // `startOffset` bukan Compose state dan BERGESER saat buffer di-trim di
                // 10.000 baris; Compose mengevaluasi key secara lazy, sehingga dua item
                // bisa menghasilkan key sama → IllegalArgumentException "Key was used
                // multiple times" = force close. Key bersifat opsional untuk daftar
                // append-only seperti terminal; menghapusnya melenyapkan seluruh kelas bug ini.
                items(relCount) { rel ->
                    val abs = buffer.startOffset + rel
                    val lineText = buffer.get(abs) ?: ""
                    if (lineText.isNotEmpty()) {
                        Text(
                            text = ansiCache.render(abs, lineText),
                            fontFamily = resolvedFontFamily,
                            fontSize = fontSizeSp,
                            lineHeight = lineHeightSp
                        )
                    }
                }
                // current line (output yang belum diakhiri newline) + input + cursor.
                // FIX 2026-08-12: dulu ditulis `item(key = { -1L })` — yang terkirim
                // BUKAN angka -1L melainkan sebuah objek lambda (Function0). Compose
                // menyimpan key ke Bundle saat save-state; key bertipe lambda adalah
                // bom waktu. Key dihapus, konsisten dengan items() di atas.
                item {
                    Column {
                        val partial = buffer.currentLine()
                        if (partial.isNotEmpty()) {
                            Text(
                                text = ansiCache.render(buffer.totalLines, partial),
                                fontFamily = resolvedFontFamily,
                                fontSize = fontSizeSp,
                                lineHeight = lineHeightSp
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = inputVal.text,
                                color = Color.White,
                                fontFamily = resolvedFontFamily,
                                fontSize = fontSizeSp
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
                        Spacer(modifier = Modifier.height(8.dp))
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
                textStyle = TextStyle(fontSize = fontSizeSp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val line = inputVal.text
                        appendToTerminal("out", line + "\n")
                        session?.sendInput(line + "\n")
                        inputVal = TextFieldValue("")
                        focusRequester.requestFocus()
                    }
                )
            )
        }
    }
}

private fun stateLabel(s: SessionState): String = when (s) {
    SessionState.START -> "START"
    SessionState.RUNNING -> "RUNNING"
    SessionState.WAITING_FOR_INPUT -> "INPUT…"
    SessionState.INTERRUPTING -> "INTERRUPTING"
    SessionState.STOPPING -> "STOPPING"
    SessionState.EXITED -> "EXITED"
    SessionState.FAILED -> "FAILED"
}

private fun stateColor(s: SessionState): Color = when (s) {
    SessionState.EXITED -> Color(0xFF2E7D32)
    SessionState.FAILED -> Color(0xFFB3261E)
    SessionState.WAITING_FOR_INPUT -> Color(0xFFFFB000)
    SessionState.INTERRUPTING, SessionState.STOPPING -> Color(0xFFFFB000)
    else -> Color(0xFF8A9BB0)
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
