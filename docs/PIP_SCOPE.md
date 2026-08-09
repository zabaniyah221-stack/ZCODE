# 📦 PIP_SCOPE — Cakupan Pip/Paket yang Layak untuk ZCODE (2026-08)

Dokumen ini mendefinisikan **cakupan paket pip yang ZCODE akui, uji, dan dukung**
di perangkat target (khususnya **armeabi-v7a / ARMv7** dengan RAM terbatas).
Bukan janji "semua PyPI jalan", tapi peta jujur: mana yang dijamin, mana yang
berat, mana yang beneran di luar jangkauan. Dokumen ini dipakai sebagai sumber
data untuk `LIBRARY_DESIGN.md` (katalog paket di menu INSTALL MODULES).

Aturan tim berlaku: *honest about anything / be meticulous in everything*.

---

## 1. Kenapa dokumen ini ada

ZCODE meng-embed Python in-process via **Chaquopy 3.11** (lihat
`CHAQUOPY_STRATEGY_2026_08.md`). Pip berjalan on-device, menginstal ke
`user_packages` (`--target`). Realita cakupan:

- **Pure-Python** = praktis 100% PyPI (Chaquopy bisa build sdist).
- **Native (C extension)** = hanya yang punya **Android wheel** di repo Chaquopy
  (`chaquo.com/pypi-13.1/`). Subset besar tapi tidak lengkap.
- **Butuh GUI / native lib besar / arsitektur tak didukung** = di luar jangkauan,
  harus gagal dengan pesan **bersih & jujur**, bukan traceback berantakan.

Dokumen ini mencegah dua kesalahan:
1. Mengklaim "semua library lengkap" (tidak feasible offline, tidak jujur).
2. Membebankan user menebak paket mana yang jalan di HP-nya.

---

## 2. Aturan klasifikasi (4 tag)

| Tag | Arti | Warna usulan |
|---|---|---|
| ✅ **recommended** | Pure-Python populer ATAU native yang wheel-nya ada di Chaquopy + ringan; dipastikan jalan | hijau |
| ⚠️ **heavy** | Secara arsitektur jalan, tapi makan RAM/storage/CPU di HP ampas — pasang hanya kalau perlu | kuning |
| ❌ **unsupported** | Tidak ada wheel Android/ARM, butuh native lib yang tak tersedia, atau bentrok arsitektur | merah |
| 🚫 **out-of-scope** | Butuh GUI desktop/web server penuh/browser — ranah ZPLAY atau App Mode, bukan inti ZCODE | abu-abu |

> Tag **dihitung runtime** dari ABI perangkat (`Build.SUPPORTED_ABIS`) + RAM,
> dicocokkan dengan metadata `kind`, `supported_abis`, `heavy_on_low_end`.
> Jadi di HP ARMv7, paket native yang cuma punya wheel arm64 bisa tampil ❌ walau
> di HP lain ✅. Tag bersifat **advisory + jujur**, bukan garansi mutlak.

---

## 3. Kurasi paket yang didukung (contoh isi katalog)

Daftar ini **bukan daftar final yang dibundle** — ini usulan isi
`assets/libraries.json`. Roda (wheel) TIDAK di-bundle ke APK (bikin gendut);
install tetap on-device. Target ~150–300 paket populer.

### 3.1 Web & Networking ✅ (mayoritas pure-Python)

| Paket | Tag | Catatan |
|---|---|---|
| `requests` | ✅ | Standar HTTP, ringan |
| `httpx` | ✅ | Modern sync/async |
| `urllib3` | ✅ | Dep requests |
| `beautifulsoup4` | ✅ | Parsing HTML |
| `lxml` | ⚠️ | Native — ada wheel Chaquopy tapi agak berat |
| `flask` | ✅ | Jalur **App Mode** (Flask+WebView) |
| `fastapi` | ⚠️ | + `uvicorn`; butuh sedikit resource |
| `waitress` | ✅ | Server WSGI murni-Python (dipakai keluarga ZABACODE) |
| `websockets` | ✅ | Untuk App Mode interaktif |
| `aiohttp` | ⚠️ | Native, agak berat |

### 3.2 Data & Sains

| Paket | Tag | Catatan |
|---|---|---|
| `numpy` | ✅ | Wheel Chaquopy tersedia; dasar banyak paket |
| `pandas` | ⚠️ | Berat di HP ampas; dataset besar bikin ngos-ngosan |
| `matplotlib` | ⚠️ | Backend **Agg** → keluarin PNG (lihat roadmap "Matplotlib Inline Image"); GUI backend ❌ |
| `pillow` | ✅ | Native, wheel ada; ringan |
| `scipy` | ⚠️ | Berat |
| `sympy` | ✅ | Murni-Python, matematika simbolik |
| `scikit-learn` | ⚠️ | Native + berat; model kecil saja |
| `scikit-image` | ⚠️ | Native, turunan numpy |
| `opencv-python` | ❌/⚠️ | `opencv` headless berat; perlu verifikasi wheel ARM |

