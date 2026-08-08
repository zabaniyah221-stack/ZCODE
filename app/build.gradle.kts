import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    // Chaquopy 3.11 — KEEP 3.11 for armeabi-v7a (3.12 drops 32-bit, see changelog #709)
    id("com.chaquo.python")
}

android {
    namespace = "com.zaba.zcode"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zaba.zcode"
        minSdk = 26 // 26 = EncryptedSharedPreferences stable, Zabacode minApi 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0-fase2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // ARMv7 (HP kamu) + ARM64 + x86_64 emulator
            // App Bundle akan split; universal APK untuk sideload
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        viewBinding = false
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    packaging {
        // ZMUX lesson: PRoot needs legacy packaging, but Fase 0 no proot yet
        // Keep for future PTY Alpine
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
            jniLibs.srcDir("src/main/jniLibs")
        }
    }
}

// Chaquopy 15.0 — Python 3.11 in-process runtime (Fase 1: on-device execution).
// - version 3.11: satu-satunya yang masih mendukung armeabi-v7a (HP user)
// - pip build-time sengaja kosong; instalasi package lewat PipScreen saat runtime
// - buildPython: CI menyediakan python3 (3.11) di PATH
chaquopy {
    defaultConfig {
        version = "3.11"
    }
}

dependencies {
    // Core AndroidX — minimal for Go footprint (must be BEFORE any task that may resolve)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.48.1")
    ksp("com.google.dagger:hilt-compiler:2.48.1")

    // Security Crypto — EncryptedSharedPreferences (S-19 fix)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Terminal-view PTY (Fase 1 PTY, already prepared Fase 0, no Alpine yet)
    // implementation("com.termux.termux-app:terminal-view:0.118.0")
    // implementation("com.termux.termux-app:terminal-emulator:0.118.0")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.test:core:1.5.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Fase 0 guard: verify Ace bundled before any APK is considered good
// NOTE: tasks are AFTER dependencies to avoid "Cannot mutate dependencies after resolved" (Gradle 8.5 stricter)
tasks.register("verifyAceBundled") {
    doLast {
        val ace = layout.projectDirectory.file("src/main/assets/editor/ace/ace.js").asFile
        check(ace.isFile && ace.length() > 0) { "Ace not bundled: ${ace.path} missing — offline-first violation" }
        val mode = layout.projectDirectory.file("src/main/assets/editor/ace/mode-python.js").asFile
        check(mode.isFile) { "Ace mode-python.js missing" }
        println("✅ Ace 1.44.0 bundled — ${ace.length()} bytes")
    }
}
tasks.named("preBuild") { dependsOn("verifyAceBundled") }

tasks.register("verifyNoUnverifiedSSL") {
    doLast {
        val hasBad = fileTree("src/main/java").matching { include("**/*.kt") }
            .any { it.readText().contains("ssl._create_unverified_context") || it.readText().contains("trustAllCerts") }
        check(!hasBad) { "❌ Found unverified SSL — breaks verified TLS (S-22)" }
        println("✅ No unverified SSL")
    }
}
