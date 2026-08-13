#!/usr/bin/env bash
# ZCODE bionic-ARMv7 runner (senjata pamungkas)
# Menjalankan Python 3.14 bionic + wheel native Android (Termux) di sandbox via qemu.
# Cara pakai: bash /home/user/bionic_armv7.sh  # lalu beri argumen python
# Contoh: bash /home/user/bionic_armv7.sh -c "import numpy; print(numpy.__version__)"
#
# Prasyarat (sudah disiapkan di /home/user):
#   - qemu-armhf (apt install qemu-user-static)
#   - /home/user/android_sys/system/bin/linker   (bionic linker, dari system.img API 24)
#   - /home/user/android_sys/system/lib/*.so     (bionic libc + Termux libs)
#   - /home/user/termux_root/.../usr/bin/python3.14 (bionic python)
#   - /home/user/mnt_sys24 (Android system image API 24, untuk tzdata)
#   - site-packages di termux_root (numpy, pillow, dll)

PY=/home/user/termux_root/data/data/com.termux/files/usr/bin/python3.14
export ANDROID_ROOT=/home/user/mnt_sys24
export ANDROID_DATA=/home/user/mnt_sys24/data
export TZDIR=/home/user/mnt_sys24/usr/share/zoneinfo
export QEMU_LD_PREFIX=/home/user/android_sys
# site-packages otomatis
SP=/home/user/termux_root/data/data/com.termux/files/usr/lib/python3.14/site-packages
export PYTHONPATH="$SP${PYTHONPATH:+:$PYTHONPATH}"

exec qemu-armhf -L /home/user/android_sys "$PY" "$@"
