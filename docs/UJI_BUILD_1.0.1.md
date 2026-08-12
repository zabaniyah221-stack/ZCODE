# 🧪 Panduan Uji ZCODE v1.0.1 — build "hentikan pendarahan"

CI hijau, APK siap diunduh.
Run: `31614934302` · branch `arena/019ff292-zcode` · artifact **ZCODE-Fase12-APK**

---

## Cara ambil APK

GitHub → repo ZCODE → tab **Actions** → run paling atas di branch
`arena/019ff292-zcode` (judulnya `fix(build): import operator delegasi Compose…`)
→ scroll ke bawah → **Artifacts** → `ZCODE-Fase12-APK`.

**Pastikan versinya benar:** buka **About** → harus tertulis **v1.0.1**.
Kalau masih `v1.0.0`, itu APK lama — jangan diuji, hasilnya menyesatkan.
(Angka ini sekarang dibaca dari APK, bukan tulisan mati di kode.)

---

## Yang berubah di build ini

| Yang diperbaiki | Efek yang diharapkan |
|---|---|
| `packaging` masuk bundle | **Install Modules hidup** untuk paket pure-Python |
| Race `Python.start()` | kandidat force close saat tap ▶ hilang |
| `batcher.start()` telanjang di komposisi | dibungkus `DisposableEffect` |
| `item(key = { -1L })` (lambda jadi key) | key dihapus |
| key dari `buffer.startOffset` (tidak stabil) | key dihapus |
| `requestFocus()` sebelum node siap | ditunda 1 frame + `runCatching` |
| Tulis-disk ±75×/detik di UI thread | dipindah ke thread IO |
| `catch (Exception)` meloloskan OOM | jadi `catch (Throwable)` |
| Timeout jaringan tidak ada | `socket.setdefaulttimeout(30)` |
| **Breadcrumb + Crash Reporter** | ZCODE bisa cerita saat mati |

---

## Urutan uji (mohon berurutan)

### TES 1 — Diagnostik hidup
1. Buka ZCODE → **About**
2. Cek tertulis **v1.0.1**
3. Tap **Lihat Diagnostik**

**Harus terlihat** baris seperti:
```
08-12 21:15:03 | APP_START | v1.0.1 api=31 abi=armeabi-v7a
```
Kalau panelnya kosong → diagnostik gagal, laporkan ini dulu sebelum tes lain.

### TES 2 — Tap ▶ Run (INI YANG UTAMA)
1. Buka `main.py` (yang bawaan, jangan yang berat dulu)
2. Tap **▶**

**Kalau JALAN** → lanjut TES 3.

**Kalau MASIH FORCE CLOSE** → buka ulang ZCODE → About → Lihat Diagnostik →
**Salin** → kirim ke saya. Baris terakhir menunjukkan langkah terjauh yang tercapai:

| Berhenti di | Artinya |
|---|---|
| `FAB_TAP` | crash saat menyimpan file |
| `TERMINAL_COMPOSE` | crash saat menyusun layar terminal |
| `TERMINAL_EFFECT` | crash saat menyiapkan log |
| `SESSION_START_CALL` | crash saat memulai session |
| `PYTHON_START_BEGIN` | **crash di dalam Chaquopy** (native) |
| `SCRIPT_BEGIN` | crash di dalam script Python |

Apa pun hasilnya, jejaknya berguna — kita tidak lagi menebak.

### TES 3 — Install module
**Install Modules** → tab **MANUAL INSTALL** → ketik `colorama` → **Install**

- ✅ berhasil → bug `packaging` beres
- ❌ masih `No module named packaging` → `packaging` tidak ikut ke APK
- ❌ error lain → **salin pesannya**, itu lapis masalah berikutnya

Kalau `colorama` berhasil, coba **`requests`** — ini yang dibutuhkan MJURRAN.

### TES 4 — MJURRAN mode ONLINE
Kalau `requests` terpasang, jalankan script MJURRAN.
Seharusnya kini muncul `MJURRAN [ONLINE]`, bukan `[OFFLINE]` terus.

⚠️ Sebelum menjalankan, pertimbangkan mengubah 1 hal di script Anda:

```python
if not kb.phrases:
    st = scraper.run()      # ← bootstrap scrape saat start
```

`api.pushshift.io` sudah lama ditutup untuk umum, jadi baris ini kemungkinan
besar hanya menghabiskan waktu (dulu ±50 detik diam di layar). Boleh
dinonaktifkan sementara agar startup instan. **Ini saran, bukan keharusan.**

### TES 5 — Script berat (opsional, kalau 1–4 lancar)
Jalankan script yang mencetak banyak baris, contoh:
```python
for i in range(5000):
    print(i, "baris panjang untuk menguji buffer terminal")
```
Ini menguji perbaikan I/O thread + key LazyColumn. Kalau lancar tanpa macet
atau mati, dua perbaikan itu terbukti.

---

## Yang **BELUM** diperbaiki (jangan diharapkan jalan)

Peraturan #1 — jujur juga soal yang belum:

1. **numpy / matplotlib / pillow masih GAGAL.** Pencocokan tag wheel Android
   belum diperbaiki (`sys_tags()` menghasilkan `linux_armv7l`, sementara wheel
   Chaquopy bertag `android_21_armeabi_v7a`). Ini pekerjaan build berikutnya —
   67 dari 300 paket terdampak.

2. **Script masih belum bisa dihentikan paksa.** `Ctrl+C`/`Stop` hanya
   menyalakan flag yang dibaca saat `input()`. Script yang berputar di loop atau
   menunggu jaringan tetap jalan walau Anda menekan Back. Yang bertambah
   sekarang hanya *keterlihatannya* (`live=N` di breadcrumb). Solusi
   sesungguhnya = proses Python terpisah, belum dikerjakan.

3. **Halaman detail LIBRARY masih pop-up sempit** dengan nomor `1. 2. 4. …`
   dan data kosong untuk 280 paket. Belum disentuh sama sekali.

4. **Force close belum dijamin sembuh.** Tiga tersangka diperbaiki sekaligus,
   tapi tanpa logcat saya tidak bisa memastikan salah satunya memang
   pelakunya. Keyakinan jujur: ±60% sembuh, ±95% kita tahu TKP-nya dari
   breadcrumb.

---

## Yang saya butuhkan dari Anda

1. Hasil **TES 1–3** (minimal)
2. Kalau force close: **isi Diagnostik** (tombol Salin)
3. Kalau install gagal: **teks error di INSTALLATION LOG**

Dari situ kita tentukan build berikutnya.
