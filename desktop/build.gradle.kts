plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("org.jetbrains.compose") version "1.8.0"
}

dependencies {
    implementation(compose.desktop.currentOs)
    // Editor RSTA Swing murni (migrasi 15 Sep, irisan 3): tanpa Chromium,
    // tanpa download 500MB. Pin eksak, Maven Central terverifikasi.
    implementation("com.fifesoft:rsyntaxtextarea:3.6.0")
    implementation("com.fifesoft:autocomplete:3.3.2")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        // Paket .deb (diskusi 14 Sep): install sekali, klik dari menu.
        // RSTA Swing murni: tanpa runtime Chromium, .deb puluhan MB.
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
