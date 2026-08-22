// =====================================================================
// ZCODE — CodeMirror 6 editor (pengganti Ace 1.44.0, migrasi 2026-08)
// Kontrak bridge dipertahankan 1:1 dengan versi Ace (lihat docs/MIGRASI_CM6.md §3):
//   setCode, getCode, insertText, undo, redo, duplicateRows,
//   toggleCommentLines, onEditorReady (JS→Kotlin), ZCODE.onCodeChange
//   openFind()  — pengganti akses Find yang tadinya via mobile-menu Ace.
//   gotoLine(n) — batch anti-sepi: dipakai 🔍 mode Line/Find & TODO Extractor.
// Batch anti-sepi (PLAN_BATCH_ANTI_SEPI.md): autocomplete kasta 1+2
//   (kata dokumen + keyword + builtins + snippet) — kasta 3 (jedi) nanti.
// Prinsip: jujur & teliti — semantik fungsi harus identik dengan versi Ace.
// =====================================================================

import { EditorState, Compartment } from "@codemirror/state";
import {
  EditorView,
  keymap,
  lineNumbers,
  highlightActiveLine,
  highlightActiveLineGutter,
  drawSelection,
  dropCursor,
  rectangularSelection,
} from "@codemirror/view";
import {
  defaultKeymap,
  history,
  historyKeymap,
  indentWithTab,
  isolateHistory,
  undo as cmUndo,
  redo as cmRedo,
  undoDepth,
  redoDepth,
} from "@codemirror/commands";
import {
  indentUnit,
  syntaxHighlighting,
  bracketMatching,
  indentOnInput,
  HighlightStyle,
  foldGutter,
  foldKeymap,
} from "@codemirror/language";
import { search, searchKeymap, openSearchPanel, highlightSelectionMatches } from "@codemirror/search";
import { autocompletion, closeBrackets } from "@codemirror/autocomplete";
// Gerbong A v1.0.19: lint gutter — sumber diagnostik = Checker Kotlin via
// bridge setDiagnostics(json), BUKAN linter JS (satu sumber kebenaran).
import { setDiagnostics as cmSetDiagnostics, lintGutter } from "@codemirror/lint";
// A2: penanda whitespace bawaan @codemirror/view (trailing berbahaya di
// Python; toggle terpisah, default OFF — keputusan user 2026-08-17).
import { highlightTrailingWhitespace } from "@codemirror/view";
import { tags } from "@lezer/highlight";
import { python } from "@codemirror/lang-python";

// ---------------------------------------------------------------------
// Autocomplete kasta 1+2 (batch anti-sepi S4) — offline, deterministik.
// Sumber: kata dalam dokumen + keyword Python + builtins + snippet.
// Kasta 3 (jedi/LSP) = batch terpisah, lihat backlog.
// ---------------------------------------------------------------------

const PY_KEYWORDS = [
  "False", "None", "True", "and", "as", "assert", "async", "await",
  "break", "class", "continue", "def", "del", "elif", "else", "except",
  "finally", "for", "from", "global", "if", "import", "in", "is",
  "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
  "while", "with", "yield",
];

const PY_BUILTINS = [
  "abs", "all", "any", "bin", "bool", "bytearray", "bytes", "callable",
  "chr", "classmethod", "compile", "complex", "delattr", "dict", "dir",
  "divmod", "enumerate", "eval", "exec", "filter", "float", "format",
  "frozenset", "getattr", "globals", "hasattr", "hash", "help", "hex",
  "id", "input", "int", "isinstance", "issubclass", "iter", "len",
  "list", "locals", "map", "max", "min", "next", "object", "oct",
  "open", "ord", "pow", "print", "property", "range", "repr", "reversed",
  "round", "set", "setattr", "slice", "sorted", "staticmethod", "str",
  "sum", "super", "tuple", "type", "vars", "zip", "__init__", "__name__",
];

