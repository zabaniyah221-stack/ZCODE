package com.zaba.zcode.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
// WAJIB untuk sintaks delegasi `var x by remember { mutableStateOf(...) }`.
// getValue/setValue adalah operator extension yang HARUS di-import; menuliskannya
// dengan nama berkualifikasi penuh TIDAK bisa menggantikan import ini
// (penyebab CI merah 2026-08-12 di step "Build Debug APK").
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaba.zcode.R

/**
 * AboutScreen — identity, GPLv3 legal notice/provenance, and contribution links.
 *
 * v1.0.23 Model B (keputusan user 2026-09-08: expandable in-place, copy
 * English, chip tagline, teks bisa disalin):
 * - Ringkasan di halaman; teks hukum PENUH di balik baris expandable (pola
 *   drawer PLUGINS). Tiga file license dimuat dari assets yang SUDAH
 *   ter-package (GPL-3.0.txt, NOTICE.txt, MIT.txt) — About dan APK
 *   menampilkan byte yang sama dengan yang dijaga test lisensi repo.
 * - Konten yang di-expand MENGALIR ke scroll halaman (tanpa jendela
 *   nested-scroll tetap 150dp versi lama — keluhan kerapian user).
 * - SelectionContainer: seluruh teks lisensi bisa disalin (PRD: semua teks
 *   harus bisa disalin).
 * - Copy UI = English (konsisten keputusan copy v1.0.22).
 */

private const val ZCODE_SOURCE_URL = "https://github.com/muzape28-blip/ZCODE"
private const val ZCODE_ISSUES_URL = "https://github.com/muzape28-blip/ZCODE/issues"

/** Muat teks lisensi dari assets ter-package (path lengkap); fallback jujur bila gagal. */
private fun readLicenseAsset(context: android.content.Context, name: String): String =
    runCatching {
        context.assets.open(name)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrElse { error ->
        "License text failed to load from APK ($name): ${error.message ?: "unknown error"}"
    }

@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val gplText = remember { readLicenseAsset(context, "licenses/GPL-3.0.txt") }
    val noticeText = remember { readLicenseAsset(context, "licenses/NOTICE.txt") }
    val mitText = remember { readLicenseAsset(context, "licenses/MIT.txt") }

    // Versi dibaca dari PackageInfo (single source: gradle.properties), BUKAN
    // literal — angka hardcode pernah menyesatkan QA (catatan v1.0.22).
    // versionCode ikut ditampilkan agar user bisa memastikan APK yang
    // terpasang benar-benar build yang diuji (kontrak update 25→26).
    val versionLabel = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Throwable) {
            "1.0.0"
        }
    }
    val versionCodeLabel = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            // Pola UpdateReceipt.kt: longVersionCode (API 28+) dengan
            // fallback versionCode lama (minSdk 26).
            if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
            else @Suppress("DEPRECATION") info.versionCode
        } catch (e: Throwable) {
            0
        }
    }

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
                        "← Back",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "About ZCODE",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                // A0 v1.0.19: root wajib scrollable (landscape 360dp).
                // Model B v1.0.23: satu-satunya scroll di layar ini —
                // konten lisensi expand mengalir ke sini (tanpa nested).
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Image(
                painter = painterResource(id = R.drawable.zcode_logo),
                contentDescription = "Logo ZCODE",
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Text(
                "ZCODE",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "v$versionLabel · versionCode $versionCodeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            // Chip tagline (keputusan user 2026-09-08) — informasional,
            // teks stabil, bukan dekorasi emoji (AGENTS §12).
            Surface(
                shape = RoundedCornerShape(50.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                color = Color.Transparent
            ) {
                Text(
                    "Python IDE · offline-first · ARMv7-first",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            // ---------- LICENSE & PROVENANCE — Model B expandable ----------
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                // Kerapian landscape (UAT 2026-08-18): batas lebar kartu —
                // kolom rapi di tengah, portrait tak berubah.
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "License & Provenance",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "ZCODE is free software: you may use, study, modify, and share it " +
                            "under GPLv3, without warranty. It contains portions derived " +
                            "from ZABACODE.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    LicenseExpandableRow(title = "GNU GPL v3 — full text", body = gplText)
                    LicenseExpandableRow(title = "NOTICE — ZABACODE provenance", body = noticeText)
                    LicenseExpandableRow(title = "MIT — independent parts", body = mitText)
                    Text(
                        "CodeMirror 6 editor · Chaquopy runtime — full credits in NOTICE.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ---------- SUPPORT & CONTRIBUTION ----------
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Support & Contribution",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Found a bug or have an idea? Reports go straight to GitHub Issues — " +
                            "paste your Diagnostics text to help.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    Button(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ZCODE_ISSUES_URL)))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Open Issues / Contribute",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 12.sp
                        )
                    }
                    // Satu tombol primary saja (hierarki jelas); tautan
                    // source berupa teks — bukan tombol hijau kedua.
                    Text(
                        "View source ↗",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ZCODE_SOURCE_URL)))
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

/**
 * Baris lisensi expandable (Model B — keputusan user 2026-09-08). Collapsed:
 * satu baris judul + chevron. Expanded: teks hukum PENUH mengalir ke scroll
 * halaman (bukan jendela nested 150dp lama) dan bisa disalin via
 * SelectionContainer. State per baris dipertahankan lintas rotasi
 * (rememberSaveable — pelajaran SKILL 17).
 */
@Composable
private fun LicenseExpandableRow(title: String, body: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (expanded) "▾" else "▸",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
        }
        if (expanded) {
            SelectionContainer {
                Text(
                    body,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.LightGray
                )
            }
        }
    }
}
