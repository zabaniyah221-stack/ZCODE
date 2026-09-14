plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("org.jetbrains.compose") version "1.8.0"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("io.github.kevinnzou:compose-webview-multiplatform-desktop:2.0.1")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        // Paket .deb (diskusi 14 Sep): install sekali, klik dari menu.
        // Catatan jujur: bundle CEF (~500MB) TIDAK ikut — diunduh KCEF
        // saat run pertama (butuh internet sekali).
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "zcode"
            version = "0.0.1"
            vendor = "ZABA"
            description = "ZCODE Desktop — IDE Python offline-first"
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}
