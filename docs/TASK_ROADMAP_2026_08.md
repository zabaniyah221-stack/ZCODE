# 🗺️ TASK_ROADMAP — Alur Kerja ZCODE (2026-08)

Dokumen ini menyusun **alur eksekusi** semua pekerjaan yang diarsipkan di sesi
2026-08, dibagi dalam **3 fase** menurut tingkat risiko, dan aturan main yang
jelas. Dibaca bersama dokumen per-fitur: `PERF_PASS.md`, `SETTINGS_DESIGN.md`,
`CM6_FEATURE_MAP.md`, `TOOLS_CATALOG.md`, `VPP_DESIGN.md`, `LIBRARY_DESIGN.md`,
`PIP_SCOPE.md`, `TERMINAL_THEMES.md`, `CHAQUOPY_STRATEGY_2026_08.md`.

Tiga aturan inti berlaku: **(1) jujur apa adanya, (2) teliti sampai hal kecil,
(3) build for user not for ego** — kerjakan yang meringankan beban user di
perangkat target (Infinix Smart 9 HD, ARMv7, RAM kecil) sebelum yang seru secara
teknis.

---

## Aturan main (WAJIB dibaca)

### A. Aturan commit (sudah dikoreksi)
- **BUKAN "1 task = 1 commit".** Aturan yang benar: **satu perubahan koheren =
  satu commit** (satu ide; bisa 1 file atau beberapa file yang saling terkait).
- **Commit harus berdiri sendiri:** kode tetap kompil & guard hijau. Jangan
  tinggalkan state setengah jadi yang bikin merah.
- **Task/fitur besar dipecah** jadi beberapa commit (mis. refactor dulu yang
  lulus, baru fitur di atasnya).
- **Jangan mikro-commit berisik** ("typo", "ganti spasi" terpisah) — gabungkan
  dengan perubahan yang relevan.

### B. Branch & PR (model yang dipakai: "satu rumah sampai puas")
- **Satu branch kerja** (`arena/019fe8ce-zcode`) menampung **semua fase** sampai user
  puas; commit-nya tetap kecil per perubahan koheren.
- **Satu PR (DRAFT)** dibuka dari branch ini sebagai penanda & tempat diskusi;
  **TIDAK di-merge sampai user memberi lampu hijau di akhir.**
- **CI tetap jalan per push ke branch/PR** walau belum merge — jadi APK/artifact
  untuk UAT tetap dihasilkan. Alur: push → CI hijau → user download artifact →
  UAT di Infinix → baru putuskan merge.
- Fase yang sudah UAT-lolos tidak perlu terburu di-merge; cukup menumpuk di
  branch. Pecah PR/merge hanya bila atas permintaan user.
- Fase 3 tetap diperlakukan istimewa: item berisiko dikerjakan **satu per satu**
  sebagai spike terisolasi (commit/branch eksperimen bila perlu), mudah revert.

> Alasan model ini (berdasarkan pengalaman user): user tidak harus segera merge
> (yang akan menandai akhir sesi), kode tetap aman di remote, dan ganti model/sesi
> di Arena tidak menghilangkan pekerjaan karena semua terdokumentasi & ter-push.

### C. Guard & kejujuran keterbatasan
- Guard sandbox: `pytest` (fase0/1/3), `bash tools/check.sh`, `py_compile`
  sample, `verifyEditorBundled`.
- **Sandbox TANPA JDK/Android SDK** → Kotlin tidak bisa dikompilasi di sini.
  Hijau di sandbox **bukan jaminan APK**; **CI & UAT Infinix yang hakim.**
- Perubahan editor CodeMirror **WAJIB rebuild bundle** (`cd editor-src && npm
  run build`) lalu commit hasilnya; `verifyEditorBundled` gagal kalau kontrak
  bridge (`setCode/getCode/...`) rusak.

### D. Protokol kolaborasi (berdasarkan pengalaman lintas repo)
1. **File `.github/workflows/*` = ranah user.** Agent tidak bisa/ tidak boleh
   mengubahnya (butuh kredensial scope `workflow`). Bila sebuah task butuh
   mengubah CI, agent menyiapkan **isi file lengkap + link raw**, lalu **user
   yang menimpa & commit di `main`**. Agent akan eksplisit menandai
   "butuh campur tangan user".
2. **CI merah jangan ditebak.** Agent boleh cek status/run via `gh`, tetapi bila
   log error tidak bisa diakses/dibaca, **agent berhenti dan meminta user
   menyalin log error spesifik**. Diagnosis hanya dibuat dari log asli.
