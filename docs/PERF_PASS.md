# ⚡ PERF_PASS — Audit & Perbaikan Performa (2026-08)

Dokumen ini mencatat **investigasi "tersendat/lag"** di ZCODE pada perangkat
ampas (Infinix Smart 9 HD, ARMv7, RAM kecil) dan rencana perbaikannya. Prinsip:
jujur soal temuan dari kode, teliti ke akar masalah, dan perbaikan kecil per-
commit (protokol HATI-HATI). Tujuan: mengetik tetap ringan, navigasi tidak
ngebongkar editor, dan jalannya kode punya indikator yang jelas.

---

## 1. Lima akar masalah (berdasarkan pembacaan kode)

Bukti dirujuk ke `MainActivity.kt`, `WorkspaceViewModel.kt`, `TerminalScreen.kt`,
dan `WorkbenchScreen.kt`.

### A. WebView dibongkar tiap navigasi (paling nendang)
- `NavHost` menukar `WorkbenchScreen` ↔ `TerminalScreen` saat user menekan Run.
- WebView CodeMirror 6 di-destroy dan dibuat ulang setiap pindah layar.
- Biaya: inisialisasi bundle JS + parse ulang + reload tema → sendat tiap Run.
- **Arah fix:** jadikan terminal sebagai **overlay/layer** di atas editor (atau
  pertahankan instance WebView saat navigasi) sehingga editor tidak di-recreate.

### B. Python cold-start tanpa indikator
- `TerminalScreen.LaunchedEffect` memanggil `Python.start()` yang pertama kali
  bisa makan ~1–3 detik di ARMv7.
- Terminal tampak kosong/tak responsif; user mengira app hang.
- **Arah fix:** pesan **"Menyalakan Python…"** + spinner (rencana Batch 1.5),
  dan **pre-warm** Python saat app start (di background) tanpa memblokir UI.

### C. Disk write tiap ketik + state di luar main
- `EditorBridge.onCodeChange` (dipanggil dari thread WebView) →
  `vm.updateCode` → `FileManager.saveFile` (sinkron) + mutasi `mutableStateOf`.
- Menyimpan ke disk pada setiap penekanan tombol = I/O di jalur ketik; mutasi
  state Compose dari thread non-main juga tidak aman.
- **Arah fix:** **debounce** save (mis. 400–800 ms setelah berhenti mengetik),
  pastikan mutasi state di main thread, pisahkan state "kode aktif" dari
  "perlu disimpan".

### D. Rekomposisi lebar
- `activeCode` berada di state puncak, sehingga **seluruh `WorkbenchScreen.kt`**
  (~1000+ baris) ikut rekomposisi tiap ketik.
- **Arah fix:** pecah composable menjadi scope kecil, pakai
  `derivedStateOf` untuk nilai turunan, dan lewatkan parameter stabil/lambda
  agar hanya bagian editor yang rekompos.

### E. Terminal = 1 string raksasa + `scrollTo` tiap chunk
- `TerminalScreen.append` membuat string baru untuk seluruh output lalu
  `scrollState.scrollTo` dipanggil tiap ada potongan output.
- Output panjang → banyak alokasi + jank saat scroll.
- **Arah fix:** **coalescing** scroll (mis. tiap ~120 ms), dan pertimbangkan
  `LazyColumn`/buffer terbatas (ring buffer) untuk output panjang.

---

### F. Khusus: tombol Run (FAB ▶) terasa lambat/lag — LAPORAN USER (2026-08)
- **Gejala (user):** tombol Run saja yang terasa kurang responsif/lag setelah
  di-tap. Tombol lain tidak dikeluhkan. Diuji hanya di **Infinix Smart 9 HD**
  (ARMv7, satu-satunya perangkat user) — jadi ini adalah perangkat TARGET,
  bukan kasus pinggiran.
- **Akar dari kode (`WorkbenchScreen.kt` onClick FAB):**
  1. `vm.applyAutoTrimIfEnabled()` dijalankan di **thread UI** (bisa memindai
     seluruh `activeCode`).
  2. `pushCode()` menulis kode ke `WebView` via `evaluateJavascript("setCode(...)")`
     secara sinkron di jalur klik — di editor file panjang, ini menahan respons
     tap.
  3. `onRun(active)` → `navController.navigate("output/...")` yang **memulai
     `TerminalScreen` baru**. Di sana `LaunchedEffect` melakukan cold-start
     Python (~1–3 dtk di ARMv7) — layar sempat tampil kosong/putih tanpa
     feedback, sehingga user mengira tap-nya telat.
  4. Perpindahan layar juga membongkar `WorkbenchScreen` (WebView CM6 di-recreate
     saat kembali) — lihat akar A; menambah kesan "berat" tiap kali Run.
- **Dampak:** tap Run tidak langsung memberi reaksi visual; terasa delay sebelum
  terminal muncul, apalagi pada file berukuran sedang/besar di HP RAM kecil.
