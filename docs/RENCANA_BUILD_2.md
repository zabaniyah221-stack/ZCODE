# 📋 Rencana Build #2 — perbaikan installer ZCODE

Dokumen persetujuan. **Belum ada kode yang diubah.**
Ditulis 2026-08-13 untuk dibaca user sebelum memberi izin.

---

## Ringkasan satu kalimat

Lima bug, semuanya milik ZCODE sendiri (bukan Chaquopy), semuanya sudah
dibuktikan dengan menjalankan datanya — bukan dugaan.

---

## BUG A — filter versi Python membandingkan barang yang salah

**Lokasi:** `app/src/main/python/package_runtime/resolve.py:114`

```python
def _requires_python_ok(requires_python: str | None, version: str) -> bool:
    return SpecifierSet(requires_python).contains(Version(version))
```

`requires_python` berisi syarat **versi Python** (`">=3.7"`).
`version` berisi **versi PAKET** (`"0.4.6"`).

Yang ditanyakan ZCODE: *"apakah colorama 0.4.6 memenuhi `>=3.7`?"*
→ `0.4.6 < 3.7` → **False, dibuang.**

Yang seharusnya: *"apakah Python 3.11 memenuhi `>=3.7`?"* → True.

**Bukti (metadata PyPI asli, dihitung 2026-08-13):**

| Paket | Kandidat sekarang | Seharusnya |
|---|---|---|
| colorama | 7 | 13 |
| urllib3 | 23 | 82 |
| pygments | 16 | 50 |
| **mdurl** | **0** | **3** |

`mdurl` = 0 kandidat → persis pesan error di HP user:
`mdurl: Tidak ada wheel kompatibel`.

**Perbaikan:** bandingkan dengan versi Python runtime (`3.11`), bukan versi paket.

---

## BUG B — "The source file doesn't exist"

**Lokasi:** `DependencyResolver.kt:98-99` + `PackageEngineV2.kt:153-156`

```kotlin
url       = p.optString("url"),
localPath = p.optString("local_path"),   // ← tidak ada di JSON
```

```kotlin
if (p.localPath != null) {              // "" BUKAN null → masuk
    val local = File(p.localPath)       // File("") = path kosong
    local.copyTo(wheelFile)             // ← "The source file doesn't exist"
}
```

`org.json.JSONObject.optString()` **tidak pernah mengembalikan null** — kalau
field tidak ada, hasilnya string kosong `""`. Jadi setiap paket dari PyPI
dianggap punya wheel lokal, dan **download tidak pernah dijalankan.**

Ini penyebab persis log user: `colorama` lolos Resolve → lolos Transaction →
mati di Download.

**Perbaikan:** `if (p.isNull("local_path")) null else p.optString(...)`,
diberlakukan juga untuk `url`. Ditambah penjaga `localPath?.takeIf { it.isNotBlank() }`.

---

## BUG C — `stdlib.json` tidak pernah dibaca resolver

**Lokasi:** `resolve.py` — kata "stdlib" muncul **0 kali**.

ZCODE punya daftar 305 nama stdlib di `assets/package_catalog/stdlib.json`,
tapi resolver tidak pernah membukanya. Akibatnya `analyze math` dikirim ke PyPI
dan gagal dengan pesan menyesatkan:
`math: Tidak ada wheel kompatibel untuk runtime ZCODE ini.`

Padahal `math` **sudah ada** di Python — tidak perlu diinstall sama sekali.

**Perbaikan:** cek `sys.stdlib_module_names` lebih dulu; kalau cocok, balas
"sudah tersedia di Python, tidak perlu install" (bukan error).

---

## BUG D — pemilihan versi diurutkan secara ALFABETIS

**Lokasi:** `app/src/main/python/package_runtime/wheelinfo.py:119`

```python
ranked.sort(key=lambda r: (r[0], r[1].get("filename", "")))
```

Diurutkan sebagai **teks**, bukan versi:

- `"0.3.5" < "0.4.6"` → selalu ambil yang tertua
- `urllib3-1.10` dikira **lebih kecil** dari `urllib3-1.9` (karena `'1' < '9'`)

**Bukti — simulasi pipeline penuh memproduksi ulang log HP user:**

| Paket | Hasil simulasi | Setelah fix |
|---|---|---|
| colorama | **0.3.5** ← sama persis dengan log user | 0.4.6 |
| requests | 2.0.0 (2013) | 2.34.2 |
| urllib3 | 1.11 | 2.7.0 |
| click | 7.0 | 8.4.2 |

Simulasi mengeluarkan `colorama-0.3.5` **tanpa diberi tahu** — bukti model
diagnosis ini akurat.

**Perbaikan:** urutkan pakai `packaging.version.Version`, bukan string.
Tambahan (pelajaran dari ZABACODE): untuk "install nama tanpa versi", pakai
`data["urls"]` (versi terbaru saja) — Bug A & D otomatis tidak punya tempat
hidup di jalur tersering.

