# Pelajaran Berdarah

Dibaca sebelum eksekusi destruktif. Semua pernah kejadian sungguhan.

1. **Jangan `pkill -f <pola>`** — pola cocok dengan baris perintah agent sendiri → bunuh diri (2x). Pakai nomor PID persis.
2. **Jangan sentuh proses `hermes*` dari dalam** — runtime melindungi keluarganya, executor kena SIGTERM mental (signal 15).
3. **`vo=xv` buta di bawah Compiz** (layar hitam) — pakai `vo=gpu` (~33% CPU) untuk wallpaper video.
4. **Tebak koordinat tap = judi** — selalu `uiautomator dump` / `ui dump --json` / `ui find` dulu, tap by name/center.
5. **Android 14 blokir tree-access ke Download top-level** — bikin SUBFOLDER dulu (Download/NOTEZ).
6. **Debug vs release beda tanda tangan** — `INSTALL_FAILED_UPDATE_INCOMPATIBLE` → uninstall dulu (hapus data! konfirmasi user).
7. **PKCS12 keystore Java** — storepass == keypass, atau signing gagal "block not properly padded".
8. **Secret B64 keystore basi** — tiap ganti keystore, update NOTEZ_KEYSTORE_B64.
9. **`sudo pkill lightdm/Xorg` = restart sesi setengah-setengah** — hasilnya WM ganda + xfdesktop zombie. Lebih aman reboot bersih.
10. **xfdesktop zombie** (proses hidup, jendela Desktop hilang) → kill PID + jalankan fresh.
11. **Ikon wifi hilang** = nm-applet tak ada di autostart sesi. Fix: Settings → Sesi → Aplikasi mulai otomatis → centang NetworkManager + `xfce4-panel -r`. Jangan restart applet manual (tak persisten).
12. **Monitor timeout JANGAN dirangkai aksi destruktif** — loop pantau yang habis batas lalu jatuh ke purge/kill = eksekusi buta (kejadian 15 Sep: purge Epiphany jalan padahal download 64M). Destruktif wajib guard verifikasi selesai dulu, atau pisah jadi job kedua setelah ACC.

13. **m-banking (Livin) + automation = RTP-02.** Zerotap MCP jalan via Accessibility Service → Livin anggap lingkungan tak aman, tolak buka/QRIS (RTP-02). TERKONFIRMASI orang dalam IT Livin (17 Sep): RTP-02 = flag root / system-control-navigate leak / app sideload non-PlayStore — intinya Livin menolak proses yang terlihat seperti bot atau screen-stealth. SOP sebelum buka Livin: matikan MCP Server + Accessibility zerotap, disconnect ADB, force-stop Livin (clear cache saja, JANGAN clear data), buka ulang. Masih bandel → reboot.
