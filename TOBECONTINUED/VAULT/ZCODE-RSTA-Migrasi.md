# BLUEPRINT Migrasi ZCODE Desktop: KCEF/CEF → RSyntaxTextArea

Status: DRAFT — TUNGGU APPROVAL Zaqi. Jangan eksekusi sebelum ACC.
Tanggal: 2026-09-15 dini hari. Branch kerja: arena/zcode-desktop (repo zabaniyah221-stack/ZCODE).

## 1. Tujuan

Bunuh 539MB Chromium, blink putih, dan drama fokus. Ganti editor CodeMirror-in-CEF dengan RSyntaxTextArea (Swing murni). Target: .deb puluhan MB, start detik, F5 sepele, tanpa overlay drama.

## 2. Perjalanan berdarah (kenapa sampai sini)

- Workbench v0.0.1: editor CM6 byte-exact + Run subprocess + drawer kanan + splash hitam. Installed verified, E2E requests OK.
- Blink putih: 3 lapis dicat hitam, splash +800ms, veil (dihapus ganggu kursor), drawer sibling → overlay → sibling lagi.
- 3x CI merah: LaunchedEffect sebelum deklarasi, Modifier.align konflik ColumnScope/BoxScope, AnimatedVisibility receiver ganda.
- Dual-instance crash: java anak yatim rebutan CEF lock. Pelajaran: kill script + java anak.
- VERDICT T1 via splash merah nempel: overlay Compose TAK BISA menutup area CEF native (heavyweight di atas). Merah nutup toolbar+status, editor tembus hitam. Saran teman (splash atas editor) GUGUR untuk area editor.
- Keputusan final sementara: drawer SIBLING (b44820a, verified penuh final 12-29).
- Diskusi RSTA: CodeMirror tak bisa di-bundle (Java vs JS), multi-cursor korban kecil, autocomplete penting (dua jalur bisa), transparan penuh butuh lepas CEF.

## 3. Sumber riset (terverifikasi)

- https://github.com/bobbylight/RSyntaxTextArea (1254 stars, push Agu 2026, BSD, Maven com.fifesoft)
- SYNTAX_STYLE_PYTHON + Theme.java verified via raw source.
- Wiki: Keyboard-Shortcut-List, Visual-Feature-Overview, Design-Overview.
- Pemakai: Arduino IDE 1.x (fork arduino/RSyntaxTextArea, PR Arduino#3019).
- Lib resmi: bobbylight/AutoComplete (184 stars).
- Harta karun bawaan: Ctrl+/ comment, Alt+Up/Down pindah baris, Ctrl+D hapus baris, template, clipboard history, mark occurrences, bracket popup, bookmark Ctrl+F2, macro, Parser framework (squiggle), ligature.
- Komparasi Android vs Desktop (subagent, dari source): bundle CM md5 identik; Android streaming virtualized vs desktop dump readText; input() didukung vs jalan buntu.
- Forum: CEF issue #1984 white flash OSR (bug upstream), CEF Forum t=15337, VS Code menutup blink pakai splash.
## 4. Sentuh / Ubah / Hapus / Tambah

### HAPUS (desktop/src/main/kotlin/Main.kt)
- JsBridge ZcodeRun + register handler + JS keydown F5 (~15 baris).
- Poll BRIDGE_OK 20x + delay splash 800ms + logLine bridge (~25 baris).
- LaunchedEffect F5-in-page + autofocus CM6 evaluateJavaScript (~15 baris).
- Import: multiplatform-webview (WebView, navigator, jsbridge, factory), KCEF init.
- desktop/build.gradle.kts: dependensi KCEF + multiplatform-webview.
- desktop/src/main/resources/zcode-www (bundle CM, ~MB) + zcode_logo splash-wait logic terkait bridge.
- KCEF bundle ~/.cache/zcode/kcef-bundle 539MB (instruksi hapus manual pasca-install versi baru).

### UBAH
- WebView(...) → SwingPanel(factory = { RSyntaxTextArea + RTextScrollPane }) di Box editor yang sama.
- doRun: getCode via evaluateJavaScript → textArea.text langsung (sinkron, hapus tGet/timing ambil).
- F5: tangkap via window KeyEventDispatcher yang sudah ada (CMP-7700) → doRun(). Shortcut lain ikut gampang.
- Fokus klik: Press → textArea.requestFocusInWindow() (Swing native, tanpa setFocus jembatan).
- Tema: Theme XML tiru Tomorrow-Night-Eighties + GitHub-Dark (sekali, ~50 baris).
- Runner.kt + zcode_run.py: TETAP (subprocess tak tersentuh migrasi ini).
- doRun output/drawer/status: TETAP (DrawerPanel sibling final tak berubah).

### TAMBAH
- build.gradle.kts: com.fifesoft:rsyntaxtextarea:3.6.x + com.fifesoft:autocomplete:3.3.x (total <2MB).
- AutoCompletion isi keyword Python + Template (for/if/def).
- Custom Parser: py_compile saat Run → ParserNotice squiggle + ikon gutter.
- Ctrl+/ comment, Alt+Up/Down, Ctrl+D: GRATIS bawaan (nol kode).

## 5. Kendala spesifik + penanganan

1. Swing di Compose (SwingPanel): fokus toolbar↔editor jembatan manual. Tangani: requestFocusInWindow + hapus semua hack CEF. Risiko sedang.
2. RSTA look: tiru tema via XML sekali. Risiko kecil.
3. Autocomplete/parser: wiring 1-2 hari ikut contoh wiki. Risiko kecil.
4. Kehilangan multi-cursor + byte-exact parity: disepakati korban kecil (catat di ADR).
5. KCEF cache user: versi baru deteksi ~/.cache/zcode/kcef-bundle → tawar hapus (hemat 539MB disk user).

## 6. Langkah eksekusi (setelah ACC)

1. Commit kecil 1: tambah dep + RSTA tampil hello (tanpa hapus CEF), CI.
2. Commit 2: doRun baca textArea + F5 dispatcher, CEF masih ada tapi tak dipakai, CI.
3. Commit 3: hapus CEF/WebView/JsBridge/splash-wait/zcode-www, CI.
4. Commit 4: tema XML + autocomplete + parser, CI.
5. Unduh .deb → pkill total → install → uji: ketik, F5, output 30 baris, tutup-buka, force-close-restart.
6. Ukur: size .deb, start detik, RAM vs baseline (JVM285+jcef712+chromium283).

## 7. Kriteria terima

- .deb <100MB, start <60 detik di Celeron, F5 tanpa blink, output penuh, fokus klik mouse langsung, tanpa folder 539MB.
- Rollback: branch arena/zcode-desktop tag pre-rsta; revert = checkout Main.kt + gradle + resources.

## 8. Non-tujuan (Fase ini)

- input() interaktif, minimap, multi-cursor, transparan penuh, sidebar lengkap. Nanti.
