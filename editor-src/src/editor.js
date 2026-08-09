// =====================================================================
// ZCODE — CodeMirror 6 editor (pengganti Ace 1.44.0, migrasi 2026-08)
// Kontrak bridge dipertahankan 1:1 dengan versi Ace (lihat docs/MIGRASI_CM6.md §3):
//   setCode, getCode, insertText, undo, redo, duplicateRows,
//   toggleCommentLines, onEditorReady (JS→Kotlin), ZCODE.onCodeChange
//   BARU: openFind() — pengganti akses Find yang tadinya via mobile-menu Ace.
// Prinsip: jujur & teliti — semantik fungsi harus identik dengan versi Ace.
// =====================================================================

import { EditorState } from "@codemirror/state";
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
  undo as cmUndo,
  redo as cmRedo,
} from "@codemirror/commands";
import {
  indentUnit,
  syntaxHighlighting,
  bracketMatching,
  indentOnInput,
  HighlightStyle,
} from "@codemirror/language";
import { search, searchKeymap, openSearchPanel } from "@codemirror/search";
import { tags } from "@lezer/highlight";
import { python } from "@codemirror/lang-python";

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
      fontSize: "12px", // font 12px — keputusan tim (tidak berubah)
      height: "100%",
    },
    ".cm-scroller": {
      fontFamily: "monospace",
      lineHeight: "1.5",
      overflow: "auto",
    },
    ".cm-content": { caretColor: "#39FF14", padding: "4px 0" },
    // Gutter ramping — port dari .ace_gutter + .ace_gutter-cell override
    ".cm-gutters": {
      backgroundColor: "#0A100D",
      color: "#4D7A5A",
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
      EditorView.lineWrapping, // wrap: true (perilaku Ace lama)
      EditorState.tabSize.of(4),
      indentUnit.of("    "), // useSoftTabs + tabSize 4
      keymap.of([
        ...defaultKeymap,
        ...historyKeymap,
        ...searchKeymap,
        indentWithTab,
      ]),
      python(),
      syntaxHighlighting(tneHighlight),
      zcodeTheme,
      EditorView.updateListener.of((update) => {
        if (update.docChanged && !isSettingValue && window.ZCODE) {
          window.ZCODE.onCodeChange(update.state.doc.toString());
        }
      }),
      // Catatan konfigurasi vs Ace lama:
      // - showFoldWidgets: false  → foldGutter memang TIDAK dipasang
      // - showPrintMargin: false  → CM6 tidak punya print margin
      // - enableBasicAutocompletion: false → paket autocomplete sengaja
      //   tidak di-import (fase LSP nanti, lihat docs/MIGRASI_CM6.md §10)
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

function setCode(code) {
  if (!view) return;
  isSettingValue = true;
  view.dispatch({
    changes: { from: 0, to: view.state.doc.length, insert: code },
  });
  isSettingValue = false;
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
  if (view) cmUndo(view);
}

function redo() {
  if (view) cmRedo(view);
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
    host.style.fontSize = "12px";
    host.textContent =
      "⚠ Editor gagal dimuat (WebView mungkin terlalu lama). " +
      "Coba update 'Android System WebView' lalu buka ulang ZCODE. (" +
      (err && err.message ? err.message : "unknown error") +
      ")";
  }
}

// Expose ke window (Kotlin memanggil via evaluateJavascript)
window.setCode = setCode;
window.getCode = getCode;
window.insertText = insertText;
window.undo = undo;
window.redo = redo;
window.duplicateRows = duplicateRows;
window.toggleCommentLines = toggleCommentLines;
window.openFind = openFind;

// Handshake — dipanggil bahkan jika init gagal, agar Kotlin tidak hang
// menunggu (setCode dkk. aman sebagai no-op).
if (window.ZCODE && typeof window.ZCODE.onEditorReady === "function") {
  window.ZCODE.onEditorReady();
}
