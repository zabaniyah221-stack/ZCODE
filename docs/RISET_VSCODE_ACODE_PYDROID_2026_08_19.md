# RISET POSISI PRODUK — VS CODE, ACODE, PYDROID, DAN ZCODE

Tanggal audit: 2026-08-19  
Status: **RESEARCH COMPLETED (arsitektur + ekosistem representatif)**

## 1. Batas kejujuran

"Membaca seluruh VS Code dan semua pluginnya" tidak mungkin diklaim literal:
VS Code memiliki jutaan baris dan marketplace berisi ribuan extension pihak
ketiga, sebagian closed-source. Audit ini mencakup:

- organisasi source, editor, workbench, extension host/API, terminal,
  filesystem, SCM, dan 96 built-in extension VS Code;
- 82 repository sample extension resmi;
- sampling source extension besar/representatif: Python, ESLint, Prettier,
  GitLens, built-in Git/TypeScript/JSON/HTML/CSS;
- seluruh struktur repo utama Acode, plugin loader/API/docs, terminal/LSP,
  metadata 237 plugin store, dan source plugin Python/Git/Prettier;
- Pydroid melalui listing resmi karena source utamanya proprietary;
- perbandingan terhadap source/status ZCODE saat audit.

Jadi hasil ini kuat untuk **model produk dan arsitektur**, bukan klaim bahwa
setiap baris/extension marketplace telah dibaca.

## 2. Snapshot sumber

### VS Code

- Repo: https://github.com/microsoft/vscode
- SHA audit: `a1c7d1be7ebeddac39ee87a311d940b04b2e5da2`
- Source organization:
  https://github.com/microsoft/vscode/wiki/source-code-organization
- Extension API:
  https://code.visualstudio.com/api
- Remote extension architecture:
  https://code.visualstudio.com/api/advanced-topics/remote-extensions
- Language features/LSP:
  https://code.visualstudio.com/api/language-extensions/programmatic-language-features

Sparse checkout area yang diaudit sendiri sudah memuat sekitar 1,49 juta baris
TypeScript. Ditemukan 96 manifest built-in extension dan 82 sample extension
resmi.

External extension sample source:

- Python `f5e644a` — 861 TS/JS files, ±147.936 lines;
- ESLint `b3d5ea1` — 21 files, ±6.735 lines;
- Prettier `e0f1d3c` — 45 files, ±4.051 lines;
- GitLens `6167e7f` — 1.305 files, ±400.872 lines.

### Acode

- Repo: https://github.com/Acode-Foundation/Acode
- SHA audit: `85106aa0d2a95f4e53361a0daa693468628a7ca2`
- Docs plugin: https://docs.acode.app/
- Plugin template: https://github.com/Acode-Foundation/acode-plugin
- F-Droid listing:
  https://f-droid.org/packages/com.foxdebug.acode/

Repo utama v1.13.1 saat audit: 322 JS, 107 TS, 51 Java, 16 Kotlin; sekitar
90 ribu baris JS, 24 ribu TS, dan 22 ribu Java/Kotlin. Ini Cordova/hybrid app
dengan native plugin, bukan aplikasi Compose murni.

Plugin source yang diaudit:

- docs `4f4366e`;
- Python/Pyodide `e80afc5`;
- Git SCM `1aa84a5`;
- Prettier `9b6f148`.

API store `https://acode.app/api/plugins` pada tanggal audit memberi 237 plugin
unik: 206 gratis, 31 berbayar; 69 mencantumkan repository; 176 menyatakan
support semua editor dan 61 khusus CodeMirror.

### Pydroid

Source utama tidak terbuka, sehingga fakta produk memakai listing resmi:

- https://play.google.com/store/apps/details?id=ru.iiec.pydroid3

Listing menyebut interpreter Python offline, pip + custom repository, terminal,
compiler C/C++/Fortran, Cython, PDB debugger, samples, scientific package,
Tkinter/Kivy/PySide/Pygame, editor tabs/symbol bar, dan fitur bertanda premium.

## 3. VS Code — apa sebenarnya produk ini

VS Code adalah **general-purpose extensible workbench**, bukan runtime bahasa.
Core-nya menyediakan:

