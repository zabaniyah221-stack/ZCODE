#!/usr/bin/env bash
# Pasang senjata ARMv7 di /var/tmp — JANGAN ke /home/user (disk workspace).
# Idempotent: skip bagian yang sudah ada.
# Lihat docs/SKILLS.md § "Senjata ARMv7 (2026-08-13 malam)".
set -euo pipefail

need_cmd() { command -v "$1" >/dev/null || { echo "butuh $1" >&2; exit 1; }; }
need_cmd curl
need_cmd python3
# `need_cmd` memanggil exit, jadi pola lama `need_cmd qemu-armhf || install`
# tidak pernah mencapai fallback. Periksa tanpa exit dulu, baru validasi.
if ! command -v qemu-armhf >/dev/null; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq qemu-user-static
fi
need_cmd qemu-armhf
need_cmd dpkg-deb
need_cmd unzip
command -v debugfs >/dev/null || command -v /usr/sbin/debugfs >/dev/null || sudo apt-get install -y -qq e2fsprogs
DEBUGFS=$(command -v debugfs || echo /usr/sbin/debugfs)

echo "== A: CPython 3.11.15 glibc armv7hf =="
if [[ ! -x /var/tmp/py311/python/bin/python3.11 ]]; then
  mkdir -p /var/tmp/py311 /tmp/a-dl
  curl -fL --retry 3 -o /tmp/a-dl/py.tgz \
    'https://github.com/astral-sh/python-build-standalone/releases/download/20260807/cpython-3.11.15%2B20260807-armv7-unknown-linux-gnueabihf-install_only.tar.gz'
  tar -xzf /tmp/a-dl/py.tgz -C /var/tmp/py311
  rm -f /tmp/a-dl/py.tgz
