#!/usr/bin/env bash
# Runner tipis — CPython 3.11.15 ARMv7 glibc (bukan Android).
# Untuk resolve_json + tag Android. Pasang: bash tools/setup_armv7_emu.sh
set -e
if [[ ! -x /var/tmp/armpy ]]; then
  echo "belum terpasang. jalankan: bash tools/setup_armv7_emu.sh" >&2
  exit 2
fi
exec /var/tmp/armpy "$@"