- text model/editor, undo stack, multi-editor/group;
- filesystem/virtual filesystem;
- workbench: commands, menus, panels, views, settings, keybindings;
- integrated terminal dan task/debug orchestration;
- SCM UI/API;
- extension host terpisah dan RPC main-thread ↔ extension-host;
- local, web-worker, dan remote extension host;
- webview sandbox + message passing;
- LSP/DAP/provider APIs;
- marketplace/distribution/activation/contribution points.

Built-in extension bukan sekadar addon kecil. Dari 96 manifest:

- 55 berkontribusi language definition;
- 51 grammar;
- 25 configuration;
- 19 commands;
- 15 menus;
- 14 snippets;
- sisanya mencakup debugger, task, notebook, terminal, SCM, auth, webview,
  themes, AI/chat, dan lain-lain.

Extension Python resmi tidak membawa Python interpreter. Ia mendeteksi dan
mengorkestrasi environment/interpreter milik host, debugger, terminal,
configuration, package/environment tools, dan language tooling. Hal serupa
berlaku untuk banyak extension: VS Code adalah pengatur toolchain, bukan
pembawa semua toolchain.

### Kekuatan arsitektur VS Code

1. Extension host terpisah membatasi freeze/crash plugin dari UI utama.
2. API memakai contribution/provider abstraction, bukan akses bebas ke object
   internal editor.
3. Activation event menghindari semua extension hidup saat startup.
4. LSP/DAP memisahkan UI editor dari language/runtime process.
5. FileSystem provider membuat local/remote/virtual workspace satu model.
6. Command registry adalah tulang punggung: keyboard, menu, palette, plugin
   memanggil command yang sama.
7. Undo stop membuat edit programatik dapat menjadi satu aksi logis.

### Hal yang tidak cocok disalin mentah ke ZCODE

- desktop process/memory budget;
- Node extension host;
- Electron/workbench complexity;
- marketplace trust model;
- asumsi compiler/interpreter tersedia di host;
- UI padat keyboard/mouse;
- remote server architecture.

VS Code adalah **sekolah arsitektur dan UX** bagi ZCODE, bukan pesaing langsung.

## 4. Acode — apa sebenarnya produk ini

Acode adalah **general mobile code editor + web IDE + Linux terminal + plugin
platform**. Versi yang diaudit sudah memakai CodeMirror 6 dan memiliki:

- banyak paket `@codemirror/lang-*`;
- built-in LSP client/runtime provider;
- xterm.js + attach/fit/search/image/webgl addons;
- Alpine/PRoot terminal;
- file provider, FTP/SFTP, preview/browser/webview;
- command registry, palette, themes, snippets, formatter API;
- 237 plugin store entries.

Acode jauh lebih dekat ke VS Code daripada versi lamanya: bahkan Git SCM
plugin secara eksplisit meniru pola VS Code `Shell → Parse → Render` dan
menyediakan SCM API kepada plugin lain.

### Model plugin Acode

Plugin adalah ZIP dengan `plugin.json` dan `main.js`. Loader memasukkan script
plugin ke `document.head`, lalu plugin mendaftarkan init melalui
`acode.setPluginInit`. API global memberi akses ke editor, filesystem, command,
LSP, terminal, page/sidebar/dialog, theme, formatter, webview, dan native
bridge.

Kelebihan:

- sangat mudah dikembangkan dengan JavaScript/TypeScript;
- akses UI/editor dalam;
- plugin bisa menambah command, formatter, language, theme, terminal, SCM;
- cocok untuk eksperimen mobile cepat.

Risiko/biaya:

- plugin berjalan sebagai script di konteks UI utama, bukan extension host
  terpisah seperti VS Code;
- blast radius plugin lebih besar;
- API juga mengekspos global/internal object (`editorManager`);
- cleanup mengandalkan callback unmount plugin;
- timeout/auto-disable membantu startup, tetapi bukan process isolation;
- source store tidak selalu tersedia: hanya 69/237 metadata menyatakan repo;
- store memiliki plugin berbayar.

### Python di Acode

Python bukan runtime inti Acode. Plugin Python resmi yang diaudit memakai
Pyodide dalam Web Worker dan README menyebut keterbatasan multi-file import.
Di sisi lain, terminal Alpine memungkinkan memasang Python Linux. Ini membuat
Acode kuat sebagai editor/terminal umum, tetapi Python experience terpecah
antara plugin/browser runtime dan distro Linux.

