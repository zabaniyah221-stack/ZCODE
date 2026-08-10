// ZCODE root build — single source of truth for version (Fix F-09/E-02)
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.48.1" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    // Chaquopy 15.0.1 — Python 3.11 in-process runtime (Fase 1 on-device execution)
    // Versi ini mendukung AGP 7.0–8.5 (kita 8.2.2) + Python 3.11 (armv7 masih didukung;
    // 3.12+ drop 32-bit, lihat changelog #709)
    id("com.chaquo.python") version "17.0.0" apply false
}

// Version catalog is single source — no hardcoded 1.2.0 drift
// See gradle/libs.versions.toml
