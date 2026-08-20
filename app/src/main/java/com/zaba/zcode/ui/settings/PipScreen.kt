package com.zaba.zcode.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.zaba.zcode.core.packageengine.SourceRef
import com.zaba.zcode.core.packageengine.TelemetryStore
import com.zaba.zcode.core.logging.SemanticLogKind
import com.zaba.zcode.core.samples.SampleEntry
import com.zaba.zcode.core.samples.SampleLibrary
import com.zaba.zcode.ui.samples.SampleRequirementDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PipTab { LIBRARY, MANUAL }

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
    onBack: () -> Unit,
    onOpenSample: (SampleEntry) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val repository = remember { PackageRepository(context) }
    val engine = remember { PackageEngineV2(context) }
    val compat = remember { CompatibilityEngine(context) }

    // v1.0.19 final: tab tap-only adalah topology stabil yang sudah melewati
    // UAT sebelum Pager diperkenalkan. State screen tetap di owner agar
    // Library/Manual tidak kehilangan input atau posisi scroll saat berganti.
    var activeTab by remember { mutableStateOf(PipTab.LIBRARY) }
    val libraryListState = rememberLazyListState()
    val manualPageScroll = rememberScrollState()

    fun selectPipTab(tab: PipTab) {
        if (activeTab == tab) return
        activeTab = tab
        com.zaba.zcode.core.diagnostics.Breadcrumb.log("PIP_TAB", tab.name)
    }

    LaunchedEffect(Unit) {
        com.zaba.zcode.core.diagnostics.Breadcrumb.log("PIP_TAB", activeTab.name)
    }

    var searchQuery by remember { mutableStateOf("") }
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }

    var installedMap by remember { mutableStateOf(mapOf<String, String>()) }
    var runtimeInfo by remember { mutableStateOf<RuntimeProbe.RuntimeInfo?>(null) }

    // Library details
    var selectedPackage by remember { mutableStateOf<PackageDetails?>(null) }
    var detailsAnalysis by remember { mutableStateOf<CompatibilityEngine.Analysis?>(null) }
    var pendingSample by remember { mutableStateOf<SampleEntry?>(null) }
    var missingSamplePackages by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingUninstall by remember { mutableStateOf<PackageDetails?>(null) }

    // Manual install. Draft tetap milik field; activeRequirement adalah
    // snapshot immutable milik operasi agar Cancel/diagnostics tidak membaca
    // ulang teks yang mungkin berubah di UI.
    var packageName by remember { mutableStateOf("") }
    var activeRequirement by remember { mutableStateOf<String?>(null) }
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
    // v1.0.18: antrian requirements.txt multi-baris. Paste beberapa baris
    // tidak lagi dibuang ke baris pertama saja — sisanya mengantre dan
    // diproses berurutan begitu engine idle (pop-on-dispatch, anti-loop).
    var installQueue by remember { mutableStateOf(listOf<String>()) }
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

    fun appendMessage(text: String, kind: SemanticLogKind) {
        text.lines().filter { it.isNotBlank() }.forEach { line ->
            addConsole(ConsoleLine(line.trim(), kind))
        }
    }

    // Reader kompatibilitas untuk output lama/eksternal. Producer baru dilarang
    // menyisipkan emoji status; makna harus datang melalui SemanticLogKind.
    fun appendLegacyLog(text: String) {
        text.lines().filter { it.isNotBlank() }.forEach { line ->
            addConsole(parseLegacyConsoleLine(line))
        }
    }

    fun handleEngineStep(step: PackageEngineV2.Step) {
        when (step) {
            is PackageEngineV2.Step.Begin ->
                addConsole(ConsoleLine(step.label, SemanticLogKind.STEP))
            is PackageEngineV2.Step.Message ->
                addConsole(ConsoleLine(step.text.trim(), step.kind))
            is PackageEngineV2.Step.Finish -> {
                val kind = when (step.result) {
                    PackageEngineV2.FinishResult.OK -> SemanticLogKind.OK
                    PackageEngineV2.FinishResult.FAIL -> SemanticLogKind.FAIL
                    PackageEngineV2.FinishResult.STOP -> SemanticLogKind.STOP
                }
                val detail = if (step.detail.isBlank()) "" else " — ${step.detail}"
                addConsole(ConsoleLine("${step.label}$detail", kind))
            }
        }
    }

    fun startInstall(req: String, plan: DependencyResolver.ResolvePlan? = null) {
        if (isInstalling) return
        val trimmed = req.trim()
        if (trimmed.isBlank()) {
            activeRequirement = null
            appendMessage("Requirement kosong.", SemanticLogKind.WARN)
            return
        }
        if (PackageEngineV2.isBusy()) {
            activeRequirement = null
            appendMessage("Instalasi lain masih berjalan. Tunggu selesai.", SemanticLogKind.WARN)
            return
        }
        activeRequirement = trimmed
        isInstalling = true
        consoleLines = emptyList()
        // BUG J: jejak Install Modules sebelumnya TIDAK tercatat sama sekali —
        // breadcrumb hanya meliputi jalur Run (7 dari 49 berkas). Padahal justru
        // installer yang sedang bermasalah, dan user tidak punya logcat.
        com.zaba.zcode.core.diagnostics.Breadcrumb.log("PKG_INSTALL_BEGIN", trimmed)
        appendMessage("install $trimmed", SemanticLogKind.STEP)
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
                activeRequirement = null
                if (result.ok) {
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                        "PKG_INSTALL_OK", "$trimmed -> ${result.installed.joinToString(",")}"
                    )
                    appendMessage("Install selesai: ${result.installed.joinToString(", ")}", SemanticLogKind.OK)
                    refreshInstalled()
                } else {
                    val cancelled = result.code == "CANCELLED"
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                        if (cancelled) "PKG_INSTALL_CANCELLED" else "PKG_INSTALL_FAIL",
                        "$trimmed [${result.code}/${result.stage}] ${result.humanMessage}"
                    )
                    // Pesan teknis dicatat TERPISAH. Menggabungkannya ke baris di
                    // atas membuat satu baris breadcrumb raksasa yang sulit dibaca;
                    // sebagai baris sendiri ia tetap utuh dan tetap mudah di-grep.
                    result.technicalMessage?.takeIf { it.isNotBlank() }?.let {
                        com.zaba.zcode.core.diagnostics.Breadcrumb.log("PKG_INSTALL_DETAIL", it)
                    }
                    appendMessage(
                        "[${result.code}] ${result.humanMessage}",
                        if (cancelled) SemanticLogKind.STOP else SemanticLogKind.FAIL
                    )
                    if (result.rollbackPerformed) {
                        appendMessage(
                            "Rollback dilakukan — environment lama utuh",
                            SemanticLogKind.INFO
                        )
                    }
                    // Tampilkan juga di konsol: user melapor dari HP tanpa PC, jadi
                    // penyebab teknis harus terlihat langsung dan bisa disalin —
                    // bukan hanya tersimpan di file yang harus dicari dulu.
                    result.technicalMessage?.takeIf { it.isNotBlank() }?.let {
                        appendMessage("--- detail teknis (salin ini saat melapor) ---\n$it", SemanticLogKind.RAW)
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
                appendMessage("Membatalkan analisis setelah operasi jaringan aktif selesai…", SemanticLogKind.WAIT)
                com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                    "PKG_ANALYZE_CANCEL_REQUEST",
                    activeRequirement ?: packageName.trim()
                )
            }
            isInstalling && engine.requestInstallCancel() -> {
                isCancelling = true
                appendMessage("Membatalkan instalasi di checkpoint berikutnya…", SemanticLogKind.WAIT)
            }
            else -> appendMessage("Operasi sudah selesai atau di tahap yang tidak bisa dibatalkan.", SemanticLogKind.INFO)
        }
    }

    fun analyzeThenInstall(req: String) {
        if (isInstalling) return
        val trimmed = req.trim()
        if (trimmed.isBlank()) {
            appendMessage("Requirement kosong.", SemanticLogKind.WARN)
            return
        }
        if (PackageEngineV2.isBusy()) {
            appendMessage("Instalasi/analisis lain masih berjalan. Tunggu selesai.", SemanticLogKind.WARN)
            return
        }
        activeRequirement = trimmed
        isInstalling = true
        isAnalyzing = true
        isCancelling = false
        consoleLines = emptyList()
        com.zaba.zcode.core.diagnostics.Breadcrumb.log("PKG_ANALYZE_BEGIN", trimmed)
        appendMessage("analyze $trimmed", SemanticLogKind.STEP)
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
                    activeRequirement = null
                    appendMessage(e.message ?: "Analisis gagal", SemanticLogKind.FAIL)
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
                    activeRequirement = null
                    if (plan.errorCode == "CANCELLED") {
                        com.zaba.zcode.core.diagnostics.Breadcrumb.log("PKG_ANALYZE_CANCELLED", trimmed)
                        appendMessage("Analisis dibatalkan. Tidak ada package yang diubah.", SemanticLogKind.STOP)
                    } else {
                        com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                            "PKG_ANALYZE_FAIL", "$trimmed [${plan.errorCode}] ${plan.humanError}"
                        )
                        appendMessage("[${plan.errorCode}] ${plan.humanError}", SemanticLogKind.FAIL)
                        plan.technicalError?.takeIf { it.isNotBlank() }?.let {
                            appendMessage("--- detail teknis ---\n$it", SemanticLogKind.RAW)
                        }
                    }
                    return@withContext
                }
                // BUG C: modul stdlib bukan kegagalan.
                if (plan.stdlib.isNotEmpty() && plan.packages.isEmpty()) {
                    isInstalling = false
                    activeRequirement = null
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log("PKG_STDLIB", trimmed)
                    appendMessage(plan.stdlib.joinToString(" ") { it.reason }, SemanticLogKind.INFO)
                    return@withContext
                }
                // BUG X (2026-08-16): dua cabang ini dulu hanya menulis ke
                // console tanpa Breadcrumb — di Diagnostics, resolve odfpy/
                // telegram/crontab/pypeln tampak berakhir tanpa verdict
                // (WORKER_END lalu senyap). Console sudah jujur; log-nya yang
                // bolong. Samakan dengan cabang PKG_ANALYZE_FAIL di atas.
                if (plan.conflicts.isNotEmpty()) {
                    isInstalling = false
                    activeRequirement = null
                    val detail = plan.conflicts.joinToString("; ") { "${it.name}: ${it.versionA} vs ${it.versionB}" }
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                        "PKG_ANALYZE_FAIL", "$trimmed [DEPENDENCY_CONFLICT] $detail"
                    )
                    appendMessage("[DEPENDENCY_CONFLICT] $detail", SemanticLogKind.FAIL)
                    return@withContext
                }
                if (plan.unavailable.isNotEmpty()) {
                    isInstalling = false
                    activeRequirement = null
                    val detail = plan.unavailable.joinToString("; ") { it.name + ": " + it.reason }
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                        "PKG_ANALYZE_FAIL", "$trimmed [PACKAGE_NOT_AVAILABLE] $detail"
                    )
                    appendMessage("[PACKAGE_NOT_AVAILABLE] $detail", SemanticLogKind.FAIL)
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


    // Dispatcher antrian requirements.txt (v1.0.18): saat engine idle dan
    // antrian berisi, ambil item berikutnya. Risky-dialog otomatis menahan
    // antrian (isInstalling masih true selama dialog tampil). Item di-pop
    // SEBELUM dieksekusi sehingga item gagal tidak mengulang selamanya.
    LaunchedEffect(installQueue, isInstalling, isAnalyzing, isCancelling) {
        if (installQueue.isNotEmpty() && !isInstalling && !isAnalyzing && !isCancelling) {
            val next = installQueue.first()
            installQueue = installQueue.drop(1)
            packageName = next
            appendMessage("antrian: $next (${installQueue.size} tersisa)", SemanticLogKind.STEP)
            analyzeThenInstall(next)
        }
    }

    fun installFromLibrary(req: String) {
        if (isInstalling) return
        selectPipTab(PipTab.MANUAL)
        packageName = req
        analyzeThenInstall(req)
    }

    fun doUninstall(canonical: String) {
        if (isInstalling || isAnalyzing || PackageEngineV2.isBusy()) {
            appendMessage(
                "Tunggu operasi package yang sedang berjalan sebelum uninstall.",
                SemanticLogKind.WARN
            )
            com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                "PKG_UNINSTALL_FAIL", "$canonical: engine busy"
            )
            return
        }
        com.zaba.zcode.core.diagnostics.Breadcrumb.log(
            "PKG_UNINSTALL_REQUEST", canonical
        )
        scope.launch(Dispatchers.Default) {
            val (ok, msg) = engine.uninstall(canonical) { event ->
                scope.launch { appendMessage(event.text, event.kind) }
            }
            withContext(Dispatchers.Main) {
                com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                    if (ok) "PKG_UNINSTALL_OK" else "PKG_UNINSTALL_FAIL",
                    if (ok) canonical else "$canonical: $msg"
                )
                appendMessage(
                    if (ok) "Uninstall $canonical berhasil." else msg,
                    if (ok) SemanticLogKind.OK else SemanticLogKind.FAIL
                )
                refreshInstalled()
            }
        }
    }

    fun doSupportRequest(canonical: String) {
        val (ok, msg) = engine.requestSupport(canonical, "Dari UI katalog (status tidak tersedia/incompatible).")
        appendMessage(msg, if (ok) SemanticLogKind.OK else SemanticLogKind.FAIL)
    }

    fun requestOpenSample(sampleId: String) {
        val entry = SampleLibrary.findSample(sampleId)
        if (entry == null) {
            com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                "LIBRARY_SAMPLE_MISSING", sampleId
            )
            Toast.makeText(context, "Contoh lengkap belum tersedia", Toast.LENGTH_SHORT).show()
            return
        }
        val missing = com.zaba.zcode.core.packageengine.InstalledPackages
            .missingFrom(context, entry.requiresPackage)
        if (missing.isEmpty()) {
            com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                "LIBRARY_SAMPLE_OPEN", entry.id
            )
            selectedPackage = null
            onOpenSample(entry)
        } else {
            com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                "LIBRARY_SAMPLE_NEEDS_PACKAGE",
                "${entry.id} -> ${missing.joinToString(",")}"
            )
            pendingSample = entry
            missingSamplePackages = missing
        }
    }

    LaunchedEffect(Unit) {
        TelemetryStore.init(context)
        refreshInstalled()
        withContext(Dispatchers.Default) {
            runtimeInfo = RuntimeProbe.probe(context)
        }
    }

    // v1.0.18 ②: Detail = HALAMAN penuh yang menimpa layar (pola Samples
    // level-2), bukan dialog card. BackHandler: Detail → daftar → keluar.
    selectedPackage?.let { pkg ->
        val analysis = detailsAnalysis ?: CompatibilityEngine.Analysis(pkg.status, emptyList(), pkg.status.installable())
        BackHandler { selectedPackage = null }
        PackageDetailScreen(
            pkg = pkg,
            analysis = analysis,
            installedVersion = installedMap[pkg.name.lowercase().replace("_", "-")],
            onBack = { selectedPackage = null },
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
                pendingUninstall = pkg
            },
            onSupport = {
                selectedPackage = null
                doSupportRequest(pkg.name.lowercase().replace("_", "-"))
            },
            onOpenSample = ::requestOpenSample
        )
        pendingSample?.let { entry ->
            SampleRequirementDialog(
                entry = entry,
                missingPackages = missingSamplePackages,
                onDismiss = { pendingSample = null },
                onInstallFirst = {
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                        "LIBRARY_SAMPLE_TO_INSTALL",
                        missingSamplePackages.joinToString(",")
                    )
                    // Field Manual hanya menerima satu requirement. Untuk
                    // sample multi-package, mulai dari paket pertama; setelah
                    // aktif, gate akan menunjukkan sisanya saat sample dibuka lagi.
                    packageName = missingSamplePackages.firstOrNull().orEmpty()
                    pendingSample = null
                    selectedPackage = null
                    selectPipTab(PipTab.MANUAL)
                },
                onOpenAnyway = {
                    com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                        "LIBRARY_SAMPLE_OPEN_ANYWAY", entry.id
                    )
                    pendingSample = null
                    selectedPackage = null
                    onOpenSample(entry)
                }
            )
        }
        pendingUninstall?.let { target ->
            val canonical = target.name.lowercase().replace("_", "-")
            AlertDialog(
                onDismissRequest = { pendingUninstall = null },
                title = { Text("Uninstall ${target.displayName}?", fontSize = 16.sp) },
                text = {
                    Text(
                        "Package ini akan dihapus dari environment ZCODE.\n\n" +
                            "ZCODE belum memeriksa reverse dependency. Package lain " +
                            "yang masih membutuhkannya mungkin berhenti bekerja sampai " +
                            "dependency ini dipasang kembali."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingUninstall = null
                        selectedPackage = null
                        doUninstall(canonical)
                    }) {
                        Text("Uninstall", color = Color(0xFFFF6B6B))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingUninstall = null }) {
                        Text("Batal")
                    }
                }
            )
        }
        return
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
                            "← Back",
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
                        TabBox("LIBRARY", activeTab == PipTab.LIBRARY) {
                            selectPipTab(PipTab.LIBRARY)
                        }
                        TabBox("MANUAL INSTALL", activeTab == PipTab.MANUAL) {
                            selectPipTab(PipTab.MANUAL)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (activeTab) {
                PipTab.LIBRARY -> LibraryTab(
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
                    listState = libraryListState,
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
                PipTab.MANUAL -> ManualTab(
                    packageName = packageName,
                    activeRequirement = activeRequirement,
                    onPackageNameChange = { value ->
                        if (!isInstalling && !isAnalyzing) packageName = value
                    },
                    isInstalling = isInstalling,
                    isAnalyzing = isAnalyzing,
                    isCancelling = isCancelling,
                    onInstall = { analyzeThenInstall(packageName) },
                    onCancel = { cancelCurrentAnalyze() },
                    onRequirementsTxt = {
                        appendMessage("requirements.txt: salin SEMUA isinya lalu tap Paste — semua baris akan diinstall berurutan (komentar # dilewati).", SemanticLogKind.INFO)
                    },
                    onQueueLines = { lines ->
                        installQueue = installQueue + lines
                        appendMessage("${lines.size} requirement masuk antrian. Tap Install untuk memulai.", SemanticLogKind.INFO)
                    },
                    consoleLines = consoleLines,
                    consoleScroll = consoleScroll,
                    pageScroll = manualPageScroll
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
                activeRequirement = null
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
                    activeRequirement = null
                }) { Text("Batal") }
            }
        )
    }

    // ---- Package Details dialog (SPEC §11) ----
}