Jadi Acode bukan "Pydroid dengan editor web". Ia adalah editor web/mobile umum
yang Python-nya salah satu workload.

## 5. Pydroid — model produk

Pydroid adalah **Python IDE/runtime untuk Android**. Pusat produknya:

```text
Python interpreter → run/debug → pip/custom wheels/compiler → samples/GUI
```

Editor, terminal, package repository, debugger, dan GUI framework dibangun
mengelilingi Python. Ini sama dengan pusat gravitasi ZCODE.

Kekuatan Pydroid yang belum dikejar ZCODE sepenuhnya:

- runtime lebih baru pada rilis terkini;
- SciPy/scikit-learn/Jupyter dan native package breadth;
- compiler C/C++/Fortran + Cython;
- PDB breakpoint/watch;
- Tkinter/Kivy/PySide/Pygame/OpenCV;
- terminal/REPL matang;
- pengalaman dan distribusi production bertahun-tahun.

Batas/peluang:

- proprietary/freemium;
- code prediction dan beberapa library ditandai premium;
- custom wheel repository tidak transparan seperti source ZCODE;
- target low-end/ARMv7 bukan komitmen publik utama;
- diagnostik installer/rollback tidak terlihat seketat ZCODE.

## 6. ZCODE — posisi sebenarnya

Pusat ZCODE:

```text
Python 3.11 Android/ARMv7 → editor → run/input → package engine → curated
Library/Samples → diagnostics
```

Itu membuat ZCODE **secara kategori produk jauh lebih dekat ke Pydroid**.

ZCODE lebih kuat daripada Acode untuk niche Python Android tertentu:

- interpreter native Python langsung menjadi core, bukan plugin;
- package compatibility ARMv7/Chaquopy dipahami aplikasi;
- transactional install, smoke test, rollback, cancel, breadcrumbs;
- Library menyatakan TESTED/COMPATIBLE/UNAVAILABLE + alasan;
- sample dependency gate;
- traceback-to-editor;
- offline-first dan tanpa premium lock;
- UI/diagnostik dirancang untuk user tanpa PC.

Tetapi ZCODE belum menyaingi breadth Acode/VS Code:

- hanya fokus Python;
- belum punya open third-party plugin host;
- belum Git/FTP/SFTP/general web preview;
- belum LSP process/Jedi matang;
- terminal belum shell;
- command/workbench API jauh lebih kecil.

Dan ZCODE belum menyaingi breadth Pydroid:

- tidak punya compiler/native repository sendiri;
- tidak punya SciPy/scikit-learn di Chaquopy ARMv7;
- belum GUI framework;
- belum debugger PDB penuh;
- belum private-process interpreter/hard kill;
- Python harus tetap 3.11 demi ARMv7.

## 7. Matriks posisi produk

| Kemampuan | VS Code | Acode | Pydroid | ZCODE |
|---|---|---|---|---|
| Fokus utama | Workbench umum | Mobile editor/web IDE | Python Android | Python Android ARMv7 |
| Embedded Python core | Tidak | Tidak (plugin/Alpine) | Ya | Ya |
| Android-native target | Tidak | Ya | Ya | Ya |
| ARMv7 kelas satu | Tidak relevan | Bukan fokus utama | Tidak jelas | Ya |
| Offline run Python | Tool host | Via plugin/distro | Ya | Ya |
| Package manager Python | Tool host | Via distro/plugin | pip + repo/compiler | Engine transaksional |
| Scientific breadth | Via host | Via Alpine | Sangat luas | Terbatas wheel Android |
| General languages | Sangat luas | Sangat luas | Python-centric | Python-only |
| Plugin ecosystem | Sangat besar | 237 store entries | Plugin pendukung app | Internal curated plugins |
| Plugin isolation | Extension host process/worker | Main web context | Tidak diketahui | Tidak ada third-party host |
| LSP/debugger | Matang | LSP berkembang | PDB | Checker + fitur lokal |
| Real shell terminal | Ya | Ya, Alpine/PRoot | Ya | Belum |
| Free tanpa paywall | Core gratis; marketplace campur | Core + free/paid plugin | Freemium | Ya, prinsip inti |
| In-app diagnostics tanpa PC | Bukan target utama | Sebagian | Terbatas/tidak terbuka | Target utama |

