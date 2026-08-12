# 🔬 Bagaimana IDE Python Android lain menangani masalah yang sama?

Riset 2026-08-13, menjawab pertanyaan user: *"apakah mereka pernah dapat case
seperti ZCODE, bagaimana mereka menanganinya atau malah membiarkannya?"*

Jawaban singkat: **semuanya kena. Tidak ada satu pun yang menyelesaikannya.**
Yang membedakan hanyalah **cara mereka menyerah.**

---

## 1. Empat strategi yang ada di dunia

| IDE | Strategi | Konsekuensi |
|---|---|---|
| **Pydroid 3** | Bangun **repositori wheel sendiri** | numpy/scipy jalan; **torch = PREMIUM ONLY** |
| **QPython** | Repositori sendiri (QPypi) | **binary-incompatible** dengan Pydroid |
| **PocketCode** | CPython → **WebAssembly** | **tidak ada jaringan**, stdlib saja, no pip |
| **Termux** | Linux userland + compiler | `pip install` sering **gagal build** (butuh Rust/C) |
| **ZCODE** | Chaquopy + resolver sendiri | 4 bug resolver + wheel native terbatas |
| **ZMUX** | **Alpine + PRoot + apk** | pip & apk **asli** |

---

## 2. Pydroid 3 — "solusi" yang ternyata paywall

Pydroid tidak memakai PyPI biasa untuk paket native. Ia punya **repository
plugin** berisi wheel yang mereka bangun sendiri.

Temuan yang paling relevan buat ZCODE:

> "Installing PyTorch (torch) requires the **premium version**, where it is
> marked as **PREMIUM ONLY** in the package manager due to the effort involved
> in porting and providing custom prebuilt wheels. Free users encounter a
> **paywall**."

**Jadi Pydroid tidak menyelesaikan masalah — Pydroid MENJUAL masalah itu.**
Persis batas yang sama (wheel native harus dibangun manual per-ABI), tapi
solusinya: yang sulit ditaruh di balik bayaran.

Ini penting buat ZCODE karena visi produknya **100% gratis tanpa premium lock**.
Artinya ZCODE memilih jalan yang **secara sadar lebih sulit dari Pydroid**.

Catatan tambahan: repositori Pydroid dan QPython saling **binary-incompatible** —
memasang wheel QPython di Pydroid merusak instalasi. Membangun repo wheel
sendiri berarti mewarisi seluruh beban pemeliharaannya selamanya.

---

## 3. PocketCode — jalan WebAssembly, dan harganya

Klaim resminya: CPython 3.11 penuh, on-device, offline.

Batas jujur yang mereka tulis sendiri:

> "**No internet from a script.** The interpreter runs in a WebAssembly sandbox
> with networking disabled — a script can't open a socket or fetch a URL."
>
> "**Standard-library Python only.** If a tutorial's first line is
> `pip install ...`, that part won't apply here."

**Tidak ada pip. Tidak ada jaringan.** Ini mengonfirmasi ulang penolakan
Pyodide/WASM untuk ZCODE — dengan bukti dari produk komersial yang sudah rilis,
bukan cuma dokumentasi.

Buat ZCODE yang harus menjalankan MJURRAN (butuh `requests`), ini otomatis gugur.

---

## 4. Termux — pip asli, tapi tetap gagal

