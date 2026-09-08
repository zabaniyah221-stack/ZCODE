# RISET KELUHAN USER IDE PYTHON MOBILE — & COCOKAN DENGAN ZCODE (2026-08-26)

**Status: RESEARCH COMPLETED (sampling komunitas, bukan sensus).**
Misi (permintaan user): panen keluhan nyata pengguna IDE Python mobile —
terutama ARMv7 — lalu cocokkan: mana yang SUDAH ditangani ZCODE, mana yang
BELUM (kandidat roadmap).

## 1. Batas kejujuran (aturan #1)

- Keluhan komunitas = **lead kelas-6** (tangga sumber AGENTS.md §17): dipakai
  untuk menemukan pola, bukan bukti insiden ZCODE. Tidak ada klaim
  "kemungkinan X% user terdampak" — hanya frekuensi kemunculan lintas sumber.
- Sampling: r/Pydroid3, r/learnpython, r/learnprogramming, r/pythontips,
  r/PythonLearning, r/termux, Play Store reviews (Pydroid 3), GitHub issues
  QPython, Stack Overflow / Android StackExchange, artikel pelengkap.
  Bukan seluruh komunitas; Play review = sampel kecil.
- Semua klaim keluhan mencantumkan URL. Status "ZCODE sudah/belum"
  merujuk dokumen repo + kode yang sudah dibaca sesi ini; yang belum sempat
  diverifikasi di kode ditandai **[cek-kode]**.

## 2. Sumber yang dipanen

- https://www.reddit.com/r/Pydroid3/ (front page complaints)
- https://www.reddit.com/r/learnpython/comments/1cww7bb/ (terminal reset saat minimize)
- https://play.google.com/store/apps/details?id=ru.iiec.pydroid3 (reviews + FAQ premium)
- https://github.com/qpython-android/qpython.org/issues/119 (pip PIE Android 5+)
- https://github.com/qpython-android/qpython.org/issues/17 (bz2 hilang)
- https://copyprogramming.com/howto/how-do-i-install-modules-on-qpython3-android-port-of-python (rangkuman error QPython: compiler, permission, versi)
- https://stackoverflow.com/questions/77965881/ (Termux tablet: pip numpy gagal → harus pkg)
- https://stackoverflow.com/questions/55434255/ (matplotlib di Pydroid: plot tak tampil)
- https://stackoverflow.com/questions/76163339/ (matplotlib interaktif di Android: mustahil native)
- https://android.stackexchange.com/questions/256751/ (Android 12 phantom process killer vs Termux)
- https://learning-python.com/...2021+Android+12.png.note (phantom killer + FUSE lambat + SAF)
- https://www.reddit.com/r/termux/comments/hohc2d/ (background + clipboard diblok Android 10)
- https://www.reddit.com/r/pythontips/comments/17udh4s/ + r/learnprogramming + r/PythonLearning (belajar coding di HP: keyboard = keluhan #1)

## 3. Klaster keluhan & coccokan ZCODE

### Klaster A — Instalasi paket = neraka (keluhan paling universal)

