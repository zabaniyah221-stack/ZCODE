#!/usr/bin/env bash
# Start full ARMv7 Android API24 emulator without KVM.
# Must not run concurrently with Gradle build in a 2 GB sandbox.
set -euo pipefail

ROOT=${ZCODE_ARMV7_FULL_ROOT:-/var/tmp/zcode-armv7-full}
SDK="$ROOT/sdk"
AVD_HOME="$ROOT/avd-home"

[[ -x "$SDK/emulator/emulator" ]] || {
  echo "❌ belum setup: bash tools/setup_armv7_full_emu.sh" >&2
  exit 1
}

if pgrep -f "$SDK/emulator/emulator64-arm" >/dev/null; then
  echo "❌ emulator ARMv7 sudah berjalan" >&2
  exit 1
fi

available_mb=$(awk '/MemAvailable:/ {print int($2/1024)}' /proc/meminfo)
(( available_mb >= 1200 )) || {
  echo "❌ butuh minimal 1.2 GB RAM available sebelum start; ada ${available_mb} MB" >&2
  echo "   Stop Gradle/server berat dulu. Jangan jalankan build dan emulator bersamaan." >&2
  exit 1
}

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export ANDROID_AVD_HOME="$AVD_HOME"
export ANDROID_EMULATOR_HOME="$ROOT/emulator-home"

wipe=()
[[ ${ZCODE_ARMV7_WIPE:-0} == 1 ]] && wipe=(-wipe-data)

# Dua pembatas berbeda dan keduanya wajib:
# -memory 512 mengatur hardware config; `-qemu -m 512` memaksa QEMU klasik agar
# tidak menaikkan guest ke 1024 MB. Tanpa override, RSS terukur mencapai 1.55 GB.
# SwiftShader wajib untuk WebView: `-gpu off` membuat Chromium abort saat gagal
# membuat EGL pbuffer surface.
exec "$SDK/emulator/emulator" @zcode_armv7 \
  -engine classic \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -no-snapshot \
  "${wipe[@]}" \
  -gpu swiftshader \
  -memory 512 \
  -port 5554 \
  -qemu -m 512
