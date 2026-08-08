package com.zaba.zcode.ui.terminal

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * TerminalScreen — layer Terminal PTY full-screen (pindah layer, bukan panel).
 * - Ketik langsung di terminal: TextField transparan 1dp mengikat keyboard Android,
 *   Enter mengirim baris ke stdin proses python (tanpa tombol Send).
 * - Ctrl+C asli (SIGINT) lewat tombol merah di toolbar bawah.
 * - Back di pojok kiri atas untuk kembali ke Editor.
 * - Output di-cap MAX_OUTPUT_CHARS agar script `while True: print(...)` tidak membludak.
 */
@Composable
fun TerminalScreen(
    filename: String,
    filesDir: File,
    onBack: () -> Unit
) {
    var terminalText by remember { mutableStateOf("ZCODE Terminal — Running $filename\n" + "-".repeat(40) + "\n") }
    var inputVal by remember { mutableStateOf(TextFieldValue("")) }
    var session by remember { mutableStateOf<ExecutionEngine.InteractiveSession?>(null) }
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun append(text: String) {
        val combined = terminalText + text
        terminalText = if (combined.length > ExecutionEngine.MAX_OUTPUT_CHARS) {
            "\n…[output truncated: >${ExecutionEngine.MAX_OUTPUT_CHARS} chars]…\n" +
                combined.takeLast(ExecutionEngine.MAX_OUTPUT_CHARS)
        } else {
            combined
        }
        scope.launch {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // Jalankan proses python saat terminal dibuka
    LaunchedEffect(filename) {
        val targetFile = File(filesDir, filename)
        if (!targetFile.exists()) {
            append("\nError: File $filename not found!\n")
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val activeSession = ExecutionEngine.startInteractiveSession(targetFile)
                session = activeSession
                val reader = BufferedReader(InputStreamReader(activeSession.stdout, Charsets.UTF_8))
                val batch = StringBuilder()
                var charCode: Int
                while (reader.read().also { charCode = it } != -1) {
                    batch.append(charCode.toChar())
                    if (batch.length >= 256) {
                        val chunk = batch.toString()
                        batch.clear()
                        withContext(Dispatchers.Main) { append(chunk) }
                    }
                }
                if (batch.isNotEmpty()) {
                    val chunk = batch.toString()
                    withContext(Dispatchers.Main) { append(chunk) }
                }
                val exitCode = activeSession.process.waitFor()
                withContext(Dispatchers.Main) {
                    append("\n\nProcess finished with exit code $exitCode\n")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    append("\nExecution error: ${e.message}\n")
                }
            }
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
            Surface(color = Color(0xFF1E1F29)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            session?.sendCtrlC()
                            append("^C\nProcess Interrupted\n")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Ctrl+C", fontSize = 12.sp, color = Color.White)
                    }
                    Text(
                        "Tap terminal untuk mengetik langsung",
                        color = Color.Gray,
                        fontSize = 11.sp
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
                Column {
                    Text(
                        text = terminalText,
                        color = Color(0xFF39FF14), // phosphor green
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
}
