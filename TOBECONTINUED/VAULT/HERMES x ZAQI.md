# HERMES x ZAQI (sesi utama, ID 20260911_085108_6fe906)

Sesi berjalan sejak 11 Sep 2026. Bahasa: Indonesia santai.

## Status desktop (13 Sep 2026)

- WM: Compiz cube (edge-flip kanan, flip 180ms), autostart `~/.local/bin/compiz-start.sh` (delay 4 dtk). Picom OFF permanen (backup di /tmp — HILANG tiap reboot, tidak penting).
- Wallpaper statis: `/home/zaqi/Gambar/dark.png` (zoom, via xfconf, permanen). Live wallpaper MATI (autostart off).
- Jendela baru selalu tengah (Compiz place mode=Centered).
- Maximize = jendela di atas panel (winrules below=type=Dock, permanen). Panel hanya terlihat saat desktop kosong.
- XFCE AutoSave=false (pernah restore terminal fullscreen hantu — file sesi dihapus).
- Bot login hermes-lanjut-sesi: MATI total (proses + autostart).
- Telegram: bot @Maelinea_bot (H3RMES) ONLINE dua arah (14 Sep). Token + allowed user di ~/.hermes/.env. Tujuan: notif CI otomatis.

## Status NOTEZ (com.zaba.notez)

- v0.1.0 rilis internal tuntas (signed, R8 2MB). PR #7 merged.
- v0.2.0 (PR #8 merged c221ee3): default GitHub Dark + ekspor JSON/TXT + impor JSON + backup otomatis harian (SAF, folder Download/NOTEZ di HP). Teruji E2E di device.
- Syarat publik terpenuhi sisi data (LESTARIKAN). Minor: status bar masih biru.
- Repo: ~/PROJECTS/NOTEZ, branch main. Build HANYA di GitHub Actions (laptop Celeron lemah).

## Status ZCODE

- Repo utama: ~/PROJECTS/ZCODE/ZCODE (upstream muzape28-blip/ZCODE, v1.0.23). Fork playground: ~/PROJECTS/ZCODE-fork.
- APK terpasang di HP (com.zaba.zcode). Uji ketik manual via agent: TERHENTI (koneksi ADB flapping + bug escaping `input text` adb-skill).

## Android agent

- Skill: `android-pilot`. Tools di ~/android-agent: adb-skill ✓, adb-agent (MCP, ~/.local/bin) ✓, mobile-harness (.venv) ✓, Midscene (PENDING API key).
- HP: Infinix X6532C, wireless ADB port SELALU berubah — tanya user tiap putus. USB paling stabil.
Update 14 Sep malam. Zaqi setup zram 4GB prio 100 plus swapfile existing prio rendah, total swap 8GB, service zram4g permanen. Cavasik plus cava DIHAPUS makan 25 persen CPU, config bersih. Gaya Zaqi absurd tapi tajam uji langsung di device, gas semua cara sampai stabil, semua hal wajib catat Obsidian. ZCODE desktop dual instance crash rebutan CEF lock pelajaran kill java anak. Eksperimen splash merah nempel sedang jalan penentu overlay vs CEF native.