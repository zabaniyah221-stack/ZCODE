# DESAIN TAMPILAN ZCODE — Draft v0.1
### Dari Zabacode WebView ke Kotlin Compose Workbench

> Jawaban untuk: *“Ngga mau desain tampilan dulu?”* — Yes, desain dulu baru `gradlew init`. Dokumen ini ngulik warna, layout, dan komponen ZABACODE yang ada, terus nawarin desain ZCODE Kotlin yang lebih matang.

**Tanggal:** 2026-08-08  
**Acuan warna:** `zabacode/themes/definitions.py` (12 tema) + `templates/index.html` CSS vars (`--bg #050806`, `--text-bright #39FF14`, `--ai #FFB000`, `--err #FF4B4B`) + `assets/logo.png` + `assets/presplash.png`  
**Prinsip:** Pydroid simple (1-tap), VS Code detail (Workbench), Acode touch (QuickTools) — tapi semua **Compose native + CodeMirror 6 bundled** (migrasi 2026-08 dari Ace 1.44.0 — `docs/MIGRASI_CM6.md`).

---

## 1. Warna & Tipografi — Jangan Bikin Baru, Perhalus yang Ada

### 1.1 Palet utama ZABACODE (yang terbukti enak di OLED)

ZABACODE sekarang pakai **12 tema** — kita pertahankan semua, tapi rapikan jadi Material 3:

| Tema | Feel | Kapan dipakai | Hex inti |
|---|---|---|---|
| **retro** (default) | Hijau phosphor `bg #050806` `text-bright #39FF14` | Default, hemat baterai AMOLED true-black `presplash #050806` | `#050806` |
| **tokyo_night** | Biru-ungu `bg #191C2B` | Malam, cozy | `#191C2B` |
| **dracula** | Ungu `bg #282A36` | Pro, contrast tinggi | `#282A36` |
| **nord** | Arctic `bg #2E3440` | Siang, soft | `#2E3440` |
| **solarized** | Teal `bg #002B36` | Baca lama | `#002B36` |
| **cyberpunk, synthwave84** | Neon pink-cyan | Demo / content | — |
| **monokai, gruvbox, catppuccin, forest, one_dark** | Klasik | User choice | — |

