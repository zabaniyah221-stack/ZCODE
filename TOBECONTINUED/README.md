# TOBECONTINUED — paket handoff ZCODE Desktop

Ditulis 16 Sep 2026. Zaqi berlayar 2 tahun; paket ini agar sesi berikutnya
(agent baru atau Zaqi sendiri) bisa lanjut tanpa merekonstruksi obrolan.

## Isi

- SESSION.md — ringkasan sesi, keputusan, temuan, pelajaran.
- SCRIPTS/ — harness probe Java + alat ukur blink (lolos /tmp yang ke-wipe).
- VAULT/ — salinan catatan Obsidian relevan (harian, proyek, pelajaran).
- OPEN.md — daftar loop terbuka + langkah eksekusi berikutnya.

## Status kerja (cabang arena/zcode-desktop, repo zabaniyah221-stack/ZCODE)

HEAD saat paket dibuat: f972495 (lihat git log). Semua commit CI hijau.
Installed di Celeron: zcode 0.0.1-1 (jar 332db87e... = f972495).

Yang SUDAH verified user (device Celeron N2840, Mint 22.3 XFCE):
- Popup autocomplete otomatis ~160ms + scrollbar gelap = PERFECT.
- GitHub render di Falkon/Epiphany OK.

Yang BELUM verified / terbuka (lihat OPEN.md):
- 1 ikon panel (fix JDialog, device belum cek).
- Blink F5 drawer instan (device belum cek).
- Garis squiggle single-line doc (fix span presisi, device belum cek).

## Kredensial (JANGAN commit isi secret ke repo!)

- PAT GitHub zabaniyah221-stack: di GNOME keyring
  (service github, account zabaniyah221-stack) + cadangan di
  ~/Dokumen/ZPAT.env (file lokal, BUKAN di repo).
- Push akun kedua via helper /home/zaqi/.local/bin/git-keyring-helper
  (sudah config per-repo). gh login utama = muzape28-blip (read-only upstream).
- gh auth akun aktif bisa dicek: gh auth status.

## Cara lanjut (ringkas)

1. Baca SESSION.md + OPEN.md.
2. Baca ~/vault/Proyek/ZCODE.md + Harian terbaru (vault = memori eksternal).
3. Repo main: ~/PROJECTS/ZCODE-fork (branch arena/zcode-desktop).
4. Build HANYA di GitHub Actions (Celeron lemah); pola:
   commit kecil → push → tunggu CI hijau (gh run list)
   → unduh artifact zcode-deb via curl resume → dpkg -i → uji device.
5. Aturan: AGENTS.md kanonik di ~/Unduhan/AGENTS.md. Refactor besar =
   blueprint + ACC user dulu. Satu slice diverifikasi sebelum lanjut.
6. Sesi agent utama: HERMES x ZAQI (session 20260911_085108_6fe906).

## Mesin (Celeron N2840, RAM 3.7GB, Mint 22.3 XFCE)

- zram 4GB + swapfile (~8GB total). Build lokal DILARANG.
- Browser harian: Falkon (default). ISP Foxline blokir DuckDuckGo
  (resolve ke block.foxline.net.id); Google sering CAPTCHA.
- Search agent: SearXNG lokal :8080 via ~/browser-search
  (node scripts/searxng/searxng.mjs), BUKAN web_search DuckDuckGo.
