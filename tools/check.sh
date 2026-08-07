#!/usr/bin/env bash
# ZCODE local check — mirrors CI (port zabacode/tools/check.sh)
# Fase 0: no JDK/Android SDK in sandbox, so we check file structure + python tests

set -e
echo "=== ZCODE Local Check Fase 0 ==="

echo "[1/6] Verify Ace 1.44.0 bundled (offline-first)"
test -f app/src/main/assets/editor/ace/ace.js && echo "✅ ace.js exists" || (echo "❌ ace.js missing" && exit 1)
grep -q "1.44.0" app/src/main/assets/editor/ace/ace.js && echo "✅ Ace 1.44.0" || (echo "❌ Ace not 1.44.0" && exit 1)
test -f app/src/main/assets/editor/ace/mode-python.js && echo "✅ mode-python.js exists" || (echo "❌ mode-python.js missing" && exit 1)

echo "[2/6] Verify no unverified SSL (S-22)"
if grep -R "trustAllCerts\|ssl._create_unverified_context\|TRUST_ALL" app/src/main/java --include="*.kt" 2>/dev/null; then
  echo "❌ Found unverified SSL" && exit 1
else echo "✅ No unverified SSL"; fi

echo "[3/6] Verify taskAffinity + singleTop + allowBackup (C-50/S-27/S-21)"
grep -q 'taskAffinity="com.zaba.zcode"' app/src/main/AndroidManifest.xml && echo "✅ taskAffinity" || (echo "❌ taskAffinity missing" && exit 1)
grep -q 'singleTop' app/src/main/AndroidManifest.xml && echo "✅ singleTop" || (echo "❌ singleTop missing" && exit 1)
grep -q 'allowBackup="false"' app/src/main/AndroidManifest.xml && echo "✅ allowBackup false" || (echo "❌ allowBackup missing" && exit 1)

echo "[4/6] Verify Topbar faded grey + three lines ≡ (user request)"
grep -q "3A4452\|TopbarFadedGrey" app/src/main/java/com/zaba/zcode/ui/theme/ZcodeTheme.kt && echo "✅ Topbar faded grey" || (echo "❌ Topbar color missing" && exit 1)
grep -q "≡" app/src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt && echo "✅ ≡ three lines" || (echo "❌ hamburger text found or ≡ missing" && exit 1)
if grep -q "hamburger" app/src/main/java/com/zaba/zcode/ui/workbench/WorkbenchScreen.kt; then echo "❌ hamburger word should not appear (use ≡)" && exit 1; else echo "✅ no hamburger word"; fi

echo "[5/6] Verify Execution guards (S-18)"
grep -q "MAX_CODE_BYTES = 512" app/src/main/java/com/zaba/zcode/core/execution/ExecutionEngine.kt && echo "✅ MAX_CODE_BYTES" || (echo "❌ MAX_CODE_BYTES missing" && exit 1)
grep -q "MAX_INTERACTIVE_QUEUE = 10000" app/src/main/java/com/zaba/zcode/core/execution/ExecutionEngine.kt && echo "✅ MAX_INTERACTIVE_QUEUE" || (echo "❌ queue missing" && exit 1)

echo "[6/6] Run Python strict tests (Fase 0)"
python3 -m pytest test_zcode_fase0.py -v
python3 test_zcode_fase0.py 2>&1 | tail -n 20

echo ""
echo "=== All local checks passed ==="