**Keputusan desain ZCODE:**
- **True-black `#050806`** tetap jadi `default` — presplash, Adaptive Icon background, dan `editor_bg` semua sinkron (fix Zabacode issue #2 white square).
- **Material 3 `colorScheme`** generate dari `bg` tiap tema: `primary = text-bright`, `error = err (#FF4B4B)`, `tertiary = ai (#FFB000)`. Jadi tema Zabacode langsung jadi `MaterialTheme`.
- **Tipografi:** `JetBrains Mono` (sudah di Zabacode `--mono`) 13-14sp editor, 11sp status bar, 12sp QuickTools. Line height 1.5-1.6.

### 1.2 Aturan warna yang kita jaga dari Zabacode
- **Editor + gutter sinkron:** `editor_bg = bg`, `gutter = bg_panel` — tidak ada putih nyasar.
- **AI = amber `#FFB000`**, **Error = red `#FF4B4B`**, **Success = `#39FF14`** — konsisten di Oracle card, output, status dot.
- **CRT scanlines** optional toggle (Zabacode `#scanlines` opacity .5) — kita pertahankan sebagai `Settings → CRT` switch, tapi default OFF biar hemat GPU di ARMv7.

---

## 2. Layout — Dari “Sidebar + Modal” ke “Workbench VS Code”

### 2.1 Zabacode sekarang (WebView)

```
[Topbar 44px: ☰ ZABACODE ● ● Ln 12]
[Tab bar 32px: untitled_1.py x  test.py x  +]
[Code Editor (CodeMirror 6) flex:1]
[Mobile Symbol Bar (TAB : ( ) [ ] ...) — muncul kalau plugin symbol_bar aktif]
[Run/Clear bar 8px]
[Sidebar 280px overlay dari kiri: Files / Plugins / Settings]
[Output view (full-screen swap, bukan panel) — stdout/stderr + Oracle card + stdin row]
```
- **Kelebihan:** Simple, 1-tap Run, QuickTools kepakai.
- **Kekurangan:** Output swap full-screen (kehilangan konteks editor), sidebar overlay (nggak ada Activity Bar permanen), tidak ada Command Palette visual, tidak ada Problems panel.

### 2.2 ZCODE Kotlin (Compose) — Workbench beneran, tapi tetap 1-tap

```
┌─────────────────────────────────────────────────────────────┐
│ Status/Topbar 40px: [≡] ZCODE  ● Python 3.11  Ln 4 Col 12  [⟳] [⚙] │
├──────┬─────────────────────────┬────────────────────────────┤
│ ACT  │ SIDE BAR 260px          │ EDITOR GROUPS flex:1      │
│ BAR  │ Explorer                │ ┌─ Tabs ────────────────┐ │
│ 44px │ ┌─ Files ───────────┐   │ │ main.py ●  app.py    │ │
│  ☰   │ │ ▶ files/           │   │ ├─────────────────────┤ │
│  🔍  │ │   main.py          │   │ │ 1  def hello():     │ │
│  ▷   │ │   utils.py         │   │ │ 2      print("hi")  │ │
│  ◧   │ │ + New File         │   │ │ 3                      │ │
│  ⚙   │ │                    │   │ │                      │ │
│      │ │ Search [____]      │   │ ├─ QuickTools ────────┤ │
│      │ │ Ctrl+P Quick Open  │   │ │ TAB : ( ) [ ] { } " ' = _ def return import │ │
│      │ │                    │   │ └─────────────────────┘ │
│      │ │ Outline            │   │                        │
├──────┴─────────────────────────┴────────────────────────────┤
│ PANEL 180px (resizable, swipe): [Output | Oracle 🔮 | Problems | Terminal] │
│ > hello world                                              │
│ 🔮 IndexError — Reached Past the End — Line 2 [Jump]       │
│ [stdin: ____ ] [Send]                                      │
└─────────────────────────────────────────────────────────────┘
│ Status Bar 22px: main.py  Python  CRLF  UTF-8  ● Oracle  ARMv7 │
└─────────────────────────────────────────────────────────────┘
```

**Kenapa begini:**
- **Activity Bar 44px vertikal** (Explorer, Search, Run & Debug, Plugins, Settings) — kayak VS Code, tapi icon 18-20px biar jempol kena. Di HP 5" portrait, Activity Bar bisa collapse jadi bottom nav (Acode style) — kita buat responsive: `>600dp` = side, `<600dp` = bottom.
- **Editor Groups + Tabs 32px** (copy Zabacode tab bar, tapi Compose) — tab aktif `border-top 2px #39FF14`, dot `●` untuk unsaved, `×` close.
- **QuickTools** — `LazyRow` horizontal scroll (copy `mobile-symbol-bar` Zabacode: `TAB : ( ) [ ] { } " ' = _ def return import` + `Undo Redo Find Palette Search`). Tinggi 36px, gap 6px, `symbol-btn` 12sp.
- **Panel bawah** (bukan swap full-screen) — user tetap lihat editor sambil lihat Output/Oracle. Bisa swipe up/down, default 180px (30% layar), persistent.
- **Floating menu** `•••` (copy Zabacode `editor-floating-trigger` + `floating-menu`) — muncul pas select text: Copy/Cut/Paste/Select All/Undo/Redo/Find/Palette/Search. Posisi `above selection`, jangan di tengah (fix floating menu overlap).
- **Status Bar 22px** — kayak VS Code: file, Python version, CRLF, Oracle dot, arch.

### 2.3 Perbedaan Portrait vs Landscape vs Tablet
- **Portrait HP (<600dp):** Activity Bar → bottom nav 48px, Side Bar → bottom sheet 50% height, Panel → 35% height.
- **Landscape / Tablet (>600dp):** Layout di atas (Activity Bar kiri permanen).
- **Keyboard muncul:** Panel auto-resize, `debounce 100ms` + `imePadding()` — cegah loncat 4-5 baris (ZMUX lesson).

---

## 3. Komponen — 1:1 Port dari Zabacode tapi Compose

| Zabacode (WebView) | ZCODE (Compose) | Catatan desain |
|---|---|---|
| `topbar 44px` + `status-dot` | `TopAppBar 40px` + `StatusDot` (running amber, ok green, err red) | Dot 8px, anim `blink .6s` |
| `tab-bar 32px` | `ScrollableTabRow 32px` | Active `bg #050806 + border-top 2px #39FF14`, close `×` hover `err-dim` |
| `mobile-symbol-bar` | `QuickTools LazyRow` | Chip `bg-panel + border #1B4D2E`, active `border-bright` |
| `editor-floating-menu` 8 tombol | `FloatingMenu` `DropdownMenu` di atas selection | Copy/Cut/Paste/SelectAll/Undo/Redo/Find/Palette/Search — jangan overlap keyboard |
| `sidebar 280px` + `backdrop` | `ModalNavigationDrawer 260px` + `NavigationRail 44px` | Side Bar: Files (list + `+ New File` FAB), Search, Outline, Plugins |
| `view-output` full swap | `ModalBottomSheet / Panel` resizable | Tabs: Output / Oracle / Problems / Terminal — swipe, persist height di DataStore |
| `oracle-card` amber border-left | `OracleCard` amber `border-left 3px #FFB000` + `tappable line` | Title `Reached Past...`, `Fix:` green, `oracle-src` `bg rgba(0,0,0,.28)` + Jump underline |
| `output-image` `white bg` | `OutputImage` `white bg + border-left amber` + `zoom` | `max-width 100%`, `click → expanded` (copy Zabacode `.output-image img.expanded`) |
| `terminal-stdin-row` | `StdinRow` (`[stdin: ____] [Send]`) | Input `bg-panel-2 #0F1712 + border #1B4D2E`, Send `border-bright` |
| `settings dashboard` modal | `SettingsScreen` Scaffold + `SearchBar` (port `navigation.py` `get_all_settings`) | Palette: `get_command_palette_items(q)`, QuickOpen: `get_quick_open_items(q)` |
| `boot-screen` matrix + logo | `SplashScreen` `presplash #050806` + logo 120px `pulseLogo 2s` + boot lines | Copy Zabacode boot, tapi pakai `SplashScreen API` Android 12+ |

---

## 4. Interaksi — Pydroid Simple, tapi VS Code Detail

### 4.1 1-Tap Run (Pydroid)
- **Tombol `▶ Run` di TopBar + di `editor-controls` (`flex:1` green `#39FF14`)** — tap → `CommandManager.execute("run.python")` → `EditorBridge.getValue()` → `ExecutionEngine.runIsolated()` → Panel Output auto-open (bukan swap). `stdin` row muncul kalau `input()` terdeteksi.
- **FAB `+ New File`** di Explorer — tap → `untitled_N.py` + `revision 0`.

### 4.2 VS Code Detail
- **Command Palette `Ctrl+Shift+P` / `> `** — `ModalBottomSheet` + `SearchBar`, list dari `get_command_palette_items(q)` (copy Zabacode `/api/palette`).
- **Quick Open `Ctrl+P`** — `Quick Open: files + symbols` (`/api/quickopen`).
- **Go to Line `Ctrl+G`** — dialog `line:column`.
- **Find `Ctrl+F` / floating Find** — panel `@codemirror/search` (via palette "Find in File" / `openFind()`), di-style OLED.
- **Outline `Ctrl+Shift+O`** — `DocumentSymbol` tree dari `editor_intelligence.get_symbol_outline`.

### 4.3 Acode Touch
- **QuickTools scroll horizontal** — `isHorizontalScrollBarEnabled=false`, `HorizontalScrollView` (copy ZMUX `KeyCapView` fix).
- **Tab swipe + close `×`** — `swipe to close` + `long-press → close others`.
- **Multi-cursor** `Ctrl+Click` — CM6 belum punya true multi-caret (hanya rectangular selection); kalau dibutuhkan, pola Acode `multiCursorSelectionExtension` bisa di-port (backlog, lihat docs/MIGRASI_CM6.md §5).

---

## 5. Mockup — Bayangan Visual (akan di-generate)

> 3 layar utama yang perlu kamu approve sebelum `gradlew init`:

1. **Workbench Retro Green (default)** — Activity Bar + Editor + Panel Output + QuickTools, true-black `#050806`, `JetBrains Mono`, `status dot green`.
2. **Editor Focus + Floating Menu** — seleksi text → `•••` → menu Copy/Cut/Paste/Undo/Redo/Find/Palette, tab `main.py ●` unsaved.
3. **Settings + Command Palette** — search `theme` → list `retro/dracula/tokyo_night`, palette `> Format Document`.

Kita generate setelah kamu setuju arah warna — mau **retro green tetap default** (kayak Zabacode) atau mau **tokyo_night** sebagai default ZCODE biar beda identitas?

---

## 6. Pertanyaan Desain Buat Kamu (biar nggak nebak)

| # | Tanya | Opsi A (rekom) | Opsi B | Ngaruh ke |
|---|---|---|---|---|
| **D1** | **Default theme** | **Retro Green `#050806` + `#39FF14`** (Zabacode identity, hemat AMOLED) | Tokyo Night `#191C2B` (lebih modern, VS Code feel) | Branding + presplash |
| **D2** | **Activity Bar posisi di HP** | **Bottom nav di HP <600dp** (Acode style, jempol) | Tetap kiri 44px (VS Code style, butuh HP lebar) | Ergonomi |
| **D3** | **Panel Output** | **Bottom sheet resizable 30%** (tetap lihat editor) | Full-screen swap kayak Zabacode sekarang | Konteks |
| **D4** | **CRT scanlines** | **OFF default, toggle di Settings** (hemat GPU ARMv7) | ON default (retro feel) | Performa |
| **D5** | **QuickTools** | **Selalu visible di bawah editor** (Pydroid/Acode) | Auto-hide, muncul pas ketik (hemat space) | Space |

Jawab `D1-D5` (misal `D1 retro, D2 bottom, D3 sheet, D4 off, D5 visible`) — nanti aku generate 3 mockup hi-fi sesuai pilihanmu + update `PLAN v0.3` bagian UI.

---

---

## 7. Update v0.4 — Fase 0 Final (Faded Grey + PTY + No Icon + Contribute)

**Tanggal:** 2026-08-08 — sesuai diskusi final:
- **Topbar faded grey `#3A4452`** (bukan retro neon) — `≡` tiga garis kiri (tanpa tulisan hamburger), `+` add tab kanan, tabs `main.py` kuning. Faded grey kalem buat coding 5299 baris.
- **Editor OLED `#050806` true-black** + gutter 40px line numbers `1 2 3 4` (kayak terminal editor umumnya)
- **QuickTools handle** `Tab | : | ; | ' | # | ( | ) | [ | ] | def | return | import` scroll horizontal (`HorizontalScrollView`, `isHorizontalScrollBarEnabled=false` — fix ZMUX)
- **FAB ▶ kuning di atas handle pojok kanan bawah** — di atas QuickTools, tidak nempel di handle, jadi handle tetep scroll. FAB above handle.
- **Output pindah layer/halaman full-screen** (bukan panel 30%) — `Run` → `Terminal PTY` layer, ketik langsung di terminal (no stdin input field). PTY via `terminal-view 0.118.0` + `realpty.py` tanpa Alpine (ketik langsung untuk chatbot/calculator).
- **Drawer text-only no icon**: `Settings` → `Theme`, `Plugin/Addon`, `Pip` (pindah layer lagi buat `pip install`), `About` — plus `Contribute` → `https://github.com/muzape28-blip/ZCODE/issues` (no Gmail, no icon debug leluasa)
- **No AI/Oracle di kerangka** — skip dulu, fokus editor/files/run/pip
- **Contribute** menggantikan `Premium` → langsung repo Issues/Feedback, iconless

*— DESIGN v0.4, 2026-08-08 — faded grey #3A4452, FAB above handle, PTY pindah layer, Contribute Issues, ready for Fase 0.*
