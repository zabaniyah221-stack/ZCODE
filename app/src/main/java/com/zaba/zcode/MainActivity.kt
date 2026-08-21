package com.zaba.zcode

import android.os.Bundle
import android.os.Process
import android.widget.Toast
import com.zaba.zcode.core.diagnostics.Breadcrumb
import com.zaba.zcode.core.runtime.NativeRuntimeState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zaba.zcode.core.files.Paths
import com.zaba.zcode.core.samples.SampleEntry
import com.zaba.zcode.ui.samples.SamplesScreen
import com.zaba.zcode.ui.settings.AboutScreen
import com.zaba.zcode.ui.settings.DiagnosticsScreen
import com.zaba.zcode.ui.settings.PackageOperationViewModel
import com.zaba.zcode.ui.settings.PipScreen
import com.zaba.zcode.ui.settings.SettingsScreen
import com.zaba.zcode.ui.terminal.TerminalScreen
import com.zaba.zcode.ui.theme.ZcodeTheme
import com.zaba.zcode.ui.theme.fontFamilyFor
import com.zaba.zcode.ui.workbench.WorkbenchScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: WorkspaceViewModel by viewModels()
    private val packageOperations: PackageOperationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previousPid = intent.getIntExtra(EXTRA_REBIRTH_FROM_PID, -1)
        val restarted = previousPid > 0 && NativeRuntimeState.completeRestart(this, previousPid)

        fun showWorkspace() {
            setContent {
                // Audit 2026-08: jenis font pilihan user berlaku untuk seluruh UI.
                ZcodeTheme(
                    themeType = vm.themeType,
                    fontFamily = fontFamilyFor(vm.appFontFamily)
                ) {
                    AppNavHost(
                        vm = vm,
                        packageOperations = packageOperations,
                        onRestartRuntime = ::requestRuntimeRestart,
                    )
                }
            }
        }

        if (restarted) {
            setContentView(BinaryRainView(this, "Menyiapkan workspace…"))
            // One frame is a real process-handoff frame, not a cosmetic delay.
            window.decorView.postOnAnimation {
                showWorkspace()
                window.decorView.post {
                    Toast.makeText(
                        this,
                        "Python berhasil dimulai ulang.\nProgram siap dijalankan.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            showWorkspace()
        }
    }

    private fun requestRuntimeRestart(): Boolean {
        if (!vm.flushSaveSync(verifyAllDrafts = true)) {
            return false
        }
        if (!NativeRuntimeState.prepareRestart(this)) {
            Breadcrumb.log("RUNTIME_RESTART_ABORT", "receipt gagal disimpan")
            return false
        }
        val oldPid = Process.myPid()
        setContentView(BinaryRainView(this))
        startActivity(ZcodeRebirthActivity.intent(this, oldPid))
        overridePendingTransition(0, 0)
        return true
    }

    override fun onPause() {
        super.onPause()
        vm.flushSaveSync()
    }

    companion object {
        const val EXTRA_REBIRTH_FROM_PID = "zcode.rebirth.from_pid"
    }
}

@Composable
private fun AppNavHost(
    vm: WorkspaceViewModel,
    packageOperations: PackageOperationViewModel,
    onRestartRuntime: () -> Boolean,
) {
    val nav = rememberNavController()
    // FIX: `applicationContext` tidak resolvable di scope composable —
    // ambil dari LocalContext.current (compile error sebelumnya: unresolved reference)
    val appContext = LocalContext.current.applicationContext

    fun openSampleInEditor(entry: SampleEntry) {
        val (ok, msg) = vm.createSampleFromAsset(
            entry.assetPath,
            entry.id,
            companionAssets = entry.companionAssets
        )
        Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
        if (ok && !nav.popBackStack("editor", inclusive = false)) {
            nav.navigate("editor") { launchSingleTop = true }
        }
    }

    NavHost(navController = nav, startDestination = "editor") {
        composable("editor") {
            WorkbenchScreen(
                vm = vm,
                onRun = { filename ->
                    // ▶ Run → pindah layer ke Terminal PTY full-screen (bukan panel)
                    nav.navigate("output/$filename")
                },
                onNavigateToPip = { nav.navigate("pip") },
                onNavigateToAbout = { nav.navigate("about") },
                onNavigateToSamples = { nav.navigate("samples") },
                onNavigateToDiagnostics = { nav.navigate("diagnostics") },
                onNavigateToSettings = { nav.navigate("settings") }
            )
        }
        // SAMPLES (redesign 2026-08): halaman 2 level; tap sample → file baru
        // dibuat dari assets → balik ke editor dengan tab baru terbuka (keputusan user)
        composable("samples") {
            SamplesScreen(
                onBack = { nav.navigateUp() },
                onPick = ::openSampleInEditor,
                // Gerbong B (v1.0.19): dialog "butuh paket X" → INSTALL MODULES
                onGoToInstallModules = { nav.navigate("pip") }
            )
        }
        composable("output/{filename}") { backStackEntry ->
            val filename = backStackEntry.arguments?.getString("filename") ?: "main.py"
            TerminalScreen(
                filename = filename,
                filesDir = Paths.filesDir(appContext),
                context = appContext,
                onBack = { nav.navigateUp() },
                showPythonIndicator = vm.showPythonIndicator, // F2.4: toggle indikator Python
                terminalOutputLimit = vm.terminalOutputLimit,
                themeType = vm.themeType,
                // Audit 2026-08: ukuran font setting kini khusus terminal.
                terminalFontSize = vm.terminalFontSize,
                // A3 v1.0.19: tap traceback → balik ke editor di baris salah.
                onGotoEditorLine = { tracebackFile, line ->
                    vm.requestGotoLine(tracebackFile, line)
                    nav.navigateUp()
                },
                tracebackJumpEnabled = vm.tracebackJumpEnabled
            )
        }
        composable("pip") {
            PipScreen(
                context = appContext,
                operations = packageOperations,
                onBack = { nav.navigateUp() },
                onOpenSample = ::openSampleInEditor,
                onRestartRuntime = onRestartRuntime
            )
        }
        // DIAGNOSTICS (build #3): layar penuh sendiri, bukan kotak kecil di About.
        // User memakai HP tanpa PC — ini satu-satunya jendela ke dalam aplikasi.
        composable("diagnostics") {
            DiagnosticsScreen(
                context = appContext,
                onBack = { nav.navigateUp() }
            )
        }
        composable("about") {
            AboutScreen(onBack = { nav.navigateUp() })
        }
        // F1.3: Settings — halaman pengaturan (route "settings")
        composable("settings") {
            SettingsScreen(
                vm = vm,
                onBack = { nav.navigateUp() },
            )
        }
    }
}
