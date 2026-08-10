# 🎨 TERMINAL_THEMES — Palet Tema Terminal (2026-08)

Dokumen ini merancang **palet warna untuk terminal ZCODE**. Prinsip yang sudah
disepakati: **latar terminal TETAP hitam/OLED** (jangan disentuh), yang
berganti adalah **warna depan (foreground) + palet ANSI**. Ini selaras dengan
Batch 1.5: pemetaan SGR/ANSI ke warna, dan dengan tiga tema app yang sudah ada
(RETRO, DRACULA, TOKYO_NIGHT).

> Tujuan: terminal tetap nyaman di mata (mis. retro fosfor) tanpa merusak
> identitas "hitam OLED" yang sudah dikunci.

---

## 1. Aturan yang dikunci

- **Background terminal = hitam/OLED** (`#000000` atau panel tergelap tema).
  Tidak ada tema dengan latar terang.
- **Foreground** mengikuti palet yang dipilih; kursor & ansi menyesuaikan.
- Warna mengikuti SGR/ANSI standar (30–37, 90–97, dst) — yang di-emit Python
  lewat kode escape.
- Palet default menunggu keputusan user (opsi: `ikut tema`, `phosphor/retro`,
  atau `terserah`). Dokumen ini menyiapkan struktur, bukan memaksa pilihan.

---

## 2. Palet yang diusulkan

| Palet | Latar | Foreground | Aksen/ciri |
|---|---|---|---|
| **Phosphor (Retro)** | `#000000` | `#39FF14` | Hijau CRT; identitas ZCODE (cocok tema RETRO) |
| **Dracula** | `#000000` | `#F8F8F2` | ANSI ala Dracula (merah `#FF5555`, ungu `#BD93F9`, hijau `#50FA7B`) |
| **Tokyo Night** | `#000000` | `#C0CAF5` | ANSI Tokyo (biru `#7AA2F7`, merah `#F7768E`, hijau `#9ECE6A`) |
| **Solarized Dark** | `#000000` | `#93A1A1` | ANSI solarized (tenang di mata) |
| **Monokai** | `#000000` | `#F8F8F2` | ANSI Monokai (klasik editor) |
| **ZABACODE Classic** | `#000000` | hijau fosfor/amber | Menghormati akar; sekadar varian retro |

Catatan:
- Walaupun palet asli (Dracula/Tokyo) punya latar non-hitam, ZCODE **memaksa
  latar hitam** dan mengambil foreground + ANSI-nya saja.
- Untuk keterbacaan, pastikan kontras foreground di atas hitam ≥ 4.5:1 bila
  mungkin; warna aksen boleh lebih redup untuk pesan sekunder.

---

## 3. Palet ANSI (16 warna) — contoh format

Setiap palet mendefinisikan 16 warna: 8 normal + 8 terang. Contoh **Phosphor**:
```
black=000000  red=FF4B4B  green=39FF14  yellow=FFB000
blue=4B8BFF  magenta=FF4BD8  cyan=4BFFD8  white=C8FFC8
brightBlack=0A100D ... brightWhite=E8FFE8
```
Contoh **Dracula** (ambil dari `ZcodeTheme.kt`):
```
red=FF5555 green=50FA7B yellow=F1FA8C blue=BD93F9
magenta=FF79C6 cyan=8BE9FD white=F8F8F2 ...
```
Implementasi bisa memetakan `\x1b[3Xm`/`\x1b[9Xm` (dan 4X/10X untuk bg meski
latar default hitam) ke warna Compose/span.

---

## 4. Integrasi dengan kode

- Tema app ada di `ui/theme/ZcodeTheme.kt` dengan `ZcodeThemeType.RETRO`,
  `.DRACULA`, `.TOKYO_NIGHT`. Palet terminal bisa **mengikuti tema app** atau
  **dipilih terpisah** (opsi "ikut tema" = default yang diusulkan).
- Terminal di `ui/terminal/TerminalScreen.kt` + `core/execution/TerminalBridge.kt`
  perlu tahu palet aktif saat merender SGR. Catat kebutuhan ini di
  `PERF_PASS.md` (rendering ANSI tidak boleh menambah jank saat output deras).
- Simpan preferensi di DataStore/SharedPreferences terenkripsi yang sudah
  dipakai ZCODE (privasi & konsistensi state).

---

## 5. Edge case (teliti)

- **Kode SGR tak dikenal / gabungan** (bold+warna, reset): fallback ke default.
- **Warna khusus**: `sys.stderr`/error → merah palet; pesan sistem ZCODE → aksen;
  pastikan tak bentrok dengan warna teks biasa.
- **Latar paksa hitam**: bila skrip mengirim ANSI background, boleh dihormati
  sebagian tapi default selalu hitam (jangan sampai latar jadi putih).
- **Aksesibilitas**: teks redup (bright black) harus tetap terbaca di OLED.
- **Konsistensi dengan editor**: tema editor saat ini fixed OLED; palet terminal
  tidak mewajibkan editor ikut berubah (keputusan editor theme terpisah).

---

## 6. Pengujian & UAT

1. **Sandbox:** fungsi pemetaan SGR murni bisa dites tanpa Android (input kode
   escape → warna yang diharapkan, reset, kombinasi).
2. **CI:** kompilasi Kotlin.
3. **UAT Infinix Smart 9 HD:**
   - [ ] Ganti palet terminal; latar tetap hitam, foreground ikut berubah.
   - [ ] Output berwarna (mis. `rich`, `colorama`, `tqdm`) tampil dengan palet.
   - [ ] Error/`stderr` tampak merah; pesan sistem jelas.
   - [ ] Output deras tetap mulus (lihat PERF_PASS).
   - [ ] Preferensi bertahan setelah app ditutup.

---

## 7. Tahapan (per-commit kecil)

1. Definisikan model `TerminalPalette` (16 warna + fg/bg/cursor).
2. Tambah 2–3 palet awal (Phosphor, Dracula, Tokyo) + unit test pemetaan SGR.
3. Hubungkan ke rendering terminal (tanpa ubah latar).
4. Pilihan palet di pengaturan (default "ikuti tema app" atau "Phosphor" —
   menunggu keputusan user).
5. Tambah palet lain (Solarized/Monokai) setelah dasar stabil.

---

## 8. Hubungan dengan dokumen lain

- `ui/theme/ZcodeTheme.kt` — sumber warna RETRO/DRACULA/TOKYO_NIGHT.
- `PERF_PASS.md` — rendering SGR & output deras harus tetap ringan.
- `CM6_FEATURE_MAP.md` — pemisahan keputusan tema editor vs terminal.
- `docs/RENCANA_UPDATE_2026_08.md` — aturan jujur/teliti & UAT.

---

*Rancangan palet — latar hitam dikunci. Pilihan default menunggu suara user;
struktur kode disiapkan agar penambahan palet cukup dengan menambah data.*