// Snippet Pack (S5) — konten IDENTIK dengan SnippetLibrary.kt + template
// ZABACODE (GPLv3; Zaqi + ZABACODE Contributors; see root NOTICE). Jaga sinkron!
const SNIPPETS = [
  {
    label: "flask_app",
    detail: "Flask Web App",
    body: "from flask import Flask, jsonify\n\napp = Flask(__name__)\n\n@app.route(\"/\")\ndef index():\n    return jsonify({\"message\": \"Hello from ZCODE!\"})\n\n@app.route(\"/api/data\")\ndef get_data():\n    return jsonify({\"items\": []})\n\nif __name__ == \"__main__\":\n    app.run(host=\"127.0.0.1\", port=5000)\n",
  },
  {
    label: "web_scraper",
    detail: "Web Scraper (BS4)",
    body: "import requests\nfrom bs4 import BeautifulSoup\n\nurl = \"https://example.com\"\nresponse = requests.get(url, timeout=10)\n\nif response.status_code == 200:\n    soup = BeautifulSoup(response.text, \"html.parser\")\n    titles = soup.find_all(\"h1\")\n    for title in titles:\n        print(title.get_text(strip=True))\nelse:\n    print(f\"Error: {response.status_code}\")\n",
  },
  {
    label: "async_fetch",
    detail: "Async HTTP Fetcher",
    body: "import asyncio\nimport urllib.request\n\nasync def fetch(url):\n    loop = asyncio.get_event_loop()\n    req = urllib.request.Request(url)\n    response = await loop.run_in_executor(None, lambda: urllib.request.urlopen(req, timeout=10))\n    data = response.read().decode(\"utf-8\", errors=\"replace\")\n    print(f\"Fetched {len(data)} bytes from {url}\")\n    return data\n\nasync def main():\n    urls = [\"https://httpbin.org/get\", \"https://httpbin.org/ip\"]\n    results = await asyncio.gather(*[fetch(u) for u in urls])\n    for r in results:\n        print(r[:200])\n\nasyncio.run(main())\n",
  },
  {
    label: "rest_api",
    detail: "REST API Client",
    body: "import json\nimport urllib.request\n\ndef api_get(url, headers=None):\n    req = urllib.request.Request(url, headers=headers or {})\n    with urllib.request.urlopen(req, timeout=10) as resp:\n        return json.loads(resp.read())\n\ndef api_post(url, data, headers=None):\n    body = json.dumps(data).encode()\n    hdrs = {\"Content-Type\": \"application/json\"}\n    if headers:\n        hdrs.update(headers)\n    req = urllib.request.Request(url, data=body, headers=hdrs)\n    with urllib.request.urlopen(req, timeout=10) as resp:\n        return json.loads(resp.read())\n\n# Example\nresult = api_get(\"https://httpbin.org/get\")\nprint(json.dumps(result, indent=2))\n",
  },
];

function zcodeCompletions(context) {
  // Trigger: saat mengetik kata, setelah '.', atau eksplisit (Ctrl-Space).
  const afterDot = context.matchBefore(/\.\w*$/);
  const word = afterDot
    ? context.matchBefore(/\w*$/)
    : context.matchBefore(/\w+$/);
  if (!afterDot && !word && !context.explicit) return null;

  const options = [];
  const seen = new Set();
  const push = (label, type, detail, apply) => {
    if (seen.has(label)) return;
    seen.add(label);
    const o = { label, type };
    if (detail) o.detail = detail;
    if (apply) o.apply = apply;
    options.push(o);
  };

  // Kasta 1: kata-kata dalam dokumen (≥2 char, maks ~60 pool)
  const docWords =
    context.state.doc.toString().match(/[A-Za-z_][A-Za-z0-9_]{1,}/g) || [];
  let collected = 0;
  for (const w of docWords) {
    if (collected >= 60) break;
    if (seen.size < 120) { push(w, "variable"); collected++; }
  }
  // Kasta 2: keyword + builtins Python
  for (const k of PY_KEYWORDS) push(k, "keyword");
  for (const b of PY_BUILTINS) push(b, "function", "builtin");
  // Snippet sebagai item autocomplete (hanya jika tidak setelah '.')
  if (!afterDot) {
    for (const s of SNIPPETS) push(s.label, "text", s.detail, s.body);
  }

  return { from: word ? word.from : context.pos, options };
}

// ---------------------------------------------------------------------
// Tema OLED + Tomorrow-Night-Eighties — port 1:1 dari:
//   ace/theme-tomorrow_night_eighties.js  (warna token)
//   index.html lama                        (override OLED #050806, gutter,
//                                           active-line, selection, search)
// Tidak ada CSS !important; semua deklaratif via EditorView.theme.
// ---------------------------------------------------------------------

