# Android Pilot

Cara mengendalikan HP Infinix X6532C dari agent. Detail operasional ada di skill `android-pilot`.

- Koneksi: wireless ADB, port SELALU berubah → cek `adb devices` dulu; putus = tanya user IP:port fresh. USB paling stabil.
- `protocol fault` → `adb kill-server; start-server; connect`.
- Multi-device (ghost TLS) → selalu `--device IP:PORT` / `ADB_SERIAL`.
- Tools: adb-skill (deterministik), adb-agent (presisi + logcat), mobile-harness (Python), Midscene (pending API key).
- NOTEZ: APK debug dari CI, `run-as` bisa (debug build). DB: pull lalu inspeksi lokal (tak ada sqlite3 di HP).
