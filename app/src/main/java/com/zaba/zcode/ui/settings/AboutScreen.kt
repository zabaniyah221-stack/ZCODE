package com.zaba.zcode.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaba.zcode.R

/**
 * AboutScreen — identitas ZCODE + Lisensi (MIT) + Contribute.
 * Contribute → GitHub Issues (keputusan tim: langsung ke repo, tanpa email).
 * Deskripsi lama diganti teks lisensi (permintaan user): ramah kontribusi, mengisi
 * ruang kosong, telusur penuh bisa discroll dengan pembatas.
 */

private const val MIT_LICENSE_TEXT = """MIT License

Copyright (c) 2026 ZCODE contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""

@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

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
                // A0 v1.0.19 (laporan user 2026-08-18): root About kini
                // scrollable. Di landscape ±360dp, logo 92dp + judul + versi
                // memakan setengah layar dan tombol Issues/Contribute
                // terdampar di luar layar tanpa jalan masuk. Portrait: konten
                // muat → scroll tak aktif → identik sebelum fix.
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Logo aplikasi baru (konsep {Z} — sama dengan icon launcher)
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
            // Versi dibaca dari PackageInfo (single source: gradle.properties),
            // BUKAN literal. Saat menguji perbaikan, user perlu memastikan APK yang
            // terpasang benar-benar versi baru — angka hardcode pernah menyesatkan.
            // Fallback "1.0.0" hanya dipakai bila PackageManager gagal.
            val versionLabel = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
                } catch (e: Throwable) {
                    "1.0.0"
                }
            }
            Text(
                "v$versionLabel",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            // ---------- License (MIT) — pembatas + scrollable (permintaan user) ----------
            Divider(color = Color.White.copy(alpha = 0.08f))

            Text(
                "License — MIT",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "ZCODE itu open source. Siapa pun bebas membaca, memakai, fork, dan " +
                    "berkontribusi — tidak perlu izin, tidak perlu sungkan.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    MIT_LICENSE_TEXT,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.LightGray
                )
            }

            // A0: dulu Spacer(weight(1f)) "dorong Contribute ke dasar layar".
            // Di kolom scrollable, weight tak bermakna (tinggi unbounded) dan
            // spacer kolaps jadi 0 — diganti jarak tetap agar deterministik
            // di kedua orientasi.
            Spacer(modifier = Modifier.height(18.dp))

            // Diagnostik dihapus dari About (v1.0.18, laporan user 2026-08-16):
            // sejak DiagnosticsScreen full-screen lahir (sidebar), panel ini
            // duplikat yang lebih lemah. Fungsi DiagnosticsCard dibiarkan mati
            // di bawah? TIDAK — dihapus penuh agar tidak jadi kode zombi.

            // Contribute — langsung ke GitHub Issues
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
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
                        "Punya saran atau menemukan bug? Laporkan langsung lewat GitHub Issues.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/muzape28-blip/ZCODE/issues")
                            )
                            context.startActivity(intent)
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
                }
            }
        }
    }
}

