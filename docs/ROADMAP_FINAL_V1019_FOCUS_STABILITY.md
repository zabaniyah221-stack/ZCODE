# Roadmap Final v1.0.19 — Stabilitas INSTALL MODULES

Tanggal keputusan: **2026-08-19**

Status: **IMPLEMENTED + LOCALLY VERIFIED — menunggu CI dan UAT perangkat**

Branch: `arena/v1019-fondasi`

Basis: `25a6a9e618a7484514ae106af8305dd33cda8777`

## 1. Tujuan

Menutup regresi force close terakhir sebelum release v1.0.19 tanpa memperluas
scope, mengganti runtime, atau melakukan upgrade dependency berisiko.

Kontrak release:

> INSTALL MODULES harus stabil untuk berpindah antara Library dan Manual,
> mengetik requirement, install, cancel dialog uninstall, dan uninstall pada
> perangkat target. Swipe bukan syarat release.

North Star tetap berlaku: fitur kenyamanan tidak boleh mengorbankan kemampuan
user untuk berkarya pada perangkat yang dimiliki.

## 2. Fakta dan batas bukti saat ini

### Terbukti

- Runtime laporan crash: **INFINIX Infinix X6532C**, Android **14 / API 34**,
  ABI `armeabi-v7a, armeabi`.
- Crash berulang setelah navigasi ke `MANUAL`, termasuk sebelum
  `PKG_ANALYZE_BEGIN`.
- Exception tepat:

  ```text
  java.lang.IllegalStateException:
  Event can't be processed because we do not have an active focus target.
  ```

- Stack menunjuk `androidx.compose.ui.focus.FocusOwnerImpl.dispatchKeyEvent`.
- Source Compose UI 1.6.1 memang melempar exception tersebut ketika key event
  masuk tanpa active focus target.
- Regresi fokus-relevan setelah commit swipe `2c51250` adalah:
  `HorizontalPager`, lifecycle page, dan dua `clearFocus(force = true)`.
- Install `hashid`, install `hashids==1.3.1`, dan uninstall `hashids` berhasil.
  Package Engine bukan akar force close ini dengan confidence **99,5%**.

### Belum terbukti

- `clearFocus(force = true)` sebagai penyebab tunggal.
- Pager sebagai penyebab tunggal.
- Pengaruh khusus IME, Android 14, atau ROM/OEM sebagai pemicu timing.
- Versi Compose yang lebih baru pasti menyelesaikan kasus ZCODE.

Kesimpulan kerja: kelas masalah adalah **inkonsistensi focus tree Compose saat
Pager/TextField menerima key event**. Confidence **99%**. Revert topology ke
jalur tap-only yang sebelumnya telah melewati UAT adalah solusi release paling
aman. Confidence **±97%**, tetap harus dibuktikan pada artifact baru.

## 3. Keputusan v1.0.19

### Dilakukan

1. `LIBRARY` ↔ `MANUAL INSTALL` kembali menjadi **tap-only**.
2. Hapus `HorizontalPager` dan `rememberPagerState` dari `PipScreen`.
3. Hapus kedua `clearFocus(force = true)` beserta focus manager yang hanya
   dipakai untuk perpindahan tab.
4. Gunakan satu state enum `PipTab` sebagai sumber kebenaran tab aktif.
5. Pertahankan state yang sudah di-hoist:
   - `libraryListState`;
   - `manualPageScroll`;
   - `consoleScroll`;
   - `packageName`;
   - search/category state;
   - queue, console, analyze/install, dialog, dan uninstall state.
6. Breadcrumb `PIP_TAB` tetap dicatat saat tab benar-benar dipilih.
7. Tambahkan guard permanen dan buktikan guard melalui mutasi.

### Tidak dilakukan

- Tidak upgrade Compose, Kotlin, AGP, Java, Chaquopy, atau Python.
- Tidak mengubah Package Engine/resolver/transaction flow.
- Tidak menambah focus interception, key-event hook, atau diagnostics lifecycle
  baru ke kandidat release.
- Tidak menyembunyikan/menelan exception.
- Tidak melakukan eksperimen swipe pada APK kandidat release.
- Tidak membuka PR, merge, atau release sebelum seluruh gate di bawah lulus.

## 4. Urutan pekerjaan