const zcodeTheme = EditorView.theme(
  {
    "&": {
      backgroundColor: "#050806",
      color: "#CCCCCC",
      fontSize: "14px", // font 14px — keputusan audit 2026-08 (sebelumnya 12px)
      height: "100%",
    },
    ".cm-scroller": {
      // fontFamily TIDAK statis di sini — dikendalikan fontFamilyCompartment
      // (bridge setFontFamily dari Settings, audit 2026-08).
      lineHeight: "1.5",
      overflow: "auto",
    },
    ".cm-content": { caretColor: "#39FF14", padding: "4px 0" },
    // Gutter ramping — port dari .ace_gutter + .ace_gutter-cell override
    ".cm-gutters": {
      backgroundColor: "#0A100D",
      // WCAG AA normal text: 5.09:1 terhadap background gutter.
      color: "#5A8F68",
      border: "none",
      borderRight: "1px solid #111612",
    },
    ".cm-lineNumbers .cm-gutterElement": {
      padding: "0 3px 0 6px",
      minWidth: "20px",
    },
    ".cm-activeLine": { backgroundColor: "rgba(27, 77, 46, 0.12)" },
    ".cm-activeLineGutter": { backgroundColor: "rgba(27, 77, 46, 0.18)" },
    ".cm-cursor, .cm-dropCursor": { borderLeftColor: "#39FF14" },
    ".cm-selectionBackground, &.cm-focused .cm-selectionBackground, ::selection":
      { backgroundColor: "rgba(27, 77, 46, 0.55) !important" },
    // Panel Find/Replace (@codemirror/search) — anti kasus gelap-di-gelap PR #6
    ".cm-panels": {
      backgroundColor: "#101613",
      color: "#D7DBE0",
      border: "1px solid #1B4D2E",
    },
    ".cm-panels input, .cm-panels textarea": {
      backgroundColor: "#050806",
      color: "#E6EDF3",
      border: "1px solid #1B4D2E",
      borderRadius: "4px",
    },
    ".cm-panels label": { color: "#8A9A90" },
    ".cm-button": {
      backgroundImage: "none",
      backgroundColor: "#1B4D2E",
      color: "#E6EDF3",
      border: "none",
      borderRadius: "4px",
    },
    ".cm-searchMatch": {
      backgroundColor: "rgba(27, 77, 46, 0.45)",
      outline: "1px solid #1B4D2E",
    },
    ".cm-searchMatch.cm-searchMatch-selected": {
      backgroundColor: "rgba(57, 255, 20, 0.25)",
    },
    // Tooltip (autocomplete/hover di masa depan) — sudah OLED sejak awal
    ".cm-tooltip": {
      backgroundColor: "#101613",
      color: "#D7DBE0",
      border: "1px solid #1B4D2E",
      borderRadius: "8px",
    },
    ".cm-tooltip .cm-tooltip-arrow:before": {
      borderTopColor: "#1B4D2E",
      borderBottomColor: "#1B4D2E",
    },
    ".cm-tooltip .cm-tooltip-arrow:after": {
      borderTopColor: "#101613",
      borderBottomColor: "#101613",
    },
    ".cm-matchingBracket": {
      backgroundColor: "rgba(27, 77, 46, 0.4)",
      outline: "1px solid #4D7A5A",
    },
    ".cm-selectionMatch": { outline: "1px solid #515151" },
    // Popup autocomplete (batch anti-sepi) — OLED penuh, maks 5 baris
    ".cm-tooltip.cm-tooltip-autocomplete": {
      backgroundColor: "#101613",
      border: "1px solid #1B4D2E",
      borderRadius: "8px",
      overflow: "hidden",
    },
    ".cm-tooltip-autocomplete ul": { maxHeight: "9.5em" },
    ".cm-tooltip-autocomplete ul li": {
      padding: "3px 8px",
      color: "#D7DBE0",
      fontSize: "12px",
    },
    ".cm-tooltip-autocomplete ul li[aria-selected]": {
      backgroundColor: "rgba(27, 77, 46, 0.55)",
      color: "#E6EDF3",
    },
    ".cm-completionDetail": { color: "#8A9A90", fontStyle: "normal" },
    ".cm-completionMatchedText": {
      textDecoration: "none",
      color: "#9ECE6A",
      fontWeight: "bold",
    },
  },
  { dark: true }
);