---

## BUG E — terminal kosong padahal ada `print()`

**Lokasi:** `core/execution/OutputBatcher.kt`

```kotlin
@Volatile private var running = true    // ← hanya di-set sekali saat objek dibuat

fun close() { running = false; ... }

fun start() {
    thread = Thread { while (running) { ... } }   // run ke-2: langsung berhenti
}
```

Setelah `close()`, `running` tinggal `false` selamanya. `start()` berikutnya
membuat thread yang loop-nya langsung berakhir → batcher **hidup tapi tuli**,
semua output script dibuang **tanpa error apa pun**.

Cocok dengan laporan user: baris `[sys]` tetap muncul (jalur lain), output
script hilang total, dan run ID-nya `..._4_` (run **ke-4**, bukan pertama).

⚠️ **Ini bug yang saya buat sendiri di build #1** saat memindahkan
`batcher.start()` ke `DisposableEffect`. Sebelumnya tidak ada.

**Perbaikan:** `running = true` di awal `start()` (sudah ditulis di workspace,
belum di-commit).

---

## Ringkasan dampak

| Paket | Sekarang | Sesudah | Keyakinan |
|---|---|---|---|
| colorama, tqdm, click, tabulate | ❌ | ✅ terbaru | ~90% |
| requests + urllib3 + certifi + idna | ❌ | ✅ terbaru | ~85% |
| rich (butuh mdurl + pygments) | ❌ | ✅ | ~85% |
| `math`, `json` (stdlib) | ❌ pesan salah | ✅ pesan benar | ~95% |
| terminal `print()` | ❌ kosong | ✅ tampil | ~85% |
| **numpy, pandas, pillow, matplotlib** | ❌ | ❌ **tetap** | — |
| **scipy** | ❌ | ❌ **selamanya** | — |

**Yang TIDAK diperbaiki build ini, dan alasannya:**

- **numpy/pandas/pillow/matplotlib** — butuh perbaikan pencocokan tag Android
  (`sys_tags()` menghasilkan `linux_armv7l`, wheel Chaquopy bertag
  `android_21_armeabi_v7a`). Wheel-nya **ADA** (lihat
  `ARMV7_COMPAT_2026_08_13.md`), hanya belum dikenali. → build #3.
- **scipy** — tidak ada cp311 di Chaquopy. Mustahil di ARMv7 selamanya.
  Hanya bisa lewat Alpine (`apk add py3-scipy`). → build #4.
- **Menghentikan script paksa** — butuh proses terpisah. → build #4.
- **Terminal sidebar + ZMUX** — → build #4.
- **Library "perpustakaan mini" 50 entri** — → build #5.

---

## Yang juga akan disentuh (kecil tapi wajib)

**Sinkronisasi `tested-manifest.json`.** Isinya `numpy==1.26.4` dan
`pillow==10.3.0`; kedua versi itu **tidak ada** di indeks Chaquopy. Yang ada
`1.26.2` dan `9.2.0`. Kalau tidak diralat, build #3 akan gagal walau fix
tag-nya benar.

---

## Cara verifikasi (setiap bug wajib punya guard yang terbukti bisa gagal)

Sesuai peraturan #2. Untuk tiap bug:

1. tulis test yang **gagal** pada kode sekarang
2. perbaiki kodenya
3. jalankan uji mutasi: kembalikan bug → test **harus merah**; pulihkan → hijau

Guard yang tidak pernah bisa gagal adalah guard palsu. Saat ini 244 test;
target ±252.

Yang **tidak bisa** saya verifikasi: sandbox tidak punya JDK/Android SDK, jadi
kode Kotlin tidak bisa dikompilasi di sini. Kebenaran akhir hanya terbukti di
HP Anda.

---

## Risiko

| Risiko | Besar | Mitigasi |
|---|---|---|
| CI merah lagi | sedang | 3 pemindai pola + 244 guard sebelum push |
| Perbaikan bikin bug baru | **nyata** — Bug E buktinya | uji mutasi tiap guard |
| numpy tetap gagal | **pasti** | sudah dinyatakan di atas, bukan kejutan |

Estimasi jujur: **±2 jam kerja + 1 siklus CI**. Bukan berhari-hari.

---

## Yang diminta

Izin mengerjakan **Bug A–E + sinkronisasi manifest**, lalu push dan pantau CI
sampai hijau.

Setelah APK jadi, yang dibutuhkan dari user:

1. Tap ▶ pada script `print("hello")` → **output muncul?**
2. Install Modules → `colorama` → **berhasil?**
3. Install `requests` → **berhasil?**
4. `analyze math` → **bilang "sudah tersedia", bukan error?**

Kalau keempatnya lewat, build #3 (tag Android → numpy/matplotlib) bisa
dikerjakan dengan pijakan yang jelas.
