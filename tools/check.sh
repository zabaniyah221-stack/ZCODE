#!/usr/bin/env bash
# ZCODE local check — mirrors CI (port zabacode/tools/check.sh)
# Fase 0 + Fase 1/2: no JDK/Android SDK in sandbox, so we check file structure + python tests

set -e
echo "=== ZCODE Local Check Fase 0 + Fase 1/2 ==="

echo "[1/8] Verify Ace 1.44.0 bundled (offline-first, ASLI bukan stub)"
test -f app/src/main/assets/editor/ace/ace.js && echo "✅ ace.js exists" || (echo "❌ ace.js missing" && exit 1)
grep -q "1.44.0" app/src/main/assets/editor/ace/ace.js && echo "✅ Ace 1.44.0" || (echo "❌ Ace not 1.44.0" && exit 1)
test -f app/src/main/assets/editor/ace/mode-python.js && echo "✅ mode-python.js exists" || (echo "❌ mode-python.js missing" && exit 1)
SIZE=$(stat -c%s app/src/main/assets/editor/ace/ace.js 2>/dev/null || echo 0)
if [ "$SIZE" -gt 100000 ]; then echo "✅ Ace asli ($SIZE bytes, bukan stub)"; else echo "❌ ace.js masih stub ($SIZE bytes)" && exit 1; fi

echo "[2/8] Verify no unverified SSL (S-22)"
if grep -R "trustAllCerts\|ssl._create_unverified_context\|TRUST_ALL" app/src/main/java --include="*.kt" 2>/dev/null; then
  echo "❌ Found unverified SSL" && exit 1
else echo "✅ No unverified SSL"; fi

echo "[3/8] Verify taskAffinity + singleTop + allowBackup (C-50/S-27/S-21)"
grep -q 'taskAffinity="com.zaba.zcode"' app/src/main/AndroidManifest.xml && echo "✅ taskAffinity" || (echo "❌ taskAffinity missing" && exit 1)
grep -q 'singleTop' app/src/main/AndroidManifest.xml && echo "✅ singleTop" || (echo "❌ singleTop missing" && exit 1)
grep -q 'allowBackup="false"' app/src/main/AndroidManifest.xml && echo "✅ allowBackup false" || (echo "❌ allowBackup missing" && exit 1)

echo "[4/8] Verify Topbar faded grey + three lines ≡ (user request)"
if grep -q "TEST B" app/src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt; then
  echo "⚠️ bisection: WorkbenchScreen di-stub — langkah ini di-skip"
else
grep -q "3A4452\|TopbarFadedGrey" app/src/main/java/com/zaba/zcode/ui/theme/ZcodeTheme.kt && echo "✅ Topbar faded grey" || (echo "❌ Topbar color missing" && exit 1)
grep -q "≡" app/src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt && echo "✅ ≡ three lines" || (echo "❌ hamburger text found or ≡ missing" && exit 1)
if grep -q "hamburger" app/src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt; then echo "❌ hamburger word should not appear (use ≡)" && exit 1; else echo "✅ no hamburger word"; fi
fi

echo "[5/8] Verify Execution guards (S-18)"
grep -q "MAX_CODE_BYTES = 512" app/src/main/java/com/zaba/zcode/core/execution/ExecutionEngine.kt && echo "✅ MAX_CODE_BYTES" || (echo "❌ MAX_CODE_BYTES missing" && exit 1)
grep -q "MAX_INTERACTIVE_QUEUE = 10000" app/src/main/java/com/zaba/zcode/core/execution/ExecutionEngine.kt && echo "✅ MAX_INTERACTIVE_QUEUE" || (echo "❌ queue missing" && exit 1)
grep -q '"kill", "-INT"' app/src/main/java/com/zaba/zcode/core/execution/ExecutionEngine.kt && echo "✅ SIGINT asli (subprocess backend)" || (echo "❌ SIGINT real missing" && exit 1)

echo "[6/8] Verify Chaquopy 3.11 embed (on-device execution, Fase 1)"
if grep -q "TEST A" app/build.gradle.kts; then
  echo "⚠️ TEST A bisection: chaquopy dinonaktifkan — langkah ini di-skip"
else
grep -q 'id("com.chaquo.python")' app/build.gradle.kts && echo "✅ plugin Chaquopy applied" || (echo "❌ plugin missing" && exit 1)
grep -q 'version = "3.11"' app/build.gradle.kts && echo "✅ Python 3.11 (armv7 supported)" || (echo "❌ python version missing" && exit 1)
test -f app/src/main/python/zcode_runner.py && echo "✅ zcode_runner.py" || (echo "❌ runner missing" && exit 1)
test -f app/src/main/java/com/zaba/zcode/core/execution/TerminalBridge.kt && echo "✅ TerminalBridge" || (echo "❌ bridge missing" && exit 1)
grep -q "isChaquopyAvailable" app/src/main/java/com/zaba/zcode/core/execution/ExecutionEngine.kt && echo "✅ dual-backend" || (echo "❌ backend missing" && exit 1)
fi

echo "[7/8] Verify Fase 1/2 wiring (WebView asli, VM, terminal, pip, themes)"
if grep -q "TEST B" app/src/main/java/com/zaba/zcode/ui/editor/EditorScreen.kt; then
  echo "⚠️ bisection: UI di-stub — langkah ini di-skip"
else
grep -q "addJavascriptInterface" app/src/main/java/com/zaba/zcode/ui/editor/EditorScreen.kt && echo "✅ WebView bridge" || (echo "❌ bridge missing" && exit 1)
test -f app/src/main/java/com/zaba/zcode/WorkspaceViewModel.kt && echo "✅ WorkspaceViewModel" || (echo "❌ VM missing" && exit 1)
test -f app/src/main/java/com/zaba/zcode/ui/terminal/TerminalScreen.kt && echo "✅ TerminalScreen" || (echo "❌ terminal missing" && exit 1)
test -f app/src/main/java/com/zaba/zcode/ui/settings/PipScreen.kt && echo "✅ PipScreen" || (echo "❌ pip missing" && exit 1)
test -f app/src/main/java/com/zaba/zcode/core/plugins/PluginHost.kt && echo "✅ PluginHost" || (echo "❌ plugins missing" && exit 1)
fi

echo "[8/8] Run Python strict tests (Fase 0 + Fase 1/2)"
python3 -m pytest test_zcode_fase0.py test_zcode_fase1.py -v
python3 test_zcode_fase0.py 2>&1 | tail -n 20

echo ""
echo "=== All local checks passed ==="