// Warna token Tomorrow-Night-Eighties (dari theme Ace asli):
// keyword #CC99CC, operator #66CCCC, string #99CC99, comment #999999,
// number/param #F99157, variable #6699CC/#F2777A, class/type #FFCC66.
const tneHighlight = HighlightStyle.define([
  { tag: tags.keyword, color: "#CC99CC" },
  { tag: tags.controlKeyword, color: "#CC99CC" },
  { tag: tags.moduleKeyword, color: "#CC99CC" },
  { tag: tags.operator, color: "#66CCCC" },
  { tag: tags.comment, color: "#999999" },
  { tag: tags.string, color: "#99CC99" },
  { tag: tags.number, color: "#F99157" },
  { tag: tags.bool, color: "#F99157" },
  { tag: tags.null, color: "#F99157" },
  { tag: tags.function(tags.variableName), color: "#6699CC" },
  { tag: tags.definition(tags.variableName), color: "#6699CC" },
  { tag: tags.variableName, color: "#F2777A" },
  { tag: tags.propertyName, color: "#6699CC" },
  { tag: tags.className, color: "#FFCC66" },
  { tag: tags.typeName, color: "#FFCC66" },
  { tag: tags.self, color: "#F2777A", fontStyle: "italic" },
  { tag: tags.invalid, color: "#CDCDCD", backgroundColor: "#F2777A" },
]);

// ---------------------------------------------------------------------
// State bridge
// ---------------------------------------------------------------------

let view = null;
let isSettingValue = false; // guard anti echo-loop (sama dengan versi Ace)

// Satu EditorView, tetapi setiap file memiliki EditorState sendiri. State memuat
// dokumen, selection, dan history CodeMirror; switch tab hanya menukar state,
// bukan mengganti seluruh isi di satu undo stack.
const documentStates = new Map();
let activeDocumentId = null;
let lastCanUndo = null;
let lastCanRedo = null;

function notifyHistoryState(force = false) {
  const canUndo = !!view && undoDepth(view.state) > 0;
  const canRedo = !!view && redoDepth(view.state) > 0;
  if (!force && canUndo === lastCanUndo && canRedo === lastCanRedo) return;
  lastCanUndo = canUndo;
  lastCanRedo = canRedo;
  // Pakai SATU callback editor yang sudah lama DEVICE VERIFIED. Memisahkan
  // status history ke method bridge baru terbukti tidak mengaktifkan tombol
  // pada WebView device walau history CM6 berubah.
  if (window.ZCODE && typeof window.ZCODE.onCodeChange === "function") {
    window.ZCODE.onCodeChange(
      activeDocumentId || "",
      view ? view.state.doc.toString() : "",
      canUndo,
      canRedo
    );
  }
}

// Gerbong A v1.0.19: Compartment lint gutter & whitespace — toggle live via
// bridge tanpa reload editor (kill-switch: OFF = perilaku lama persis).
const lintCompartment = new Compartment();
const whitespaceCompartment = new Compartment();

// F1.7 & F1.8: Compartment untuk toggle closeBrackets & highlightSelectionMatches
// via bridge Kotlin↔JS (reconfigure tanpa recreate editor — anti jank di HP ampas).
const closeBracketsCompartment = new Compartment();
const highlightSelectionMatchesCompartment = new Compartment();
// Audit 2026-08: compartment fontFamily — jenis font dipilih user di Settings
// (UI & editor; terminal tetap Monospace di sisi Compose). Default monospace.
const fontFamilyCompartment = new Compartment();

let currentLintEnabled = true;
let currentWhitespaceEnabled = false;
let currentCloseBracketsEnabled = true;
let currentSelectionMatchesEnabled = true;
let currentFontFamily = "monospace";

