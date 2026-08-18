# RANCANGAN GERBONG A v1.0.19 — "Editor yang nunjukin salahmu di mana"

Status: **DESIGNED — menunggu ACC user** (2026-08-18).
Branch: lanjut di `arena/v1019-fondasi` (keputusan user: satu PR utuh).
Input eksternal: 18 saran MIMO CLAW sudah ditelaah — 7 sudah ada/terencana,
4 diadopsi (2 masuk gerbong ini), 4 kulkas, 3 ditolak dengan alasan.

---

## 0. KOREKSI USER (2026-08-18) — dua hal, dua-duanya mengikat

**(a) Kejujuran di atas kenaikan kelas.** ZCODE boleh "naik kelas jadi IDE
yang mengajari", tapi TIDAK dengan meninggalkan prinsip kejujuran. Aturan
turunannya untuk gerbong ini: hint tidak pernah mengklaim pasti ("Mungkin
maksudmu…"), lint tidak pura-pura menangkap semua error (keterbatasan
Checker ditulis di release notes), reference card tidak menggurui, dan
fitur yang gagal UAT dilaporkan gagal — bukan dipoles.

**(b) Bug rotate (laporan + 8 screenshot user).** Crosscheck 2026-08-18
menemukan SATU KELAS bug di TIGA titik — layar didesain dgn asumsi tinggi
portrait ±640dp; landscape Infinix = ±360dp:
1. **Drawer**: isi ModalDrawerSheet = Column TANPA verticalScroll →
   landscape: item bawah (TOOLS expanded, SETTINGS, About) tak terjangkau.
2. **AboutScreen**: root Column TANPA scroll (hanya kotak license yang
   scrollable) → landscape: tombol Issues/Contribute terdampar di luar layar.
3. **PipScreen ManualTab**: console weight(1f) kelaparan ruang — area
   input+hints fix ~250dp makan duluan → console tersisa ±50dp.
Audit layar lain (Settings/Diagnostics/Samples/Terminal): aman, punya
scroll container di akar. Lolos selama ini karena configChanges
orientation (tak pernah crash) + UAT selalu portrait.

→ Masuk gerbong sebagai **A0**, dikerjakan PERTAMA (bug nyata user > fitur).

## 1. MAU APA (A0 + 6 item, 1 pencatatan)

| # | Item | Asal | Default |
|---|---|---|---|
| **A0** | **Rotate resilience** — kelas fix: (1) isi drawer dibungkus verticalScroll; (2) root AboutScreen scrollable; (3) ManualTab: bila tinggi layar < ~480dp → kolom scrollable + console tinggi tetap (≥200dp), portrait tak berubah (BoxWithConstraints); + guard string per titik + aturan baru di SKILLS: "layar statis wajib survive 360dp" | laporan user 2026-08-18 | — |

