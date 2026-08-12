# 🧪 Panduan Uji ZCODE v1.0.2 — build #2 "installer jalan"

Ambil artifact **ZCODE-Fase12-APK** dari run terbaru di branch
`arena/019ff292-zcode` (Actions → run teratas → Artifacts).

**Cek dulu: About harus tertulis `v1.0.2`.** Kalau masih `v1.0.1`, itu APK
build #1 — jangan diuji, hasilnya menyesatkan.

---

## Urutan uji

### TES 1 — `print()` muncul lagi (Bug E)

Buka `main.py`, tap **▶**.

Harus tampil `Hello, ZCODE!`. Di build #1 layar ini **kosong**.

> Bug ini saya yang sebabkan di build #1 saat memindahkan `batcher.start()`
> ke `DisposableEffect` — flag `running` tidak pernah di-reset, jadi batcher
> hidup tapi tuli dan membuang semua output tanpa error.

### TES 2 — copas (Bug I)

Di layar terminal:
1. **Long-press** salah satu baris → harus bisa diseleksi
2. Tap tombol abu **Salin** → toast "Output disalin (N baris)"
3. Paste di WhatsApp/Notes → teksnya masuk?

Sebelumnya **tidak ada satu pun teks di ZCODE yang bisa disalin**.
Ini alat kerja utama Anda sebagai QA tester tanpa PC.

### TES 3 — install `colorama` (Bug A, B, D)

Install Modules → MANUAL INSTALL → `colorama` → Install.

| Yang dicek | Harus |
|---|---|
| versi di plan | **0.4.6** — bukan 0.3.5 |
| tahap Download | **lewat** — bukan "The source file doesn't exist" |
| hasil | ✅ terpasang |

Kalau versinya masih `0.3.5`, berarti Bug D belum kena.

### TES 4 — install `requests`

Harus menarik **4 paket**: requests, urllib3, certifi, idna/charset-normalizer.
Versinya harus modern (requests ~2.3x, urllib3 ~2.x) — bukan 2.0.0 (2013).

Kalau berhasil, **MJURRAN bisa mode ONLINE** untuk pertama kalinya.

### TES 5 — install `rich` (Bug A)

Sebelumnya gagal: `mdurl: Tidak ada wheel kompatibel; pygments: ...`
Sekarang keduanya harus ketemu.

### TES 6 — `analyze math` (Bug C)

Harus menjawab kira-kira:

> ℹ️ 'math' adalah modul bawaan Python — sudah tersedia di ZCODE dan tidak
> perlu dipasang. Langsung `import math` di script.

Bukan lagi ❌ "Tidak ada wheel kompatibel".

### TES 7 — Diagnostik lebih besar

About → **Lihat Diagnostik**. Sekarang 60% tinggi layar (dulu 220dp ≈ 3 baris),
font 11sp, 200 baris (dulu 40). Isinya juga bisa diseleksi.

Cari baris `PKG_INSTALL_BEGIN` / `PKG_INSTALL_OK` — jejak Install Modules
sebelumnya **tidak pernah dicatat sama sekali** (Bug J).

---

## Kalau ada yang gagal

Sekarang Anda bisa **menyalin**. Kirim:

1. isi **INSTALLATION CONSOLE** (tombol seleksi/long-press)
2. isi **INSTALLATION LOG**
3. **Diagnostik** dari About

---

## Yang TIDAK diperbaiki di build ini

Jujur, supaya tidak ada harapan palsu:

| | Kenapa | Kapan |
|---|---|---|
| **numpy, pandas, pillow, matplotlib** | pencocokan tag Android belum diperbaiki. Wheel-nya **ADA** (numpy 1.26.2, pillow 9.2.0) | build #3 |
| **scipy** | tidak ada cp311 di Chaquopy — mustahil di ARMv7 | hanya lewat Alpine, build #4 |
| **pyyaml** | butuh perbaikan tag yang sama | build #3 |
| **Hentikan script paksa** | butuh proses terpisah | build #4 |
| **EDITOR HANDLE**, layar DIAGNOSTICS di sidebar | UI | build #3 |
| **Terminal Alpine** | | build #4 |
| **Library 50 entri** | | build #5 |

---

## Yang berubah (10 bug)

| ID | Bug | Akibat sebelumnya |
|---|---|---|
| A | `Requires-Python` dibanding versi **paket** | mdurl 0 kandidat, colorama mundur ke 2015 |
| B | `optString()` → `""` bukan `null` | "The source file doesn't exist" |
| C | `stdlib.json` tak pernah dibaca | `math` dicari ke PyPI |
| D | versi disortir **alfabetis** | selalu ambil versi tertua |
| E | `OutputBatcher.running` tak di-reset | terminal kosong |
| F | wheel `manylinux` tak ditolak | risiko SIGSEGV |
| G | cache wheel tak dipisah per-ABI | risiko crash setelah restore |
| H | WebView `about:blank` tak dijaga | risiko crash loop cold-start |
| I | tak ada teks yang bisa disalin | mustahil melapor |
| J | breadcrumb tak meliputi installer | install gagal tanpa jejak |

Plus: `tested-manifest.json` diralat (`numpy 1.26.4`→`1.26.2`,
`pillow 10.3.0`→`9.2.0`; dua versi lama itu **tidak ada** di indeks Chaquopy).

**265 test hijau.** Setiap bug dikunci guard yang dibuktikan lewat uji mutasi:
bug dikembalikan → test merah, dipulihkan → hijau.
