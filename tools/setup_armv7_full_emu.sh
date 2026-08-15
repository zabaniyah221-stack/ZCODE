#!/usr/bin/env bash
# Full Android ARMv7 emulator for ZCODE — resource-bounded sandbox profile.
#
# Semua image besar disimpan di /var/tmp, BUKAN /home/user/workspace.
# Official ARMv7 image terakhir yang praktis untuk emulator klasik: API 24.
# Production ZCODE minSdk 26 TIDAK dapat dipasang ke image ini; gunakan hanya
# APK emulator-only minSdk24 untuk menguji Android/JVM/Chaquopy/ARMv7.
set -euo pipefail

ROOT=${ZCODE_ARMV7_FULL_ROOT:-/var/tmp/zcode-armv7-full}
SDK="$ROOT/sdk"
SYS="$SDK/system-images/android-24/default/armeabi-v7a"
AVD_HOME="$ROOT/avd-home"
AVD_DIR="$ROOT/zcode_armv7.avd"
DOWNLOAD="$ROOT/download"

EMULATOR_URL='https://dl.google.com/android/repository/emulator-linux-4848055.zip'
SYSTEM_URL='https://dl.google.com/android/repository/sys-img/android/armeabi-v7a-24_r07.zip'
PLATFORM_TOOLS_URL='https://dl.google.com/android/repository/platform-tools-latest-linux.zip'

need() { command -v "$1" >/dev/null || { echo "❌ butuh command: $1" >&2; exit 1; }; }
need curl
need unzip
need awk
need df

[[ $(uname -m) == x86_64 ]] || {
  echo "❌ profil ini memakai emulator Linux x86_64 + emulasi guest ARMv7" >&2
  exit 1
}

free_mb=$(df -Pm /var/tmp | awk 'NR==2 {print $4}')
mem_mb=$(awk '/MemTotal:/ {print int($2/1024)}' /proc/meminfo)
(( free_mb >= 5500 )) || {
  echo "❌ /var/tmp butuh minimal 5.5 GB kosong; tersedia ${free_mb} MB" >&2
  exit 1
}
(( mem_mb >= 1800 )) || {
  echo "❌ sandbox butuh minimal ~1.8 GB RAM; tersedia ${mem_mb} MB" >&2
  exit 1
}

mkdir -p "$DOWNLOAD" "$SDK" "$AVD_HOME" "$AVD_DIR"

download() {
  local url=$1 dest=$2
  [[ -s $dest ]] && return
  echo "↓ $(basename "$dest")"
  curl -fL --retry 3 --retry-delay 1 -o "$dest.part" "$url"
  mv "$dest.part" "$dest"
}

if [[ ! -x "$SDK/emulator/emulator64-arm" ]]; then
  download "$EMULATOR_URL" "$DOWNLOAD/emulator.zip"
  rm -rf "$SDK/emulator"
  unzip -q "$DOWNLOAD/emulator.zip" -d "$SDK"
fi

if [[ ! -f "$SYS/system.img" ]]; then
  download "$SYSTEM_URL" "$DOWNLOAD/system.zip"
  mkdir -p "$(dirname "$SYS")"
  rm -rf "$SYS" "$ROOT/system-unpack"
  mkdir -p "$ROOT/system-unpack"
  unzip -q "$DOWNLOAD/system.zip" -d "$ROOT/system-unpack"
  mv "$ROOT/system-unpack/armeabi-v7a" "$SYS"
  rmdir "$ROOT/system-unpack"
fi

if [[ ! -x "$SDK/platform-tools/adb" ]]; then
  download "$PLATFORM_TOOLS_URL" "$DOWNLOAD/platform-tools.zip"
  rm -rf "$SDK/platform-tools"
  unzip -q "$DOWNLOAD/platform-tools.zip" -d "$SDK"
fi

# Launcher lama menganggap SDK root rusak bila platform-tools tidak ada.
# Image/API files sudah cukup untuk runtime; platform android.jar tidak dibutuhkan.

# Emulator 27.3.8 membawa Qt/SwiftShader sendiri, tetapi tetap dinamis terhadap
# beberapa library host. Pasang hanya bila benar-benar hilang.
export LD_LIBRARY_PATH="$SDK/emulator/lib64/qt/lib:$SDK/emulator/lib64${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
missing=$(ldd "$SDK/emulator/emulator64-arm" 2>/dev/null | awk '/not found/ {print $1}' | tr '\n' ' ')
if [[ -n $missing ]]; then
  echo "⚠️ library host hilang: $missing"
  if command -v sudo >/dev/null && command -v apt-get >/dev/null; then
    sudo apt-get update -qq
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
      libpulse0 libx11-6 libxcomposite1 libxcursor1 libxi6 libxtst6 libnss3 libgl1
  else
    echo "❌ instal library di atas secara manual, lalu ulangi" >&2
    exit 1
  fi
fi

cat > "$AVD_HOME/zcode_armv7.ini" <<EOF
avd.ini.encoding=UTF-8
path=$AVD_DIR
target=android-24
EOF

cat > "$AVD_DIR/config.ini" <<EOF
PlayStore.enabled=no
abi.type=armeabi-v7a
avd.ini.encoding=UTF-8
avd.name=zcode_armv7
disk.cachePartition=yes
disk.cachePartition.size=66MB
disk.dataPartition.path=<temp>
disk.dataPartition.size=1024M
disk.systemPartition.size=0
fastboot.forceChosenSnapshotBoot=no
fastboot.forceColdBoot=yes
fastboot.forceFastBoot=no
hw.audioInput=no
hw.audioOutput=no
hw.battery=yes
hw.camera.back=none
hw.camera.front=none
hw.cpu.arch=arm
hw.cpu.model=cortex-a8
hw.cpu.ncore=1
hw.dPad=no
hw.gps=no
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
hw.initialOrientation=portrait
hw.keyboard=yes
hw.lcd.density=240
hw.lcd.depth=16
hw.lcd.height=800
hw.lcd.width=480
hw.mainKeys=no
hw.ramSize=512
hw.screen=multi-touch
hw.sdCard=no
hw.trackBall=no
hw.useext4=yes
image.sysdir.1=system-images/android-24/default/armeabi-v7a/
kernel.newDeviceNaming=autodetect
kernel.supportsYaffs2=autodetect
runtime.network.latency=none
runtime.network.speed=full
showDeviceFrame=no
tag.display=Default
tag.id=default
target=android-24
vm.heapSize=192M
EOF

# userdata.img hanya template read-only; symlink menghindari duplikasi 550 MB.
ln -sfn "$SYS/userdata.img" "$AVD_DIR/userdata.img"

# Arsip tidak diperlukan setelah extract; hemat ±460 MB.
rm -f "$DOWNLOAD"/*.zip
rmdir "$DOWNLOAD" 2>/dev/null || true

"$SDK/emulator/emulator" -version | head -1
"$SDK/platform-tools/adb" version | head -1

echo "✅ Full ARMv7 Android emulator siap di $ROOT"
echo "   Start : bash tools/start_armv7_full_emu.sh"
echo "   Verify: bash tools/verify_armv7_full_emu.sh [apk-test-min24]"
echo "   Stop  : bash tools/stop_armv7_full_emu.sh"
echo "⚠️ Production APK minSdk26 tidak kompatibel dengan guest API24 — ini disengaja."
