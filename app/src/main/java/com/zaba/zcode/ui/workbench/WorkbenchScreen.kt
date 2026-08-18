package com.zaba.zcode.ui.workbench

import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.zaba.zcode.ui.components.ZIcons
import com.zaba.zcode.ui.editor.EditorScreen
import com.zaba.zcode.ui.editor.escapeJavaScriptString
import com.zaba.zcode.ui.theme.ZcodeThemeType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * WorkbenchScreen — workspace utama ZCODE (Fase 1 + Fase 2).
 *
 * Tata letak (dari atas ke bawah):
 *   Topbar (judul file, drawer swipe-only — ≡ dihapus audit 2026-08) → Tab bar (tanpa underline,
 *   long-press = close) → banner syntax → Editor CodeMirror 6 OLED → QuickTools/Symbol
 *   bar (opsional, toggle di drawer) → FAB ▶.
 *
 * Drawer (redesign 2026-08, hasil diskusi user — lihat docs/RENCANA_UPDATE_2026_08.md):
 * INSTALL MODULES → SAMPLES (halaman baru) → TOOLS expandable (plugin + Symbol bar +
 * THEME cycle satu tombol + Clear All) → About & Contribute (paling bawah).
 * Seksi NAVIGATION / EDITOR / SELECT THEME / FILES MANAGER dibuang total.
 *
 * Topbar (DRAWER-SWIPE-ONLY, audit 2026-08): nama file (tap → dialog Rename/Delete) |
 * folder → menu file Open/Save/Save as | kaca pembesar polos (palette: Line &
 * Find) | plus. Sidebar dibuka via swipe kiri (ModalNavigationDrawer); ikon ≡
 * tiga garis dihapus atas permintaan user.
 * Ikon vektor polos ZIcons (di-tint mengikuti tema) menggantikan emoji topbar.
 *
 * Anti-regresi:
 * - Drawer swipe-only: marker "DRAWER-SWIPE-ONLY" wajib ada (digrep tools/check.sh)
 * - Semua tombol ter-wire ke WorkspaceViewModel / onRun / navigate ke layer output
 * - JANGAN menulis glob bintang mentah (mis. MIME tipe teks-slash-bintang) apa
 *   adanya di dalam block comment mana pun — Kotlin block comment BERSARANG,
 *   pembuka nested nyasar = "Unclosed comment" di compiler (insiden CI
 *   2026-08-09 baris ini; dijaga tools/kotlin_sanity_check.py via check.sh).
 */

