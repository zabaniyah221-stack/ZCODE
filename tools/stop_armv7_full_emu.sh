#!/usr/bin/env bash
# Stop only the ZCODE full ARMv7 emulator; leave bionic311 probes untouched.
set -euo pipefail

ROOT=${ZCODE_ARMV7_FULL_ROOT:-/var/tmp/zcode-armv7-full}
ADB="$ROOT/sdk/platform-tools/adb"
PATTERN="$ROOT/sdk/emulator/emulator64-arm"

if [[ -x $ADB ]]; then
  timeout 10 "$ADB" -s emulator-5554 emu kill >/dev/null 2>&1 || true
fi

for _ in {1..20}; do
  pgrep -f "$PATTERN" >/dev/null || { echo "✅ emulator berhenti"; exit 0; }
  sleep 0.25
done

pkill -TERM -f "$PATTERN" 2>/dev/null || true
sleep 1
pkill -KILL -f "$PATTERN" 2>/dev/null || true

echo "✅ emulator dihentikan paksa"