Keluhan nyata:
- QPython: "No working compiler found / arm-linux-androideabi-gcc not found",
  bz2 tak ada, `/sdcard` permission, "could not find any downloads",
  versi Play terbelakang (copyprogramming rangkuman; issues #17/#119).
- Termux: `pip install numpy` GAGAL di tablet → harus tahu rahasia
  `pkg install python-numpy` (dua package manager membingungkan) — SO 77965881.
- Umum: "whl is not a supported wheel on this platform" — kebingungan
  32-bit vs 64-bit (SO 28568070, dsb.).
- Pydroid: beberapa library penting = premium-only, "extremely hard to
  port, provided to premium users only" (FAQ Play listing).

**ZCODE: SUDAH DITANGANI (keunggulan inti).** Package Engine transaksional,
tag wheel per ABI/Python (kelas bug A/F dimakamkan), verdict jujur
SOURCE_NOT_FOUND/NETWORK/COMPATIBILITY (SKILL 21–22), smoke test + rollback,
Library ber-vonis + alternatif. Paket perlu kompilasi tetap mustahil —
fisika yang sama dengan semua app — bedanya ZCODE bilang jujur + kasih
alternatif (PRD prinsip #4). Tanpa premium lock (PRD #1).

### Klaster B — Monetisasi & trust (Pydroid)

Keluhan: iklan tiap habis Run ("annoying fast when debugging"), premium $20
"expensive", pembeli lifetime license tetap kena iklan, "premium-only
libraries" (r/Pydroid3; Play reviews).

**ZCODE: SUDAH DITANGANI BY DESIGN** — gratis, tanpa iklan, tanpa paywall.
(Kompetitif: ini klaster yang bikin user pindah, dan ZCODE menang tanpa
harus melakukan apa pun selain tetap konsisten.)

### Klaster C — Reliability, background & process death

Keluhan:
- Pydroid: terminal session RESET saat minimize/lock layar (Android 14) —
  solusinya user akal akalin sendiri lewat setting baterai
  (r/learnpython 1cww7bb); gagal jalan intermiten di Android 12; rusak di
  Android 15; rusak sebagai secondary user (r/Pydroid3).
- Android 12+ **phantom process killer** membunuh child process Termux
  (limit 32) — android.SE 256751; learning-python.com melaporkan sama +
  FUSE storage lambat.
- Versi Python naik → **project user rusak** ("since pydroid updated to py
  3.13 some of my projects stopped working", r/Pydroid3).

**ZCODE: SEBAGIAN BESAR DITANGANI, ada 2 sisa.**
Sudah: runtime in-process (tak ada child process untuk dibunuh phantom
killer — keuntungan arsitektur), autosave 600ms, flush-all-drafts sebelum
update/rebirth, stale-runtime relaunch (SKILL 23), update continuity
DEVICE VERIFIED + versi Python di-pin 3.11 dengan alasan ABI (anti
"upgrade merusak project").
BELUM: (1) **survival Run saat layar mati/user pindah app** — perilaku
sekarang belum terdokumentasi/diuji **[cek-kode+UAT]**; (2) **hard-stop
script** (loop `while True:` = beku) — diketahui, jawabannya T2 private
process `:python` (terminal v1.0.24).

### Klaster D — Keyboard & layar kecil (keluhan #1 komunitas belajar)

Keluhan: "Programming requires so many special characters… extremely slow,
autocorrect will constantly fuck with things" (r/pythontips); "switching
keyboards… it'll suck"; solusi yang disarankan komunitas: keyboard kustom /
bluetooth / stylus (r/learnprogramming, r/PythonLearning — thread 2020–2025).

**ZCODE: SEBAGIAN.** QuickTools symbol bar + terminal symbol bar "kereta"
dengan ^C + swipe sidebar + IME/pinch guard device-verified (SKILL 24.7) +
hardware shortcut editor (SKILL 18). BELUM/candidate: tombol **Tab** di
QuickTools? **[cek-kode]**, auto-tutup bracket/quote, snippet cepat lebih
banyak, dan posisi kursor fine-grain (arrow key hold-to-repeat ala ZMUX?).
Ini klaster dengan upside UX terbesar karena dialami SEMUA user HP.

### Klaster E — Visualisasi & GUI

Keluhan:
- matplotlib di Pydroid: `plt.show()` tak tampil di beberapa device →
  user dipaksa `savefig` manual (SO 55434255); matplotlib interaktif
  (zoom/pan) di Android = mustahil native (SO 76163339).
- GUI framework (Kivy/pygame/Tkinter): tersedia di Pydroid (sebagian
  premium/banyak laporan rusak: "I can't get Pygame working", Tkinter
  scaling); QPython kivy gagal (issue #57); komentator: "Python on mobile
  has limited support for GUI libraries" (r/PythonLearning).

**ZCODE: BELUM (gap nyata).** Pola resmi ZCODE = `savefig` (samples).
Yang belum ada: **penampil hasil** — auto-buka/preview PNG hasil `savefig`
dari terminal/Files **[cek-kode: apakah Files bisa preview gambar?]**
versi murah dari "lihat grafik saya". Interactive figure/GUI framework =
kelas berat, jangan dijanjikan (PRD #7).

### Klaster F — Fitur IDE yang diharapkan

- Debugger breakpoint (Pydroid premium punya; Dcoder punya) → ZCODE: BELUM
  (kandidat jangka menengah, mahal).
- Jupyter (Pydroid sering rusak per laporan) → ZCODE: tidak; berat; parkir.
- Shell/terminal penuh (Pydroid/QPython/Termux punya) → ZCODE: v1.0.24
  (parkir, T1–T4 sudah dirancang).
- Autocomplete cerdas + lint pre-run → v1.0.23 (riset selesai, RFC keep).
- Catatan kompetitif: **tidak ada IDE Python mobile yang terbukti punya
  lint semantik pre-run** — v1.0.23 akan unik di kelasnya.

### Klaster G — Akses storage & Android API

Keluhan: `/sdcard` permission (QPython), FUSE lambat sejak Android 11
(learning-python.com), clipboard di background dihapus Android 10
(r/termux hohc2d), pyperclip/xsel mustahil — "Android is very sandboxed"
(r/learnpython r8xcts).

**ZCODE: desain sandbox-first menghindari sebagian besar** (workspace
internal, tanpa SAF di jalur utama, copy selalu in-app). BELUM: **jembatan
API Android untuk script user** (clipboard/TTS/sensors/notifications ala
helper Pydroid/Termux:API) — kandidat menarik karena menjawab frustrasi
"Android sandboxed" TANPA membuka bahaya; dan **export keluar sandbox**
(share/save ke Downloads via SAF) **[cek-kode]**.

### ARMv7 spesifik

Keluhan pola: device lama tak bisa install (QPython "not compatible"),
wheel 32-bit tidak ada, scipy/sklearn mustahil, device lambat →
persis alasan ZCODE ada. Keunggulan ZCODE di klaster ini sudah terdokumentasi
(index Chaquopy + resolver + Library verdicts). Sisa gap ARMv7: scipy =
Alpine T4 (parkir); kecepatan = semua keputusan v1.0.23 sudah
dirancang HP-ampas-first.

## 4. Tabel ringkas — BELUM ditangani ZCODE (urut nilai/efort)

| # | Gap | Klaster | Efort | Nilai dugaan | Slot alami |
|---|---|---|---|---|---|
| 1 | Hard-stop script (loop beku) | C | besar | sangat tinggi | v1.0.24 terminal/T2 (sudah di roadmap) |
| 2 | Preview hasil plot/savefig (auto-lihat PNG) | E | kecil | tinggi (learner data) | bisa nimbrung v1.0.23/24 |
| 3 | Keyboard superpowers (Tab, auto-bracket, arrow-hold) | D | kecil-menengah | tinggi (semua user) | iterasi UX kapan saja |
| 4 | Run survival saat layar mati (uji+FGS/dok jujur) | C | menengah | tinggi | bersama T2 (service) |
| 5 | Bridge API Android untuk script (clipboard, TTS, dll) | G | menengah | menengah-tinggi | pasca-terminal |
| 6 | Export/share keluar sandbox (SAF) | G | kecil | menengah | kapan saja |
| 7 | Debugger breakpoint | F | besar | menengah (power user) | jangka menengah |
| 8 | GUI framework / interactive plot | E | sangat besar | menengah | parkir permanen sampah bukti baru |

Yang SUDAH ditangani (untuk tidak dikerjakan ulang): installer transaksional
(A), gratis tanpa iklan (B), autosave/flush/receipt + phantom-killer-immune
in-process (C sebagian), traceback jump, update continuity, pin 3.11 jujur,
QuickTools/IME/pinch (D sebagian), verdict Library ARMv7 (A/ARMv7).

## 5. Prinsip penutup

Komunitas mengkonfirmasi arah produk ZCODE: keluhan terbesar user mobile
Python bukan "kurang pintar editornya" melainkan **instalasi paket, iklan/
paywall, kehilangan sesi/data, dan keyboard** — tiga dari empat sudah
menjadi keunggulan ZCODE hari ini. v1.0.23 (lint+autocomplete) mengisi
klaster F yang justru kosong di kompetitor mobile; sisa gap bermuara ke
v1.0.24 (terminal/T2) yang sudah diparkir.
