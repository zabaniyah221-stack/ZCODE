#!/usr/bin/env bash
# Runner tipis — Python 3.11.15 BIONIC ARMv7 + linker Android API 24.
# Prefix HIDUP di /var/tmp (bukan /home/user). Pasang dulu:
#   bash tools/setup_armv7_emu.sh
# Contoh:
#   bash tools/bionic311.sh -c 'import numpy,pandas,matplotlib; print(numpy.__version__)'
set -e
if [[ ! -x /var/tmp/bionic311.sh ]]; then
  echo "belum terpasang. jalankan: bash tools/setup_armv7_emu.sh" >&2
  exit 2
fi
exec /var/tmp/bionic311.sh "$@"