- **Arah fix (urut dampak):**
  1. **Feedback seketika:** saat FAB di-tap, langsung tampilkan state "running"
     (mis. FAB berubah jadi spinner/"Menyiapkan…", atau overlay tipis) JANGAN
     tunggu Python.
  2. **Pindahkan kerja berat keluar thread UI:** `pushCode()` & auto-trim cukup
     kirim sinyal/simpan state (defer), bukan `setCode` sinkron raksasa; bila
     perlu ambil kode dari VM/bridge yang sudah otoritatif tanpa round-trip JS.
  3. **Indikator cold-start "Menyalakan Python…" + pre-warm** (sudah direncanakan
     di §1 B) sehingga layar terminal tidak kosong.
  4. **Terminal sebagai overlay/layer** (jangan navigate yang bongkar editor) —
     lihat akar #1 A. Ini sekaligus mempercepat kembali ke editor setelah Run.

## 2. Rencana perbaikan (PERF_PASS)

| # | Perbaikan | Berkas utama | Risiko |
|---|---|---|---|
| 1 | Terminal jadi overlay (jangan recreate WebView) | `MainActivity.kt`, navigasi | Sedang — ubah struktur UI |
| 2 | Indikator cold-start + pre-warm Python (termasuk feedback FAB Run) | `TerminalScreen.kt`, `WorkspaceViewModel.kt`, `WorkbenchScreen.kt` | Rendah |
| 3 | Debounce save + thread-safety state | `WorkspaceViewModel.kt`, `FileManager.kt` | Rendah–sedang (jangan sampai hilang ketikan) |
| 4 | Pecah composable + `derivedStateOf` | `WorkbenchScreen.kt` | Sedang (wajib regresi visual) |
| 5 | Coalesce scroll + buffer terminal | `TerminalScreen.kt` | Rendah |

Urutan yang disarankan: **(2) indikator/pre-warm** dulu (cepat, mengurangi
persepsi ngehang), lalu **(3) debounce** dan **(5) scroll**, terakhir **(1)
overlay** dan **(4) pecah composable** yang mengubah struktur.

---

## 3. Edge case yang HARUS dijaga (teliti)

- **Jangan sampai ketikan hilang.** Debounce save harus tetap flush saat:
  layar ditutup, app di-background/pause, file diganti, atau Run ditekan.
- **Pre-warm Python** tidak boleh menambah waktu start app atau menampilkan
  error sebelum user membuka terminal.
- **Overlay terminal** harus tetap mengirim `input()`/stdin dengan benar
  (lihat `TerminalBridge` — taruhan nyawa, wajib UAT).
- **Scroll coalescing** tidak boleh bikin output terakhir tidak kelihatan
  (selalu scroll ke bawah saat user tidak sedang scroll ke atas).
- **State thread-safety:** perubahan `activeCode` harus konsisten dengan jembatan
  JS↔Kotlin (`onCodeChange`/`setCode`); hindari feedback loop (JS set state,
  state setCode balik).
- Rotasi/perubahan konfigurasi tidak boleh mereset buffer/posisi.

---

## 4. Pengukuran (bukan asumsi)

- Sebelum/sesudah: ukur waktu dari tap Run sampai terminal siap; waktu dari
  ketik sampai huruf muncul di HP ARMv7.
- Pakai **logcat timing** sederhana (bukan profiler berat) untuk cold-start
  Python & durasi save.
- Patokan: mengetik harus terasa instan; tidak ada frame hilang saat output
  deras; pindah editor↔terminal tidak memuat ulang editor.

---

## 5. Pengujian & UAT

1. **Sandbox:** test logika debounce/flush sebagai unit murni bila dipisah ke
   kelas kecil (tanpa Android). `tools/check.sh` + pytest tetap hijau.
2. **CI:** hakim kompilasi Kotlin.
3. **UAT di Infinix Smart 9 HD:**
   - [ ] Mengetik cepat di file panjang tidak patah-patah.
   - [ ] Teks tersimpan setelah berhenti (~<1 s); tetap tersimpan saat Run/pindah file/background.
   - [ ] Run pertama menampilkan "Menyalukan Python…", tidak ngehang.
   - [ ] **Tap tombol Run langsung bereaksi (spinner/state) tanpa jeda**, bahkan
         di file panjang; terminal tidak pernah layar kosong tanpa pesan.
   - [ ] Pindah ke terminal & kembali tidak me-reload editor/posisi kursor.
   - [ ] Output deras (mis. loop `print`) tidak bikin UI freeze; baris terakhir terlihat.
   - [ ] `input()` tetap berfungsi di terminal.

---

## 6. Hubungan dengan dokumen lain

- `docs/RENCANA_UPDATE_2026_08.md` §1 — indikator "Menyalakan Python…" &
  Batch 1.5 (audit ketikan ringan).
- `docs/PLAN_BATCH_ANTI_SEPI.md` — konteks ringan/anti-sepi.
- `VPP_DESIGN.md` — panel error tidak boleh menambah beban rekomposisi.
- `LIBRARY_DESIGN.md` — install pip streaming tanpa blokir UI (butuh #2/#5).

---

*Catatan investigasi — angka & perbaikan diverifikasi dengan ukuran nyata di
perangkat, bukan ditebak dari sandbox.*