private val OledBlack = Color(0xFF050806)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchScreen(
    vm: WorkspaceViewModel,
    onRun: (String) -> Unit,
    onNavigateToPip: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSamples: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // EASTER EGG (audit 2026-08): tap logo {Z} 7x → Frieren bawa papan ngeledek
    // 2.8 dtk lalu fade out & wordmark+logo balik sendiri. State sengaja TIDAK
    // persist — easter egg yang cuma sekali itu sedih.
    var eggTaps by remember { mutableStateOf(0) }
    var lastEggTap by remember { mutableStateOf(0L) }
    var showEgg by remember { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var showTerminalOverlay by rememberSaveable { mutableStateOf(false) }

    // state dialog
    var fileToRename by remember { mutableStateOf<String?>(null) }
    var renameNewName by remember { mutableStateOf("") }
    var fileToDelete by remember { mutableStateOf<String?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var showPalette by remember { mutableStateOf(false) }
    // A5 v1.0.19: Reference Card (tombol "?" di terowongan symbol bar)
    var showReferenceCard by remember { mutableStateOf(false) }
    // Tap nama file aktif di topbar → dialog Rename/Delete (pengganti FILES MANAGER)
    var showFileActions by remember { mutableStateOf(false) }
    // Redesign 2026-08: drawer TOOLS expandable (plugin + settings satu kotak)
    var toolsExpanded by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    var showTodoResults by remember { mutableStateOf(false) }
    var todoItems by remember { mutableStateOf<List<TodoItem>>(emptyList()) }
    var showOutlineResults by remember { mutableStateOf(false) }
    var outlineSymbols by remember { mutableStateOf<List<OutlineItem>>(emptyList()) }
    var showGoToDefinitionDialog by remember { mutableStateOf(false) }
    var goToDefinitionQuery by remember { mutableStateOf("") }
    var showRenameSymbolDialog by remember { mutableStateOf(false) }
    var renameOldName by remember { mutableStateOf("") }
    var renameNewSymbolName by remember { mutableStateOf("") }
    // F1.9: State cycle Change Case (upper → lower → title → upper…)
    var changeCaseMode by remember { mutableStateOf("upper") }

    val context = LocalContext.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // F1.x (PERF): aksi dari drawer WAJIB menunggu drawer selesai menutup dulu.
    // Tanpa ini, animasi drawer (≈250ms default Material3) tabrakan dengan compose layar baru /
    // buka dialog di frame yang sama → terasa lag/jeda di HP ampas. Menutup dulu
    // lalu bertindak membuat transisi ke layer baru terasa satu gerakan mulus.
    // Duration diturunkan dari default 250ms → 150ms: cukup cepat terasa responsif,
    // tapi tidak terlalu cepat sampai terasa kasar/snap di Infinix ARMv7.
    fun closeDrawerThen(action: suspend () -> Unit) {
        scope.launch {
            drawerState.animateTo(DrawerValue.Closed, tween(durationMillis = 150))
            action()
        }
    }

    fun pushCode() {
        webViewRef.value?.evaluateJavascript("setCode(${escapeJavaScriptString(vm.activeCode)});", null)
    }

    // 📁 Ikon folder topbar → file manager HP (SAF), filter text/* (keputusan user).
    // File yang dipilih di-IMPORT COPY ke workspace oleh VM (file asli tidak diubah);
    // uri null = user batal memilih → tidak ada toast mengganggu.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (ok, msg) = vm.importExternalFile(uri)
        toast(msg)
        if (ok) pushCode()
    }

    fun gotoLine(n: Int) {
        webViewRef.value?.evaluateJavascript("gotoLine($n);", null)
    }

    // A3 v1.0.19: konsumsi pendingGotoLine dari traceback tap (terminal →
    // navigateUp → sini). LaunchedEffect pada nilai pending; delay singkat
    // memberi WebView waktu selesai load (kelas BUG H about:blank) — best
    // effort: kalau tetap kalah cepat, user tinggal tap lagi di terminal.
    androidx.compose.runtime.LaunchedEffect(vm.pendingGotoLine) {
        val line = vm.pendingGotoLine
        if (line > 0) {
            kotlinx.coroutines.delay(350)
            gotoLine(line)
            vm.pendingGotoLine = 0
        }
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
            "outline_generator" -> {
                toast("Membaca Outline/Simbol…")
                vm.runPythonPlugin("outline_generator") { ok, report ->
                    if (ok) {
                        outlineSymbols = report.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                            val parts = line.split(":")
                            if (parts.size >= 3) {
                                val type = parts[0]
                                val name = parts[1]
                                val lineNum = parts[2].toIntOrNull() ?: 1
                                OutlineItem(type, name, lineNum)
                            } else null
                        }
                        showOutlineResults = true
                    } else {
                        toast("Gagal membaca outline: $report")
                    }
                }
            }
            "go_to_definition" -> {
                showGoToDefinitionDialog = true
            }
            "rename_symbol" -> {
                showRenameSymbolDialog = true
            }
            "snippets" -> {
                showSnippets = true
            }
            // F1.9: Transform teks kecil (Kotlin/JS murni, tanpa pip)
            "sort_lines" -> {
                webViewRef.value?.evaluateJavascript("sortLines();", null)
                pushCode()
            }
            "change_case" -> {
                // F1.9: Cycle upper → lower → title → upper…
                val mode = changeCaseMode
                webViewRef.value?.evaluateJavascript("changeCase('$mode');", null)
                pushCode()
                // Cycle ke mode berikutnya
                changeCaseMode = when (mode) {
                    "upper" -> "lower"
                    "lower" -> "title"
                    else -> "upper"
                }
                toast("Change Case: ${mode.uppercase()}")
            }
            "trim_now" -> {
                webViewRef.value?.evaluateJavascript("trimNow();", null)
                pushCode()
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

    Box(modifier = Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(300.dp)
            ) {
                // A0 v1.0.19 (laporan user 2026-08-18, screenshot landscape):
                // seluruh isi drawer dibungkus SATU kolom scrollable. Tanpa ini,
                // di landscape Infinix (tinggi ±360dp) item bawah (TOOLS
                // expanded, SETTINGS, About) BUKAN sekadar terpotong — tak
                // terjangkau sama sekali karena Column biasa tidak scroll.
                // Scroll sumbu Y tidak bentrok dgn gesture swipe-close drawer
                // (sumbu X). Portrait: konten muat → scroll tak aktif → layout
                // identik dengan sebelum fix.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Header drawer — "ZCODE" + logo app baru di kanan (tanpa subtitle, permintaan user)
                // EASTER EGG: tap logo 7x (jeda <800ms) → header melar mulus,
                // wordmark+logo crossfade ke Frieren bawa papan 2.8 dtk, fade out,
                // lalu wordmark+logo balik sendiri (coroutine delay, tanpa persist).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .animateContentSize(animationSpec = tween(300))
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    // Crossfade (bukan AnimatedVisibility): di dalam drawer ada
                    // receiver ColumnScope, sehingga overload ColumnScope.
                    // AnimatedVisibility yang terpilih dan menolak align 2D.
                    // Crossfade komposable umum — mulus dua arah.
                    Crossfade(
                        targetState = showEgg,
                        animationSpec = tween(400),
                        label = "headerEasterEgg"
                    ) { egg ->
                        if (egg) {
                            // 120dp + aspect 16:9 → gambar utuh tanpa crop; header ≈ ×1.7.
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.easter_frieren),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .height(120.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "ZCODE",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                // Audit 2026-08: ikon dibesarkan 36dp → 56dp (dulu kelihatan
                                // kayak "stiker nyasar"); aset 512px tetap tajam.
                                // Sekaligus pemicu easter egg: 7 tap cepat.
                                Image(
                                    painter = painterResource(id = R.drawable.zcode_logo),
                                    contentDescription = "Logo ZCODE",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            val now = System.currentTimeMillis()
                                            eggTaps = if (now - lastEggTap > 800) 1 else eggTaps + 1
                                            lastEggTap = now
                                            if (eggTaps >= 7 && !showEgg) {
                                                eggTaps = 0
                                                showEgg = true
                                                scope.launch {
                                                    delay(2800)
                                                    showEgg = false
                                                }
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                // ---------- tujuan aplikasi (tanpa label "NAVIGATION" — redesign 2026-08) ----------
                DrawerItem("INSTALL MODULES") {
                    closeDrawerThen { onNavigateToPip() }
                }
                DrawerItem("SAMPLES") {
                    closeDrawerThen { onNavigateToSamples() }
                }
                // DIAGNOSTICS sejajar dengan tujuan utama lain, bukan terkubur di
                // dalam About: saat ada yang salah, user harus bisa mencapainya
                // dalam satu tap, bukan tiga.
                DrawerItem("DIAGNOSTICS") {
                    closeDrawerThen { onNavigateToDiagnostics() }
                }

                Divider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 6.dp))

                // ---------- TOOLS (redesign 2026-08: kotak expandable tunggal) ----------
                // Satu kotak: plugin (scroll ~3 baris) + Symbol bar + THEME cycle +
                // Clear All. Nama polos "TOOLS" tanpa emoji (keputusan user).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toolsExpanded = !toolsExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "TOOLS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (toolsExpanded) "▾" else "▸",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                AnimatedVisibility(visible = toolsExpanded) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .border(1.dp, Color(0xFF1B4D2E), RoundedCornerShape(10.dp))
                            .padding(vertical = 2.dp)
                    ) {
                        // A4 v1.0.19 (keputusan user 2026-08-18): SATU kotak
                        // scroll utk PLUGINS + EDITOR (dulu: plugin scroll
                        // sendiri, Symbol bar dipaku — sah saat penghuni pinned
                        // cuma 2; dgn 3 toggle editor baru, area pinned akan
                        // makan setengah drawer 720p). THEME TETAP dipaku di
                        // dasar kotak (ketokan user: ganti tema tanpa scroll).
                        // Drawer induk sudah scrollable (A0) — heightIn menjaga
                        // kotak ini tidak menelan seluruh drawer.
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        ) {
                            item { ToolsSectionLabel("PLUGINS") }
                            items(PluginRegistry.plugins) { plugin ->
                                PluginRow(
                                    plugin = plugin,
                                    enabled = vm.isPluginEnabled(plugin.id),
                                    onToggle = { vm.setPluginEnabled(plugin.id, !vm.isPluginEnabled(plugin.id)) },
                                    onRun = { pluginAction(plugin)() }
                                )
                            }
                            item { ToolsSectionLabel("EDITOR") }
                            item {
                                ToolsToggleRow(
                                    "Lint gutter",
                                    "Garis merah di baris yang bermasalah",
                                    vm.lintGutterEnabled
                                ) { vm.setLintGutter(it) }
                            }
                            item {
                                ToolsToggleRow(
                                    "Whitespace guard",
                                    "Sorot spasi buntut & campuran tab/spasi",
                                    vm.whitespaceGuardEnabled
                                ) { vm.setWhitespaceGuard(it) }
                            }
                            item {
                                ToolsToggleRow(
                                    "Traceback jump",
                                    "Tap error di terminal → lompat ke barisnya",
                                    vm.tracebackJumpEnabled
                                ) { vm.setTracebackJump(it) }
                            }
                            item {
                                ToolsToggleRow(
                                    "Symbol bar",
                                    "Baris simbol cepat di bawah editor",
                                    vm.symbolBarEnabled
                                ) { vm.setSymbolBar(it) }
                            }
                        }
                        Divider(color = Color.White.copy(alpha = 0.06f))

                        // THEME — satu tombol cycle: tap-tap sampai cocok (keputusan user).
                        // Nama tema aktif selalu terlihat agar user tidak menebak-nebak.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.cycleTheme() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "THEME",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                vm.themeType.name.replace('_', ' '),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 6.dp))

                // F1.3: SETTINGS — di atas About (privasi & preferensi global)
                DrawerItem("SETTINGS") {
                    closeDrawerThen { onNavigateToSettings() }
                }

                // About — warga paling bontot di sidebar (permintaan user, redesign 2026-08)
                DrawerItem("About & Contribute") {
                    closeDrawerThen { onNavigateToAbout() }
                }
                } // penutup Column scrollable A0 (rotate resilience)
            }
        }
    ) {
        Scaffold(
            topBar = {
                // DRAWER-SWIPE-ONLY: tidak ada lagi onOpenDrawer — sidebar via swipe.
                WorkbenchTopBar(
                    vm, webViewRef,
                    onOpenPalette = { showPalette = true },
                    onPickFile = { importLauncher.launch(arrayOf("text/*")) },
                    onOpenFileActions = { if (vm.activeFile != null) showFileActions = true }
                )
            },
            floatingActionButton = {
                // ▶ Run → onRun(filename) → MainActivity navigate ke layer output full-screen (pindah layer)
                // padding bawah menyesuaikan: 52dp saat symbol bar tampil agar tidak tertutup
                // F1.1 (PERF_PASS F): FAB ditekan = scale mengecil seketika supaya tap
                // terasa "kebaca", meski cold-start Python terjadi di layer terminal.
                var fabPressed by remember { mutableStateOf(false) }
                val fabScale by animateFloatAsState(
                    targetValue = if (fabPressed) 0.86f else 1f,
                    animationSpec = tween(durationMillis = 90),
                    label = "fabScale"
                )
                FloatingActionButton(
                    onClick = {
                        // Breadcrumb: titik paling awal jalur Run. Kalau file jejak
                        // berhenti tepat setelah baris ini, berarti crash terjadi
                        // sebelum layar terminal sempat dikomposisi (diagnostik 2026-08-12).
                        com.zaba.zcode.core.diagnostics.Breadcrumb.log("FAB_TAP", vm.activeFile ?: "-")
                        // BEHAVIOR auto_trim_on_run berjalan di sini (F5)
                        vm.applyAutoTrimIfEnabled()
                        vm.flushSaveSync()
                        com.zaba.zcode.core.diagnostics.Breadcrumb.log("SAVE_OK")
                        showTerminalOverlay = true
                    },
                    // S6: FAB syntax-aware — MERAH saat ada error syntax tapi TETAP
                    // BISA RUN (warn-only never block; lesson RUN-mati Zabacode)
                    containerColor = if (vm.syntaxError != null) Color(0xFFFF4B4B)
                        else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(bottom = if (vm.symbolBarEnabled) 52.dp else 8.dp)
                        .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    fabPressed = true
                                    val released = tryAwaitRelease()
                                    if (released) fabPressed = false
                                }
                            )
                        }
                ) {
                    // ▶ FAB — ikon vektor polos (bukan emoji), tint ikut contentColor tema
                    Icon(
                        imageVector = ZIcons.Play,
                        contentDescription = "Run",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(OledBlack)
            ) {
                // Tab bar — audit 2026-08: HANYA muncul kalau ≥ 2 tab (tidak menuhin
                // editor saat satu file; konteks nama file tetap ada di topbar).
                // Fix anti double-trigger: seleksi & close ditangani SATU combinedClickable di
                // wrapper Box; Tab(onClick = {}) no-op sehingga tidak ada event ganda.
                if (vm.openedFiles.size > 1) ScrollableTabRow(
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
                                    fontSize = 12.sp // font size 12 (keputusan tim)
                                )
                            }
                        }
                    }
                }

                // F2.5: Problems Banner (VPP - Visual Problems Panel)
                ProblemsBanner(problems = vm.problems, onGotoLine = { gotoLine(it) })

                // Editor (CodeMirror 6 WebView)
                Box(modifier = Modifier.weight(1f)) {
                    EditorScreen(
                        code = vm.activeCode,
                        onCodeChange = { vm.updateCode(it) },
                        webViewRef = webViewRef,
                        vm = vm // F1.7 & F1.8: apply editor settings ke CM6 bridge
                    )
                }

                // QuickTools / symbol bar — bisa dimatikan user lewat drawer (EDITOR → Symbol bar)
                if (vm.symbolBarEnabled) {
                    // EDITOR HANDLE (build #3) — komponen yang sama dipakai di
                    // terminal. A5 v1.0.19: terowongan (slot yang tak ikut
                    // scroll) kini diisi tombol "?" → Reference Card. Pas
                    // secara semantik: referensi = jangkar, bukan penumpang.
                    com.zaba.zcode.ui.common.EditorHandle(
                        keys = com.zaba.zcode.ui.common.pythonEditorKeys(),
                        tunnelKey = com.zaba.zcode.ui.common.HandleKey(
                            label = "?",
                            onClick = { showReferenceCard = true }
                        ),
                        onInsert = { text ->
                            webViewRef.value?.evaluateJavascript(
                                "insertText(${escapeJavaScriptString(text)});", null
                            )
                        }
                    )
                }
            }
        }
    }
    } // End of ModalNavigationDrawer wrapping Box

    if (showTerminalOverlay) {
        BackHandler(enabled = showTerminalOverlay) {
            showTerminalOverlay = false
        }
        com.zaba.zcode.core.diagnostics.Breadcrumb.log("TERMINAL_COMPOSE")
        val activeFileForTerminal = vm.activeFile ?: "main.py"
        val terminalFilesDir = com.zaba.zcode.core.files.Paths.filesDir(context)
        com.zaba.zcode.ui.terminal.TerminalScreen(
            filename = activeFileForTerminal,
            filesDir = terminalFilesDir,
            context = context,
            onBack = { showTerminalOverlay = false },
            showPythonIndicator = vm.showPythonIndicator,
            terminalOutputLimit = vm.terminalOutputLimit,
            themeType = vm.themeType,
            terminalFontSize = vm.terminalFontSize
        )
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

    // ---------- Dialog: File Actions (tap nama file aktif di topbar) ----------
    // Pengganti FILES MANAGER drawer (redesign 2026-08): rename/delete tetap
    // reach-able tanpa nambah ikon di topbar.
    if (showFileActions) {
        val active = vm.activeFile
        AlertDialog(
            onDismissRequest = { showFileActions = false },
            title = {
                Text(
                    active ?: "No Active File",
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    Text("Mau ngapain sama file ini?", fontSize = 13.sp, color = Color.Gray)
                    TextButton(
                        onClick = {
                            showFileActions = false
                            fileToRename = active
                            renameNewName = active ?: ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Rename", fontSize = 14.sp) }
                    TextButton(
                        onClick = {
                            showFileActions = false
                            fileToDelete = active
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Delete", fontSize = 14.sp, color = Color(0xFFFFB4AB)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFileActions = false }) { Text("Batal") }
            }
        )
    }

    // ---------- Dialog: Reference Card (A5 v1.0.19) ----------
    if (showReferenceCard) {
        com.zaba.zcode.ui.common.ReferenceCardDialog(
            context = context,
            onInsert = { text ->
                webViewRef.value?.evaluateJavascript(
                    "insertText(${escapeJavaScriptString(text)});", null
                )
            },
            onDismiss = { showReferenceCard = false }
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

    // ---------- Dialog: Outline / Symbols ----------
    if (showOutlineResults) {
        OutlineDialog(
            items = outlineSymbols,
            onDismiss = { showOutlineResults = false },
            onJump = { line ->
                showOutlineResults = false
                gotoLine(line)
            }
        )
    }

    // ---------- Dialog: Go to Definition ----------
    if (showGoToDefinitionDialog) {
        AlertDialog(
            onDismissRequest = { showGoToDefinitionDialog = false },
            title = { Text("Go to Definition", fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = goToDefinitionQuery,
                    onValueChange = { goToDefinitionQuery = it },
                    label = { Text("Nama Simbol (Fungsi/Kelas/Variabel)", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showGoToDefinitionDialog = false
                    if (goToDefinitionQuery.isNotBlank()) {
                        vm.runPythonPlugin("go_to_definition", goToDefinitionQuery) { ok, report ->
                            val line = report.trim().toIntOrNull() ?: 0
                            if (line > 0) {
                                gotoLine(line)
                                toast("Lompat ke baris $line!")
                            } else {
                                toast("Definisi simbol '$goToDefinitionQuery' tidak ditemukan.")
                            }
                        }
                    }
                }) { Text("Cari") }
            },
            dismissButton = {
                TextButton(onClick = { showGoToDefinitionDialog = false }) { Text("Batal") }
            }
        )
    }

    // ---------- Dialog: Rename Symbol ----------
    if (showRenameSymbolDialog) {
        AlertDialog(
            onDismissRequest = { showRenameSymbolDialog = false },
            title = { Text("Rename Symbol (Satu File)", fontSize = 16.sp) },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameOldName,
                        onValueChange = { renameOldName = it },
                        label = { Text("Nama Simbol Lama", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = renameNewSymbolName,
                        onValueChange = { renameNewSymbolName = it },
                        label = { Text("Nama Simbol Baru", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRenameSymbolDialog = false
                    if (renameOldName.isNotBlank() && renameNewSymbolName.isNotBlank()) {
                        vm.runPythonPlugin("rename_symbol", "$renameOldName:$renameNewSymbolName") { ok, report ->
                            if (ok) {
                                pushCode()
                                toast(report)
                            } else {
                                toast("Gagal: $report")
                            }
                        }
                    }
                }) { Text("Ganti Nama") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameSymbolDialog = false }) { Text("Batal") }
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
                .map { p -> p.name to pluginAction(p) } + listOf<Pair<String, () -> Unit>>(
                // Migrasi CM6: panel search @codemirror/search via bridge.
                // Tipe eksplisit listOf<Pair<String, () -> Unit>>: tanpa ini,
                // lambda ber-akhir `?.evaluateJavascript(...)` (Unit?) merusak
                // inferensi — kelas bug yang sama dengan PR #3 (WorkbenchScreen).
                "Find in File (panel search)" to {
                    webViewRef.value?.evaluateJavascript("openFind();", null)
                },
                "Open Pip Manager" to { onNavigateToPip() },
                "Open About" to { onNavigateToAbout() }
            )
        PaletteDialog(
            activeCode = vm.activeCode,
            onGotoLine = { n -> gotoLine(n) },
            commands = paletteCommands,
            onDismiss = { showPalette = false },
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
    onOpenPalette: () -> Unit,
    onPickFile: () -> Unit,
    onOpenFileActions: () -> Unit
) {
    // Audit 2026-08: menu file di ikon folder (Open/Save/Save as) + toast hasil.
    val topbarContext = LocalContext.current
    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            val (_, msg) = vm.saveActiveAs(uri)
            Toast.makeText(topbarContext, msg, Toast.LENGTH_SHORT).show()
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // DRAWER-SWIPE-ONLY (audit 2026-08): sidebar dibuka HANYA dengan swipe
            // dari pinggir kiri (gesture bawaan ModalNavigationDrawer). Ikon ≡
            // dihapus atas permintaan user — topbar lebih bersih & judul file
            // dapat ruang lebih lebar. (Marker ini digrep tools/check.sh.)

            // Judul file — TAP membuka dialog Rename/Delete
            // (pengganti FILES MANAGER drawer, keputusan redesign 2026-08)
            Text(
                vm.activeFile ?: "No Active File",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenFileActions)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 📁 ikon folder → menu file (audit 2026-08): Open / Save / Save as.
                // Open = perilaku import SAF lama; Save = timpa file asli di device
                // (izin tulis persisten diambil saat import); Save as = file device
                // baru via SAF CreateDocument lalu di-link untuk Save berikutnya.
                Box {
                    var showFileMenu by remember { mutableStateOf(false) }
                    Icon(
                        imageVector = ZIcons.Folder,
                        contentDescription = "Menu file (Open/Save/Save as)",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable { showFileMenu = true }
                            .padding(10.dp)
                            .size(20.dp)
                    )
                    DropdownMenu(
                        expanded = showFileMenu,
                        onDismissRequest = { showFileMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open") },
                            leadingIcon = {
                                Icon(ZIcons.Folder, null, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                showFileMenu = false
                                onPickFile()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save") },
                            leadingIcon = {
                                Icon(ZIcons.Save, null, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                showFileMenu = false
                                val (_, msg) = vm.saveActiveToSource()
                                Toast.makeText(topbarContext, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save as") },
                            leadingIcon = {
                                Icon(ZIcons.SaveAs, null, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                showFileMenu = false
                                saveAsLauncher.launch(vm.activeFile ?: "zcode.py")
                            }
                        )
                    }
                }
                // 🔍 lama → kaca pembesar polos (palette: Line & Find) — ikut warna tema
                Icon(
                    imageVector = ZIcons.Search,
                    contentDescription = "Go to line & find",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable(onClick = onOpenPalette)
                        .padding(10.dp)
                        .size(20.dp)
                )
                // + lama → ikon plus polos (tambah file untitled_N.py)
                Icon(
                    imageVector = ZIcons.Add,
                    contentDescription = "File baru",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable {
                            vm.createNewFile()
                            webViewRef.value?.evaluateJavascript("setCode(${escapeJavaScriptString(vm.activeCode)});", null)
                        }
                        .padding(10.dp)
                        .size(20.dp)
                )
            }
        }
    }
}

// =====================================================================
// QuickTools — chips bulat, scroll horizontal, semua ter-wire
// =====================================================================

// =====================================================================
// Command Palette & Quick Open
// =====================================================================

@Composable
/**
 * Palette 🔍 (redesign 2026-08) — dua fungsi saja sesuai keputusan user:
 *  1) Line: input nomor → OK → loncat; validasi jelas + pesan receh di bawah input
 *     (dialog TIDAK ditutup saat error — user bisa langsung koreksi).
 *  2) Find: cari kata di file aktif (hasil tap → loncat ke baris).
 * Prefix power-user tetap hidup: ">" = perintah (plugin ENABLED), ":" = goto line
 * Mode File (quick-open) dibuang — pindah file via tab bar / ikon folder topbar.
 */
private fun PaletteDialog(
    activeCode: String,
    onGotoLine: (Int) -> Unit,
    commands: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    onRunCommand: (() -> Unit) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var chipMode by remember { mutableStateOf("line") }
    var lineError by remember { mutableStateOf<String?>(null) }

    val isCommandMode = query.startsWith(">")
    val isLinePrefix = query.startsWith(":")
    val mode = if (isCommandMode) "command" else if (isLinePrefix) "line" else chipMode
    val filter = when {
        isCommandMode -> query.drop(1).trim()
        isLinePrefix -> query.drop(1).trim()
        else -> query.trim()
    }

    val commandResults = if (mode == "command") commands.filter { it.first.contains(filter, ignoreCase = true) } else emptyList()
    // Mode Find: cari kata di file aktif, maks 100 hasil
    val findResults: List<Pair<Int, String>> = if (mode == "find" && filter.isNotBlank()) {
        val out = mutableListOf<Pair<Int, String>>()
        activeCode.split('\n').forEachIndexed { idx, line ->
            if (out.size >= 100) return@forEachIndexed
            if (line.contains(filter, ignoreCase = true)) out.add((idx + 1) to line.trim())
        }
        out
    } else emptyList()

    // Go to Line — jumlah baris file aktif (dipakai validasi 1..N)
    val totalLines = activeCode.split('\n').size
    fun attemptJump() {
        val target = filter.toIntOrNull()
        lineError = when {
            filter.isBlank() -> "Ketik nomor barisnya dulu ya 😅"
            target == null -> "Itu bukan angka — isi nomor baris ya (1..$totalLines)"
            target !in 1..totalLines -> "Baris $target nggak ada njiir — file lo cuma $totalLines baris 😭"
            else -> {
                onGotoLine(target)
                onDismiss()
                null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                // Chips mode — label polos tanpa emoji (keputusan ikon monokrom)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaletteModeChip("Line", mode == "line") {
                        chipMode = "line"
                        lineError = null
                    }
                    PaletteModeChip("Find", mode == "find") {
                        chipMode = "find"
                        lineError = null
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        lineError = null // koreksi user langsung membersihkan error
                    },
                    placeholder = {
                        Text(
                            when (mode) {
                                "command" -> "Perintah… (contoh: > Beautifier)"
                                "find" -> "Cari kata di file aktif…"
                                else -> "Nomor baris… (mis. 42)"
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                // Pesan error Go to Line — muncul DI BAWAH input, dialog tetap kebuka
                lineError?.let { err ->
                    Text(
                        err,
                        fontSize = 12.sp,
                        color = Color(0xFFFFB4AB),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
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
                    else -> {
                        // "line": tombol OK tegas (flow keputusan user: input → OK → loncat)
                        Text(
                            "File ini punya $totalLines baris. Ketik nomor lalu tap OK.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                        TextButton(
                            onClick = { attemptJump() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            Text("OK — Lompat Ke Baris", fontSize = 14.sp)
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

// A4 v1.0.19: label seksi tipis di dalam kotak TOOLS satu-scroll —
// pemisah visual PLUGINS/EDITOR, bukan header collapsible.
@Composable
private fun ToolsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

// A4: baris toggle seragam utk seksi EDITOR (judul + deskripsi 1 baris + switch).
@Composable
private fun ToolsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

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
// Komponen kecil drawer (label seksi era lama dihapus — redesign 2026-08:
// drawer hanya berisi item + kotak TOOLS)
// =====================================================================

@Composable
private fun DrawerItem(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            // Satu titik catat untuk SELURUH navigasi sidebar. Menaruhnya di
            // sini, bukan di tiap pemanggil, membuat item baru otomatis
            // terekam — jejak tidak bisa lupa diperbarui.
            .clickable {
                com.zaba.zcode.core.diagnostics.Breadcrumb.log("NAV", label)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun ProblemsBanner(
    problems: List<com.zaba.zcode.core.editor.Problem>,
    onGotoLine: (Int) -> Unit
) {
    if (problems.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }

    val bgColor = Color(0x1AFF4B4B)
    val textColor = Color(0xFFFFB4AB)
    val icon = "❌"

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$icon ",
                    fontSize = 12.sp
                )
                Text(
                    text = problems.first().message,
                    fontSize = 11.sp,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (problems.size > 1 && !expanded) {
                    Surface(
                        color = textColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = "(+${problems.size - 1})",
                            fontSize = 10.sp,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = if (expanded) "▴" else "▾",
                    fontSize = 12.sp,
                    color = textColor
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Divider(color = textColor.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Daftar Masalah (${problems.size}):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    problems.forEach { problem ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onGotoLine(problem.line)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "❌ Baris ${problem.line}: ",
                                fontSize = 11.sp,
                                color = textColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = problem.message.replace("on line ${problem.line}", "").trim(),
                                fontSize = 11.sp,
                                color = textColor.copy(alpha = 0.9f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}


data class OutlineItem(val type: String, val name: String, val line: Int)

@Composable
fun OutlineDialog(
    items: List<OutlineItem>,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Outline / Symbols", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            if (items.isEmpty()) {
                Text("Tidak ada kelas atau fungsi ditemukan njiir 🤷", fontSize = 13.sp)
            } else {
                Column(modifier = Modifier.heightIn(max = 280.dp)) {
                    Text("Pilih simbol untuk lompat ke baris:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(items) { item ->
                            val icon = when (item.type) {
                                "CLASS" -> "🗂️"
                                "FUNC" -> "λ"
                                "METHOD" -> "⚙️"
                                else -> "⚓"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onJump(item.line) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(icon, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = item.name,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Baris ${item.line}",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Divider(color = Color.White.copy(alpha = 0.05f))
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