fi
if [[ ! -e /var/tmp/glibc-armhf/lib/ld-linux-armhf.so.3 ]]; then
  mkdir -p /var/tmp/glibc-armhf /tmp/a-dl
  curl -fL -o /tmp/a-dl/libc6.deb 'https://deb.debian.org/debian/pool/main/g/glibc/libc6_2.41-12+deb13u3_armhf.deb'
  curl -fL -o /tmp/a-dl/libgcc.deb 'https://deb.debian.org/debian/pool/main/g/gcc-14/libgcc-s1_14.2.0-19_armhf.deb'
  dpkg-deb -x /tmp/a-dl/libc6.deb /var/tmp/glibc-armhf
  dpkg-deb -x /tmp/a-dl/libgcc.deb /var/tmp/glibc-armhf
  mkdir -p /var/tmp/glibc-armhf/lib
  ln -sfn ../usr/lib/arm-linux-gnueabihf/ld-linux-armhf.so.3 /var/tmp/glibc-armhf/lib/ld-linux-armhf.so.3
  rm -f /tmp/a-dl/*.deb
fi
cat > /var/tmp/armpy << 'EOF'
#!/usr/bin/env bash
export QEMU_LD_PREFIX=/var/tmp/glibc-armhf
exec qemu-armhf -L /var/tmp/glibc-armhf /var/tmp/py311/python/bin/python3.11 "$@"
EOF
chmod +x /var/tmp/armpy
# packaging
SP=/var/tmp/py311/python/lib/python3.11/site-packages
mkdir -p "$SP"
if [[ ! -d "$SP/packaging" ]]; then
  WHL=$(python3 -c "import json,urllib.request; j=json.load(urllib.request.urlopen('https://pypi.org/pypi/packaging/json')); print(next(u['url'] for u in j['urls'] if u['packagetype']=='bdist_wheel' and 'py3' in u['filename']))")
  curl -fL -o /tmp/pack.whl "$WHL"
  python3 -c "import zipfile; zipfile.ZipFile('/tmp/pack.whl').extractall('$SP')"
  rm -f /tmp/pack.whl
fi
/var/tmp/armpy -c 'import sys,platform,packaging; print("A", sys.version.split()[0], platform.machine(), packaging.__version__)'

echo "== B+: linker API 24 (extract selektif, HAPUS system.img) =="
if [[ ! -x /var/tmp/bionic-sys/system/bin/linker ]]; then
  mkdir -p /var/tmp/bionic-img /var/tmp/bionic-sys/system/bin /var/tmp/bionic-sys/system/lib /var/tmp/bionic-sys/usr/share/zoneinfo /var/tmp/bionic-sys/data
  curl -fL --retry 3 -o /tmp/sysimg24.zip \
    'https://dl.google.com/android/repository/sys-img/android/armeabi-v7a-24_r07.zip'
  unzip -o /tmp/sysimg24.zip 'armeabi-v7a/system.img' -d /var/tmp/bionic-img
  rm -f /tmp/sysimg24.zip
  IMG=/var/tmp/bionic-img/armeabi-v7a/system.img
  # image root = /system
  for spec in \
    'bin/linker:system/bin/linker' \
    'lib/libc.so:system/lib/libc.so' \
    'lib/libm.so:system/lib/libm.so' \
    'lib/libdl.so:system/lib/libdl.so' \
    'lib/liblog.so:system/lib/liblog.so' \
    'lib/libz.so:system/lib/libz.so'
  do
    src=${spec%%:*}; dest=${spec##*:}
    "$DEBUGFS" -R "dump -p $src /var/tmp/bionic-sys/$dest" "$IMG" 2>/dev/null
  done
  "$DEBUGFS" -R "dump -p usr/share/zoneinfo/tzdata /var/tmp/bionic-sys/usr/share/zoneinfo/tzdata" "$IMG" 2>/dev/null || true
  rm -rf /var/tmp/bionic-img
fi
# Extension `binascii` Termux ditautkan ke SONAME libz.so.1, sedangkan image
# Android API 24 menyediakan nama libz.so. Keduanya ABI library yang sama.
ln -sfn libz.so /var/tmp/bionic-sys/system/lib/libz.so.1

echo "== B+: Termux python3.11.15 bionic (TUR) =="
if [[ ! -x /var/tmp/tur311/data/data/com.termux/files/usr/bin/python3.11 ]]; then
  mkdir -p /var/tmp/tur311 /tmp/tur-dl
  curl -fL --retry 3 -o /tmp/tur-dl/py311.deb \
    'https://tur.kcubeterm.com/pool/tur/python3.11_3.11.15_arm.deb'
  dpkg-deb -x /tmp/tur-dl/py311.deb /var/tmp/tur311
  rm -f /tmp/tur-dl/py311.deb
fi
USR=/var/tmp/tur311/data/data/com.termux/files/usr
# libandroid-support + libpython ke prefix qemu
if [[ ! -f /var/tmp/bionic-sys/system/lib/libandroid-support.so ]]; then
  curl -fL -o /tmp/las.deb 'https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-support/libandroid-support_29-1_arm.deb'
  mkdir -p /tmp/las
  dpkg-deb -x /tmp/las.deb /tmp/las
  cp -a /tmp/las/data/data/com.termux/files/usr/lib/libandroid-support.so /var/tmp/bionic-sys/system/lib/
  rm -rf /tmp/las /tmp/las.deb
fi
cp -a "$USR/lib/"libpython3.11.so* /var/tmp/bionic-sys/system/lib/ 2>/dev/null || true

# `_ssl.cpython-311.so` dari TUR bergantung libssl.so.3 + libcrypto.so.3.
# Tanpanya urllib diam-diam tidak mendaftarkan HTTPSHandler dan resolver gagal
# `unknown url type: https`. Pin paket Termux ARM yang diverifikasi 2026-08-13.
if [[ ! -f /var/tmp/bionic-sys/system/lib/libssl.so.3 ]]; then
  curl -fL --retry 3 -o /tmp/openssl-arm.deb \
    'https://packages.termux.dev/apt/termux-main/pool/main/o/openssl/openssl_1%3A3.6.3_arm.deb'
  rm -rf /tmp/openssl-arm
  mkdir -p /tmp/openssl-arm
  dpkg-deb -x /tmp/openssl-arm.deb /tmp/openssl-arm
  cp -a /tmp/openssl-arm/data/data/com.termux/files/usr/lib/libssl.so* \
        /tmp/openssl-arm/data/data/com.termux/files/usr/lib/libcrypto.so* \
        /var/tmp/bionic-sys/system/lib/
  rm -rf /tmp/openssl-arm /tmp/openssl-arm.deb
fi

# packaging di 3.11 bionic
mkdir -p "$USR/lib/python3.11/site-packages"
if [[ ! -d $USR/lib/python3.11/site-packages/packaging ]]; then
  WHL=$(python3 -c "import json,urllib.request; j=json.load(urllib.request.urlopen('https://pypi.org/pypi/packaging/json')); print(next(u['url'] for u in j['urls'] if u['packagetype']=='bdist_wheel' and 'py3' in u['filename']))")
  curl -fL -o /tmp/pack.whl "$WHL"
  python3 -c "import zipfile; zipfile.ZipFile('/tmp/pack.whl').extractall('$USR/lib/python3.11/site-packages')"
  rm -f /tmp/pack.whl
fi

# runner B+ (hosts dari resolver HOST; JANGAN export PYTHONHOME sebelum python3 host)
cat > /var/tmp/bionic311.sh << 'EOF'
#!/usr/bin/env bash
set -e
bash /var/tmp/start_dnsproxyd.sh 2>/dev/null || true
PY=/var/tmp/tur311/data/data/com.termux/files/usr/bin/python3.11
PREFIX=/var/tmp/bionic-sys
USR=/var/tmp/tur311/data/data/com.termux/files/usr
env -u PYTHONHOME -u PYTHONPATH /usr/bin/env python3 - <<'PYH'
import socket
from pathlib import Path
need = [
    "pypi.org", "files.pythonhosted.org", "pypi.python.org",
    "chaquo.com", "www.chaquo.com",
    "github.com", "objects.githubusercontent.com", "codeload.github.com",
    "raw.githubusercontent.com", "release-assets.githubusercontent.com",
    "packages.termux.dev", "packages-cf.termux.dev", "tur.kcubeterm.com",
]
extra = Path("/var/tmp/bionic-extra-hosts.txt")
if extra.is_file():
    need += [ln.strip() for ln in extra.read_text().splitlines() if ln.strip() and not ln.startswith("#")]
lines = ["127.0.0.1 localhost"]
for h in need:
    try:
        ips = sorted({x[4][0] for x in socket.getaddrinfo(h, 443) if x[0].name == "AF_INET"})
        if ips:
            lines.append(f"{ips[0]} {h}")
    except Exception:
        pass
text = "\n".join(lines) + "\n"
for dest in (Path("/var/tmp/bionic-sys/etc/hosts"), Path("/var/tmp/bionic-sys/system/etc/hosts")):
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(text)
PYH
export ANDROID_ROOT="$PREFIX" ANDROID_DATA="$PREFIX/data"
export TZDIR="$PREFIX/usr/share/zoneinfo" QEMU_LD_PREFIX="$PREFIX"
export PYTHONHOME="$USR"
export PYTHONPATH="/var/tmp/chaquo-sp:$USR/lib/python3.11/site-packages"
export SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
export REQUESTS_CA_BUNDLE="$SSL_CERT_FILE"
exec qemu-armhf -L "$PREFIX" "$PY" "$@"
EOF
chmod +x /var/tmp/bionic311.sh

# dnsproxyd di /dev/socket (bukan prefix) — internet universal
if [[ -f "$(dirname "$0")/dnsproxyd.py" ]]; then
  cp "$(dirname "$0")/dnsproxyd.py" /var/tmp/dnsproxyd.py
fi
sudo mkdir -p /dev/socket || true
if [[ ! -S /dev/socket/dnsproxyd ]]; then
  sudo env DNSPROXYD_PATH=/dev/socket/dnsproxyd python3 /var/tmp/dnsproxyd.py >/tmp/dnsproxyd.live.log 2>&1 &
  sleep 0.3
fi

/var/tmp/bionic311.sh -c 'import sys,platform; print("B+", sys.version.split()[0], platform.machine())'
echo "siap. runner: /var/tmp/armpy  /var/tmp/bionic311.sh"
echo "JANGAN commit /var/tmp. Workspace /home/user harus tetap kecil."
