package com.zaba.zcode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaba.zcode.WorkspaceViewModel
import com.zaba.zcode.ui.theme.ZcodeThemeType

/**
 * SettingsScreen — halaman pengaturan ZCODE (F1.3).
 * LazyColumn dengan header kelompok (Tampilan, Editor, Run, Privasi & Data)
 * supaya ringan di HP ampas ARMv7 (jangan Column scroll raksasa).
 *
 * F1.3: cangkang + route + item sidebar.
 * F1.4: Clear All dipindah ke sini (Privasi & Data).
 * F1.5: Pemilih tema (RETRO/DRACULA/TOKYO_NIGHT).
 * F1.6: Cerminkan toggle Symbol bar & Auto Trim.
 * F1.7: Toggle auto-close brackets (CM6).
 * F1.8: Toggle selection match highlight (CM6).
 */
@Composable
fun SettingsScreen(
    vm: WorkspaceViewModel,
    onBack: () -> Unit,
    onClearAll: () -> Unit
) {
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
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
                        "Settings",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    ) { padding ->
        // v1.0.18 (saran user): seksi model EXPAND ala Library — layar lega,
        // satu bahasa navigasi (Library/Samples/Settings). Default: hanya
        // "Tampilan" terbuka supaya layar pertama tidak kosong. State tidak
        // perlu persist — preferensi buka-tutup bukan data.
        var openSections by remember { mutableStateOf(setOf("tampilan")) }
        fun toggle(id: String) {
            openSections = if (id in openSections) openSections - id else openSections + id
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ===========================================================
            // 🎨 Tampilan / Appearance
            // ===========================================================
            item { SettingsGroupHeader("🎨 Tampilan / Appearance", expanded = "tampilan" in openSections, onToggle = { toggle("tampilan") }) }

            // F1.5: Pemilih tema yang jelas (RETRO/DRACULA/TOKYO_NIGHT)
            if ("tampilan" in openSections) item {
                ThemePickerRow(
                    current = vm.themeType,
                    onSelect = { vm.setTheme(it) }
                )
            }

            if ("tampilan" in openSections) item {
                FontSizeRow(
                    current = vm.terminalFontSize,
                    onSelect = { vm.setFontSize(it) }
                )
            }

            if ("tampilan" in openSections) item {
                FontFamilyRow(
                    current = vm.appFontFamily,
                    onSelect = { vm.setFontFamily(it) }
                )
            }

            item { SettingsDivider() }

            // ===========================================================
            // ⌨️ Editor
            // ===========================================================
            item { SettingsGroupHeader("⌨️ Editor", expanded = "editor" in openSections, onToggle = { toggle("editor") }) }

            // F1.6: Cerminkan toggle Symbol bar
            if ("editor" in openSections) item {
                SettingsToggleRow(
                    label = "Symbol bar",
                    description = "Baris simbol cepat di bawah editor",
                    checked = vm.symbolBarEnabled,
                    onCheckedChange = { vm.setSymbolBar(it) }
                )
            }

            // F1.7: Toggle auto-close brackets (CM6)
            if ("editor" in openSections) item {
                SettingsToggleRow(
                    label = "Auto-close brackets",
                    description = "Tutup kurung/kutip otomatis saat mengetik",
                    checked = vm.closeBracketsEnabled,
                    onCheckedChange = { vm.setCloseBrackets(it) }
                )
            }

            // F1.8: Toggle selection match highlight (CM6)
            if ("editor" in openSections) item {
                SettingsToggleRow(
                    label = "Sorot kata yang diseleksi",
                    description = "Highlight semua kemunculan kata yang dipilih",
                    checked = vm.highlightSelectionMatchesEnabled,
                    onCheckedChange = { vm.setHighlightSelectionMatches(it) }
                )
            }

            item { SettingsDivider() }

            // ===========================================================
            // ▶️ Run & Terminal
            // ===========================================================
            item { SettingsGroupHeader("▶️ Run & Terminal", expanded = "run" in openSections, onToggle = { toggle("run") }) }

            // F1.6: Cerminkan toggle Auto Trim on Run
            if ("run" in openSections) item {
                SettingsToggleRow(
                    label = "Auto Trim saat Run",
                    description = "Buang spasi akhir tiap baris sebelum eksekusi",
                    checked = vm.isPluginEnabled("auto_trim_on_run"),
                    onCheckedChange = { vm.setPluginEnabled("auto_trim_on_run", it) }
                )
            }

            // F2.4: Toggle indikator "Menyalakan Python…" di terminal
            if ("run" in openSections) item {
                SettingsToggleRow(
                    label = "Indikator \"Menyalakan Python…\"",
                    description = "Tampilkan status cold-start Python di terminal",
                    checked = vm.showPythonIndicator,
                    onCheckedChange = { vm.setPythonIndicator(it) }
                )
            }

            // F2.2: Batas Output Terminal (Ring Buffer)
            if ("run" in openSections) item {
                OutputLimitRow(
                    current = vm.terminalOutputLimit,
                    onSelect = { vm.setOutputLimit(it) }
                )
            }

            item { SettingsDivider() }

            // ===========================================================
            // 🔒 Privasi & Data
            // ===========================================================
            item { SettingsGroupHeader("🔒 Privasi & Data", expanded = "privasi" in openSections, onToggle = { toggle("privasi") }) }

            // F1.4: Clear All dipindah dari TOOLS ke sini (destruktif, bukan tool)
            if ("privasi" in openSections) item {
                SettingsDestructiveRow(
                    label = "Clear All Drafts & Files",
                    description = "Hapus semua file .py di workspace",
                    onClick = onClearAll
                )
            }

            item { SettingsDivider() }

            // ===========================================================
            // ℹ️ Tentang
            // ===========================================================
            item { SettingsGroupHeader("ℹ️ Tentang", expanded = "tentang" in openSections, onToggle = { toggle("tentang") }) }

            if ("tentang" in openSections) item {
                // v1.0.18: JANGAN hardcode versi — layar ini sempat berbohong
                // "v1.0.0" saat APK sudah 1.0.18 (laporan user 2026-08-16,
                // "satu ZCODE dua versi"). Baca dari packageManager seperti
                // AboutScreen: satu sumber kebenaran.
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val versi = remember {
                    try {
                        "v" + (ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?")
                    } catch (e: Exception) { "v?" }
                }
                SettingsInfoRow(label = "Versi app", value = versi)
            }

            if ("tentang" in openSections) item {
                SettingsInfoRow(
                    label = "Tema aktif",
                    value = vm.themeType.name.replace('_', ' ')
                )
            }

            // Spacer bawah supaya tidak tertutup FAB/keyboard
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// =====================================================================
// Komponen kecil SettingsScreen
// =====================================================================

@Composable
private fun SettingsGroupHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    // v1.0.18: header seksi tap-able (model expand ala Library — saran user).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (expanded) "▾" else "▸",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SettingsDivider() {
    Divider(
        color = Color.White.copy(alpha = 0.06f),
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsDestructiveRow(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = Color(0xFFFFB4AB)
        )
        Text(
            description,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun SettingsInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ThemePickerRow(
    current: ZcodeThemeType,
    onSelect: (ZcodeThemeType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            "Tema aplikasi",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            ZcodeThemeType.values().forEach { theme ->
                val isSelected = theme == current
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onSelect(theme) }
                ) {
                    Text(
                        text = theme.name.replace('_', ' '),
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OutputLimitRow(
    current: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            "Batas Output Terminal",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf(65536 to "64 KB", 262144 to "256 KB", 1048576 to "1 MB").forEach { (limit, label) ->
                val isSelected = limit == current
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onSelect(limit) }
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun FontSizeRow(
    current: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            // Audit 2026-08: jujur di label — ukuran hanya berlaku untuk terminal
            // (editor fix 14px di bundle CM6).
            "Ukuran Font Terminal",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf(10, 12, 14, 16, 18, 20).forEach { size ->
                val isSelected = size == current
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onSelect(size) }
                ) {
                    Text(
                        text = "${size}px",
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FontFamilyRow(
    current: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            // Audit 2026-08: jenis font berlaku untuk UI & editor (bukan terminal).
            "Jenis Font (UI & Editor)",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            // Audit 2026-08: font coding bundel (OFL) + default monospace device.
            listOf("Monospace", "JetBrains Mono", "Fira Code", "Source Code Pro").forEach { family ->
                val isSelected = family == current
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onSelect(family) }
                ) {
                    Text(
                        text = family,
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
