package com.zaba.zcode.ui.common

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * ReferenceCard — A5 Gerbong A v1.0.19 (adopsi saran eksternal MIMO #4,
 * ditelaah 2026-08-18).
 *
 * "Cheat sheet" pola Python yang bisa diakses tanpa keluar editor: tombol
 * "?" → dialog daftar pola per seksi → tap = insert di kursor. Menggantikan
 * alur "buka browser → cari → copy" yang memutus fokus belajar (dan makan
 * kuota — konteks user HP tanpa PC).
 *
 * Data di assets/reference/python_reference.json (BUKAN hardcode Kotlin):
 * menambah pola = edit JSON, tanpa sentuh kode; py-compile-able oleh guard.
 * Offline penuh, zero network, zero state.
 */

data class RefItem(val label: String, val insert: String)
data class RefSection(val title: String, val items: List<RefItem>)

object ReferenceLibrary {

    /** Parse assets JSON → sections. Gagal baca = daftar kosong (dialog
     *  tetap terbuka dgn pesan), tidak pernah crash — best-effort. */
    fun load(context: Context): List<RefSection> = try {
        val raw = context.assets.open("reference/python_reference.json")
            .bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val sections = mutableListOf<RefSection>()
        val arr = root.getJSONArray("sections")
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val items = mutableListOf<RefItem>()
            val itemsArr = s.getJSONArray("items")
            for (j in 0 until itemsArr.length()) {
                val it = itemsArr.getJSONObject(j)
                items.add(RefItem(it.getString("label"), it.getString("insert")))
            }
            sections.add(RefSection(s.getString("title"), items))
        }
        sections
    } catch (e: Exception) {
        com.zaba.zcode.core.diagnostics.Breadcrumb.log(
            "REFCARD_LOAD_FAIL", e.message ?: ""
        )
        emptyList()
    }
}

@Composable
fun ReferenceCardDialog(
    context: Context,
    onInsert: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sections = remember { ReferenceLibrary.load(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Referensi Python", fontSize = 16.sp) },
        text = {
            if (sections.isEmpty()) {
                Text("Referensi tidak bisa dimuat — cek Diagnostics.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    sections.forEach { section ->
                        item {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }
                        items(section.items.size) { idx ->
                            val item = section.items[idx]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        com.zaba.zcode.core.diagnostics.Breadcrumb.log(
                                            "REFCARD_INSERT", item.label
                                        )
                                        onInsert(item.insert)
                                        onDismiss()
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    item.label,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    // pratinjau satu baris pertama, monospace
                                    item.insert.lineSequence().firstOrNull() ?: "",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                            Divider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}