function buildState(doc) {
  return EditorState.create({
    doc,
    extensions: [
      lineNumbers(),
      highlightActiveLineGutter(),
      highlightActiveLine(),
      drawSelection(),
      dropCursor(),
      rectangularSelection(),
      history(),
      bracketMatching(),
      indentOnInput(),
      foldGutter(),
      EditorView.lineWrapping, // wrap: true (perilaku Ace lama)
      // Nama semantic untuk TalkBack/screen reader. Tanpa ini CM6 hanya
      // diumumkan sebagai textbox generik tanpa konteks.
      EditorView.contentAttributes.of({ "aria-label": "Editor kode Python" }),
      EditorState.tabSize.of(4),
      indentUnit.of("    "), // useSoftTabs + tabSize 4
      keymap.of([
        ...defaultKeymap,
        ...historyKeymap,
        ...searchKeymap,
        ...foldKeymap,
        indentWithTab,
      ]),
      python(),
      syntaxHighlighting(tneHighlight),
      // Autocomplete kasta 1+2 (S4): kata dokumen + keyword + builtins + snippet
      autocompletion({
        override: [zcodeCompletions],
        activateOnTyping: true,
        maxRenderedOptions: 5, // popup ringkas di layar HP
        optionClass: () => "zcode-completion-option",
      }),
      // Gerbong A: lint gutter default ON (ikon di gutter + underline merah;
      // tooltip muncul via tap di CM6 mobile). Data dari Kotlin Checker.
      lintCompartment.of(currentLintEnabled ? lintGutter() : []),
      whitespaceCompartment.of(
        currentWhitespaceEnabled ? highlightTrailingWhitespace() : []
      ),
      closeBracketsCompartment.of(
        currentCloseBracketsEnabled ? closeBrackets() : []
      ),
      highlightSelectionMatchesCompartment.of(
        currentSelectionMatchesEnabled ? highlightSelectionMatches() : []
      ),
      fontFamilyCompartment.of(
        EditorView.theme(
          { ".cm-scroller": { fontFamily: currentFontFamily } },
          { dark: true }
        )
      ),
      zcodeTheme,
      EditorView.updateListener.of((update) => {
        if (update.docChanged && !isSettingValue) {
          // force=true: code harus tetap dikirim meski boolean history sama.
          notifyHistoryState(true);
        }
      }),
      // Catatan konfigurasi vs Ace lama:
      // - showFoldWidgets: false  → foldGutter memang TIDAK dipasang
      // - showPrintMargin: false  → CM6 tidak punya print margin
      // - autocomplete: KASTA 1+2 AKTIF sejak batch anti-sepi (lihat
      //   autocompletion() di atas); paket lint tetap belum dipasang
      //   (fase Problems Panel nanti)
      // - animatedScroll: false → default CM6 memang tanpa animasi
    ],
  });
}

function initEditor() {
  const host = document.getElementById("editor");
  view = new EditorView({ state: buildState(""), parent: host });
}

// ---------------------------------------------------------------------
// Kontrak bridge — nama & semantik identik dengan versi Ace
// ---------------------------------------------------------------------

function replaceCurrentDocument(code) {
  if (!view || view.state.doc.toString() === code) return false;
  isSettingValue = true;
  try {
    view.dispatch({
      changes: { from: 0, to: view.state.doc.length, insert: code },
      // Transform programatik (beautify/rename symbol) = satu aksi Undo utuh.
      annotations: isolateHistory.of("full"),
    });
  } finally {
    isSettingValue = false;
  }
  notifyHistoryState(true);
  return true;
}

function openDocument(documentId, code) {
  if (!view) return false;
  const id = String(documentId || "__untitled__");
  const text = String(code ?? "");

  if (activeDocumentId === id) {
    return replaceCurrentDocument(text);
  }

  if (activeDocumentId !== null) {
    documentStates.set(activeDocumentId, view.state);
  }

  let nextState = documentStates.get(id);
  documentStates.delete(id); // state aktif hanya dimiliki EditorView
  activeDocumentId = id;

  if (!nextState) {
    nextState = buildState(text);
  } else if (nextState.doc.toString() !== text) {
    // File pernah dibuka tetapi berubah lewat operasi eksternal. Pertahankan
    // history file itu dan jadikan replacement satu event terisolasi.
    nextState = nextState.update({
      changes: { from: 0, to: nextState.doc.length, insert: text },
      annotations: isolateHistory.of("full"),
    }).state;
  }

  isSettingValue = true;
  try {
    view.setState(nextState);
  } finally {
    isSettingValue = false;
  }
  notifyHistoryState(true);
  return true;
}

