#!/usr/bin/env bash
# ZCODE wheel builder — skeleton (SPEC-001 Phase 3: ZCODE wheel source).
#
# Membangun wheel Android dari source package memakai toolchain Chaquopy:
#   pip install chaquopy
#
# CATATAN JUJUR (belum diverifikasi di CI nyata — butuh NDK + env Android):
# Toolchain Chaquopy untuk C extension memerlukan:
#   - Android NDK (r25 atau sesuai versi Chaquopy)
#   - variabel ANDROID_NDK_HOME
#   - Python build (3.11) dengan header
# Langkah detail: https://chaquo.com/chaquopy/doc/current/android.html
#
# Pemakaian:
#   bash tools/wheel-builder/build_wheel.sh "pkg==1.0" arm64-v8a wheelhouse
set -euo pipefail

REQ="${1:?requirement (mis: 'mypkg==1.0' atau URL sdist)}"
ABI="${2:?abi (arm64-v8a | armeabi-v7a)}"
OUT="${3:?output dir}"

if ! command -v chaquopy >/dev/null 2>&1; then
  echo "❌ toolchain 'chaquopy' tidak ada. Install: pip install chaquopy"
  exit 1
fi
if [ -z "${ANDROID_NDK_HOME:-}" ] && [ -z "${ANDROID_HOME:-}" ]; then
  echo "❌ ANDROID_NDK_HOME / ANDROID_HOME tidak diset (butuh NDK)."
  exit 1
fi

mkdir -p "$OUT"
echo "==> Build '$REQ' untuk ABI $ABI (Python 3.11)…"
# Contoh minimal — flags final mengikuti dokumentasi resmi Chaquopy.
chaquopy build -a "$ABI" -p 3.11 -o "$OUT" "$REQ" \
  || { echo "❌ Build gagal untuk $ABI"; exit 1; }

echo "==> Wheel:"
ls -lh "$OUT"/*.whl
