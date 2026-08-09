package com.zaba.zcode.ui.workbench

import android.webkit.WebView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.zaba.zcode.R
import com.zaba.zcode.WorkspaceViewModel
import com.zaba.zcode.core.plugins.PluginInfo
import com.zaba.zcode.core.plugins.PluginRegistry
import com.zaba.zcode.core.plugins.SnippetLibrary
import com.zaba.zcode.core.plugins.TodoExtractor
import com.zaba.zcode.core.plugins.TodoItem
import com.zaba.zcode.ui.editor.EditorScreen
import com.zaba.zcode.ui.editor.escapeJavaScriptString
import com.zaba.zcode.ui.theme.ZcodeThemeType
import kotlinx.coroutines.launch

/**
 * WorkbenchScreen — workspace utama ZCODE (Fase 1 + Fase 2).
 *
 * Tata letak (dari atas ke bawah):
 *   Topbar (judul file di samping ≡, tanpa subtitle) → Tab bar (tanpa underline,
 *   long-press = close) → banner syntax → Editor CodeMirror 6 OLED → QuickTools/Symbol
 *   bar (opsional, toggle di drawer) → FAB ▶.
 *
 * Drawer: Navigasi (Pip/About), Code Transforms (5 plugin), Editor (toggle
 * Symbol bar), Theme (3 tema), Files Manager (rename/delete dialog konfirmasi).
 *
 * Anti-regresi:
 * - "≡" = tiga garis (ikon menu teks — jangan ganti dengan kata lain)
 * - "+" tambah file, "🔍" Command Palette di topbar
 * - Semua tombol ter-wire ke WorkspaceViewModel / onRun / navigate ke layer output
 */