// =====================================================================
// Model console
// =====================================================================

data class ConsoleLine(
    val text: String,
    val kind: SemanticLogKind,
) {
    val displayText: String
        get() = kind.prefix + text
}

private val SemanticLogKind.prefix: String
    get() = when (this) {
        SemanticLogKind.STEP -> "[>] "
        SemanticLogKind.INFO -> "[INFO] "
        SemanticLogKind.WARN -> "[WARN] "
        SemanticLogKind.WAIT -> "[WAIT] "
        SemanticLogKind.OK -> "[OK] "
        SemanticLogKind.FAIL -> "[ERR] "
        SemanticLogKind.STOP -> "[STOP] "
        SemanticLogKind.RAW -> ""
    }

/** Reader sementara untuk string status era emoji dan arsip lama. */
private fun parseLegacyConsoleLine(raw: String): ConsoleLine {
    val line = raw.trim()
    val mappings = listOf(
        listOf("✅", "[OK]") to SemanticLogKind.OK,
        listOf("❌", "[ERR]") to SemanticLogKind.FAIL,
        listOf("⚠️", "⚠", "[WARN]") to SemanticLogKind.WARN,
        listOf("ℹ️", "ℹ", "[INFO]") to SemanticLogKind.INFO,
        listOf("⏳", "[WAIT]") to SemanticLogKind.WAIT,
        listOf("🛑", "[STOP]") to SemanticLogKind.STOP,
        listOf("▶️", "▶", ">", "[>]") to SemanticLogKind.STEP,
    )
    for ((prefixes, kind) in mappings) {
        val prefix = prefixes.firstOrNull { line.startsWith(it) } ?: continue
        return ConsoleLine(line.removePrefix(prefix).trim(), kind)
    }
    return ConsoleLine(line, SemanticLogKind.RAW)
}

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
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (PackageDetails) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // v1.0.18: katalog di-hoist ke remember — loadCatalog() di body
        // LazyColumn berisiko ke-invoke tiap recomposition (tiap keystroke
        // search) di ARMv7; sekalian dipakai placeholder dinamis.
        val allItems = remember { repository.loadCatalog() }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search ${allItems.size} packages...", fontSize = 12.sp) },
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
        // v1.0.18: legend status — ikon tidak pernah dijelaskan di layar.
        // Glyph polos (keputusan user pasca-UAT), selaras statusIcon().
        Text(
            "✓ teruji · △ harusnya jalan · ! eksperimen · ✕ tidak bisa",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
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
                        // Gerbong D v1.0.19: deskripsi kategori — satu kalimat
                        // orientasi saat kategori dibuka (data statis, offline).
                        categoryDescription(cat)?.let { desc ->
                            item {
                                Text(
                                    desc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }
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

// Keputusan user 2026-08-16 (pasca UAT 341 paket): glyph POLOS 14sp, bukan
// emoji berwarna — konsisten prinsip "ikon polos" RENCANA_UPDATE & template
// Detail (✓/∆/✗). Makna: ✓ terpasang/teruji · △ harusnya jalan · ! eksperimen
// · ✕ tidak bisa. Sinyal sekali-sapu tetap ada (hemat 341 tap di 4G lambat),
// polusi visualnya yang dibuang.
private fun statusIcon(status: PackageStatus, installed: Boolean): String = when {
    installed -> "✓"
    status == PackageStatus.TESTED -> "✓"
    status == PackageStatus.COMPATIBLE -> "△"
    status == PackageStatus.EXPERIMENTAL -> "!"
    status == PackageStatus.INCOMPATIBLE -> "✕"
    status == PackageStatus.UNAVAILABLE -> "✕"
    else -> "?"
}

// =====================================================================
// PACKAGE DETAIL — halaman penuh "kartu perpustakaan" (v1.0.18, ②).
// Menggantikan AlertDialog lama: field bernomor bolong (1,6,7,9-10,14…)
// adalah sisa penomoran SPEC yang bocor ke user (screenshot 2026-08-15).
// Template 6 seksi 5W1H, keputusan user: glyph polos ✓/∆/✗ (bukan emoji,
// konsisten RENCANA_UPDATE "ikon polos"), sumber inline TAP-ABLE (↗ →
// Intent.ACTION_VIEW, pola AboutScreen), baris '· dikurasi <tanggal>'.
// Entri belum dikurasi tetap layak: description lama + WHERE dari
// works/doesNotWork/risks + "(belum dikurasi)".
// =====================================================================

@Composable
private fun PackageDetailScreen(
    pkg: PackageDetails,
    analysis: CompatibilityEngine.Analysis,
    installedVersion: String?,
    onBack: () -> Unit,
    onInstallTested: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onSupport: () -> Unit,
    onOpenSample: (String) -> Unit,
) {
    val ctx = LocalContext.current
    fun openUrl(url: String) {
        try {
            ctx.startActivity(android.content.Intent(
                android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Tidak ada browser untuk membuka tautan", Toast.LENGTH_SHORT).show()
        }
    }

    val statusColor = when (analysis.status) {
        PackageStatus.INCOMPATIBLE, PackageStatus.UNAVAILABLE -> Color(0xFFB3261E)
        PackageStatus.TESTED, PackageStatus.INSTALLED -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.primary
    }
    val statusGlyph = when {
        installedVersion != null -> "✓"
        analysis.status == PackageStatus.TESTED -> "✓"
        analysis.status == PackageStatus.INCOMPATIBLE ||
            analysis.status == PackageStatus.UNAVAILABLE -> "✗"
        else -> "∆"
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ---- Topbar (pola SamplesScreen: ← + nama) ----
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "←", fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable(onClick = onBack).padding(10.dp)
                )
                Text(
                    pkg.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$statusGlyph ${analysis.status.label}" +
                        (pkg.testedVersion?.let { " · v$it" } ?: ""),
                    fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.SemiBold
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // identitas ringkas
            Text(
                "${pkg.name} · ${pkg.type} · Python ${pkg.python.joinToString("/")}" +
                    (if (installedVersion != null) " · terpasang v$installedVersion" else ""),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            DetailSection("WHAT IS IT",
                pkg.longDescription.ifBlank { pkg.description.ifBlank { "(belum dikurasi)" } },
                pkg.sources.filter { it.untuk == "what" }, ::openUrl)

            if (pkg.whyUse.isNotBlank() || pkg.useCases.isNotEmpty()) {
                DetailSection("WHY USE IT",
                    pkg.whyUse.ifBlank { pkg.useCases.joinToString(", ") },
                    pkg.sources.filter { it.untuk == "why" }, ::openUrl)
            }

            if (pkg.example.isNotBlank()) {
                Text("HOW TO USE", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                // Panel kode: hitam pekat DISENGAJA di semua tema (aturan panel
                // terminal, bukan layar navigasi).
                SelectionContainer {
                    Text(
                        pkg.example, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        lineHeight = 15.sp, color = Color(0xFF9AE6B4),
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFF050806), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                }
                SourceChips(pkg.sources.filter { it.untuk == "how" }, ::openUrl)
                Spacer(Modifier.height(8.dp))
            }

            pkg.sampleId?.takeIf { it.isNotBlank() }?.let { sampleId ->
                TextButton(onClick = { onOpenSample(sampleId) }) {
                    Text("Coba contoh lengkap →", fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            // WHERE — milik kita, dirakit dari works/doesNotWork/risks + analysis
            Text("WHERE IT RUNS (ZCODE · ARMv7)", fontSize = 11.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            if (analysis.reasons.isNotEmpty()) {
                WhereLine("∆", analysis.reasons.joinToString(" "))
            }
            pkg.works.forEach { WhereLine("✓", it) }
            if (pkg.dependencies.isNotEmpty()) {
                WhereLine("·", "Dependensi: ${pkg.dependencies.joinToString(", ")}")
            }
            pkg.risks.forEach { WhereLine("∆", it) }
            pkg.doesNotWork.forEach { WhereLine("✗", it) }
            if (pkg.works.isEmpty() && pkg.risks.isEmpty() && pkg.doesNotWork.isEmpty()
                && analysis.reasons.isEmpty()) {
                WhereLine("∆", "Belum diverifikasi di ZCODE — status ${analysis.status.label}.")
            }
            SourceChips(pkg.sources.filter { it.untuk == "where" }, ::openUrl)
            Spacer(Modifier.height(12.dp))

            if (pkg.whoMadeIt.isNotBlank() || pkg.publisher.isNotBlank() || pkg.license.isNotBlank()) {
                DetailSection("WHO MADE IT",
                    pkg.whoMadeIt.ifBlank {
                        listOf(pkg.publisher, pkg.license).filter { it.isNotBlank() }.joinToString(" · ")
                    },
                    pkg.sources.filter { it.untuk == "who" }, ::openUrl)
            }

            // Belajar (ID) + sumber umum (source lama ikut tap-able di sini)
            val learn = pkg.sources.filter { it.untuk == "learn-id" }
            if (learn.isNotEmpty()) {
                Text("BELAJAR (ID)", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                SourceChips(learn, ::openUrl)
                Spacer(Modifier.height(12.dp))
            }
            if (pkg.source.isNotBlank() && pkg.sources.isEmpty()) {
                // entri belum dikurasi: link PyPI lama tetap tap-able
                SourceChips(listOf(SourceRef("what",
                    pkg.source.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                    pkg.source)), ::openUrl)
                Spacer(Modifier.height(8.dp))
            }

            Text(
                if (pkg.curatedAt.isNotBlank()) "· dikurasi ${pkg.curatedAt}"
                else "· (belum dikurasi — bantu lengkapi lewat About & Contribute)",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        // ---- Tombol aksi sticky (logika per-status pindah utuh dari dialog) ----
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Spacer(Modifier.weight(1f))
                when {
                    installedVersion != null && analysis.status == PackageStatus.UPDATE_AVAILABLE ->
                        TextButton(onClick = onInstallTested) { Text("Update", fontSize = 14.sp) }
                    analysis.status == PackageStatus.TESTED ->
                        TextButton(onClick = onInstallTested) { Text("Install Tested Version", fontSize = 14.sp) }
                    analysis.status == PackageStatus.COMPATIBLE ->
                        TextButton(onClick = onInstall) { Text("Install", fontSize = 14.sp) }
                    analysis.status == PackageStatus.EXPERIMENTAL ->
                        TextButton(onClick = onInstall) { Text("Install Experimental", fontSize = 14.sp) }
                    analysis.status == PackageStatus.INCOMPATIBLE ->
                        TextButton(onClick = onSupport) { Text("Kenapa? / Request Support", fontSize = 14.sp) }
                    analysis.status == PackageStatus.UNAVAILABLE ->
                        TextButton(onClick = onSupport) { Text("Request Support", fontSize = 14.sp) }
                    analysis.status == PackageStatus.INSTALLED ->
                        TextButton(onClick = onUninstall) { Text("Uninstall", fontSize = 14.sp) }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    body: String,
    sources: List<SourceRef>,
    onOpen: (String) -> Unit
) {
    Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(2.dp))
    SelectionContainer {
        Text(body, fontSize = 13.sp, lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface)
    }
    SourceChips(sources, onOpen)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SourceChips(sources: List<SourceRef>, onOpen: (String) -> Unit) {
    if (sources.isEmpty()) return
    Row(modifier = Modifier.padding(top = 2.dp)) {
        sources.take(3).forEach { s ->
            Text(
                "[${s.label} ↗]",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onOpen(s.url) }.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun WhereLine(glyph: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(glyph, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            color = when (glyph) {
                "✓" -> Color(0xFF2E7D32)
                "✗" -> Color(0xFFB3261E)
                else -> Color(0xFFB08A00)
            })
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface)
    }
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
    activeRequirement: String?,
    onPackageNameChange: (String) -> Unit,
    isInstalling: Boolean,
    isAnalyzing: Boolean,
    isCancelling: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRequirementsTxt: () -> Unit,
    onQueueLines: (List<String>) -> Unit,
    consoleLines: List<ConsoleLine>,
    consoleScroll: androidx.compose.foundation.ScrollState,
    pageScroll: androidx.compose.foundation.ScrollState
) {
    // `verticalScroll` mendelegasikan FocusTargetModifierNode pada Foundation
    // 1.6.1. Memasangnya secara kondisional saat adjustResize membuka IME
    // menyisipkan focus target di atas TextField yang sudah aktif (Google
    // b/274655703), lalu Backspace berikutnya menjatuhkan FocusOwnerImpl.
    // Topology sekarang permanen; resize hanya mengubah angka tinggi console.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val consoleHeight = (maxHeight - 250.dp).coerceAtLeast(220.dp)
        val inputLocked = activeRequirement != null || isInstalling || isAnalyzing
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(pageScroll)
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
            val installReady = packageName.isNotBlank()
            Button(
                onClick = {
                    when {
                        isCancelling -> Unit
                        cancellable -> onCancel()
                        else -> onInstall()
                    }
                },
                // Jangan toggle enabled/readOnly pada subtree yang sedang
                // menerima input di Compose 1.6.1. Tombol kosong tetap memberi
                // feedback "Requirement kosong" lewat validasi onInstall.
                enabled = true,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        cancellable -> Color(0xFF8B2E2E)
                        installReady -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    },
                    contentColor = if (installReady || cancellable)
                        MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
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
        if (inputLocked) {
            Text(
                "Requirement dikunci selama operasi: ${activeRequirement ?: packageName}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp)
            )
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
                    if (inputLocked) {
                        Toast.makeText(
                            ctx,
                            "Requirement sedang diproses — tunggu operasi selesai",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (teks.isEmpty()) {
                        Toast.makeText(ctx, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                    } else {
                        // v1.0.18: multi-baris TIDAK dibuang lagi. Baris pertama
                        // mengisi field; sisanya (bukan komentar/#) ditawarkan
                        // sebagai ANTRIAN install berurutan lewat onQueueLines.
                        val bersih = teks.lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }
                            .toList()
                        val baris = bersih.firstOrNull().orEmpty()
                        onPackageNameChange(baris)
                        if (bersih.size > 1) {
                            onQueueLines(bersih.drop(1))
                            Toast.makeText(
                                ctx,
                                "${bersih.size} requirement terdeteksi — sisanya masuk antrian setelah Install",
                                Toast.LENGTH_LONG
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
                // Jenis modifier tidak berubah saat IME/rotate; hanya nilai Dp
                // yang menyesuaikan. Minimum 220dp menjaga console landscape.
                .height(consoleHeight)
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
                        SemanticLogKind.STEP -> Color(0xFF8A9BB0)
                        SemanticLogKind.INFO -> Color(0xFF9AE6B4)
                        SemanticLogKind.WARN -> Color(0xFFFFC857)
                        SemanticLogKind.WAIT -> Color(0xFFB8C4D6)
                        SemanticLogKind.OK -> Color(0xFF39FF14)
                        SemanticLogKind.FAIL -> Color(0xFFFF6B6B)
                        SemanticLogKind.STOP -> Color(0xFFFF9F43)
                        SemanticLogKind.RAW -> Color(0xFFD7DBE0)
                    }
                    Text(
                        line.displayText,
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
    } // BoxWithConstraints (A0 rotate resilience)
}

private fun initialConsole(): List<ConsoleLine> = listOf(
    ConsoleLine("ZCODE Package Engine V2 — Chaquopy 3.11", SemanticLogKind.STEP),
    ConsoleLine("Masukkan requirement (bukan perintah shell), lalu tap Install.", SemanticLogKind.INFO),
    ConsoleLine("Instalasi transaksional: verifikasi + smoke test + rollback otomatis.", SemanticLogKind.INFO),
    ConsoleLine("Flow: Parse → Resolve → Download → Verify → Extract → Smoke → Activate", SemanticLogKind.INFO),
)

// Gerbong D v1.0.19: deskripsi 11 kategori Library — satu kalimat orientasi
// "buat apa kategori ini" saat dibuka. Bahasa santai-jelas, jujur soal batas
// ARMv7 di kategori yang memang berat.
private fun categoryDescription(cat: String): String? = when (cat) {
    "Web / API / Networking" ->
        "Ngobrol sama internet: ambil data API, scraping, bikin server mini. Mulai dari: requests."
    "Data / Math / Science" ->
        "Hitung, olah tabel, gambar grafik. Trio andalan: numpy + pandas + matplotlib (grafik jadi PNG)."
    "Files / Office / Document" ->
        "Bikin & baca Word, Excel, PDF, PowerPoint langsung dari HP. Mulai dari: python-docx / openpyxl."
    "Image / Audio / Media" ->
        "Edit gambar, bikin QR, proses audio. Mulai dari: pillow. (Video render butuh ffmpeg — tidak tersedia.)"
    "Database / Storage" ->
        "Simpan data yang awet: SQLite bawaan, ORM (peewee/sqlalchemy), key-value (diskcache)."
    "AI / ML / NLP" ->
        "Teks & kecerdasan buatan. Jujur: model besar (torch/tensorflow) mustahil di ARMv7 — yang jalan: nltk, openai API, sympy."
    "Security / Cryptography" ->
        "Enkripsi, hash, tanda tangan digital. Mulai dari: cryptography / pycryptodome."
    "Utilities / CLI / Terminal" ->
        "Pemanis & pembantu terminal: rich (tabel warna), tqdm (progress bar), tabulate."
    "Automation / Scripting" ->
        "Otomasi tugas: jadwal (apscheduler), unduh video (yt_dlp), kirim email (yagmail)."
    "Testing / Quality / Debugging" ->
        "Uji kodemu sendiri: pytest + coverage. (tox/virtualenv tidak jalan — butuh subprocess python.)"
    "GUI / Games / App Framework" ->
        "Jujur: GUI native (kivy/pygame/tkinter) tidak didukung runtime ZCODE. Alternatif tercatat di kartu masing-masing."
    else -> null
}
