#!/usr/bin/env bash
set -e
SOCK=/dev/socket/dnsproxyd
if [[ -S "$SOCK" ]] && python3 -c 'import socket; s=socket.socket(socket.AF_UNIX); s.settimeout(0.2); s.connect("/dev/socket/dnsproxyd")' 2>/dev/null; then
  exit 0
fi
sudo mkdir -p /dev/socket
# copy script if needed
SRC=/var/tmp/dnsproxyd.py
[[ -f /home/user/ZCODE/tools/dnsproxyd.py ]] && SRC=/home/user/ZCODE/tools/dnsproxyd.py
sudo cp "$SRC" /var/tmp/dnsproxyd.py
sudo chmod 755 /var/tmp/dnsproxyd.py
if [[ -f /tmp/dnsproxyd.live.pid ]] && kill -0 "$(cat /tmp/dnsproxyd.live.pid)" 2>/dev/null; then
  exit 0
fi
sudo env DNSPROXYD_PATH=/dev/socket/dnsproxyd python3 /var/tmp/dnsproxyd.py >/tmp/dnsproxyd.live.log 2>&1 &
echo $! | sudo tee /tmp/dnsproxyd.live.pid >/dev/null
sleep 0.25
