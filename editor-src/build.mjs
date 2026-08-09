// ZCODE — generator bundle CodeMirror 6
// Output: app/src/main/assets/editor/codemirror.bundle.js (single-file IIFE, DI-COMMIT).
// Prinsip: offline-first, tanpa CDN, target es2018 (WebView Android 8 / minSdk 26).
import * as esbuild from "esbuild";
import { statSync } from "node:fs";

const outfile = "../app/src/main/assets/editor/codemirror.bundle.js";

await esbuild.build({
  entryPoints: ["src/editor.js"],
  bundle: true,
  minify: true,
  format: "iife",
  target: "es2018",
  outfile,
  banner: {
    js:
      "/* ZCODE CodeMirror 6 bundle — GENERATED FILE, JANGAN EDIT MANUAL.\n" +
      "   Regenerasi: cd editor-src && npm ci && npm run build\n" +
      "   Offline-first: tidak ada CDN/fetch; semua kode ada di file ini. */",
  },
});

const size = statSync(outfile).size;
console.log(`✅ Bundle ditulis: ${outfile} (${size} bytes)`);
if (size < 100000) {
  console.error("❌ Bundle terlalu kecil (<100KB) — kemungkinan gagal/stub");
  process.exit(1);
}
