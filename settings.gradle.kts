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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
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
