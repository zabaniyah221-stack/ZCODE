// ZCODE root build — single source of truth for version (Fix F-09/E-02)
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.48.1" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    // Chaquopy 17.0.0 — Python 3.11 in-process runtime (Fase 1 on-device execution).
    // CATATAN: komentar lama menyebut "15.0.1" tapi versi aktual sudah 17.0.0 —
    // disamakan di sini (audit 2026-08). Chaquopy 17.0.0 men-drop AGP 7.0–7.2
    // (min. AGP 7.3; kita 8.2.2 — aman, terbukti di CI) + Python 3.11 dipertahankan
    // karena armv7 masih didukung (3.12+ drop 32-bit, lihat changelog #709).
    id("com.chaquo.python") version "17.0.0" apply false
}

// Version catalog is single source — no hardcoded 1.2.0 drift
// See gradle/libs.versions.toml
