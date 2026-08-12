package com.zaba.zcode

import android.app.Application
import com.zaba.zcode.core.diagnostics.Breadcrumb
import com.zaba.zcode.core.diagnostics.CrashReporter
import com.zaba.zcode.core.packageengine.TelemetryStore
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZcodeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Diagnostik WAJIB paling awal: user tidak punya PC/logcat, jadi satu-satunya
        // bukti saat force close adalah apa yang sempat tertulis ke disk.
        Breadcrumb.init(this)
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Throwable) {
            "?"
        }
        CrashReporter.install(this, version)
        Breadcrumb.log("APP_START", "v$version api=${android.os.Build.VERSION.SDK_INT} abi=${android.os.Build.SUPPORTED_ABIS.firstOrNull()}")

        // Telemetri lokal (SPEC-001 dashboard metric) — init sekali di process.
        // Dibungkus try/catch: telemetri TIDAK boleh menggagalkan startup.
        try {
            TelemetryStore.init(this)
        } catch (e: Throwable) {
            Breadcrumb.log("TELEMETRY_INIT_FAIL", e.message ?: e.javaClass.simpleName)
        }
    }
}
