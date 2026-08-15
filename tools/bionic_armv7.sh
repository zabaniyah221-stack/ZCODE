#!/usr/bin/env bash
# Kompat: nama lama. Senjata pamungkas SEKARANG = Python 3.11 bionic.
# Lihat docs/SKILLS.md. Pasang: bash tools/setup_armv7_emu.sh
set -e
if [[ -x /var/tmp/bionic311.sh ]]; then
  exec /var/tmp/bionic311.sh "$@"
fi
echo "belum terpasang. jalankan: bash tools/setup_armv7_emu.sh" >&2
exit 2