## 8. Apakah ZCODE "pesaing Pydroid"?

### Jawaban kategori: YA

User mencari:

- IDE Python Android;
- interpreter offline;
- install package;
- sample;
- editor + terminal;
- run dari HP.

Dalam keputusan download, Pydroid adalah pembanding paling langsung. VS Code
dan Acode bukan substitusi langsung tanpa environment/runtime tambahan.

### Jawaban kematangan/feature parity: BELUM

Mengatakan ZCODE sudah hampir menyamai Pydroid **secara keseluruhan** terlalu
dini. Pydroid unggul besar pada runtime breadth, compiler, debugger, GUI, dan
kematangan. ZCODE baru dapat menyaingi pada niche:

> IDE Python Android gratis, offline-first, transparan, ARMv7-first, installer
> terdiagnosis, dan kurasi package/sample yang jujur.

Pada niche itu ZCODE bukan sekadar clone; ia punya diferensiasi nyata.

### Formulasi positioning yang jujur

> ZCODE adalah Python IDE Android gratis dan offline-first yang menjadikan
> ARMv7/HP murah sebagai target kelas satu, dengan package engine transaksional,
> Library kompatibilitas, Samples runnable, dan diagnostik tanpa PC.

Bukan:

> "VS Code Android lengkap" atau "Pydroid sudah terkalahkan".

## 9. Pelajaran yang layak dibawa ke ZCODE

### Dari VS Code

1. Document model/history harus per-file.
2. Semua aksi menjadi command; UI/shortcut/plugin memanggil command sama.
3. Plugin/runtime berat harus keluar dari UI process.
4. LSP/DAP/provider boundary lebih penting daripada menambah intelligence
   langsung ke composable.
5. Contribution point terbatas lebih aman daripada global object penuh.
6. Activation/lifecycle/disposable wajib jika plugin eksternal lahir.
7. Webview memakai message passing dan CSP, bukan localhost sembarangan.

### Dari Acode

1. Mobile-first symbol bar, palette, file/sidebar, touch terminal.
2. API plugin sederhana mempercepat ekosistem.
3. CodeMirror language package dapat modular.
4. Alpine/PRoot layak sebagai mode opsional, bukan runtime utama.
5. Terminal session persistence, touch selection, fit/resize layak dipelajari.
6. Plugin timeout/auto-disable diperlukan, tetapi process isolation tetap lebih
   kuat.
7. Jangan meniru akses global plugin tanpa capability/permission boundary.

### Dari Pydroid

1. Python workflow harus satu jalur dari editor → run → package → sample.
2. REPL/debugger/terminal adalah fitur inti Python IDE.
3. Native package breadth menentukan persepsi "bisa apa".
4. Sample per-package/jalur belajar sangat bernilai.
5. GUI framework adalah keunggulan nyata, tetapi mahal dan jangan dijanjikan
   sebelum runtime-nya terbukti.

## 10. Implikasi roadmap

Urutan yang direkomendasikan:

1. Selesaikan v1.0.19 dan UAT.
2. Per-file CodeMirror state/history + Undo/Redo touch.
3. Command registry internal sebagai satu pintu aksi.
4. Private-process Python spike untuk hard-stop/crash isolation.
5. LSP/Jedi sebagai process/provider, bukan logic UI.
6. Command Console.
7. Plugin API internal bertipe/capability-based; third-party host hanya setelah
   lifecycle, permission, crash isolation, dan signing/update model jelas.
8. Alpine/PRoot on-demand bila kebutuhan shell/scipy/compiler sudah nyata.

Tidak disarankan mengejar jumlah plugin atau fitur VS Code. Target ZCODE harus
memperdalam **Python Android experience**, bukan berubah menjadi editor semua
bahasa setengah matang.

## 11. Confidence

- ZCODE lebih dekat ke Pydroid daripada VS Code/Acode: **98%**.
- ZCODE sudah pesaing Pydroid pada niche gratis+ARMv7+transparansi: **85%**.
- ZCODE hampir menyaingi Pydroid secara feature breadth: **35%**.
- VS Code/Acode paling berguna sebagai sumber pola arsitektur/UX: **95%**.
