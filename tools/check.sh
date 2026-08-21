#!/usr/bin/env bash
# ZCODE local check — mirrors CI (port zabacode/tools/check.sh)
# Fase 0 + Fase 1/2 + redesign 2026-08: no JDK/Android SDK in sandbox, so we check
# file structure + python tests + lexical sanity Kotlin (nested comment trap)

set -e
echo "=== ZCODE Local Check Fase 0 + Fase 1/2 + redesign ==="

echo "[1/10] Verify CodeMirror 6 bundled (offline-first, ASLI bukan stub — migrasi 2026-08 dari Ace)"
BUNDLE=app/src/main/assets/editor/codemirror.bundle.js
test -f $BUNDLE && echo "✅ codemirror.bundle.js exists" || (echo "❌ codemirror.bundle.js missing" && exit 1)
SIZE=$(stat -c%s $BUNDLE 2>/dev/null || echo 0)
if [ "$SIZE" -gt 100000 ]; then echo "✅ CM6 asli ($SIZE bytes, bukan stub)"; else echo "❌ bundle masih stub ($SIZE bytes)" && exit 1; fi
grep -q "setCode" $BUNDLE && grep -q "onEditorReady" $BUNDLE && echo "✅ kontrak bridge (setCode + onEditorReady)" || (echo "❌ kontrak bridge hilang" && exit 1)
grep -q "nonlocal" $BUNDLE && echo "✅ lang-python (Lezer) terbundle" || (echo "❌ lang-python missing" && exit 1)
grep -q "codemirror.bundle.js" app/src/main/assets/editor/index.html && echo "✅ index.html me-load bundle" || (echo "❌ index.html tidak me-load bundle" && exit 1)
if grep -qiE "cdnjs|unpkg|jsdelivr" $BUNDLE; then echo "❌ CDN reference ditemukan — offline-first violation" && exit 1; else echo "✅ no CDN"; fi
grep -q "gotoLine" $BUNDLE && grep -q "frozenset" $BUNDLE && echo "✅ kontrak gotoLine + autocomplete terbundle" || (echo "❌ bundle kehilangan gotoLine/autocomplete" && exit 1)
test -f app/src/main/python/zcode_plugins.py && grep -q "PORTED FROM ZABACODE (GPLv3)" app/src/main/python/zcode_plugins.py && echo "✅ zcode_plugins.py + provenance header" || (echo "❌ zcode_plugins.py / provenance hilang" && exit 1)

echo "[2/10] Verify npm/editor supply-chain invariants (ChainDrop defense-in-depth)"
python3 tools/npm_supply_chain_check.py

echo "[3/10] Kotlin lexical sanity — nested block comment trap (insiden CI 2026-08-09: Unclosed comment)"
python3 tools/kotlin_sanity_check.py || (echo "❌ lexical sanity gagal — cek block comment/string di file di atas" && exit 1)

echo "[4/10] Verify no unverified SSL (S-22)"
if grep -R "trustAllCerts\|ssl._create_unverified_context\|TRUST_ALL" app/src/main/java --include="*.kt" 2>/dev/null; then
  echo "❌ Found unverified SSL" && exit 1
else echo "✅ No unverified SSL"; fi

echo "[5/10] Verify taskAffinity + singleTop + allowBackup (C-50/S-27/S-21)"
grep -q 'taskAffinity="com.zaba.zcode"' app/src/main/AndroidManifest.xml && echo "✅ taskAffinity" || (echo "❌ taskAffinity missing" && exit 1)
grep -q 'singleTop' app/src/main/AndroidManifest.xml && echo "✅ singleTop" || (echo "❌ singleTop missing" && exit 1)
grep -q 'allowBackup="false"' app/src/main/AndroidManifest.xml && echo "✅ allowBackup false" || (echo "❌ allowBackup missing" && exit 1)

