package com.zaba.zcode.ui.samples

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.zaba.zcode.core.samples.SampleEntry

/**
 * Satu dependency gate untuk semua pintu menuju sample runnable.
 *
 * SamplesScreen dan Detail Library tidak boleh punya pesan/keputusan berbeda:
 * user dapat menuju Install Modules, tetap membuka kode dengan sadar, atau
 * membatalkan. Pemeriksaan paket dilakukan caller tepat sebelum dialog dibuka.
 */
@Composable
fun SampleRequirementDialog(
    entry: SampleEntry,
    missingPackages: List<String>,
    onDismiss: () -> Unit,
    onInstallFirst: () -> Unit,
    onOpenAnyway: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Butuh paket dulu", fontSize = 16.sp) },
        text = {
            Text(
                "Sample \"${entry.title}\" membutuhkan paket yang belum " +
                    "terpasang: ${missingPackages.joinToString(", ")}.\n\n" +
                    "Instal dulu lewat INSTALL MODULES, atau buka kodenya " +
                    "sekarang (Run akan gagal sebelum paket aktif)."
            )
        },
        confirmButton = {
            TextButton(onClick = onInstallFirst) { Text("Install dulu") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Batal") }
                TextButton(onClick = onOpenAnyway) { Text("Buka kode") }
            }
        }
    )
}
