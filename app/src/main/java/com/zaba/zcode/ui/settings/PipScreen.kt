package com.zaba.zcode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaba.zcode.core.execution.ExecutionEngine
import kotlinx.coroutines.launch

/**
 * PipScreen — Settings → Pip: instal package Python via pip + Katalog LIBRARY.
 */
@Composable
fun PipScreen(
    context: android.content.Context,
    onBack: () -> Unit
) {
    var packageName by remember { mutableStateOf("") }
    var logText by remember { mutableStateOf(initialLog()) }
    var isInstalling by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("LIBRARY") }
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }

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
        val ok = ExecutionEngine.startPipStream(
            context = context,
            packageName = trimmed,
            onLog = { line -> scope.launch { appendLog(line) } },
            onDone = { success, exitCode ->
                scope.launch {
                    if (success) {
                        appendLog("\n✅ Package '$trimmed' installed successfully!\n")
                    } else {
                        appendLog("\n❌ Installation failed (exit code $exitCode).\n")
                    }
                    isInstalling = false
                }
            }
        )
        if (!ok) {
            appendLog("\n❌ Package name tidak valid: '$trimmed'\n")
            isInstalling = false
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
                            "Pip Package Manager",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    // Tab Segmented Control
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = "LIBRARY" }
                                .background(if (activeTab == "LIBRARY") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "LIBRARY",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeTab == "LIBRARY") MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = "MANUAL" }
                                .background(if (activeTab == "MANUAL") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "MANUAL INSTALL",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeTab == "MANUAL") MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
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
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search packages...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(fontSize = 14.sp)
                )

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val allItems = com.zaba.zcode.core.library.LibraryCatalog.load(context)
                    if (searchQuery.isNotBlank()) {
                        val filtered = allItems.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            it.summary.contains(searchQuery, ignoreCase = true)
                        }
                        items(filtered) { item ->
                            LibraryCatalogRow(item = item, onInstall = {
                                packageName = item.installName
                                activeTab = "MANUAL"
                                install(item.installName)
                            })
                        }
                    } else {
                        val categories = allItems.map { it.category }.distinct()
                        categories.forEach { cat ->
                            val isExpanded = expandedCategories.contains(cat)
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedCategories = if (isExpanded) {
                                                expandedCategories - cat
                                            } else {
                                                expandedCategories + cat
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (isExpanded) "▾" else "▸",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Divider(color = Color.White.copy(alpha = 0.05f))
                            }
                            if (isExpanded) {
                                val catItems = allItems.filter { it.category == cat }
                                items(catItems) { item ->
                                    LibraryCatalogRow(item = item, onInstall = {
                                        packageName = item.installName
                                        activeTab = "MANUAL"
                                        install(item.installName)
                                    })
                                }
                            }
                        }
                    }
                }
            } else {
                // MANUAL INSTALL TAB
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
                            color = Color(0xFF39FF14),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCatalogRow(
    item: com.zaba.zcode.core.library.LibraryItem,
    onInstall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tag = when (item.compatibility) {
            com.zaba.zcode.core.library.Compatibility.RECOMMENDED -> "✅"
            com.zaba.zcode.core.library.Compatibility.HEAVY -> "⚠️"
            com.zaba.zcode.core.library.Compatibility.UNSUPPORTED -> "❌"
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tag, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = item.summary,
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (item.note.isNotBlank()) {
                Text(
                    text = item.note,
                    fontSize = 10.sp,
                    color = Color.LightGray.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onInstall,
            colors = ButtonDefaults.buttonColors(
                containerColor = when (item.compatibility) {
                    com.zaba.zcode.core.library.Compatibility.RECOMMENDED -> MaterialTheme.colorScheme.primary
                    com.zaba.zcode.core.library.Compatibility.HEAVY -> Color(0xFFFFB000)
                    com.zaba.zcode.core.library.Compatibility.UNSUPPORTED -> Color(0xFFFF4B4B)
                }
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Install", fontSize = 11.sp, color = Color.White)
        }
    }
    Divider(color = Color.White.copy(alpha = 0.05f))
}

private fun initialLog(): String =
    "ZCODE Pip Installer Layer — Chaquopy 3.11\n" +
        "-".repeat(45) + "\n" +
        "Ketik nama package di atas, lalu tap Install.\n"
