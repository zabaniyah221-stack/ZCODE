package com.zaba.zcode.ui.settings

import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaba.zcode.core.packageengine.CompatibilityEngine
import com.zaba.zcode.core.packageengine.DependencyResolver
import com.zaba.zcode.core.packageengine.PackageDetails
import com.zaba.zcode.core.packageengine.PackageEngineV2
import com.zaba.zcode.core.packageengine.PackageRepository
import com.zaba.zcode.core.packageengine.PackageStatus
import com.zaba.zcode.core.packageengine.RuntimeProbe
import com.zaba.zcode.core.packageengine.TelemetryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PipScreen — INSTALL MODULES (SPEC-001).
 * - LIBRARY  : katalog curated (100→300) + stdlib index; Package Details 18 field
 *              (SPEC §11); action per status (TESTED/COMPATIBLE/EXPERIMENTAL/
 *              INCOMPATIBLE/UNAVAILABLE/INSTALLED/UPDATE_AVAILABLE).
 * - MANUAL   : requirement interface (bukan terminal!) — Parse → Resolve →
 *              Confirm kalau risky → Install via PackageEngineV2.
 * Semua tombol install terhubung ke PackageEngineV2 (Rule 7 — satu backend).
 */
@Composable
fun PipScreen(
    context: android.content.Context,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { PackageRepository(context) }
    val engine = remember { PackageEngineV2(context) }
    val compat = remember { CompatibilityEngine(context) }

    var activeTab by remember { mutableStateOf("LIBRARY") }
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }

    var installedMap by remember { mutableStateOf(mapOf<String, String>()) }
    var runtimeInfo by remember { mutableStateOf<RuntimeProbe.RuntimeInfo?>(null) }

    // Library details
    var selectedPackage by remember { mutableStateOf<PackageDetails?>(null) }
    var detailsAnalysis by remember { mutableStateOf<CompatibilityEngine.Analysis?>(null) }

    // Manual install
    var packageName by remember { mutableStateOf("") }
    var isInstalling by remember { mutableStateOf(false) }
    // Analyze punya cooperative Cancel. Download/install belum boleh mengklaim
    // bisa dibatalkan karena transaction stage-nya berbeda.
    var isAnalyzing by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }
    // v1.0.18: Console dan Log DIGABUNG jadi satu terminal (keputusan user,
    // 2026-08-15). Panel Log lama 80% menceritakan hal yang sama dengan
    // Console dalam kotak kedua yang merebut setengah layar; jejak forensik
    // permanen sudah menjadi tugas Diagnostics (breadcrumb PKG_*).
    var consoleLines by remember { mutableStateOf(initialConsole()) }
    var pendingRiskyReq by remember { mutableStateOf<String?>(null) }
    var pendingRiskyReason by remember { mutableStateOf("") }
    var pendingRiskyPlan by remember { mutableStateOf<DependencyResolver.ResolvePlan?>(null) }

    val consoleScroll = rememberScrollState()

    fun refreshInstalled() {
        // Saring pustaka pendukung `chaquopy-*`. User tidak pernah memintanya,
        // tidak bisa memakainya langsung, dan menghapusnya justru merusak paket
        // lain yang masih membutuhkannya. Daftar ini milik user, bukan cermin
        // isi direktori.
        installedMap = repository.installedSnapshot()
            .filterKeys { !it.startsWith("chaquopy-") }
            .mapValues { it.value.version }
    }

    fun addConsole(line: ConsoleLine) {
        consoleLines = (consoleLines + line).takeLast(400)
        scope.launch { consoleScroll.scrollTo(consoleScroll.maxValue) }
    }

    // Kompat penggabungan Console+Log: pemanggil appendLog lama menulis ke
    // Console. Jenis baris ditebak dari isi pesan supaya warna bermakna
    // (\u2705 -> OK hijau, \u274c/\ud83d\uded1 -> FAIL merah, "> " -> STEP).
    fun appendLog(text: String) {
        text.lines().filter { it.isNotBlank() }.forEach { baris ->
            val kind = when {
                baris.contains("\u2705") -> ConsoleKind.OK
                baris.contains("\u274c") || baris.contains("\ud83d\uded1") -> ConsoleKind.FAIL
                baris.startsWith("> ") -> ConsoleKind.STEP
                else -> ConsoleKind.LOG
            }
            addConsole(ConsoleLine(baris, kind))
        }
    }

    fun handleEngineStep(step: PackageEngineV2.Step) {
        when (step) {
            is PackageEngineV2.Step.Begin -> addConsole(ConsoleLine("▶ ${step.label}", ConsoleKind.STEP))
            is PackageEngineV2.Step.Log -> addConsole(ConsoleLine(step.text, ConsoleKind.LOG))
            is PackageEngineV2.Step.Finish -> addConsole(
                ConsoleLine(
                    if (step.ok) "✓ ${step.label}${if (step.detail.isNotBlank()) " — ${step.detail}" else ""}"
                    else "✗ ${step.label}: ${step.detail}",
                    if (step.ok) ConsoleKind.OK else ConsoleKind.FAIL
                )
            )
        }
    }

    fun startInstall(req: String, plan: DependencyResolver.ResolvePlan? = null) {
        if (isInstalling) return
        val trimmed = req.trim()
        if (trimmed.isBlank()) {
            appendLog("\n⚠️ Requirement kosong.\n")
            return
        }
        if (PackageEngineV2.isBusy()) {
            appendLog("\n⚠️ Instalasi lain masih berjalan. Tunggu selesai.\n")
            return
        }
        isInstalling = true
        consoleLines = emptyList()
        // BUG J: jejak Install Modules sebelumnya TIDAK tercatat sama sekali —
        // breadcrumb hanya meliputi jalur Run (7 dari 49 berkas). Padahal justru
        // installer yang sedang bermasalah, dan user tidak punya logcat.
        com.zaba.zcode.core.diagnostics.Breadcrumb.log("PKG_INSTALL_BEGIN", trimmed)
        appendLog("\n> install $trimmed\n")
        scope.launch(Dispatchers.Default) {
            val result = try {
                engine.install(trimmed, plan) { step ->
                    scope.launch { handleEngineStep(step) }
                }
            } catch (e: Exception) {
                PackageEngineV2.InstallResult(
                    ok = false, code = "RUNTIME", stage = "engine",
                    humanMessage = e.message, technicalMessage = e.toString(),
                    rollbackPerformed = false, installed = emptyList()
                )
            }
            withContext(Dispatchers.Main) {
                isInstalling = false
                isCancelling = false // v1.0.18: install cancellable — reset state tombol
                if (result.ok) {
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                        "PKG_INSTALL_OK", "$trimmed -> ${result.installed.joinToString(",")}"
                    )
                    appendLog("\n✅ Install selesai: ${result.installed.joinToString(", ")}\n")
                    refreshInstalled()
                } else {
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                        "PKG_INSTALL_FAIL", "$trimmed [${result.code}/${result.stage}] ${result.humanMessage}"
                    )
                    // Pesan teknis dicatat TERPISAH. Menggabungkannya ke baris di
                    // atas membuat satu baris breadcrumb raksasa yang sulit dibaca;
                    // sebagai baris sendiri ia tetap utuh dan tetap mudah di-grep.
                    result.technicalMessage?.takeIf { it.isNotBlank() }?.let {
                        com.zaba.zcode.core.diagnostics.Breadcrumb.log("PKG_INSTALL_DETAIL", it)
                    }
                    appendLog(
                        "\n❌ [${result.code}] ${result.humanMessage}" +
                            (if (result.rollbackPerformed) "\n   (rollback dilakukan — environment lama utuh)" else "") +
                            "\n"
                    )
                    // Tampilkan juga di konsol: user melapor dari HP tanpa PC, jadi
                    // penyebab teknis harus terlihat langsung dan bisa disalin —
                    // bukan hanya tersimpan di file yang harus dicari dulu.
                    result.technicalMessage?.takeIf { it.isNotBlank() }?.let {
                        appendLog("\n--- detail teknis (salin ini saat melapor) ---\n$it\n")
                    }
                }
            }
        }
    }

    fun cancelCurrentAnalyze() {
        if (isCancelling) return
        // v1.0.18: Cancel kini menjangkau SEMUA fase yang aman dibatalkan.
        // Resolve -> cooperative via bridge (Bug M); Download/Extract ->
        // cooperative via flag engine (dicek per-chunk 64KB / antar-paket).
        // Activate tetap tidak bisa dibatalkan (atomic).
        when {
            isAnalyzing && engine.cancelCurrentOperation() -> {
                isCancelling = true
                appendLog("\n⏳ Membatalkan analisis setelah operasi jaringan aktif selesai…\n")
                com.zaba.zcode.core.diagnostics.Breadcrumb.log("PKG_ANALYZE_CANCEL_REQUEST", packageName.trim())
            }
            isInstalling && engine.requestInstallCancel() -> {
                isCancelling = true
                appendLog("\n⏳ Membatalkan instalasi di checkpoint berikutnya…\n")
            }
            else -> appendLog("\nℹ️ Operasi sudah selesai atau di tahap yang tidak bisa dibatalkan.\n")
        }
    }

    fun analyzeThenInstall(req: String) {
        if (isInstalling) return
        val trimmed = req.trim()
        if (trimmed.isBlank()) {
            appendLog("\n⚠️ Requirement kosong.\n")
            return
        }
        if (PackageEngineV2.isBusy()) {
            appendLog("\n⚠️ Instalasi/analisis lain masih berjalan. Tunggu selesai.\n")
            return
        }
        isInstalling = true
        isAnalyzing = true
        isCancelling = false
        consoleLines = emptyList()
        com.zaba.zcode.core.diagnostics.Breadcrumb.log("PKG_ANALYZE_BEGIN", trimmed)
        appendLog("\n> analyze $trimmed\n")
        scope.launch(Dispatchers.Default) {
            val plan = try {
                engine.analyze(trimmed) { step ->
                    scope.launch { handleEngineStep(step) }
                }
            } catch (e: Exception) {
                // KEGAGALAN INI PERNAH SENYAP TOTAL (v1.0.13, log perangkat).
                // Analisis matplotlib melempar timeout PyCall, dan jalur catch
                // ini hanya menulis ke layar — Diagnostics tidak mencatat apa
                // pun, sehingga dari HP mustahil dibedakan antara "aplikasi
                // hang", "jaringan putus", dan "batas waktu terlampaui".
                // Pemakai tidak punya logcat; diam di sini = buta total.
                com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                    "PKG_ANALYZE_ERROR",
                    "$trimmed [${e.javaClass.simpleName}] ${e.message ?: "tanpa pesan"}"
                )
                withContext(Dispatchers.Main) {
                    isInstalling = false
                    isAnalyzing = false
                    isCancelling = false
                    appendLog("\n❌ ${e.message}\n")
                }
                return@launch
            }
            val risk = engine.riskDescription(plan, trimmed)
            withContext(Dispatchers.Main) {
                // Python sudah kembali: baru sekarang operasi resolve terminal
                // dan tombol Start boleh tersedia lagi.
                isAnalyzing = false
                isCancelling = false
                if (!plan.ok) {
                    isInstalling = false
                    if (plan.errorCode == "CANCELLED") {
                        com.zaba.zcode.core.diagnostics.Breadcrumb.log("PKG_ANALYZE_CANCELLED", trimmed)
                        appendLog("\n🛑 Analisis dibatalkan. Tidak ada package yang diubah.\n")
                    } else {
                        com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                            "PKG_ANALYZE_FAIL", "$trimmed [${plan.errorCode}] ${plan.humanError}"
                        )
                        appendLog("\n❌ [${plan.errorCode}] ${plan.humanError}\n")
                        plan.technicalError?.takeIf { it.isNotBlank() }?.let {
                            appendLog("--- detail teknis ---\n$it\n")
                        }
                    }
                    return@withContext
                }
                // BUG C: modul stdlib bukan kegagalan.
                if (plan.stdlib.isNotEmpty() && plan.packages.isEmpty()) {
                    isInstalling = false
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log("PKG_STDLIB", trimmed)
                    appendLog("\nℹ️ ${plan.stdlib.joinToString(" ") { it.reason }}\n")
                    return@withContext
                }
                if (plan.conflicts.isNotEmpty()) {
                    isInstalling = false
                    appendLog("\n❌ [DEPENDENCY_CONFLICT] ${plan.conflicts.joinToString("; ") { "${it.name}: ${it.versionA} vs ${it.versionB}" }}\n")
                    return@withContext
                }
                if (plan.unavailable.isNotEmpty()) {
                    isInstalling = false
                    appendLog("\n❌ [PACKAGE_NOT_AVAILABLE] ${plan.unavailable.joinToString("; ") { it.name + ": " + it.reason }}\n")
                    return@withContext
                }
                if (risk != null) {
                    pendingRiskyReq = trimmed
                    pendingRiskyReason = risk
                    pendingRiskyPlan = plan
                } else {
                    isInstalling = false
                    startInstall(trimmed, plan)
                }
            }
        }
    }

    fun installFromLibrary(req: String) {
        if (isInstalling) return
        activeTab = "MANUAL"
        packageName = req
        analyzeThenInstall(req)
    }

    fun doUninstall(canonical: String) {
        scope.launch(Dispatchers.Default) {
            val (ok, msg) = engine.uninstall(canonical) { line ->
                scope.launch { appendLog(line) }
            }
            withContext(Dispatchers.Main) {
                appendLog(if (ok) "\n✅ Uninstall $canonical berhasil.\n" else "\n❌ $msg\n")
                refreshInstalled()
            }
        }
    }

    fun doSupportRequest(canonical: String) {
        val (ok, msg) = engine.requestSupport(canonical, "Dari UI katalog (status tidak tersedia/incompatible).")
        appendLog("\n${if (ok) "✅" else "❌"} $msg\n")
    }

    LaunchedEffect(Unit) {
        TelemetryStore.init(context)
        refreshInstalled()
        withContext(Dispatchers.Default) {
            runtimeInfo = RuntimeProbe.probe(context)
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
                        Text(
                            "INSTALL MODULES",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                        TabBox("LIBRARY", activeTab == "LIBRARY") { activeTab = "LIBRARY" }
                        TabBox("MANUAL INSTALL", activeTab == "MANUAL") { activeTab = "MANUAL" }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (activeTab == "LIBRARY") {
                LibraryTab(
                    repository = repository,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    expandedCategories = expandedCategories,
                    onToggleCategory = { cat ->
                        expandedCategories = if (expandedCategories.contains(cat)) expandedCategories - cat
                        else expandedCategories + cat
                    },
                    installedMap = installedMap,
                    runtimeInfo = runtimeInfo,
                    compat = compat,
                    onSelect = { pkg ->
                        selectedPackage = pkg
                        val installed = installedMap[pkg.name.lowercase().replace("_", "-")]
                        val rt = runtimeInfo
                        detailsAnalysis = if (rt != null) {
                            compat.analyze(pkg, rt, installed)
                        } else {
                            CompatibilityEngine.Analysis(pkg.status, emptyList(), pkg.status.installable())
                        }
                    }
                )
            } else {
                ManualTab(
                    packageName = packageName,
                    onPackageNameChange = { packageName = it },
                    isInstalling = isInstalling,
                    isAnalyzing = isAnalyzing,
                    isCancelling = isCancelling,
                    onInstall = { analyzeThenInstall(packageName) },
                    onCancel = { cancelCurrentAnalyze() },
                    onRequirementsTxt = {
                        appendLog("\nℹ️ requirements.txt: buka file di editor lalu salin barisnya ke sini.\n")
                    },
                    consoleLines = consoleLines,
                    consoleScroll = consoleScroll
                )
            }
        }
    }

    // ---- Risky confirmation dialog (Manual Install) ----
    pendingRiskyReq?.let { req ->
        AlertDialog(
            onDismissRequest = {
                pendingRiskyReq = null
                pendingRiskyPlan = null
                isInstalling = false
            },
            title = { Text("Install eksperimental?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Package ini belum diuji penuh oleh ZCODE:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        pendingRiskyReason,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFB3261E)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "ZCODE akan tetap menjalankan verifikasi (SHA-256, smoke test) dan " +
                            "rollback otomatis bila gagal. Environment lama tidak akan rusak.",
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val plan = pendingRiskyPlan
                    pendingRiskyReq = null
                    pendingRiskyPlan = null
                    isInstalling = false
                    startInstall(req, plan)
                }) { Text("Install Anyway") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRiskyReq = null
                    pendingRiskyPlan = null
                    isInstalling = false
                }) { Text("Batal") }
            }
        )
    }

    // ---- Package Details dialog (SPEC §11) ----
    selectedPackage?.let { pkg ->
        val analysis = detailsAnalysis ?: CompatibilityEngine.Analysis(pkg.status, emptyList(), pkg.status.installable())
        PackageDetailsDialog(
            pkg = pkg,
            analysis = analysis,
            installedVersion = installedMap[pkg.name.lowercase().replace("_", "-")],
            onDismiss = { selectedPackage = null },
            onInstallTested = {
                selectedPackage = null
                val tv = pkg.testedVersion
                if (tv != null) installFromLibrary("${pkg.name}==$tv") else installFromLibrary(pkg.name)
            },
            onInstall = {
                selectedPackage = null
                installFromLibrary(pkg.name)
            },
            onUninstall = {
                selectedPackage = null
                doUninstall(pkg.name.lowercase().replace("_", "-"))
            },
            onSupport = {
                selectedPackage = null
                doSupportRequest(pkg.name.lowercase().replace("_", "-"))
            }
        )
    }
}