Termux memakai PyPI resmi. Hasilnya (diskusi termux-app #3564):

> "Pydroid provides own library of python modules, that's why it works. But on
> Termux you use official PyPI and considering that Termux is a **custom
> platform**, the module should be built from source. In your case you don't
> have Rust compiler installed."

`pip install cryptography` di Termux → **gagal**, minta compiler Rust.

**Pelajaran penting:** pip asli **bukan jaminan**. Yang menyelamatkan bukan
"pip asli", melainkan **ketersediaan wheel prebuilt untuk ABI itu**.

Ini menurunkan ekspektasi terhadap rencana backend ZMUX: `pip install` di Alpine
tetap bisa gagal untuk paket native. Yang menyelamatkan Alpine adalah **`apk`**
(`apk add py3-numpy`) — bukan pip-nya.

---

## 5. Chaquopy — masalah ZCODE adalah masalah semua orang

Dari issue tracker Chaquopy:

| Issue | Isi |
|---|---|
| #1133 | "Can't install numpy >= 1.20 on Python 3.8" |
| #1227 | openai gagal karena `jiter` — **masih OPEN sejak Agu 2024** |
| #1192 | rpds-py tidak bisa dipasang — **masih OPEN** |
| #1237 | scipy berhenti di Python 3.10 |
| #1174 | opencv-contrib gagal |

Pola yang terlihat: setiap paket native yang belum di-port Chaquopy = **buntu**,
dan issue-nya bisa menganggur bertahun-tahun.

**Ini membenarkan keluhan user.** Rasa "diperbaiki puluhan kali, ujungnya sama"
punya dasar nyata: sebagian masalah memang **tidak ada di tangan kita**.

---

## 6. Jawaban langsung atas pertanyaan user

> *"Apakah mereka pernah mendapat case seperti ZCODE?"*

**Ya. Semuanya. Tanpa kecuali.**

> *"Bagaimana mereka menanganinya, atau malah membiarkannya?"*

Tidak ada yang menyelesaikan. Semua **memindahkan** masalahnya:

- **Pydroid** → jadikan berbayar (torch = premium)
- **QPython** → repo sendiri, pecah kompatibilitas
- **PocketCode** → buang jaringan & pip sekalian
- **Termux** → lempar ke user ("install Rust sendiri")
- **Chaquopy** → issue dibiarkan open bertahun-tahun

**Tidak ada IDE Python Android yang benar-benar menyelesaikan masalah paket
native.** Yang ada hanya pilihan: mana yang mau dikorbankan.

---

## 7. Yang membedakan ZCODE: user punya ZMUX

Ini keunggulan yang tidak dimiliki Pydroid, QPython, maupun PocketCode.

ZMUX (repo `muzape28-blip/ZMUX`, turunan ZABAWHEELS) = **Alpine Linux via PRoot**,
dan **sudah dipakai user secara rutin di HP-nya**. Itu bukan rencana, itu fakta.

Alpine punya `apk add py3-numpy`, `py3-scipy`, `py3-pandas` — sudah dikompilasi
untuk ARMv7 oleh tim Alpine, **gratis**, tanpa perlu ZCODE membangun wheel apa pun.

Perbandingan jujur:

| | Pydroid | ZCODE + ZMUX |
|---|---|---|
| numpy | ✅ (repo sendiri) | ✅ (`apk add py3-numpy`) |
| scipy ARMv7 | ✅ | ✅ **(Chaquopy tidak bisa)** |
| torch | 💰 premium | ✅ kalau ada di Alpine |
| hentikan script | ✅ proses terpisah | ✅ proses terpisah |
| biaya pemeliharaan | bangun wheel sendiri | **nol** — Alpine yang urus |

**Alpine menyelesaikan justru bagian yang Chaquopy tidak bisa: scipy di ARMv7.**

### Yang harus tetap jujur

- **Startup lambat.** PRoot + rootfs jauh lebih berat dari Chaquopy in-process.
  Untuk `print("hello")` Chaquopy tetap jauh lebih baik. ⇒ argumen kuat untuk
  **dua backend**, bukan mengganti.
- **`zmux-setup-storage` belum jalan** (dilaporkan user: output diam tanpa progres).
  Untuk integrasi ZCODE ini justru **tidak menghalangi** — ZCODE memberi path
  file secara langsung, tidak lewat `~/storage`.
- **ZMUX repo menyebut dirinya PoC** yang belum divalidasi sebagai release build,
  meski user sudah memakainya rutin. Dokumen dan kenyataan tidak sinkron.

---

## 8. Kesimpulan

Keluhan user **benar dan didukung bukti**: ini memang masalah seluruh ekosistem,
bukan kegagalan ZCODE semata. Tidak ada IDE Python Android yang menyelesaikannya.

Tapi dari riset ini muncul satu koreksi penting terhadap keputusan lama:

**Penolakan "ganti runtime" sebelumnya masih berlaku untuk Pyodide, CPython
PEP 738, dan PyO3 — tapi TIDAK berlaku untuk Alpine/PRoot.** Alpine tidak pernah
dievaluasi sebelumnya karena ZMUX belum terbukti. Sekarang terbukti: user
memakainya rutin di HP yang sama.

Rekomendasi tetap **dua backend**, bukan mengganti:

- **Chaquopy** — script cepat, startup instan (mayoritas pemakaian)
- **ZMUX/Alpine** — pip & apk asli, script bisa dihentikan, numpy + **scipy**

Dan tetap: **4 bug resolver harus diperbaiki lebih dulu**, karena keempatnya
milik ZCODE sendiri dan akan ikut terbawa ke arsitektur baru mana pun.
