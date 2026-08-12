package com.zaba.zcode.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * EDITOR HANDLE — satu baris tombol bantu, dipakai di editor DAN di terminal.
 *
 * Metafora yang disepakati bersama user (2026-08-13):
 *   TEROWONGAN — slot tetap di kiri yang TIDAK ikut bergeser. Isinya tombol
 *                darurat (`^C`). Ukuran & bentuknya sama persis dengan tombol
 *                lain; hanya warnanya merah. Sebuah tombol darurat yang harus
 *                dicari dengan menggeser adalah desain yang jahat.
 *   KERETA      — sisa baris, bisa digeser kiri-kanan sesuka hati.
 *
 * Di editor terowongannya kosong (tidak ada yang perlu dihentikan), jadi yang
 * tampak hanya keretanya. Satu komponen, dua wajah — bukan dua implementasi
 * yang harus dijaga sinkron.
 *
 * Menggantikan SYMBOL BAR lama (`QuickToolsBar`), yang memakai AssistChip
 * tanpa batas tinggi. Itu sumber "bar raksasa" di v1.0.2: komponen Material3
 * memakai tinggi minimumnya sendiri (48–56dp) bila tidak dibatasi, dan begitu
 * jumlah tombol bertambah seluruh baris ikut membengkak. Di sini setiap tombol
 * dikunci [KEY_HEIGHT] dan barisnya dikunci [BAR_HEIGHT].
 *
 * Perilaku yang diadopsi dari ZMUX (`ZmuxKeys.kt`, repo muzape28-blip/ZMUX):
 * tabel tombol berbasis data, bukan UI yang ditulis satu per satu. Menambah
 * tombol = menambah satu baris data. Sticky CTRL dan hold-to-repeat menyusul
 * di build #4 bersama PTY — tanpa PTY keduanya belum punya arti.
 */
object EditorHandleDefaults {
    val BAR_HEIGHT = 40.dp
    val KEY_HEIGHT = 30.dp
    val DANGER = Color(0xFFB3261E)
}

/**
 * Satu tombol pada EDITOR HANDLE.
 *
 * @param label teks yang tampil
 * @param insert teks yang dikirim saat ditekan (null bila memakai [onClick])
 * @param danger tampil merah — dipakai untuk aksi yang menghentikan sesuatu
 */
data class HandleKey(
    val label: String,
    val insert: String? = null,
    val danger: Boolean = false,
    val onClick: (() -> Unit)? = null
)

/** Tombol default untuk editor Python. */
fun pythonEditorKeys(): List<HandleKey> = listOf(
    HandleKey("Tab", "    "),
    HandleKey(":", ":"),
    HandleKey("=", "="),
    HandleKey("(", "("),
    HandleKey(")", ")"),
    HandleKey("[", "["),
    HandleKey("]", "]"),
    HandleKey("{", "{"),
    HandleKey("}", "}"),
    HandleKey("'", "'"),
    HandleKey("\"", "\""),
    HandleKey("#", "#"),
    HandleKey("_", "_"),
    HandleKey("def", "def "),
    HandleKey("return", "return "),
    HandleKey("import", "import "),
    HandleKey("self", "self"),
    HandleKey("print", "print()")
)

/**
 * Tombol untuk terminal ZCODE saat ini.
 *
 * Sengaja TIDAK memuat ESC / panah / Home / End walaupun ZMUX punya semuanya:
 * tombol-tombol itu mengirim escape sequence yang hanya berarti bila ada PTY
 * sungguhan. Terminal ZCODE sekarang menulis langsung ke stdin Python, jadi
 * memasangnya hanya akan menghasilkan tombol mati — lebih buruk daripada tidak
 * ada. Semuanya diaktifkan di build #4 bersama Alpine.
 */
fun terminalKeys(): List<HandleKey> = listOf(
    HandleKey("/", "/"),
    HandleKey("-", "-"),
    HandleKey("_", "_"),
    HandleKey(".", "."),
    HandleKey("~", "~"),
    HandleKey("|", "|"),
    HandleKey(">", ">"),
    HandleKey("&", "&"),
    HandleKey("*", "*"),
    HandleKey("\"", "\""),
    HandleKey("'", "'")
)

@Composable
fun EditorHandle(
    keys: List<HandleKey>,
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier,
    tunnelKey: HandleKey? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EditorHandleDefaults.BAR_HEIGHT)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // TEROWONGAN — tetap di tempat, tidak pernah ikut digeser.
            tunnelKey?.let { key ->
                HandleKeyCap(key = key, onInsert = onInsert)
            }
            // KERETA — bagian yang bergerak.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                keys.forEach { key -> HandleKeyCap(key = key, onInsert = onInsert) }
            }
        }
    }
}

@Composable
private fun HandleKeyCap(key: HandleKey, onInsert: (String) -> Unit) {
    val bg = if (key.danger) {
        EditorHandleDefaults.DANGER
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (key.danger) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .height(EditorHandleDefaults.KEY_HEIGHT)
            .defaultMinSize(minWidth = 34.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .clickable {
                val cb = key.onClick
                if (cb != null) cb() else key.insert?.let(onInsert)
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            key.label,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = fg,
            maxLines = 1
        )
    }
}
