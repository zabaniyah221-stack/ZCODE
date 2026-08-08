package com.zaba.zcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zaba.zcode.core.files.Paths
import com.zaba.zcode.ui.settings.AboutScreen
import com.zaba.zcode.ui.settings.PipScreen
import com.zaba.zcode.ui.terminal.TerminalScreen
import com.zaba.zcode.ui.theme.ZcodeTheme
import com.zaba.zcode.ui.workbench.WorkbenchScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: WorkspaceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZcodeTheme(themeType = vm.themeType) {
                AppNavHost(vm = vm)
            }
        }
    }
}

@Composable
private fun AppNavHost(vm: WorkspaceViewModel) {
    val nav = rememberNavController()
    // FIX: `applicationContext` tidak resolvable di scope composable —
    // ambil dari LocalContext.current (compile error sebelumnya: unresolved reference)
    val appContext = LocalContext.current.applicationContext
    NavHost(navController = nav, startDestination = "editor") {
        composable("editor") {
            WorkbenchScreen(
                vm = vm,
                onRun = { filename ->
                    // ▶ Run → pindah layer ke Terminal PTY full-screen (bukan panel)
                    nav.navigate("output/$filename")
                },
                onNavigateToPip = { nav.navigate("pip") },
                onNavigateToAbout = { nav.navigate("about") }
            )
        }
        composable("output/{filename}") { backStackEntry ->
            val filename = backStackEntry.arguments?.getString("filename") ?: "main.py"
            TerminalScreen(
                filename = filename,
                filesDir = Paths.filesDir(appContext),
                context = appContext,
                onBack = { nav.navigateUp() }
            )
        }
        composable("pip") {
            PipScreen(
                context = appContext,
                onBack = { nav.navigateUp() }
            )
        }
        composable("about") {
            AboutScreen(onBack = { nav.navigateUp() })
        }
    }
}
