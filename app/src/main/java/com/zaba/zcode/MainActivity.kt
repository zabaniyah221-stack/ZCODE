package com.zaba.zcode

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zaba.zcode.ui.editor.EditorScreen
import com.zaba.zcode.ui.theme.ZcodeTheme
import com.zaba.zcode.ui.workbench.WorkbenchScreen

// Hilt disabled Fase 0 — enable Fase 1
// @AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // ZMUX lesson: debounce resize 100ms to avoid prompt jump 4-5 lines
    private val resizeHandler = Handler(Looper.getMainLooper())
    private val resizeRunnable = Runnable { /* terminalView.updateSize() will be called via Compose */ }
    private val resizeDebounceMs = 100L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ZcodeTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "editor") {
                    composable("editor") {
                        WorkbenchScreen(
                            onRun = { code ->
                                nav.navigate("output")
                            },
                            onOpenSettings = { nav.navigate("settings") }
                        )
                    }
                    composable("output") {
                        Box(Modifier.fillMaxSize()) {
                            Text("Output PTY — Fase 1: PTY terminal (ketik langsung, no stdin field)")
                        }
                    }
                    composable("settings") {
                        Box(Modifier.fillMaxSize()) {
                            Text("Settings — Theme / Plugin/Addon / Pip — Fase 0 skeleton")
                        }
                    }
                }
            }
        }
    }

    fun debouncedUpdateSize() {
        resizeHandler.removeCallbacks(resizeRunnable)
        resizeHandler.postDelayed(resizeRunnable, resizeDebounceMs)
    }
}
