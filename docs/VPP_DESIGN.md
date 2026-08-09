# 🚨 VPP_DESIGN — Visual Problems Panel (2026-08)

Dokumen ini merancang **Visual Problems Panel (VPP)**: panel daftar error/
masalah yang meluas ke bawah dari banner sintaksis di `WorkbenchScreen.kt`.
Desain mengikuti **Opsi 3 yang sudah dikunci** (bukan modal, bukan pindah
screen, melainkan expand in-place ke bawah).

> Dijelaskan ke user: banner merah di atas editor saat ini cuma 1 baris
> ("Unbalanced brackets…") dan **belum bisa di-tap**. VPP bikin banner itu
> bisa di-tap untuk membuka daftar semua masalah.

---

## 1. Desain yang dikunci (Opsi 3)

### Collapsed (1 baris) — kondisi sekarang, ditambah:
- Ikon severity **terparah** di kiri (❌ error / ⚠️ warning / ℹ️ info).
- Pesan masalah pertama, dipotong elipsis bila panjang.
- Chip/counter **`(+N)`** di kanan kalau ada lebih dari 1 masalah (tidak
  muncul kalau cuma 1).
- Chevvron (▾) menandakan bisa di-expand.
- Bila **0 error → banner lenyap total** (bukan "No problem").

### Expanded (melar KE BAWAH):
- Header **"N masalah"** + tombol tutup/chevron (▴).
- Body **±5 baris terlihat**, **scrollable di dalam kotak**; editor di
  belakang tetap kelihatan dan tidak tertutup.
- Tiap item: ikon severity, pesan, nomor baris.
- **Tap item → `gotoLine(n)`** (sudah ada di `WorkbenchScreen.kt`), panel
  **tetap terbuka** sehingga user bisa membetulkan satu per satu.

### Perilaku:
- State expanded/collapsed **tidak reset saat rotasi** → `rememberSaveable`.
- Warna banner mengikuti severity terparah & tema (bukan selalu merah).
- Banner syntax lama (`vm.syntaxError`) diganti sumbernya oleh daftar masalah.

---

## 2. Temuan jujur dari kode (fondasi belum lengkap)

Dari membaca `core/editor/Checker.kt` dan `WorkbenchScreen.kt`:

- `Checker.checkSyntax(code: String): String?` hanya mengembalikan **SATU**
  error (`String?`), bukan daftar. Ia memeriksa:
  - string yang tidak tertutup (`unterminatedStringLine`),
  - keseimbangan kurung (`checkBrackets`).
- Banner di `WorkbenchScreen.kt` menampilkan `vm.syntaxError` (single string).
- `gotoLine(n)` **sudah ada** dan tinggal dipakai untuk tap item.
- `EditorScreen.kt` murni WebView + bridge (CodeMirror 6).

Artinya: **data "daftar lengkap masalah" belum ditarik ke Kotlin.** VPP butuh
salah satu jalur di bawah — ini bagian pekerjaan inti, bukan cuma UI.

---

## 3. Dari mana dapat DAFTAR masalah? Dua opsi

### Opsi A — Perluas `Checker.kt` jadi list (murah, offline)
- Ubah `checkSyntax` agar mengembalikan `List<Problem>`.
- Tetap berbasis scanner ringan yang sudah ada (kurung tak seimbang, string
  tak tertutup), lalu tambahkan diagnosis ringan lain (indentasi ekstrem,
  campuran tab/spasi, nama yang typo umum).
- **Pro:** tanpa Python, jalan saat mengetik, ringan untuk HP ampas.
- **Kontra:** bukan parser penuh; hanya cek pola, bukan kebenaran semantik.

### Opsi B — Tarik diagnostic CodeMirror 6 lewat bridge
- Pakai `@codemirror/lint` (belum dipasang — lihat `CM6_FEATURE_MAP.md`) untuk
  mengumpulkan diagnostic dari Lezer parser Python, kirim ke Kotlin via
  jembatan JS→Kotlin.
- **Pro:** lebih akurat (posisi token, error parse CM6).
- **Kontra:** rebuild bundle JS, merambah `editor-src`, dan lint source tetap
  perlu (Lezer bisa memberi syntax errors, tapi bukan pyflakes/ruff).

### Rekomendasi
Mulai dari **Opsi A** (fondasi VPP cepat & ringan, sesuai visi offline/HP
ampas), dan rancang model `Problem` agar Opsi B bisa menyatu nanti.
Level linter lanjutan (pyflakes/ruff via Chaquopy) ditahan — berat di device
ampas, masuk backlog.