private val OledBlack = Color(0xFF050806)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchScreen(
    vm: WorkspaceViewModel,
    onRun: (String) -> Unit,
    onNavigateToPip: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // state dialog
    var fileToRename by remember { mutableStateOf<String?>(null) }
    var renameNewName by remember { mutableStateOf("") }
    var fileToDelete by remember { mutableStateOf<String?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var showPalette by remember { mutableStateOf(false) }
    // Batch anti-sepi: drawer PLUGINS expandable, dialog TODO, dialog Snippets
    var pluginsExpanded by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    var showTodoResults by remember { mutableStateOf(false) }
    var todoItems by remember { mutableStateOf<List<TodoItem>>(emptyList()) }

    val context = LocalContext.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun pushCode() {
        webViewRef.value?.evaluateJavascript("setCode(${escapeJavaScriptString(vm.activeCode)});", null)
    }

    fun gotoLine(n: Int) {
        webViewRef.value?.evaluateJavascript("gotoLine($n);", null)
    }

    // Eksekusi satu plugin (drawer tap & palette — satu logika, dua pintu).
    // Semantik S2: tap = eksekusi manual; toggle = ketersediaan/behavior.
    fun pluginAction(plugin: PluginInfo): () -> Unit = {
        when (plugin.id) {
            "beautifier" -> {
                vm.beautifyActiveFile()
                pushCode()
            }
            "optimize_imports" -> {
                vm.optimizeActiveImports()
                pushCode()
            }
            "duplicate_line" -> {
                webViewRef.value?.evaluateJavascript("duplicateRows();", null)
            }
            "toggle_comment" -> {
                webViewRef.value?.evaluateJavascript("toggleCommentLines();", null)
            }
            "todo_extractor" -> {
                todoItems = TodoExtractor.extract(vm.activeCode)
                showTodoResults = true
            }
            "snippets" -> {
                showSnippets = true
            }
            "auto_trim_on_run" -> {
                // BEHAVIOR: tidak ada eksekusi manual — jelaskan statusnya
                toast(
                    if (vm.isPluginEnabled(plugin.id))
                        "Auto Trim aktif — spasi akhir dibuang otomatis saat Run"
                    else "Auto Trim nonaktif"
                )
            }
            else -> plugin.pythonId?.let { pid ->
                toast("Menjalankan ${plugin.name}…")
                vm.runPythonPlugin(pid) { ok, report ->
                    if (ok) pushCode()
                    val msg = if (ok) report.lines().take(3).joinToString("\n") else "❌ $report"
                    toast(msg)
                }
            } ?: toast("Plugin '${plugin.id}' belum punya handler")
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(300.dp)
            ) {
                // Header drawer — "ZCODE" + logo app baru di kanan (tanpa subtitle, permintaan user)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ZCODE",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Image(
                        painter = painterResource(id = R.drawable.zcode_logo),
                        contentDescription = "Logo ZCODE",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }

                DrawerSectionTitle("NAVIGATION")
                DrawerItem("Pip Package Manager") {
                    scope.launch { drawerState.close() }
                    onNavigateToPip()
                }
                DrawerItem("About & Contribute") {
                    scope.launch { drawerState.close() }
                    onNavigateToAbout()
                }

                Divider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 6.dp))

                // ---------- PLUGINS (batch anti-sepi S1/S2) ----------
                // Header tap → kotak berborder expand ke bawah (±3 baris visible,
                // scrollable). Tap badan baris = eksekusi; Switch = aktif/nonaktif.
                val activePluginCount = PluginRegistry.plugins.count { vm.isPluginEnabled(it.id) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pluginsExpanded = !pluginsExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🧩 PLUGINS ($activePluginCount aktif)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (pluginsExpanded) "▾" else "▸",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                AnimatedVisibility(visible = pluginsExpanded) {
                    LazyColumn(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .heightIn(max = 176.dp) // ≈3 baris — sisanya scroll di dalam
                            .border(1.dp, Color(0xFF1B4D2E), RoundedCornerShape(10.dp))
                            .padding(vertical = 2.dp)
                    ) {
                        items(PluginRegistry.plugins) { plugin ->
                            PluginRow(
                                plugin = plugin,
                                enabled = vm.isPluginEnabled(plugin.id),
                                onToggle = { vm.setPluginEnabled(plugin.id, !vm.isPluginEnabled(plugin.id)) },
                                onRun = { pluginAction(plugin)() }
                            )
                        }
                    }
                }
                DrawerItem("Clear All Drafts & Files") {
                    scope.launch { drawerState.close() }
                    confirmClearAll = true
                }

                Divider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 6.dp))

                DrawerSectionTitle("EDITOR")
                // Toggle Symbol bar (QuickTools) — wiring ke VM, persist di SharedPreferences
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.setSymbolBar(!vm.symbolBarEnabled) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Symbol bar",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = vm.symbolBarEnabled,
                        onCheckedChange = { vm.setSymbolBar(it) }
                    )
                }

                Divider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 6.dp))

                DrawerSectionTitle("SELECT THEME")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZcodeThemeType.values().forEach { tType ->
                        val isSelected = vm.themeType == tType
                        Button(
                            onClick = { vm.themeType = tType },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else Color.LightGray
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(tType.name.replace('_', ' '), fontSize = 10.sp)
                        }
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 6.dp))

                DrawerSectionTitle("FILES MANAGER")
                val files = vm.getAllFiles()
                if (files.isEmpty()) {
                    Text(
                        "Belum ada file .py — tap + di topbar",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = Color.Gray
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        files.forEach { fileMap ->
                            val name = fileMap["name"] as String
                            val size = fileMap["size"] as? Long ?: 0L
                            val isActive = vm.activeFile == name
                            FileRow(
                                name = name,
                                sizeKb = size / 1024f,
                                isActive = isActive,
                                onClick = {
                                    vm.selectFile(name)
                                    scope.launch { drawerState.close() }
                                    pushCode()
                                },
                                onRename = {
                                    fileToRename = name
                                    renameNewName = name
                                },
                                onDelete = { fileToDelete = name }
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = { WorkbenchTopBar(vm, webViewRef, onOpenDrawer = { scope.launch { drawerState.open() } }, onOpenPalette = { showPalette = true }) },
            floatingActionButton = {
                // ▶ Run → onRun(filename) → MainActivity navigate ke layer output full-screen (pindah layer)
                // padding bawah menyesuaikan: 52dp saat symbol bar tampil agar tidak tertutup
                FloatingActionButton(
                    onClick = {
                        // BEHAVIOR auto_trim_on_run berjalan di sini (F5)
                        vm.applyAutoTrimIfEnabled()
                        pushCode()
                        val active = vm.activeFile ?: "main.py"
                        onRun(active)
                    },
                    // S6: FAB syntax-aware — MERAH saat ada error syntax tapi TETAP
                    // BISA RUN (warn-only never block; lesson RUN-mati Zabacode)
                    containerColor = if (vm.syntaxError != null) Color(0xFFFF4B4B)
                        else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = if (vm.symbolBarEnabled) 52.dp else 8.dp)
                ) {
                    Text("▶", fontSize = 20.sp)
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(OledBlack)
            ) {
                // Tab bar — multi-file, long-press untuk close (tanpa tombol ×)
                // Fix anti double-trigger: seleksi & close ditangani SATU combinedClickable di
                // wrapper Box; Tab(onClick = {}) no-op sehingga tidak ada event ganda.
                ScrollableTabRow(
                    selectedTabIndex = (vm.openedFiles.indexOf(vm.activeFile ?: "").coerceAtLeast(0)),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 8.dp,
                    divider = { Divider(color = Color.White.copy(alpha = 0.05f)) },
                    // tanpa underline indikator (permintaan user): tab aktif cukup dibedakan warna teks
                    indicator = {}
                ) {
                    vm.openedFiles.forEach { filename ->
                        val isActive = vm.activeFile == filename
                        Tab(
                            selected = isActive,
                            onClick = { /* no-op — seleksi ditangani combinedClickable */ },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = Color.LightGray
                        ) {
                            Box(
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            vm.selectFile(filename)
                                            pushCode()
                                        },
                                        onLongClick = {
                                            vm.closeFile(filename)
                                            pushCode()
                                        }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = filename,
                                    fontSize = 12.sp, // font size 12 (keputusan tim)
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Banner syntax warning — soft, membulat, tidak mengganggu
                vm.syntaxError?.let { err ->
                    Surface(
                        color = Color(0x1AFF4B4B),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "⚠ Syntax: $err",
                            fontSize = 11.sp,
                            color = Color(0xFFFFB4AB),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Editor (CodeMirror 6 WebView)
                Box(modifier = Modifier.weight(1f)) {
                    EditorScreen(
                        code = vm.activeCode,
                        onCodeChange = { vm.updateCode(it) },
                        webViewRef = webViewRef
                    )
                }

                // QuickTools / symbol bar — bisa dimatikan user lewat drawer (EDITOR → Symbol bar)
                if (vm.symbolBarEnabled) {
                    QuickToolsBar(webViewRef)
                }
            }
        }
    }

    // ---------- Dialog: Rename ----------
    fileToRename?.let { oldName ->
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text("Rename File", fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = renameNewName,
                    onValueChange = { renameNewName = it },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = vm.renameFile(oldName, renameNewName)
                    fileToRename = null
                    if (ok) pushCode()
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { fileToRename = null }) { Text("Cancel") }
            }
        )
    }

    // ---------- Dialog: Delete ----------
    fileToDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File?", fontSize = 16.sp) },
            text = { Text("File \"$name\" akan dihapus permanen. Lanjutkan?") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = vm.deleteFile(name)
                    fileToDelete = null
                    if (ok) pushCode()
                }) { Text("Delete", color = Color(0xFFFFB4AB)) }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // ---------- Dialog: Clear All ----------
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear All Drafts & Files?", fontSize = 16.sp) },
            text = { Text("Semua file .py di workspace akan dihapus. Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    vm.clearAllDrafts()
                    pushCode()
                }) { Text("Clear All", color = Color(0xFFFFB4AB)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            }
        )
    }

    // ---------- Dialog: TODO Extractor results (batch anti-sepi) ----------
    if (showTodoResults) {
        TodoResultsDialog(
            items = todoItems,
            onDismiss = { showTodoResults = false },
            onJump = { line ->
                showTodoResults = false
                gotoLine(line)
            }
        )
    }

    // ---------- Dialog: Snippet Pack (batch anti-sepi S5) ----------
    if (showSnippets) {
        SnippetsDialog(
            onDismiss = { showSnippets = false },
            onPick = { snippet ->
                showSnippets = false
                val name = vm.createFileFromSnippet(snippet)
                pushCode()
                toast("Snippet jadi file: $name")
            }
        )
    }

    // ---------- Dialog: Command Palette & Quick Open ----------
    if (showPalette) {
        // Tipe eksplisit: tanpa ini, lambda `webViewRef.value?.evaluateJavascript(...)`
        // mengembalikan Unit? sehingga list ter-infer jadi Pair<String, () -> Unit?>
        // dan tidak cocok dengan parameter `commands: List<Pair<String, () -> Unit>>`.
        // Batch anti-sepi S2: palette hanya memuat plugin ACTION yang ENABLED.
        // Eksekusi via pluginAction() — satu logika dengan drawer PLUGINS.
        val paletteCommands: List<Pair<String, () -> Unit>> =
            PluginRegistry.enabledActions { vm.isPluginEnabled(it) }
                .map { p -> p.name to pluginAction(p) } + listOf(
                // Migrasi CM6: panel search @codemirror/search via bridge
                "Find in File (panel search)" to {
                    webViewRef.value?.evaluateJavascript("openFind();", null)
                },
                "Open Pip Manager" to { onNavigateToPip() },
                "Open About" to { onNavigateToAbout() }
            )
        PaletteDialog(
            files = vm.getAllFiles().map { it["name"] as String },
            activeCode = vm.activeCode,
            onGotoLine = { n -> gotoLine(n) },
            commands = paletteCommands,
            onDismiss = { showPalette = false },
            onOpenFile = { name ->
                showPalette = false
                vm.selectFile(name)
                pushCode()
            },
            onRunCommand = { action ->
                showPalette = false
                action()
            }
        )
    }
}

// =====================================================================
// Topbar — soft, theme-aware, pembatas tidak sharp
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkbenchTopBar(
    vm: WorkspaceViewModel,
    webViewRef: androidx.compose.runtime.MutableState<WebView?>,
    onOpenDrawer: () -> Unit,
    onOpenPalette: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ≡ tiga garis — buka drawer
            Text(
                "≡",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable(onClick = onOpenDrawer)
                    .padding(10.dp)
            )

            // Judul file persis di samping ≡ (tanpa subtitle — permintaan user)
            Text(
                vm.activeFile ?: "No Active File",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 🔍 Command Palette & Quick Open (akses jempol di topbar)
                Text(
                    "🔍",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable(onClick = onOpenPalette)
                        .padding(10.dp)
                )
                // + tambah file (untitled_N.py)
                Text(
                    "+",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable {
                            vm.createNewFile()
                            webViewRef.value?.evaluateJavascript("setCode(${escapeJavaScriptString(vm.activeCode)});", null)
                        }
                        .padding(10.dp)
                )
            }
        }
    }
}

