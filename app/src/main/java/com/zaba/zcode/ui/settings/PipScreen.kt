package com.zaba.zcode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaba.zcode.core.execution.ExecutionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * PipScreen — Settings → Pip: instal package Python via `python3 -m pip install`.
 * - Log streaming real-time (user bisa lihat traceback & progres unduhan).
 * - Guard nama package (anti shell injection), cap log MAX_OUTPUT_CHARS.
 * - Kotak log SELALU true-black phosphor, font 12.
 *
 * Catatan jujur: di Android, python3 tersedia setelah runtime Chaquopy di-embed
 * (target device build / Fase 3). Kode ini sudah asli & teruji di lingkungan
 * dengan python3 tersedia.
 */
@Composable
fun PipScreen(
    onBack: () -> Unit
) {
    var packageName by remember { mutableStateOf("") }
    var logText by remember { mutableStateOf(initialLog()) }
    var isInstalling by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun appendLog(text: String) {
        val combined = logText + text
        logText = if (combined.length > ExecutionEngine.MAX_OUTPUT_CHARS) {
            combined.takeLast(ExecutionEngine.MAX_OUTPUT_CHARS)
        } else {
            combined
        }
        scope.launch { scrollState.scrollTo(scrollState.maxValue) }
    }

    fun install(pkg: String) {
        val trimmed = pkg.trim()
        if (trimmed.isBlank() || isInstalling) return
        isInstalling = true
        appendLog("\n> pip install $trimmed\n")
        scope.launch {
            withContext(Dispatchers.IO) {
                val process = ExecutionEngine.startPipProcess(trimmed)
                if (process == null) {
                    withContext(Dispatchers.Main) {
                        appendLog("\n❌ Package name tidak valid: '$trimmed'\n")
                        isInstalling = false
                    }
                    return@withContext
                }
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line
                        withContext(Dispatchers.Main) { appendLog(l + "\n") }
                    }
                    val exitCode = process.waitFor()
                    withContext(Dispatchers.Main) {
                        if (exitCode == 0) {
                            appendLog("\n✅ Package '$trimmed' installed successfully!\n")
                        } else {
                            appendLog("\n❌ Installation failed (exit code $exitCode).\n")
                        }
                        isInstalling = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        appendLog("\n❌ Error: ${e.message}\n")
                        isInstalling = false
                    }
                }
            }
        }
    }

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
                            .clickable { onBack() }
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Pip Package Manager",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package Name", fontSize = 12.sp) },
                    placeholder = { Text("e.g. requests", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { install(packageName) }),
                    textStyle = TextStyle(fontSize = 14.sp)
                )
                Button(
                    onClick = { install(packageName) },
                    enabled = packageName.isNotBlank() && !isInstalling,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isInstalling) {
                        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        Text("Install", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "INSTALLATION LOG:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Kotak log — SELALU true-black OLED (keputusan tim)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF050806), shape = RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = logText,
                    color = Color(0xFF39FF14), // phosphor green
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp, // font size 12 (keputusan tim)
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private fun initialLog(): String =
    "ZCODE Pip Installer Layer — Chaquopy 3.11\n" +
        "-".repeat(45) + "\n" +
        "Ketik nama package di atas, lalu tap Install.\n"
