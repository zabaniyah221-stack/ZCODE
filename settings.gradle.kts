pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Chaquopy runtime (libpython + stdlib + wheel native) — Fase 1: runtime di-embed
        maven("https://chaquo.com/maven")
    }
}

dependencyResolutionManagement {
    // FAIL_ON_PROJECT_REPOS sengaja TIDAK dipakai: plugin Chaquopy mendaftarkan
    // repo "chaquopy" level proyek saat konfigurasi, dan mode tersebut melarangnya
    // ("Build was configured to prefer settings repositories over project
    // repositories but repository 'chaquopy' was added by build file").
    repositories {
        google()
        mavenCentral()
        // Chaquopy maven untuk Python runtime (libpython 3.11 + stdlib)
        maven("https://chaquo.com/maven")
        // JitPack untuk Termux terminal-view (hanya kalau dipakai nanti)
        // maven("https://jitpack.io")
    }
}

rootProject.name = "ZCODE"
include(":app")
