// ZCODE root build — single source of truth for version (Fix F-09/E-02)
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.48.1" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    // TEST A: Chaquopy dinonaktifkan sementara (bisection CI)
}

// Version catalog is single source — no hardcoded 1.2.0 drift
// See gradle/libs.versions.toml