### Fase A — Fix kecil dan dapat dibalik

Target file utama:

```text
app/src/main/java/com/zaba/zcode/ui/settings/PipScreen.kt
```

Bentuk implementasi:

```kotlin
var activeTab by remember { mutableStateOf(PipTab.LIBRARY) }

fun selectPipTab(tab: PipTab) {
    if (activeTab != tab) {
        activeTab = tab
        Breadcrumb.log("PIP_TAB", tab.name)
    }
}

when (activeTab) {
    PipTab.LIBRARY -> LibraryTab(..., listState = libraryListState)
    PipTab.MANUAL -> ManualTab(..., pageScroll = manualPageScroll)
}
```

Catatan implementasi:

- Logging awal `LIBRARY` boleh dilakukan secara eksplisit sekali bila kontrak
  diagnostics membutuhkannya; jangan memakai effect yang mengubah focus.
- `installFromLibrary` dan `LIBRARY_SAMPLE_TO_INSTALL` tetap memilih tab Manual
  lewat fungsi yang sama agar mapping tidak terpecah.
- Dependency Foundation eksplisit di Gradle dinilai terpisah. Menghapus Pager
  adalah kontrak penting; pembersihan dependency tidak boleh memperbesar risiko
  fix ini.

**Gate A:** diff hanya menyentuh topology tab/focus dan guard terkait. Tidak ada
perubahan perilaku installer.

### Fase B — Guard permanen + mutation proof

Guard membaca Kotlin setelah comment stripping dan memeriksa:

1. `HorizontalPager` tidak ada di production path `PipScreen`.
2. `rememberPagerState` tidak ada di `PipScreen`.
3. `clearFocus` tidak ada di `PipScreen`.
4. Kedua `TabBox` memetakan label ke enum yang benar.
5. `when (activeTab)` memetakan Library dan Manual ke composable yang benar.
6. `libraryListState`, `manualPageScroll`, `consoleScroll`, dan `packageName`
   tetap di-hoist di owner, bukan dibuat ulang di child/tab branch.
7. Jalur Library → install/sample tetap memilih `PipTab.MANUAL`.
8. Breadcrumb `PIP_TAB` tetap ada tanpa side effect focus.

Mutasi wajib:

| Mutasi | Hasil yang diwajibkan |
|---|---|
| Tambahkan kembali `HorizontalPager` | guard merah |
| Tambahkan kembali `rememberPagerState` | guard merah |
| Tambahkan `clearFocus` | guard merah |
| Tukar mapping tombol Library/Manual | guard merah |
| Hilangkan salah satu state hoisted | guard merah |
| Putus jalur Library → Manual | guard merah |
| Restore source | focused guard dan full suite hijau |

**Gate B:** guard tidak boleh lulus karena token di komentar atau dead code.

### Fase C — Verifikasi lokal

Jalankan secara berurutan, tanpa emulator bersamaan:

```bash
bash tools/check.sh
python3 tools/kotlin_sanity_check.py
python3 tools/npm_supply_chain_check.py
git diff --check
```

Lalu review manual:

- imports/opt-in yang sudah tidak dipakai;
- state ownership;
- seluruh pemanggil `selectPipTab`;
- tidak ada perubahan mode file yang tidak disengaja;
- tidak ada secret, cache, binary, atau artifact sementara;
- diff terhadap pre-swipe commit dan HEAD saat ini.

**Gate C:** semua pemeriksaan hijau dan mutasi sudah dibuktikan merah→hijau.
Status maksimum pada titik ini: **LOCALLY VERIFIED**, bukan device verified.

### Fase D — Commit, push aman, dan CI

1. Commit fix fokus secara koheren dan reversible.
2. Commit dokumentasi evidence/status bila dipisahkan.
3. Push hanya dengan credential sementara yang disediakan untuk push tersebut;
   hapus mekanisme credential setelah dipakai dan verifikasi remote bebas token.
4. Jalankan canonical CI.
5. Catat run ID, commit SHA, artifact ID, ukuran, dan SHA-256 APK.

**Gate D:** job check + build hijau. Status: **CI VERIFIED**. Belum release.

### Fase E — UAT perangkat, satu APK

Tidak perlu mereproduksi crash lagi pada artifact lama. Uji kandidat baru pada
INFINIX X6532C Android 14/API 34 ARMv7.