// =====================================================================
// QuickTools — chips bulat, scroll horizontal, semua ter-wire
// =====================================================================

@Composable
private fun QuickToolsBar(webViewRef: androidx.compose.runtime.MutableState<WebView?>) {
    val tools = listOf("Tab", ":", ";", "'", "#", "(", ")", "[", "]", "def", "return", "import")
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tools.forEach { symbol ->
                val insertion = if (symbol == "Tab") "    " else symbol
                AssistChip(
                    onClick = {
                        webViewRef.value?.evaluateJavascript("insertText('$insertion');", null)
                    },
                    label = { Text(symbol, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(50)
                )
            }
        }
    }
}

// =====================================================================
// Command Palette & Quick Open
// =====================================================================

@Composable
private fun PaletteDialog(
    files: List<String>,
    activeCode: String,
    onGotoLine: (Int) -> Unit,
    commands: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    onOpenFile: (String) -> Unit,
    onRunCommand: (() -> Unit) -> Unit
) {
    var query by remember { mutableStateOf("") }
    // Mode default mengikuti chips; prefix power-user tetap hidup:
    // ">" = perintah, ":" = goto line (S3)
    var chipMode by remember { mutableStateOf("file") }
    val isCommandMode = query.startsWith(">")
    val isLinePrefix = query.startsWith(":")
    val mode = if (isCommandMode) "command" else if (isLinePrefix) "line" else chipMode
    val filter = when {
        isCommandMode -> query.drop(1).trim()
        isLinePrefix -> query.drop(1).trim()
        else -> query.trim()
    }

    val fileResults = if (mode == "file") files.filter { it.contains(filter, ignoreCase = true) } else emptyList()
    val commandResults = if (mode == "command") commands.filter { it.first.contains(filter, ignoreCase = true) } else emptyList()
    // Mode Find (ala Pydroid): cari kata di file aktif, maks 100 hasil
    val findResults: List<Pair<Int, String>> = if (mode == "find" && filter.isNotBlank()) {
        val out = mutableListOf<Pair<Int, String>>()
        activeCode.split('\n').forEachIndexed { idx, line ->
            if (out.size >= 100) return@forEachIndexed
            if (line.contains(filter, ignoreCase = true)) out.add((idx + 1) to line.trim())
        }
        out
    } else emptyList()
    val lineTarget = if (mode == "line") filter.toIntOrNull() else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                // Chips mode (S3) — discoverable ala Pydroid, tanpa perlu hafal prefix
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaletteModeChip("📁 File", mode == "file") { chipMode = "file" }
                    PaletteModeChip("🔎 Find", mode == "find") { chipMode = "find" }
                    PaletteModeChip("#️ Line", mode == "line") { chipMode = "line" }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            when (mode) {
                                "command" -> "Perintah… (> Beautifier)"
                                "find" -> "Cari kata di file aktif…"
                                "line" -> "Nomor baris… (mis. 42)"
                                else -> "Cari file… (> perintah, : baris)"
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (mode) {
                    "command" -> {
                        commandResults.forEach { (label, action) ->
                            Text(
                                label,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRunCommand(action) }
                                    .padding(horizontal = 4.dp, vertical = 10.dp)
                            )
                        }
                        if (commandResults.isEmpty()) {
                            Text("Tidak ada perintah cocok", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    "find" -> {
                        findResults.forEach { (line, ctx) ->
                            Text(
                                "L$line: ${ctx.take(80)}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onGotoLine(line)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                            )
                        }
                        if (filter.isBlank()) {
                            Text("Ketik kata untuk dicari di file aktif", fontSize = 12.sp, color = Color.Gray)
                        } else if (findResults.isEmpty()) {
                            Text("Tidak ditemukan: $filter", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    "line" -> {
                        if (lineTarget != null && lineTarget >= 1) {
                            Text(
                                "→ Lompat ke baris $lineTarget",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onGotoLine(lineTarget)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 10.dp)
                            )
                        } else {
                            Text("Ketik nomor baris (angka ≥ 1)", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    else -> {
                        fileResults.forEach { name ->
                            Text(
                                name,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFile(name) }
                                    .padding(horizontal = 4.dp, vertical = 10.dp)
                            )
                        }
                        if (fileResults.isEmpty()) {
                            Text("Tidak ada file cocok", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun PaletteModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(50)
    )
}

// =====================================================================
// Komponen PLUGINS drawer (batch anti-sepi S1/S2)
// =====================================================================

@Composable
private fun PluginRow(
    plugin: PluginInfo,
    enabled: Boolean,
    onToggle: () -> Unit,
    onRun: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRun)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                plugin.name,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                plugin.description,
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Toggle = ketersediaan/behavior (S2); tap badan baris = eksekusi
        Switch(checked = enabled, onCheckedChange = { onToggle() })
    }
    Divider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.padding(horizontal = 10.dp))
}

@Composable
private fun TodoResultsDialog(
    items: List<TodoItem>,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✅ TODO / FIXME / HACK (${items.size})", fontSize = 14.sp) },
        text = {
            if (items.isEmpty()) {
                Text("Tidak ada penanda TODO/FIXME/HACK di file aktif 🎉", fontSize = 12.sp, color = Color.Gray)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    items(items) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJump(item.line) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "L${item.line}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                "[${item.tag}] ${item.text.ifBlank { "(tanpa teks)" }}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Divider(color = Color.White.copy(alpha = 0.04f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun SnippetsDialog(
    onDismiss: () -> Unit,
    onPick: (com.zaba.zcode.core.plugins.Snippet) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📜 Snippet Pack — pilih template", fontSize = 14.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SnippetLibrary.snippets.forEach { snippet ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(snippet) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(snippet.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Text(snippet.description, fontSize = 11.sp, color = Color.Gray)
                    }
                    Divider(color = Color.White.copy(alpha = 0.04f))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

// =====================================================================
// Komponen kecil drawer
// =====================================================================

@Composable
private fun DrawerSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun DrawerItem(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun FileRow(
    name: String,
    sizeKb: Float,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    else Color.Transparent
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format("%.1f KB", sizeKb),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
            Row {
                Text(
                    "Rename",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    modifier = Modifier
                        .clickable(onClick = onRename)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
                Text(
                    "Delete",
                    fontSize = 11.sp,
                    color = Color(0xFFFFB4AB),
                    modifier = Modifier
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
        Divider(
            color = Color.White.copy(alpha = 0.04f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