echo "[6/10] Verify Topbar faded grey + drawer swipe-only (audit 2026-08; ≡ dihapus)"
grep -q "3A4452\|TopbarFadedGrey" app/src/main/java/com/zaba/zcode/ui/theme/ZcodeTheme.kt && echo "✅ Topbar faded grey" || (echo "❌ Topbar color missing" && exit 1)
grep -q "DRAWER-SWIPE-ONLY" app/src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt && echo "✅ drawer swipe-only marker" || (echo "❌ DRAWER-SWIPE-ONLY marker missing" && exit 1)
if grep -q "hamburger" app/src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt; then echo "❌ hamburger word should not appear" && exit 1; else echo "✅ no hamburger word"; fi

echo "[7/10] Verify Execution guards (S-18)"
grep -q "MAX_CODE_BYTES = 512" app/src/main/java/com/zaba/zcode/core/execution/ExecutionEngine.kt && echo "✅ MAX_CODE_BYTES" || (echo "❌ MAX_CODE_BYTES missing" && exit 1)
grep -q "MAX_INTERACTIVE_QUEUE = 10000" app/src/main/java/com/zaba/zcode/core/execution/ExecutionEngine.kt && echo "✅ MAX_INTERACTIVE_QUEUE" || (echo "❌ queue missing" && exit 1)
grep -q '"kill", "-INT"' app/src/main/java/com/zaba/zcode/core/execution/ExecutionEngine.kt && echo "✅ SIGINT asli (subprocess backend)" || (echo "❌ SIGINT real missing" && exit 1)

echo "[8/10] Verify Chaquopy 3.11 embed (on-device execution, Fase 1)"
grep -q 'id("com.chaquo.python")' app/build.gradle.kts && echo "✅ plugin Chaquopy applied" || (echo "❌ plugin missing" && exit 1)
grep -q 'version = "3.11"' app/build.gradle.kts && echo "✅ Python 3.11 (armv7 supported)" || (echo "❌ python version missing" && exit 1)
test -f app/src/main/python/zcode_runner.py && echo "✅ zcode_runner.py" || (echo "❌ runner missing" && exit 1)
test -f app/src/main/java/com/zaba/zcode/core/execution/TerminalBridge.kt && echo "✅ TerminalBridge" || (echo "❌ bridge missing" && exit 1)
grep -q "isChaquopyAvailable" app/src/main/java/com/zaba/zcode/core/execution/ExecutionEngine.kt && echo "✅ dual-backend" || (echo "❌ backend missing" && exit 1)

echo "[9/10] Verify Fase 1/2 wiring (WebView asli, VM, terminal, pip, themes)"
grep -q "addJavascriptInterface" app/src/main/java/com/zaba/zcode/ui/editor/EditorScreen.kt && echo "✅ WebView bridge" || (echo "❌ bridge missing" && exit 1)
test -f app/src/main/java/com/zaba/zcode/core/plugins/PluginRegistry.kt && echo "✅ PluginRegistry (batch anti-sepi)" || (echo "❌ PluginRegistry missing" && exit 1)
test -f app/src/main/java/com/zaba/zcode/WorkspaceViewModel.kt && echo "✅ WorkspaceViewModel" || (echo "❌ VM missing" && exit 1)
test -f app/src/main/java/com/zaba/zcode/ui/terminal/TerminalScreen.kt && echo "✅ TerminalScreen" || (echo "❌ terminal missing" && exit 1)
test -f app/src/main/java/com/zaba/zcode/ui/settings/PipScreen.kt && echo "✅ PipScreen" || (echo "❌ pip missing" && exit 1)
test -f app/src/main/java/com/zaba/zcode/core/plugins/PluginHost.kt && echo "✅ PluginHost" || (echo "❌ plugins missing" && exit 1)

echo "[10/10] Run Python strict tests (Fase 0 + Fase 1/2 + redesign Fase 3 + Package Runtime + Kotlin guards)"
pytest test_zcode_fase0.py test_zcode_fase1.py test_zcode_fase3.py test_zcode_package_runtime.py test_zcode_kotlin_guards.py test_zcode_security_guards.py test_native_runtime_rebirth_guards.py test_zcode_rc_variant.py -v
python3 test_zcode_fase0.py 2>&1 | tail -n 20

echo ""
echo "=== All local checks passed ==="