| # | Item | Asal | Default |
|---|---|---|---|
| A1 | **Lint gutter** — garis merah bergelombang + ikon gutter + tooltip via tap; sumber = `vm.problems` (Checker existing) | rencana kita (= MIMO #1) | ON |
| A2 | **Whitespace guard** — highlight trailing whitespace + aturan Checker baru: indentasi campuran tab/spasi → Problem (ikut tampil di A1) | rencana kita | OFF (ketokan user) |
| A3 | **Traceback tap-to-jump** — baris `File "main.py", line N` di terminal tappable → `gotoLine(N)` editor + highlight sekejap | rencana kita | ON |
| A4 | **TOOLS satu-scroll** — LazyColumn tunggal, seksi PLUGINS/EDITOR; THEME **dipaku di dasar kotak** (ketokan user) | rencana kita | — |
| A5 | **Quick Reference Card** — tombol "?" → sheet pola syntax Python (for/if/def/class/try/f-string/comprehension...), tap = insert di kursor. Offline, data statis | adopsi MIMO #4 | — |
| A6 | **Hint import minimal** — `NameError: name 'plt'` di terminal → satu baris hint dari tabel statis alias populer (plt→matplotlib.pyplot, np→numpy, pd→pandas, sns→seaborn, dst.) + sebut kalau paketnya belum terpasang (data InstalledPackages Gerbong B!) | adopsi MIMO #6, diperkecil jujur | ON (bagian A3) |
| A7 | **Multi-file: sahkan yang sudah ada** — BUKAN fitur baru (fakta 2026-08-18: workspace sudah di sys.path sejak dulu, terbukti sandbox). Kerjaan: uji device + guard + 1 sample "Project Mini" 2 file + sebut di deskripsi kategori | koreksi klaim MIMO #10 | — |
| — | **REPL Mode** dicatat resmi sebagai kandidat utama v1.0.20 (satu-satunya ide besar MIMO yang baru; butuh desain lifecycle penuh, jangan diselipkan) | MIMO #5 | — |

Ditolak tetap ditolak: settrace debugger (vonis SKILLS), plugin API publik
(prematur+keamanan), progress tracker (gamification), bundle audit tanpa
gejala, persist undo lintas restart (I/O boros; CM6 tak dukung resmi).

## 2. RENCANA (urutan eksekusi)

0. **A0 rotate fix duluan** (bug user aktif > fitur; juga PRASYARAT A4 —
   percuma TOOLS satu-scroll kalau drawer induknya sendiri tak bisa scroll
   di landscape). Commit terpisah supaya bisa di-UAT/revert sendiri.
1. **Bundle (risiko terbesar di depan):** `cd editor-src && npm ci`
   (node v20 ✓ di sandbox); tambah `@codemirror/lint` pin eksak; expose
   `setDiagnostics(json)`, `setLintEnabled(b)`, `setWhitespaceEnabled(b)`
   via Compartment (toggle live tanpa reload); `npm run build`; commit
   bundle. Guard kontrak dua sisi (string di bundle + pemanggil Kotlin).
2. **Checker:** aturan indentasi campuran tab/spasi (+trailing WS sebagai
   INFO) + uji mutasi. Pure Kotlin—eh, pure logic, murah diuji.
3. **Bridge:** `vm.problems` → `evaluateJavascript("setDiagnostics(...)")`
   pada perubahan; debounce ikut yang existing (800ms).
4. **Terminal:** parser baris traceback (regex ketat: `File "X", line N`
   hanya bila X ada di workspace) → annotasi tappable + highlight sekejap
   di editor; tabel hint NameError statis (~15 alias populer) + cek
   InstalledPackages untuk pesan "paket X belum terpasang".
5. **TOOLS restructure** + 3 toggle baru (persist pola symbolBarEnabled).
6. **Reference card:** data JSON di assets (bukan hardcode Kotlin, biar
   gampang nambah) + sheet Compose + insert via bridge `insertText` yang
   sudah ada.
7. **A7:** sample `project_mini/` (main.py + helper.py) + guard py_compile
   + catat di SampleLibrary (butuh dukungan createSampleFromAsset utk 2
   file — cek dulu, kalau mahal: sample single-file yang menulis helper.py
   saat run pertama, jujur di deskripsi).
8. check.sh + sanity + mutasi → commit per-item → push → CI → UAT checklist.

## 3. KENDALA + PENANGANAN

| Kendala terprediksi | Penanganan |
|---|---|
| `npm ci` gagal (network/registry) | Retry; registry mirror; kalau buntu total → Gerbong A ditunda SATU build, bukan dipaksa (bundle = fondasi semua item editor) |
| Bundle baru diff besar & tak terbaca | Fungsi dijaga guard string dua sisi + UAT; bundle satu commit terpisah → revert 1 commit tanpa sentuh Kotlin |
| Tooltip lint canggung di layar sentuh | Fallback sudah didesain: tap ikon gutter sebagai pintu utama; keputusan final = UAT rasa user |
| Regex traceback salah tangkap (path dalam string user) | Pola ketat + validasi file exist di workspace + guard test kasus jebakan |
| WebView lama di device tertentu | Target es2018 tak berubah (envelope minSdk 26 yang sama dengan bundle sekarang) |
| TOOLS restructure regresi drawer | Diff terbatas blok AnimatedVisibility; UAT visual |
| Hint NameError sok tahu (alias dipakai utk hal lain) | Bahasa hint selalu "Mungkin maksudmu…", tak pernah klaim pasti; tabel hanya alias super-populer |
| A0: drawer scrollable mengubah rasa gesture (scroll vs swipe-close) | verticalScroll hanya sumbu Y, swipe-close drawer sumbu X — tak bentrok secara mekanis; UAT rasa tetap jadi hakim |
| A0: nested scroll (drawer scroll + LazyColumn plugin di dalamnya) | Pola heightIn(max) + LazyColumn sudah terbukti aman di TOOLS existing; kalau rewel di device → plugin list ikut kolom scroll tunggal (hapus nested) |
| A0: regresi portrait karena fix landscape | Perubahan bersifat aditif (scroll modifier tak mengubah layout saat konten muat); UAT dua orientasi WAJIB dua-duanya |

**Kendala tak terbayangkan — protokol umum (ini jawaban jujurnya: gw TIDAK
tahu apa yang tidak gw tahu, tapi gw tahu cara menghadapinya):**
1. Setiap fitur baru punya **kill-switch** (toggle OFF = perilaku lama
   persis) → anomali apa pun di device, user bisa pulih sendiri tanpa PC.
2. Commit per-item & atomik → bisect/revert murah.
3. Anomali baru = breadcrumb dulu (observability sebelum teori) — pelajaran
   pycurl-signal: pesan error tanpa jejak = tebak-tebakan.
4. Tangga pembuktian tetap: pytest → sanity → bionic311 (bila relevan) →
   CI → UAT; tidak lompat anak tangga.
5. Kalau dua build UAT beruntun gagal di item yang sama → item itu keluar
   dari PR, didesain ulang, bukan dipaksa (anti "puluhan kali diperbaiki
   ujungnya sama").

## 4. DAMPAK

**Bagi ZCODE:** naik kelas dari "IDE yang jujur soal paket" jadi "IDE yang
mengajari" — error terlihat di barisnya, penyebab tak kasat mata (tab/spasi)
jadi kelihatan, jarak error→perbaikan dipendekkan (tap traceback), lupa
syntax tak perlu keluar app (reference card). Semua offline-first.

**Bagi user lain (kalau ZCODE dipakai orang):** segmen HP ARMv7 murah =
persis pelajar/pemula Indonesia tanpa laptop. Fitur gerbong ini menyasar
titik nyerah pemula #1 (error tak dipahami) dan #2 (lupa syntax, harus
buka browser — kuota!). requiresPackage + katalog jujur sudah menjaga
mereka dari frustrasi instalasi; gerbong ini menjaga mereka dari frustrasi
BELAJAR. Dan setiap fitur bisa dimatikan — user mahir tak diganggu.

**Yang SENGAJA tidak diubah:** engine eksekusi, resolver (sudah DEVICE
VERIFIED build fondasi), kontrak breadcrumb, workflow CI.

## 5. TARGET (bisa diamati, bukan "lebih baik")

0. **Rotate ke landscape**: seluruh drawer terjangkau via scroll; tombol
   Issues/Contribute About tercapai; console Manual Install tetap ≥200dp
   dan terbaca. Rotate balik ke portrait: identik dengan sebelum fix.
1. Ketik `print(` tanpa tutup → merah muncul di baris itu ≤1 detik; tap →
   penjelasan; perbaiki → merah hilang.
2. File campuran tab/spasi → terdeteksi SEBELUM run (whitespace guard ON).
3. Script error → tap baris traceback → editor terbuka tepat di baris salah.
4. `NameError: plt` → hint "Mungkin maksudmu: import matplotlib.pyplot as
   plt" (+ status paketnya).
5. Tombol "?" → reference card → tap pola → ter-insert di kursor.
6. Sample Project Mini 2 file jalan di device → multi-file resmi
   terdokumentasi + ter-guard.
7. Semua toggle OFF = ZCODE v1.0.19-fondasi persis; test count naik dari
   308; CI hijau; nol regresi 231 TESTED.
8. REPL tercatat di roadmap v1.0.20 dengan konteks lengkap.