// =====================================================================
// Model console
// =====================================================================

enum class ConsoleKind { STEP, LOG, OK, FAIL }

data class ConsoleLine(val text: String, val kind: ConsoleKind)

// =====================================================================
// Tab
// =====================================================================

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabBox(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) MaterialTheme.colorScheme.primary else Color.Gray
        )
    }
}

// =====================================================================
// LIBRARY tab
// =====================================================================

@Composable
private fun LibraryTab(
    repository: PackageRepository,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    expandedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    installedMap: Map<String, String>,
    runtimeInfo: RuntimeProbe.RuntimeInfo?,
    compat: CompatibilityEngine,
    onSelect: (PackageDetails) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search 300 packages...", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(8.dp),
            textStyle = TextStyle(fontSize = 14.sp)
        )
        if (runtimeInfo != null) {
            Text(
                "Runtime: Python ${runtimeInfo.pythonVersion} · ABI ${runtimeInfo.abis.joinToString(",")} · ${runtimeInfo.chaquopyVersion}",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val allItems = repository.loadCatalog()
            val filtered = if (searchQuery.isNotBlank()) {
                repository.search(searchQuery)
            } else allItems

            if (searchQuery.isNotBlank()) {
                items(filtered) { item ->
                    CatalogRow(
                        item = item,
                        installedVersion = installedMap[item.name.lowercase().replace("_", "-")],
                        onClick = { onSelect(item) }
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "Tidak ditemukan. Coba pakai MANUAL INSTALL untuk package PyPI lain.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                repository.categories().forEach { cat ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleCategory(cat) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                cat,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            val count = allItems.count { it.category == cat }
                            Text("$count", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                text = if (expandedCategories.contains(cat)) "▾" else "▸",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Divider(color = Color.White.copy(alpha = 0.05f))
                    }
                    if (expandedCategories.contains(cat)) {
                        items(allItems.filter { it.category == cat }) { item ->
                            CatalogRow(
                                item = item,
                                installedVersion = installedMap[item.name.lowercase().replace("_", "-")],
                                onClick = { onSelect(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(
    item: PackageDetails,
    installedVersion: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(statusIcon(item.status, installedVersion != null), fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (installedVersion != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "v$installedVersion ✓",
                        fontSize = 10.sp,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
            Text(
                text = item.description,
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            "Detail ›",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
    Divider(color = Color.White.copy(alpha = 0.05f))
}

private fun statusIcon(status: PackageStatus, installed: Boolean): String = when {
    installed -> "✅"
    status == PackageStatus.TESTED -> "🟢"
    status == PackageStatus.COMPATIBLE -> "🟡"
    status == PackageStatus.EXPERIMENTAL -> "🧪"
    status == PackageStatus.INCOMPATIBLE -> "❌"
    status == PackageStatus.UNAVAILABLE -> "🚫"
    else -> "❔"
}

// =====================================================================
// Package Details dialog (SPEC §11 — urutan wajib)
// =====================================================================

@Composable
private fun PackageDetailsDialog(
    pkg: PackageDetails,
    analysis: CompatibilityEngine.Analysis,
    installedVersion: String?,
    onDismiss: () -> Unit,
    onInstallTested: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onSupport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(pkg.displayName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "${pkg.name} · ${analysis.status.label}",
                    fontSize = 12.sp,
                    color = when (analysis.status) {
                        PackageStatus.INCOMPATIBLE, PackageStatus.UNAVAILABLE -> Color(0xFFB3261E)
                        PackageStatus.TESTED, PackageStatus.INSTALLED -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .height(360.dp)
            ) {
                DetailField("1. What is it?", pkg.description)
                if (pkg.useCases.isNotEmpty()) DetailField("2. Useful for", pkg.useCases.joinToString(", "))
                if (pkg.works.isNotEmpty()) DetailField("4. Works in ZCODE", pkg.works.joinToString("; "))
                if (pkg.doesNotWork.isNotEmpty()) DetailField("5. Doesn't work", pkg.doesNotWork.joinToString("; "))
                if (analysis.reasons.isNotEmpty()) DetailField("6. Device compatibility", analysis.reasons.joinToString(" "))
                DetailField("7. Tested / latest", pkg.testedVersion ?: "belum ditetapkan (ikuti resolusi)")
                if (pkg.dependencies.isNotEmpty()) DetailField("8. Dependency plan", pkg.dependencies.joinToString(", "))
                DetailField("9-10. Size", "Download & installed size terhitung saat resolusi (lihat console install).")
                if (pkg.risks.isNotEmpty()) DetailField("12. Risks", pkg.risks.joinToString("; "))
                if (pkg.doesNotWork.isNotEmpty()) DetailField("13. Limitations", pkg.doesNotWork.joinToString("; "))
                DetailField("14. Publisher", pkg.publisher.ifBlank { "-" })
                DetailField("15. Source", pkg.source)
                DetailField("16. SHA-256", pkg.sha256 ?: "Diverifikasi saat install (dari PyPI/Chaquopy).")
                DetailField("17. License", pkg.license.ifBlank { "-" })
                DetailField("Category / type", "${pkg.category} · ${pkg.type} · Python ${pkg.python.joinToString("/")}" +
                    (if (pkg.abis.isNotEmpty()) " · ABI ${pkg.abis.joinToString(",")}" else ""))
            }
        },
        confirmButton = {
            when {
                installedVersion != null && analysis.status == PackageStatus.UPDATE_AVAILABLE ->
                    TextButton(onClick = onInstallTested) { Text("Update") }
                analysis.status == PackageStatus.TESTED ->
                    TextButton(onClick = onInstallTested) { Text("Install Tested Version") }
                analysis.status == PackageStatus.COMPATIBLE ->
                    TextButton(onClick = onInstall) { Text("Install") }
                analysis.status == PackageStatus.EXPERIMENTAL ->
                    TextButton(onClick = onInstall) { Text("Install Experimental") }
                analysis.status == PackageStatus.INCOMPATIBLE ->
                    TextButton(onClick = onSupport) { Text("Kenapa? / Request Support") }
                analysis.status == PackageStatus.UNAVAILABLE ->
                    TextButton(onClick = onSupport) { Text("Request Support") }
                analysis.status == PackageStatus.INSTALLED ->
                    TextButton(onClick = onUninstall) { Text("Uninstall") }
                else -> TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun DetailField(label: String, value: String) {
    if (value.isBlank()) return
    Text(
        label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
    Text(value, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
}

// =====================================================================
// MANUAL INSTALL tab
// =====================================================================

@Composable
private fun ManualTab(
    packageName: String,
    onPackageNameChange: (String) -> Unit,
    isInstalling: Boolean,
    isAnalyzing: Boolean,
    isCancelling: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRequirementsTxt: () -> Unit,
    consoleLines: List<ConsoleLine>,
    consoleScroll: androidx.compose.foundation.ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = packageName,
                onValueChange = onPackageNameChange,
                label = { Text("Requirement", fontSize = 12.sp) },
                placeholder = { Text("e.g. requests==2.32.3", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onInstall() }),
                textStyle = TextStyle(fontSize = 14.sp)
            )
            val cancellable = isAnalyzing || isInstalling
            Button(
                onClick = if (cancellable) onCancel else onInstall,
                enabled = if (cancellable) !isCancelling else packageName.isNotBlank(),
                // v1.0.18: JANGAN hardcode warna teks ke onPrimary — saat
                // disabled Compose mengganti container jadi kelabu dan
                // onPrimary di atasnya nyaris tak terbaca (laporan user,
                // screenshot 2026-08-15). Serahkan kontras per-state ke
                // buttonColors dengan disabled* eksplisit; label 14sp
                // (Material: label tombol >= 14sp).
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (cancellable) Color(0xFF8B2E2E)
                    else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                when {
                    // v1.0.18: fase install juga bisa dibatalkan (download/
                    // extract cooperative) — spinner-tanpa-jalan-keluar pensiun.
                    cancellable -> Text(
                        if (isCancelling) "Membatalkan…" else "Batalkan",
                        fontSize = 14.sp
                    )
                    else -> Text("Install", fontSize = 14.sp)
                }
            }
        }
        Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Didukung: requests · requests==2.32.3 · pydantic>=2,<3 · numpy==1.26.* · flask[async]",
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onRequirementsTxt, contentPadding = PaddingValues(0.dp)) {
                Text("requirements.txt?", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.weight(1f))
            // PASTE (build #3, permintaan user). Long-press pada OutlinedTextField
            // Compose memang menawarkan Paste, tetapi hanya setelah field kosong
            // ditekan lama dengan tepat — di layar sempit itu sering meleset.
            // Nama paket biasanya disalin dari chat atau web, jadi satu tap
            // eksplisit jauh lebih pasti daripada menebak gestur.
            val clipboard = LocalClipboardManager.current
            val ctx = LocalContext.current
            TextButton(
                onClick = {
                    val teks = clipboard.getText()?.text.orEmpty().trim()
                    if (teks.isEmpty()) {
                        Toast.makeText(ctx, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                    } else {
                        // Ambil baris pertama saja: menempelkan seluruh isi
                        // requirements.txt ke field satu-baris hanya membuat
                        // parser gagal dengan pesan yang membingungkan.
                        val baris = teks.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
                        onPackageNameChange(baris)
                        if (teks.lines().count { it.isNotBlank() } > 1) {
                            Toast.makeText(
                                ctx,
                                "Beberapa baris terdeteksi — hanya baris pertama dipakai",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Text("Paste", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "CONSOLE:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        // v1.0.18: satu terminal penuh (Console+Log digabung). Latar tetap
        // hitam pekat di semua tema — panel terminal adalah pengecualian OLED
        // yang disengaja, bukan layar navigasi.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF050806), shape = RoundedCornerShape(8.dp))
                .padding(12.dp)
                .verticalScroll(consoleScroll)
        ) {
            // BUG I: console harus bisa diseleksi & disalin (user melapor tanpa logcat).
            SelectionContainer {
            Column {
                consoleLines.forEach { line ->
                    val color = when (line.kind) {
                        ConsoleKind.STEP -> Color(0xFF8A9BB0)
                        ConsoleKind.LOG -> Color(0xFF9AE6B4)
                        ConsoleKind.OK -> Color(0xFF39FF14)
                        ConsoleKind.FAIL -> Color(0xFFFF6B6B)
                    }
                    Text(
                        line.text,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
            } // SelectionContainer (BUG I)
        }
    }
}

private fun initialConsole(): List<ConsoleLine> = listOf(
    ConsoleLine("ZCODE Package Engine V2 — Chaquopy 3.11", ConsoleKind.STEP),
    ConsoleLine("Masukkan requirement (bukan perintah shell), lalu tap Install.", ConsoleKind.LOG),
    ConsoleLine("Instalasi transaksional: verifikasi + smoke test + rollback otomatis.", ConsoleKind.LOG),
    ConsoleLine("Flow: Parse → Resolve → Download → Verify → Extract → Smoke → Activate", ConsoleKind.LOG),
)