function dropDocument(documentId) {
  const id = String(documentId || "");
  documentStates.delete(id);
  if (activeDocumentId === id) activeDocumentId = null;
  notifyHistoryState(true);
}

function renameDocument(oldId, newId) {
  const oldKey = String(oldId || "");
  const newKey = String(newId || "");
  if (!oldKey || !newKey || oldKey === newKey) return;
  if (documentStates.has(oldKey)) {
    documentStates.set(newKey, documentStates.get(oldKey));
    documentStates.delete(oldKey);
  }
  if (activeDocumentId === oldKey) activeDocumentId = newKey;
}

function clearDocumentStates() {
  documentStates.clear();
  activeDocumentId = null;
  if (view) view.setState(buildState(""));
  notifyHistoryState(true);
}

// Compatibility untuk pemanggil lama: replacement pada file aktif adalah satu
// aksi Undo. File switch WAJIB memakai openDocument(id, code).
function setCode(code) {
  return replaceCurrentDocument(String(code ?? ""));
}

function getCode() {
  return view ? view.state.doc.toString() : "";
}

function insertText(text) {
  if (!view) return;
  // Ace: editor.insert(text) mengganti selection dengan text, lalu focus
  view.dispatch(view.state.replaceSelection(text));
  view.focus();
}

function undo() {
  if (!view) return false;
  const changed = cmUndo(view);
  notifyHistoryState(true);
  return changed;
}

function redo() {
  if (!view) return false;
  const changed = cmRedo(view);
  notifyHistoryState(true);
  return changed;
}

// Plugin: Duplicate Active Line(s) — semantik Ace:
// duplikat baris selection (start..end), insert setelah baris end,
// selection/kursor tidak berpindah.
function duplicateRows() {
  if (!view) return;
  const state = view.state;
  const range = state.selection.main;
  const startLine = state.doc.lineAt(range.from).number;
  const endLine = state.doc.lineAt(range.to).number;
  const lines = [];
  for (let n = startLine; n <= endLine; n++) {
    lines.push(state.doc.line(n).text);
  }
  if (!lines.length) return;
  // Sisipkan di akhir baris end (== kolom 0 baris end+1 pada Ace)
  view.dispatch({
    changes: { from: state.doc.line(endLine).to, insert: "\n" + lines.join("\n") },
  });
}