#### E1. Focus/tab loop — prioritas P0

1. Buka `INSTALL MODULES`.
2. Tap Library ↔ Manual **10 kali**.
3. Fokuskan field Requirement.
4. Ketik dan hapus teks berulang kali.
5. Saat keyboard terbuka: tap Library, kembali ke Manual, lalu ketik lagi.
6. Ulangi pergantian tab dan tombol Back/keyboard seperlunya.
7. Pastikan tidak force close dan field tetap dapat menerima input.

#### E2. State preservation

1. Isi requirement yang tidak berbahaya.
2. Scroll Library ke posisi yang mudah dikenali.
3. Scroll Manual/console bila tersedia.
4. Pindah tab bolak-balik.
5. Pastikan input dan posisi scroll yang dijanjikan tetap bertahan.

#### E3. Package flow kecil

1. Install satu pure-Python package kecil bila dibutuhkan.
2. Pastikan label semantic console terlihat dan bisa disalin:
   `[>]`, `[INFO]`, `[WARN]`, `[WAIT]`, `[OK]`, `[ERR]`, `[STOP]` sesuai jalur
   yang memang muncul; warna harus sesuai kind, bukan emoji/isi teks.
3. Buka dialog uninstall.
4. Tap **Batal**: package tidak terhapus dan UI tetap stabil.
5. Buka lagi, konfirmasi **Uninstall**.
6. Pastikan breadcrumb `PKG_UNINSTALL_REQUEST` dan verdict `OK/FAIL` tersedia.

Catatan cleanup: `hashid` (singular) berbeda dari `hashids`; berdasarkan log
terakhir, `hashid` kemungkinan masih terpasang. Ia dapat dipakai sebagai target
cleanup jika UI menawarkannya, tanpa menganggapnya package yang sama.

#### E4. Smoke regression ringkas

- Back ke editor dan pastikan file/input editor masih normal.
- Undo/Redo tidak kembali redup permanen setelah typing.
- Buka Diagnostics dan salin bagian crash/package yang relevan.
- Portrait wajib; landscape hanya smoke singkat karena layout pernah diperbaiki.

**LULUS E:** tidak ada force close, input tetap hidup, state bertahan, dialog
Batal aman, uninstall memiliki verdict, dan fungsi inti tidak regresi.

Jika crash yang sama kembali, **STOP release**. Jangan menambah patch spekulatif
ke artifact yang sama; pindahkan ke Focus Reliability Lab v1.0.20 dengan data
artifact baru.

### Fase F — Sanitasi dokumentasi dan final review

Setelah UAT kandidat lulus:

1. Tandai klaim swipe lama sebagai **REGRESSION FOUND**, bukan menghapus sejarah.
2. Koreksi dokumen perangkat dari Android 12/API lama menjadi runtime-proven:
   **Android 14 / API 34**, ABI `armeabi-v7a, armeabi`.
3. Bedakan nama pemasaran perangkat dari `Build.DEVICE`/`Build.MODEL` bila data
   yang tersedia tidak identik; jangan menebak.
4. Catat status semantic logs dan uninstall berdasarkan hasil visual/UAT nyata.
5. Review README, PRD, rencana, SKILLS, dan laporan UAT untuk klaim stale.
6. Jalankan ulang seluruh local gate setelah perubahan dokumentasi.

**Gate F:** dokumen tidak lagi mengklaim swipe stabil atau Android 12 sebagai
runtime final ketika bukti crash report menunjukkan Android 14/API 34.

### Fase G — Release gate dan PR

PR `arena/v1019-fondasi → main` hanya dibuka setelah persetujuan user terpisah.
Sebelum meminta persetujuan, laporan final wajib memuat:

- commit dan status workspace;
- ringkasan fix dan non-goals;
- hasil mutation proof;
- local test counts;
- CI run/artifact/hash;
- perangkat, OS/API, ABI, dan checklist UAT;
- limit yang masih terbuka;
- rollback point;
- konfirmasi **belum RELEASED**.

Release bukan konsekuensi otomatis dari CI hijau atau PR terbuka.

## 5. Jalur rollback

Jika tap-only menyebabkan regresi lokal/CI:

