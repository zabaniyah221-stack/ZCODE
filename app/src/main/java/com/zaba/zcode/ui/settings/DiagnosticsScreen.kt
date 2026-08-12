package com.zaba.zcode.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaba.zcode.core.diagnostics.Breadcrumb
import com.zaba.zcode.core.diagnostics.CrashReporter
import com.zaba.zcode.core.files.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * DIAGNOSTICS — layar penuh, dibuka dari sidebar (build #3).
 *
 * Menggantikan kotak 220dp di halaman About yang praktis hanya memuat beberapa
 * baris. Alasannya bukan estetika: user adalah QA tester tunggal, memakai HP
 * tanpa PC, tanpa `adb logcat`. Layar ini adalah satu-satunya jendela ke dalam
 * aplikasi saat ada yang salah, jadi ia harus lapang, bisa digulir, bisa
 * diseleksi, disalin, dan dibagikan.
 *
 * Tab memfilter berdasarkan awalan langkah breadcrumb sehingga jejak yang
 * relevan bisa ditemukan tanpa menggulir ratusan baris:
 *   SEMUA · RUN (jalur ▶) · PAKET (Install Modules) · CRASH
 *
 * Catatan jujur yang ditampilkan apa adanya: cakupan breadcrumb belum meliputi
 * seluruh aplikasi. Menampilkan layar megah yang isinya setengah kosong tanpa
 * penjelasan akan menyesatkan, jadi keterbatasannya ditulis di layar.
 */
private enum class DiagTab(val label: String, val prefixes: List<String>) {
    SEMUA("SEMUA", emptyList()),
    RUN("RUN", listOf("FAB_", "SAVE_", "TERMINAL_", "SESSION_", "PY_", "PYTHON_", "SCRIPT_", "LOGGER_", "FILE_", "FOCUS_")),
    PAKET("PAKET", listOf("PKG_")),
    CRASH("CRASH", listOf("FATAL_", "PY_THROWABLE", "PY_EXCEPTION", "WEBVIEW_", "SHARE_"))
}

@Composable
fun DiagnosticsScreen(
    context: Context,
    onBack: () -> Unit
) {
    var tab by remember { mutableStateOf(DiagTab.SEMUA) }
    var crumbs by remember { mutableStateOf<List<String>>(emptyList()) }
    var crash by remember { mutableStateOf<String?>(null) }
    var runLogs by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Baca dari disk di IO — file bisa 128KB dan ini layar diagnostik,
        // paling tidak pantas kalau justru ia yang membuat UI tersendat.
        val loaded = withContext(Dispatchers.IO) {
            Triple(
                Breadcrumb.tail(2000).lines().filter { it.isNotBlank() },
                CrashReporter.lastReport(context),
                runCatching {
                    Paths.runLogsDir(context).listFiles()
                        ?.sortedByDescending { it.lastModified() }
                        ?.take(50) ?: emptyList()
                }.getOrDefault(emptyList())
            )
        }
        crumbs = loaded.first
        crash = loaded.second
        runLogs = loaded.third
        loading = false
    }

    val filtered = remember(tab, crumbs) {
        if (tab == DiagTab.SEMUA) crumbs
        else crumbs.filter { line -> tab.prefixes.any { line.contains(it) } }
    }

    val teksLengkap = remember(filtered, crash) {
        buildString {
            append("=== ZCODE DIAGNOSTICS (${tab.label}) ===\n")
            append("baris: ${filtered.size} dari ${crumbs.size}\n\n")
            filtered.forEach { append(it).append('\n') }
            append("\n=== CRASH TERAKHIR ===\n")
            append(crash ?: "(belum pernah crash Java)")
        }
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Column {
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
                        Text("DIAGNOSTICS", style = MaterialTheme.typography.titleSmall)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DiagTab.values().forEach { t ->
                            val aktif = t == tab
                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .background(
                                        if (aktif) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { tab = t }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    t.label,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (aktif) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = Color(0xFF1E1F29)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${filtered.size} baris · ${runLogs.size} log run",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Salin",
                            color = Color(0xFF8A9BB0),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as? android.content.ClipboardManager
                                    cm?.setPrimaryClip(
                                        android.content.ClipData.newPlainText("ZCODE diagnostics", teksLengkap)
                                    )
                                    android.widget.Toast.makeText(
                                        context, "Diagnostik disalin", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Text(
                            "Bagikan",
                            color = Color(0xFF8A9BB0),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable {
                                    runCatching {
                                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, "ZCODE Diagnostics")
                                            putExtra(android.content.Intent.EXTRA_TEXT, teksLengkap)
                                        }
                                        context.startActivity(
                                            android.content.Intent.createChooser(send, "Bagikan diagnostik")
                                        )
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
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
                .background(Color(0xFF050806))
                .padding(horizontal = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            if (loading) {
                Text("Memuat…", color = Color.Gray, fontSize = 12.sp)
            } else {
                SelectionContainer {
                    Column {
                        if (filtered.isEmpty()) {
                            Text(
                                "Belum ada jejak untuk tab ini.",
                                color = Color(0xFF8A9BB0),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        filtered.forEach { line ->
                            Text(
                                line,
                                color = warnaBaris(line),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "=== CRASH TERAKHIR ===",
                            color = Color(0xFFFFB000),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            crash ?: "(belum pernah crash Java — kalau ZCODE tetap menutup " +
                                "sendiri, penyebabnya di luar JVM: lihat baris terakhir jejak di atas)",
                            color = if (crash != null) Color(0xFFFF6B6B) else Color.Gray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (runLogs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "=== LOG RUN TERSIMPAN (${runLogs.size}) ===",
                                color = Color(0xFF39FF14),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            runLogs.forEach { f ->
                                Text(
                                    "  ${f.name}  ${f.length() / 1024}KB",
                                    color = Color(0xFF8A9BB0),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "CATATAN JUJUR: jejak baru meliputi jalur Run dan Install Modules. " +
                                "Aktivitas file, Library, dan Settings BELUM dicatat — itu " +
                                "pekerjaan berikutnya, bukan tanda tidak ada yang terjadi.",
                            color = Color(0xFF6B7280),
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

private fun warnaBaris(line: String): Color = when {
    line.contains("FATAL_") || line.contains("_FAIL") || line.contains("THROWABLE") -> Color(0xFFFF6B6B)
    line.contains("_OK") || line.contains("SUCCESS") -> Color(0xFF39FF14)
    line.contains("PKG_") -> Color(0xFF7DD3FC)
    else -> Color(0xFF9AE6B4)
}