Model data usulan:
```kotlin
data class Problem(
    val severity: Severity,   // ERROR, WARNING, INFO
    val message: String,
    val line: Int,            // 1-based; 0 bila tak tentu
    val column: Int? = null,
    val source: String = "checker" // "checker" | "codemirror" | "pyflakes" ...
)
```

---

## 4. Struktur UI (Compose)

- `ProblemsBanner(problems, expanded, onToggle, onGotoLine)` di area yang
  sekarang menampilkan banner merah.
- Saat `problems.isEmpty()` → tidak dirender (`if (problems.isNotEmpty())`).
- Body `Column` dengan `Modifier.heightIn(max = …)` + `verticalScroll`,
  dibungkus `AnimatedVisibility` untuk expand/collapse yang halus (tetap
  ringan — hindari animasi yang memicu rekomposisi editor; lihat
  `PERF_PASS.md`).
- Tiap baris `ProblemRow` klik → `onGotoLine(problem.line)`; untuk `line < 1`
  tombol goto dinonaktifkan.

---

## 5. Alur data

1. User mengetik → jembatan `onCodeChange` → `vm.updateCode`.
2. ViewModel menghitung `List<Problem>` (idealnya di latar belakang/terjadwal,
   bukan tiap karakter tanpa henti — lihat debounce di `PERF_PASS.md`).
3. `WorkbenchScreen` mengamati state `problems`.
4. Banner collapsed menampilkan problem terparah; expand menampilkan semua;
   tap item memanggil `gotoLine` yang sudah ada.

---

## 6. Edge case (teliti)

- **1 error saja:** chip `(+N)` disembunyikan; tap banner tetap expand yang
  menampilkan detail pesan (berguna bila pesan panjang).
- **Pesan panjang:** ellipsis di collapsed, penuh di expanded (wrap text).
- **Banyak error (>50):** virtualisasi/batasi item yang dirender; pencarian
  `gotoLine` harus tetap ke baris yang benar.
- **Rotasi:** state expanded & posisi scroll diingat (`rememberSaveable`).
- **Error tanpa nomor baris:** tampilkan tanpa nomor, baris tak bisa di-tap goto.
- **Warna:** ikut tema dan severity; pastikan kontras di semua tema (TERMINAL_THEMES).
- **Kursor/fokus:** tap item tidak boleh menutup keyboard atau mengganggu
  seleksi editor; panel tetap di atas editor (bukan menutup area ketik).
- **Performa:** perhitungan problem & rekomposisi panel tidak boleh bikin
  mengetik lag (skop rekomposisi, lihat PERF_PASS).

---

## 7. Pengujian & UAT

1. **Sandbox:**
   - Unit test `Checker` untuk `List<Problem>` (kurung tak seimbang, string
     tak tertutup, kasus string yang berisi `:`/`(` seperti `print(' :)'`,
     triple-quote, f-string, `async def`).
   - Pastikan test lama tetap hijau (jangan mematahkan B-11/B-19/F-07).
2. **CI:** kompilasi Kotlin.
3. **UAT Infinix Smart 9 HD:**
   - [ ] Banner hanya muncul saat ada masalah; hilang saat bersih.
   - [ ] Tap banner → expand ke bawah; tap lagi → collapse.
   - [ ] Chip `(+N)` akurat; pesan panjang terlihat di expanded.
   - [ ] Tap item → kursor pindah ke baris; panel tetap terbuka.
   - [ ] Rotasi tidak mereset state.
   - [ ] Mengetik dengan panel terbuka tetap mulus.

---

## 8. Tahapan (per-commit kecil)

1. Ubah `Checker.checkSyntax` → `List<Problem>` tanpa mengubah UI dulu
   (sediakan helper kompatibilitas bila perlu), lengkapi unit test.
2. Tambah state `problems` + `expanded` di ViewModel.
3. Bangun `ProblemsBanner` collapsed (ikon + pesan + `(+N)`).
4. Tambah expanded list + `gotoLine`.
5. `rememberSaveable`, warna tema, edge case.
6. (Nanti, opsional) Sambungkan diagnostic `@codemirror/lint` (Opsi B).

---

## 9. Hubungan dengan dokumen lain

- `CM6_FEATURE_MAP.md` — `@codemirror/lint` belum dipakai (fondasi Opsi B).
- `PERF_PASS.md` — panel harus ringan; debounce & skop rekomposisi.
- `docs/PLAN_ZCODE.md` — VPP termasuk target UI (Problems Panel).
- `docs/RENCANA_UPDATE_2026_08.md` — aturan jujur/teliti & UAT.

---

*Rancangan — Opsi 3 dikunci. Fondasi data (daftar problem) adalah pekerjaan
inti; UI mengikuti setelah sumber daftar disepakati.*
