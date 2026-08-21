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
        // Single source: gradle.properties (F-09 anti-drift) — fallback literal di sini
        // hanya kalau property hilang; string "1.0.0" dipertahankan untuk pin test CI
        versionCode = (project.findProperty("zcode.versionCode") as? String)?.toInt() ?: 3
        versionName = (project.findProperty("zcode.versionName") as? String) ?: "1.0.0"

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
        // v1.0.20-rc1: satu APK kandidat internal yang optimized/release-like,
        // tetapi package dan data tetap terpisah dari production. Signing masih
        // ephemeral debug key; production key user belum masuk CI pada tahap RC.
        create("rc") {
            applicationIdSuffix = ".rc"
            versionNameSuffix = "-rc1"
            isDebuggable = false
            isProfileable = true
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rc.pro"
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

// Chaquopy 17.0.0 — Python 3.11 in-process runtime (Fase 1: on-device execution).
// (Komentar lama menyebut "15.0" — versi plugin aktual dideklarasikan di root
//  build.gradle.kts: 17.0.0; disamakan saat audit 2026-08.)
// - version 3.11: satu-satunya yang masih mendukung armeabi-v7a (HP user)
// - pip WAJIB di-bundle di sini: Chaquopy TIDAK menyertakan pip secara default.
//   Tanpa blok pip{} runtime selalu gagal: ModuleNotFoundError: No module named 'pip'
//   (akar bug PipScreen "no module name pip"). Komentar lama "pip build-time sengaja
//   kosong" salah kaprah — pip runtime butuh interpreter pip di assets.
// - Pin pip 23.3.1: pip 24+ crash di Chaquopy karena importlib.metadata memindai
//   distribution via AssetFinder → AssetPath tidak punya .parent → AttributeError.
//   Lapis kedua monkey-patch tetap ada di zcode_pip.py (belt-and-suspenders).
// - setuptools/wheel: fallback build sdist paket pure-Python tanpa build backend.
// - buildPython: CI menyediakan python3 (3.11) di PATH
chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("pip==23.3.1")     // jangan latest 24+ (bug AssetPath.parent)
            install("setuptools==68.2.2")
            install("wheel==0.41.2")
            // WAJIB — jangan dihapus (fix FATAL 2026-08-12).
            // `packaging` TIDAK ikut dengan pip/setuptools/wheel: ketiganya hanya
            // memuat salinan ter-vendor (pip._vendor.packaging,
            // pkg_resources._vendor.packaging, wheel.vendored.packaging) yang TIDAK
            // bisa di-`import packaging`. Tanpa baris ini, tiga modul runtime kita
            // (package_runtime/requirement.py, resolve.py, wheelinfo.py) gagal import
            // dan SELURUH fitur Install Modules mati dengan pesan
            // "ModuleNotFoundError: No module named 'packaging'".
            // Dijaga oleh test_zcode_kotlin_guards.py::TestChaquopyPipBundle.
            install("packaging==24.1")
        }
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
    // Dipakai langsung oleh HorizontalPager INSTALL MODULES. BOM 2024.02.00
    // memetakan Foundation 1.6.1; deklarasi eksplisit menghindari bergantung
    // secara kebetulan pada dependency transitif Material3.
    implementation("androidx.compose.foundation:foundation")
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

// Fase 0 guard: verify editor bundled before any APK is considered good
// (Migrasi CM6 2026-08: Ace 1.44.0 → CodeMirror 6 bundle, lihat docs/MIGRASI_CM6.md)
// NOTE: tasks are AFTER dependencies to avoid "Cannot mutate dependencies after resolved" (Gradle 8.5 stricter)
tasks.register("verifyEditorBundled") {
    doLast {
        val bundle = layout.projectDirectory.file("src/main/assets/editor/codemirror.bundle.js").asFile
        check(bundle.isFile && bundle.length() > 100_000) {
            "CodeMirror 6 not bundled (or stub): ${bundle.path} — offline-first violation"
        }
        val text = bundle.readText()
        check("setCode" in text && "onEditorReady" in text) { "Bundle CM6 kehilangan kontrak bridge (setCode/onEditorReady)" }
        check("index.html" .let { layout.projectDirectory.file("src/main/assets/editor/$it").asFile.readText().contains("codemirror.bundle.js") }) { "index.html tidak memuat codemirror.bundle.js" }
        println("✅ CodeMirror 6 bundled — ${bundle.length()} bytes (offline-first)")
    }
}
tasks.named("preBuild") { dependsOn("verifyEditorBundled") }

tasks.register("verifyNoUnverifiedSSL") {
    doLast {
        val hasBad = fileTree("src/main/java").matching { include("**/*.kt") }
            .any { it.readText().contains("ssl._create_unverified_context") || it.readText().contains("trustAllCerts") }
        check(!hasBad) { "❌ Found unverified SSL — breaks verified TLS (S-22)" }
        println("✅ No unverified SSL")
    }
}