1. revert commit fix secara utuh;
2. jangan menggabungkan sebagian Pager dengan sebagian state tap-only;
3. periksa ulang diff terhadap parent commit swipe `2c51250^`;
4. pertahankan branch release tidak terkontaminasi eksperimen diagnostics.

Jika kandidat tap-only tetap crash di device, premis “Pager/focus clear sebagai
kelas pemicu” harus dibuka kembali. Status berubah menjadi **REGRESSION FOUND**;
release dihentikan dan investigasi v1.0.20 dinaikkan prioritas.

## 6. Pembuka v1.0.20 — Focus Reliability Lab

Ini pekerjaan terpisah dan tidak memblokir kandidat v1.0.19 setelah tap-only
lulus.

Targetnya bukan “swipe wajib kembali”, melainkan membuktikan akar pemicu dan
memilih UX yang stabil.

1. Buat reproducer minimal: dua page, TextField, IME, tap/swipe.
2. Tambahkan diagnostics ring-buffer yang minim data user:
   page request/current/settled, scroll state, field focus, window focus, IME,
   lifecycle, dan jenis key event—tanpa isi ketikan.
3. Uji matriks:

   | Varian | Pager | forced clearFocus | Compose |
   |---|---:|---:|---:|
   | A | ya | ya | 1.6.1 |
   | B | ya | tidak | 1.6.1 |
   | C | tidak | tidak | 1.6.1 |
   | D | ya | tidak | kandidat upgrade |

4. Audit compatibility Kotlin/AGP/Compose dan biaya ARMv7 sebelum upgrade.
5. Swipe hanya kembali bila pengujian berulang pada perangkat membuktikan
   stabilitas tanpa forced focus clear dan tanpa regresi input/state/performa.
6. Hasil sah juga dapat berupa keputusan mempertahankan tap-only permanen.

## 7. Sumber teknis

- Compose UI 1.6.1 source:
  https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui/1.6.1/ui-1.6.1-sources.jar
- Compose Foundation release notes:
  https://developer.android.com/jetpack/androidx/releases/compose-foundation
- Pager semantics (`currentPage` vs `settledPage`):
  https://developer.android.com/develop/ui/compose/layouts/pager
- Software keyboard controller:
  https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/SoftwareKeyboardController
- Public report dengan exception/focus-tree class yang sama:
  https://slack-chats.kotlinlang.org/t/10077161/it-seems-to-be-possible-to-get-the-new-focus-modifiers-in-an
- Pager/TextField issue di ekosistem Compose:
  https://github.com/JetBrains/compose-multiplatform/issues/4681

## 8. Catatan implementasi 2026-08-19

Fix tap-only telah diterapkan pada `PipScreen`: Pager, PagerState, opt-in Pager,
`LocalFocusManager`, dan dua forced focus clear dihapus. Satu enum `activeTab`
menjadi owner tab; input, Library scroll, Manual page scroll, dan console scroll
tetap di-hoist. Breadcrumb awal dan perubahan tab dipertahankan tanpa side
effect focus. Dependency Foundation tetap dipakai luas oleh UI lain sehingga
tidak dihapus pada fix ini.

Guard final bertambah dari kontrak swipe menjadi tujuh kontrak tap-only. Enam
arah mutasi dibuktikan merah secara terpisah: Pager kembali, PagerState kembali,
clearFocus kembali, mapping tab tertukar, state scroll tidak di-hoist, dan jalur
Library tidak menuju Manual. Source dipulihkan dan focused guard kembali hijau.

Verifikasi lokal:

```text
tools/check.sh                     : 572 passed
Kotlin lexical sanity             : 58 files passed
npm/editor supply-chain guard     : passed
git diff --check                  : passed
```

Belum ada kompilasi CI atau verifikasi perangkat untuk fix ini.

## 9. Status ringkas

```text
Crash                           : REGRESSION FOUND
Package Engine sebagai akar     : RULED OUT (confidence 99,5%)
Kelas masalah focus tree        : IDENTIFIED (confidence 99%)
Tap-only release fix            : IMPLEMENTED + LOCALLY VERIFIED
Mutation proof                  : 6 arah RED → restore GREEN
CI/device verification          : BELUM untuk fix ini
PR / merge / release            : BELUM
v1.0.20 Focus Reliability Lab   : DESIGNED, bukan scope v1.0.19
```