3. Gagal/merusak → revert commit/SHA yang bersalah, jangan ditambal berlarut.
4. Hukum keluarga: jangan mencampur pekerjaan yang belum teruji; jangan
   membundel perubahan runtime besar dengan fitur.

---

## FASE 1 🟢 — Menang cepat, langsung kerasa

**Kriteria:** risiko rendah, ketahuan langsung oleh user, tidak membongkar
arsitektur/runtime. Tujuan: kurangi rasa kesal sehari-hari (terutama tombol Run)
dan kasih fondasi Settings/editor yang langsung kepakai.

| ID | Item | Dokumen | Catatan |
|---|---|---|---|
| F1.1 | FAB ▶ **reaksi instan** (spinner/state saat di-tap) | `PERF_PASS` F | Langsung menyerang keluhan "Run lambat" |
| F1.2 | Indikator **"Menyalakan Python…"** di terminal (layar tidak kosong) | `PERF_PASS` B,F | |
| F1.3 | Cangkang **SettingsScreen** + route `settings` + item sidebar | `SETTINGS_DESIGN` | LazyColumn, di atas About |
| F1.4 | **Pindah "Clear All Drafts & Files"** ke Settings (Privasi & Data) | `SETTINGS_DESIGN` | Konfirmasi tetap; hapus dari TOOLS |
| F1.5 | Pemilih **tema yang jelas** (daftar RETRO/DRACULA/TOKYO_NIGHT) | `SETTINGS_DESIGN` | Bukan cycle buta; fungsi cycle boleh tetap |
| F1.6 | Cerminkan toggle yang sudah ada: Symbol bar, Auto Trim on Run | `SETTINGS_DESIGN` | Backend sudah ada |
| F1.7 | **Auto-close brackets** (CM6) + toggle-nya | `CM6_FEATURE_MAP`, `TOOLS_CATALOG` | `closeBrackets` sudah di dep; rebuild bundle |
| F1.8 | **Selection match highlight** (CM6) | `CM6_FEATURE_MAP` | Style sudah ada, tinggal pasang |
| F1.9 | Transform teks kecil: **Sort Lines, Change Case, Trim Now** | `TOOLS_CATALOG` | Kotlin/JS murni, tanpa pip; daftar sebagai plugin ACTION |

**Ukuran PR Fase 1:** beberapa commit kecil per item.
**Gerbang UAT Fase 1 (Infinix):**
- [ ] Tap Run langsung bereaksi (spinner) tanpa jeda; terminal tidak pernah
      kosong tanpa tulisan.
- [ ] Settings terbuka, scroll mulus; Clear All pindah & tetap minta konfirmasi.
- [ ] Ganti tema/symbol bar/auto-trim bekerja & bertahan setelah restart.
- [ ] Auto-close brackets & selection match bekerja; bundle tidak membengkak
      berlebihan; mengetik tetap ringan.

---

## FASE 2 🟡 — Beneran, butuh waktu

**Kriteria:** kerja sedang, banyak file, butuh test/UAT serius; tidak mengubah
fondasi runtime, tapi mengubah alur data/perilaku inti.

| ID | Item | Dokumen | Catatan |
|---|---|---|---|
| F2.1 | **Debounce auto-save** + **flush wajib** saat Run/pindah file/background | `PERF_PASS` C | Jangan sampai ketikan hilang |
| F2.2 | **Ring buffer/cap output** + **coalesce scrollTo** (~120 ms) | `PERF_PASS` E | Anti-jank output deras |
| F2.3 | Pangkas kerja berat di `onClick` Run (auto-trim/`setCode` sinkron) tanpa bikin feedback-loop/hilang kursor | `PERF_PASS` F | Hati-hati; perlu UAT |
| F2.4 | Item Settings lanjutan: ukuran font editor/terminal, toggle indikator Python, info perangkat (ABI/RAM) | `SETTINGS_DESIGN` | Preferensi ter-persist |
| F2.5 | **VPP** — `Checker` → `List<Problem>` + banner expand ke bawah + gotoLine | `VPP_DESIGN` | Refactor Checker hati-hati (jaga regresi B-11/B-19/F-07) |
| F2.6 | **LIBRARY** — `libraries.json`, DeviceProbe (ABI+RAM), katalog, install via `startPipStream`, gagal bersih + rollback, pip manual di bawah | `LIBRARY_DESIGN`, `PIP_SCOPE` | `requests`/`numpy` OK; `pyzmq` gagal dengan pesan jujur |
| F2.7 | **Organize imports lanjutan** (urutkan + buang tak terpakai, berbasis AST) | `TOOLS_CATALOG` | Waspadai bug Zabacode F-02/B-10; jangan rusak kode valid |
| F2.8 | **Palet terminal** penuh: model 16 warna ANSI + render SGR + pemilih di Settings (latar TETAP hitam OLED) | `TERMINAL_THEMES` | Unit test pemetaan SGR |