// Plugin: Toggle Line Comment — byte-identik dengan implementasi Ace:
// semua baris sudah '#' → hapus 1 '#' + spasi opsional; kalau tidak,
// tambahkan '# ' setelah indent (baris kosong pun jadi '# ').
function toggleCommentLines() {
  if (!view) return;
  const state = view.state;
  const range = state.selection.main;
  const startL = state.doc.lineAt(range.from);
  const endL = state.doc.lineAt(range.to);
  const lines = [];
  for (let n = startL.number; n <= endL.number; n++) {
    lines.push(state.doc.line(n).text);
  }
  if (!lines.length) return;

  const allCommented = lines.every((l) => /^\s*#/.test(l));
  const out = lines.map((l) => {
    if (allCommented) {
      return l.replace(/^(\s*)#\s?/, "$1");
    }
    const indent = (l.match(/^(\s*)/) || ["", ""])[1];
    return indent + "# " + l.replace(/^\s*/, "");
  });

  const newText = out.join("\n");
  view.dispatch({
    changes: { from: startL.from, to: endL.to, insert: newText },
    // Ace clearSelection() setelah replace → kursor di akhir replacement
    selection: { anchor: startL.from + newText.length },
  });
}

// BARU (migrasi CM6): akses Find — dulu hanya via mobile-menu Ace yang
// kini tidak ada. Dipanggil dari item palette Compose "Find in File".
function openFind() {
  if (view) openSearchPanel(view);
}

// F1.9: Sort Lines — urutkan baris selection secara alfabetis (case-insensitive).
// Semantik: sort baris yang dipilih, selection/kursor tidak berpindah.
function sortLines() {
  if (!view) return;
  const state = view.state;
  const range = state.selection.main;
  const startLine = state.doc.lineAt(range.from);
  const endLine = state.doc.lineAt(range.to);
  const lines = [];
  for (let n = startLine.number; n <= endLine.number; n++) {
    lines.push(state.doc.line(n).text);
  }
  if (!lines.length) return;
  // Sort case-insensitive, tapi pertahankan urutan relatif yang sama (stable sort).
  lines.sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
  const newText = lines.join("\n");
  view.dispatch({
    changes: { from: startLine.from, to: endLine.to, insert: newText },
    selection: { anchor: startLine.from + newText.length },
  });
}

// F1.9: Change Case — UPPER / lower / Title Case pada selection.
// type: "upper" | "lower" | "title"
function changeCase(type) {
  if (!view) return;
  const state = view.state;
  const range = state.selection.main;
  const selectedText = state.doc.sliceString(range.from, range.to);
  if (!selectedText) return;
  let transformed;
  switch (type) {
    case "upper":
      transformed = selectedText.toUpperCase();
      break;
    case "lower":
      transformed = selectedText.toLowerCase();
      break;
    case "title":
      transformed = selectedText.replace(/\w\S*/g, (txt) =>
        txt.charAt(0).toUpperCase() + txt.substring(1).toLowerCase()
      );
      break;
    default:
      return;
  }
  view.dispatch({
    changes: { from: range.from, to: range.to, insert: transformed },
    selection: { anchor: range.from, head: range.from + transformed.length },
  });
}

// F1.9: Trim Now — buang spasi akhir tiap baris di selection (manual, tanpa Run).
function trimNow() {
  if (!view) return;
  const state = view.state;
  const range = state.selection.main;
  const startLine = state.doc.lineAt(range.from);
  const endLine = state.doc.lineAt(range.to);
  const lines = [];
  for (let n = startLine.number; n <= endLine.number; n++) {
    lines.push(state.doc.line(n).text);
  }
  if (!lines.length) return;
  const out = lines.map((l) => l.trimEnd());
  const newText = out.join("\n");
  view.dispatch({
    changes: { from: startLine.from, to: endLine.to, insert: newText },
    selection: { anchor: startLine.from + newText.length },
  });
}

function reconfigureEveryDocument(compartment, extensionFactory) {
  if (view) {
    view.dispatch({ effects: compartment.reconfigure(extensionFactory()) });
  }
  for (const [id, state] of documentStates) {
    documentStates.set(
      id,
      state.update({ effects: compartment.reconfigure(extensionFactory()) }).state
    );
  }
}

// F1.7: Toggle auto-close brackets di SEMUA state file, bukan hanya tab aktif.
function setCloseBrackets(enabled) {
  currentCloseBracketsEnabled = !!enabled;
  reconfigureEveryDocument(
    closeBracketsCompartment,
    () => (enabled ? closeBrackets() : [])
  );
}

// F1.8: Toggle selection match highlight (CM6) — reconfigure via compartment.
function setHighlightSelectionMatches(enabled) {
  currentSelectionMatchesEnabled = !!enabled;
  reconfigureEveryDocument(
    highlightSelectionMatchesCompartment,
    () => (enabled ? highlightSelectionMatches() : [])
  );
}

// Gerbong A v1.0.19: terima diagnostik dari Checker Kotlin.
// json = [{from_line, to_line, column?, severity: "error|warning|info",
// message}] (1-based line). Konversi ke offset dokumen di sini; baris di
// luar dokumen di-clamp (kode bisa berubah selama debounce 800ms Kotlin).
function setDiagnostics(json) {
  if (!view) return;
  let items = [];
  try {
    items = JSON.parse(json) || [];
  } catch (e) {
    return; // JSON rusak = abaikan, jangan matikan editor
  }
  const doc = view.state.doc;
  const diags = [];
  for (const it of items) {
    const n = Math.min(Math.max(1, it.from_line || 1), doc.lines);
    const line = doc.line(n);
    // Kolom (0-based dari Checker) → offset; default seluruh baris.
    let from = line.from;
    let to = line.to;
    if (typeof it.column === "number" && it.column >= 0) {
      from = Math.min(line.from + it.column, line.to);
      // minimal 1 karakter supaya underline terlihat (baris kosong: biarkan 0)
      to = Math.min(from + 1, line.to);
      if (to === from) { from = line.from; to = line.to; }
    }
    diags.push({
      from,
      to,
      severity: it.severity === "warning" ? "warning" : it.severity === "info" ? "info" : "error",
      message: String(it.message || ""),
    });
  }
  view.dispatch(cmSetDiagnostics(view.state, diags));
}

// Gerbong A: toggle lint gutter (kill-switch — OFF = tanpa gutter & tanpa
// underline, diagnostik dikosongkan supaya tak ada sisa merah).
function setLintEnabled(enabled) {
  currentLintEnabled = !!enabled;
  reconfigureEveryDocument(
    lintCompartment,
    () => (enabled ? lintGutter() : [])
  );
  if (!enabled && view) view.dispatch(cmSetDiagnostics(view.state, []));
}

// A2: toggle whitespace guard (trailing whitespace highlight).
function setWhitespaceEnabled(enabled) {
  currentWhitespaceEnabled = !!enabled;
  reconfigureEveryDocument(
    whitespaceCompartment,
    () => (enabled ? highlightTrailingWhitespace() : [])
  );
}

// Audit 2026-08: jenis font (UI & editor) — Kotlin mengirim CSS font-family
// (mis. "'ZCodeFiraCode', monospace"); @font-face di-inject Kotlin via <style>.
// Gutter ikut karena berada di dalam .cm-scroller (inherit).
function setFontFamily(cssFamily) {
  currentFontFamily = String(cssFamily || "monospace");
  reconfigureEveryDocument(
    fontFamilyCompartment,
    () => EditorView.theme(
      { ".cm-scroller": { fontFamily: currentFontFamily } },
      { dark: true }
    )
  );
}

// BARU (batch anti-sepi F2): lompat ke baris n (1-based, di-clamp).
// Dipakai 🔍 mode Line, hasil mode Find, dan TODO Extractor — 1 fungsi
// 3 pemakai (lihat PLAN_BATCH_ANTI_SEPI.md §3 F2).
function gotoLine(n) {
  if (!view) return;
  const lineCount = view.state.doc.lines;
  const line = Math.max(1, Math.min(lineCount, Math.floor(Number(n)) || 1));
  const l = view.state.doc.line(line);
  view.dispatch({ selection: { anchor: l.from }, scrollIntoView: true });
  view.focus();
}

// ---------------------------------------------------------------------
// Init + handshake onEditorReady (fix PR #5 — WAJIB dipertahankan)
// ---------------------------------------------------------------------

try {
  initEditor();
} catch (err) {
  // Feature-guard WebView tua / environment rusak: tampilkan pesan ramah,
  // jangan layar putih. Bridge jadi no-op aman (semua cek `if (!view)`).
  const host = document.getElementById("editor");
  if (host) {
    host.style.color = "#FF4B4B";
    host.style.padding = "16px";
    host.style.fontFamily = "monospace";
    host.style.fontSize = "14px";
    host.textContent =
      "⚠ Editor gagal dimuat (WebView mungkin terlalu lama). " +
      "Coba update 'Android System WebView' lalu buka ulang ZCODE. (" +
      (err && err.message ? err.message : "unknown error") +
      ")";
  }
}

// Expose ke window (Kotlin memanggil via evaluateJavascript)
window.setCode = setCode;
window.openDocument = openDocument;
window.dropDocument = dropDocument;
window.renameDocument = renameDocument;
window.clearDocumentStates = clearDocumentStates;
window.getCode = getCode;
window.insertText = insertText;
window.undo = undo;
window.redo = redo;
window.duplicateRows = duplicateRows;
window.toggleCommentLines = toggleCommentLines;
window.openFind = openFind;
window.gotoLine = gotoLine;
window.setCloseBrackets = setCloseBrackets;
window.setHighlightSelectionMatches = setHighlightSelectionMatches;
window.setFontFamily = setFontFamily;
window.sortLines = sortLines;
window.changeCase = changeCase;
window.trimNow = trimNow;
window.setDiagnostics = setDiagnostics;
window.setLintEnabled = setLintEnabled;
window.setWhitespaceEnabled = setWhitespaceEnabled;

// Handshake — dipanggil bahkan jika init gagal, agar Kotlin tidak hang.
notifyHistoryState(true);
if (window.ZCODE && typeof window.ZCODE.onEditorReady === "function") {
  window.ZCODE.onEditorReady();
}
