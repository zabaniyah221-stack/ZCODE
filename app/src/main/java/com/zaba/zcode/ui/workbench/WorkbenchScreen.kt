package com.zaba.zcode.ui.workbench

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zaba.zcode.ui.editor.EditorScreen
import com.zaba.zcode.ui.theme.ZcodeColors

/**
 * WorkbenchScreen — Fase 0 Pydroid-style layer swap (not VSCode panel)
 * Topbar faded grey ≡ + +  |  Editor OLED + Gutter + QuickTools + FAB above handle
 * ≡ = three lines only
 */
@Composable
fun WorkbenchScreen(
    onRun: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateToOutput: () -> Unit = { onRun("") }
) {
    Scaffold(
        topBar = {
            // Topbar faded grey #3A4452 — spec: ≡ left, + right
            Surface(color = androidx.compose.ui.graphics.Color(0xFF3A4452)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // ≡ three lines — text only, no IconButton (Fase 0 iconless)
                    Box(modifier = Modifier.clickable(onClick = onOpenSettings).padding(8.dp)) {
                        Text("≡", style = MaterialTheme.typography.titleLarge)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("main.py", style = MaterialTheme.typography.titleSmall)
                        Text("/storage/emulated/0/...", style = MaterialTheme.typography.labelSmall)
                    }
                    // + add tab — top right, text only
                    Box(modifier = Modifier.clickable(onClick = { /* add untitled_N.py */ }).padding(8.dp)) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        },
        floatingActionButton = {
            // FAB above handle, bottom right — spec FAB above handle
            FloatingActionButton(
                onClick = { onNavigateToOutput(); onRun("// code") },
                containerColor = androidx.compose.ui.graphics.Color(0xFFFFD54F)
            ) {
                Text("▶")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tabs row — main.py active yellow underline
            TabRowDemo()
            // Editor OLED + Gutter
            Box(modifier = Modifier.weight(1f)) {
                EditorScreen(
                    code = "# Fase 0 skeleton — Ace 1.44.0 will be in WebView file://\nprint(\"hello ZCODE\")\n",
                    onCodeChange = {}
                )
            }
            // QuickTools handle — Tab | : | ; | ' | # | ( | ) | [ | ] | def | return | import
            QuickToolsBar()
            // Output navigate hint for PTY pindah layer
            Text("output navigate", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(2.dp))
        }
    }
}

@Composable
private fun TabRowDemo() {
    // Placeholder — real tabs will be ScrollableTabRow with main.py unsaved dot
    Surface(color = androidx.compose.ui.graphics.Color(0xFF3A4452)) {
        Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("main.py", color = androidx.compose.ui.graphics.Color(0xFFFFEB3B))
        }
    }
}

@Composable
private fun QuickToolsBar() {
    // Fase 0: Row handle — horizontal scroll, no icon
    Surface(color = androidx.compose.ui.graphics.Color(0xFF0F1712)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Tab", ":", ";", "'", "#", "(", ")", "[", "]", "def", "return", "import").forEach {
                AssistChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) })
            }
        }
    }
}
