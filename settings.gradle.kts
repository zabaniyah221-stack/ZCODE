pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Chaquopy maven for Python runtime (when network available)
        // maven("https://chaquo.com/maven")
        // JitPack for Termux terminal-view (when network available)
        // maven("https://jitpack.io")
    }
}

rootProject.name = "ZCODE"
include(":app")