### 3.3 Utility / Stdlib-enhancement ✅ (murni-Python)

| Paket | Tag |
|---|---|
| `rich`, `click`, `tqdm`, `colorama` | ✅ |
| `python-dateutil`, `pytz`, `tzdata` | ✅ |
| `pydantic` | ✅ |
| `regex` | ✅ |
| `pyyaml`, `toml`, `tomli` | ✅ |
| `certifi` | ✅ (CA bundle, penting untuk HTTPS) |
| `packaging`, `setuptools`, `wheel` | ✅ (sudah di-bundle) |

### 3.4 Office & File

| Paket | Tag |
|---|---|
| `openpyxl` | ✅ (Excel) |
| `xlsxwriter` | ✅ |
| `python-docx` | ✅ |
| `reportlab` | ⚠️ (PDF, agak berat) |
| `csv`, `json` | ✅ (stdlib, tak perlu install) |

### 3.5 Automation & Misc

| Paket | Tag |
|---|---|
| `schedule` | ✅ |
| `python-dotenv` | ✅ |
| `pyjwt`, `cryptography` | ⚠️ (cryptography native, cek wheel ARM) |
| `pynput`, `keyboard`, `pyautogui` | ❌ (butuh kontrol desktop/input Android, tak tersedia) |

---

## 4. Yang jujur OUT-OF-SCOPE (jangan ditawari)

| Kelompok | Contoh | Alasan | Jalur |
|---|---|---|---|
| ML/AI berat | `tensorflow`, `torch`, `keras` | Tak ada wheel Android/ARM yang layak, ukuran raksasa | ZMUX/PC |
| Notebook | `jupyter`, `pyzmq` | Butuh `libzmq` native (belum ada wheel ARM Chaquopy) + web UI | App Mode / ZMUX |
| GUI desktop | `tkinter`, `PyQt`, `PySide`, `kivy`, `pygame` | Chaquopy in-process tanpa surface GUI | **ZPLAY** (fork p4a) |
| Browser automation | `selenium`, `playwright` | Butuh browser/driver eksternal | PC/ZMUX |
| Sistem/syscall | `psutil` (sebagian), `pywin32` | Tergantung platform; Android membatasi | uji manual |

### Pelajaran Jupyter (jujur)
Chaquopy tidak punya wheel `pyzmq`/`libzmq` untuk ARM → instal Jupyter gagal di
tengah dan `user_packages` setengah jadi ("berantakan"). Pydroid menang karena
punya **repo wheel ARM kurasi + app Notebook**. Perbaikan ZCODE:
- Pakai `--only-binary` (default di Chaquopy 17) + guard agar sisa setengah jadi
  dibersihkan/di-rollback.
- Tampilkan pesan jelas: *"⚠ jupyter dibatalkan — pyzmq butuh libzmq native,
  belum ada wheel Android Chaquopy. Alternatif: App Mode (Flask+WebView)."*

---

## 5. Prinsip & kontrak ke user

1. **Pure-Python = otomatis didukung.** Kalau paket murni-Python, hampir pasti
   jalan; katalog boleh memprioritaskan yang populer.
2. **Native = hanya yang punya wheel Chaquopy untuk ABI user.** Jika tak ada,
   tampil ❌ dengan alasan, bukan install setengah hati.
3. **Berat = tetap boleh, tapi diberi peringatan RAM.** Jangan mempreteli
   eksperimen user, cukup jujur soal biaya.
4. **GUI/berat/luar arsitektur = arahkan ke ZPLAY / App Mode / ZMUX**, jangan
   dipaksakan di inti ZCODE ("hukum keluarga": jangan gabung yang belum teruji).
5. **Kegagalan harus bersih:** pesan ramah, log rapi (Batch 1.5), tidak ada
   traceback membingungkan, `user_packages` tidak korup.

---

## 6. Hubungan dengan dokumen lain

- `LIBRARY_DESIGN.md` — implementasi UI katalog (struktur `libraries.json`,
  deteksi perangkat, tag runtime).
- `CHAQUOPY_STRATEGY_2026_08.md` — dasar cakupan wheel & pin Python 3.11.
- `PERF_PASS.md` — pip install & log dijalankan tanpa bikin UI nge-freeze.
- `docs/RENCANA_UPDATE_2026_08.md` — aturan jujur/teliti + protokol UAT.

---

*Dokumen kurasi — isi paket akan bertambah/terkoreksi seiring hasil uji di HP
ARMv7 asli. Bukan janji kelengkapan PyPI; ini peta jujur cakupan ZCODE.*
