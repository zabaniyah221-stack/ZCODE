#!/usr/bin/env bash
# Wait for full boot, verify ABI/API, optionally install an emulator-only APK.
set -euo pipefail

ROOT=${ZCODE_ARMV7_FULL_ROOT:-/var/tmp/zcode-armv7-full}
ADB="$ROOT/sdk/platform-tools/adb"
APK=${1:-}
SERIAL=${ANDROID_SERIAL:-emulator-5554}

[[ -x $ADB ]] || { echo "❌ adb tidak ada; jalankan setup" >&2; exit 1; }

# Host-side deadline first, then guest-side boot property deadline.
timeout 300 "$ADB" -s "$SERIAL" wait-for-device

deadline=$((SECONDS + 240))
while [[ $("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r') != 1 ]]; do
  (( SECONDS < deadline )) || { echo "❌ Android tidak selesai boot dalam 240 detik" >&2; exit 1; }
  sleep 2
done

abi=$("$ADB" -s "$SERIAL" shell getprop ro.product.cpu.abi | tr -d '\r')
api=$("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')
python_note="belum diperiksa"

[[ $abi == armeabi-v7a ]] || { echo "❌ ABI guest salah: $abi" >&2; exit 1; }
[[ $api == 24 ]] || { echo "❌ API guest salah: $api" >&2; exit 1; }

echo "✅ Android boot: ABI=$abi API=$api serial=$SERIAL"

if [[ -n $APK ]]; then
  [[ -f $APK ]] || { echo "❌ APK tidak ada: $APK" >&2; exit 1; }
  if ! "$ADB" -s "$SERIAL" install -r -t "$APK"; then
    cat >&2 <<'EOF'
❌ Instalasi APK gagal.
Catatan: artifact production ZCODE memakai minSdk26, sedangkan official ARMv7
system image ini API24. Gunakan APK emulator-only minSdk24 dari source/commit
yang sama; jangan turunkan minSdk production hanya demi emulator.
EOF
    exit 1
  fi
  echo "✅ APK terpasang: $(basename "$APK")"
fi

rss_kb=$(ps -eo rss,args | awk -v p="$ROOT/sdk/emulator/emulator64-arm" '$0 ~ p {print $1; exit}')
[[ -n ${rss_kb:-} ]] && echo "ℹ️ Emulator RSS: $((rss_kb / 1024)) MB"
echo "ℹ️ $python_note"
