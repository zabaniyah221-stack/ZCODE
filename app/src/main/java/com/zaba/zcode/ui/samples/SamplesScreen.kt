package com.zaba.zcode.ui.samples

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.zaba.zcode.core.diagnostics.Breadcrumb
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaba.zcode.core.samples.SampleCategory
import com.zaba.zcode.core.samples.SampleEntry
import com.zaba.zcode.core.samples.SampleLibrary

/**
 * SamplesScreen — halaman SAMPLES (redesign 2026-08, FASE E).
 *
 * Struktur 2 level ala Pydroid (keputusan user):
 *   Level 1: daftar kategori (Basics / Numpy / Web)
 *   Level 2: daftar sample di kategori itu
 * Tap item → onPick(entry) → host membuat file baru di workspace lalu balik ke
 * editor (tab baru bernama file). Tombol ← di level 2 kembali ke level 1;
 * BackHandler sistem mengikuti hierarki yang sama (rule #2: edge case back-press).
 */


@Composable
fun SamplesScreen(
    onBack: () -> Unit,
    onPick: (SampleEntry) -> Unit
) {
    // null = level 1 (kategori); non-null = level 2 (isi kategori)
    var activeCategory by remember { mutableStateOf<SampleCategory?>(null) }

    BackHandler {
        val cat = activeCategory
        if (cat != null) activeCategory = null else onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // v1.0.18: layar navigasi ikut TEMA (laporan user: Samples tetap
            // hitam saat ganti tema). Hitam pekat hanya untuk panel terminal.
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---- Topbar halaman: ← + judul (konsisten 48dp ala topbar utama) ----
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "←",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable {
                            if (activeCategory != null) activeCategory = null else onBack()
                        }
                        .padding(10.dp)
                )
                Text(
                    activeCategory?.title ?: "Samples",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        val cat = activeCategory
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (cat == null) {
                // ---- Level 1: kategori ----
                items(SampleLibrary.categories) { category ->
                    SampleListRow(
                        title = category.title,
                        description = category.description,
                        onClick = {
                            Breadcrumb.log("SAMPLES_KATEGORI", category.id)
                            activeCategory = category
                        }
                    )
                }
            } else {
                // ---- Level 2: item di kategori ----
                items(cat.samples) { entry ->
                    SampleListRow(
                        title = entry.title,
                        description = entry.description,
                        onClick = {
                            Breadcrumb.log("SAMPLES_PILIH", entry.id)
                            onPick(entry)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleListRow(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(description, fontSize = 12.sp, color = Color.Gray)
    }
    Divider(color = Color.White.copy(alpha = 0.06f))
}