**Gerbang UAT Fase 2 (Infinix):**
- [ ] Mengetik file panjang lancar; tersimpan setelah berhenti & saat Run/pindah/background.
- [ ] Output deras (`print` loop) tidak freeze; baris terakhir terlihat; `input()` tetap jalan.
- [ ] VPP: banner muncul/hilang, expand, goto benar, rotasi aman.
- [ ] LIBRARY: tag perangkat benar; install sukses; gagal bersih.
- [ ] Terminal berwarna (`rich`/`colorama`) sesuai palet; tetap terbaca di OLED.

---

## FASE 3 🔴 — Jebakan, spike dulu

**Kriteria:** membongkar arsitektur / mengubah runtime / bisa merusak besar.
Aturan: **satu item = satu PR/spike terisolasi**, jangan dibundel. Siap
revert. Dikerjakan **setelah Fase 1–2 stabil** dan atas persetujuan user.

| ID | Item | Dokumen | Risiko utama |
|---|---|---|---|
| F3.1 | **Terminal sebagai overlay/layer** (jangan recreate WebView saat Run) | `PERF_PASS` A | Perubahan struktur navigasi; jembatan `input()`/stdin harus tetap |
| F3.2 | **Pre-warm Python** saat startup | `PERF_PASS` B | Bisa memperlambat start app; ukur dulu, default aman |
| F3.3 | **Code folding** (`foldGutter`) + **Outline/Symbols** (AST→gotoLine) | `CM6_FEATURE_MAP`, `TOOLS_CATALOG` | Rebuild bundle; perilaku editor |
| F3.4 | **Spike Chaquopy 15.0.1 → 17.0.0** (TETAP `pythonVersion "3.11"`) | `CHAQUOPY_STRATEGY` | Runtime: pip 25.3, `--only-binary` default; **WAJIB** cek monkey-patch AssetPath, `input()`/stdin, AGP, build ARMv7 asli |
| F3.5 | (Tunda, bila disetujui) Go to Definition/Rename dalam-file (fondasi LSP/jedi) | `TOOLS_CATALOG` | Berat di perangkat ampas; evaluasi terpisah |

**Protokol Fase 3 (setiap item):**
1. Satu spike = satu commit/PR terisolasi.
2. Verifikasi poin per poin (lihat dokumen terkait).
3. UAT Infinix penuh.
4. Jika ada yang patah → revert, laporkan dengan jujur, jangan dipaksa.
5. F3.4 (Chaquopy) **paling akhir**, setelah semua lain stabil.

---

## Yang TIDAK dikerjakan sekarang (jujur, backlog)

- AI/Oracle auto-fix (butuh diskusi privasi/API key terpisah).
- **ZMUX** (Alpine proot) — download-on-demand, tidak dibundle; ranah terpisah.
- **ZPLAY** (Kivy/pygame) — fork terpisah, bukan ZCODE.
- **LSP penuh (jedi/Ruff)** — berat di HP ampas; backlog (F3.5 baru fondasi).
- Multi-bahasa editor penuh — memperbesar bundle; butuh keputusan tersendiri.
- GUI native (Tkinter/Qt) — di luar arsitektur Chaquopy in-process.
- Minimap, Emmet, remote file, marketplace plugin sembarangan — ditolak
  (aturan #3: manfaat tak sebanding dengan biaya di perangkat target).

---

## Cara bergerak (ringkas)

1. Kerjakan **per item dalam fase**, buat commit per perubahan koheren; push ke
   branch kerja yang sama.
2. Jangan merge apa pun tanpa perintah user. PR dibiarkan DRAFT sampai user puas.
3. Tiap push memicu CI; kalau hijau, user UAT artifact di Infinix.
4. Selalu update dokumen bila kenyataan implementasi berbeda (rencana bukan kitab).
5. Urutan bisa berubah atas persetujuan user bila ada keluhan lebih mendesak.
6. Butuh ubah workflow? Serahkan file lengkap + link raw ke user. CI merah tak
   terbaca? Minta log asli, jangan menebak.

---

*Roadmap ini dibaca atas-bawah: kenyamanan user dulu, pekerjaan dalam kemudian,
jebakan/runtime paling akhir dengan perlindungan penuh.*
