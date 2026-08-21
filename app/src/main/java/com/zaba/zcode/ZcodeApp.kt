package com.zaba.zcode

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.zaba.zcode.core.diagnostics.Breadcrumb
import com.zaba.zcode.core.diagnostics.CrashReporter
import com.zaba.zcode.core.packageengine.TelemetryStore
import com.zaba.zcode.core.packageengine.TransactionManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZcodeApp : Application() {

    private fun currentProcessName(): String {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName()
        val pid = Process.myPid()
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val listed = manager?.runningAppProcesses
            ?.firstOrNull { it.pid == pid }
            ?.processName
        return listed ?: runCatching {
            java.io.File("/proc/self/cmdline").readText().trimEnd('\u0000')
        }.getOrDefault("")
    }

    override fun onCreate() {
        super.onCreate()
        // Helper relaunch hidup di process terpisah dan WAJIB tetap kecil: jangan
        // pasang crash reporter/telemetri, jangan prewarm atau menyentuh Chaquopy.
        if (currentProcessName().endsWith(":rebirth")) return

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

        // installed.json uses AtomicFile during package activation. Recover a
        // possible interrupted write before Python/UI reads the file directly.
        if (!TransactionManager.recoverInstalledState(this)) {
            Breadcrumb.log("PKG_STATE_RECOVERY_FAIL", "installed.json")
        }

        // Telemetri lokal (SPEC-001 dashboard metric) — init sekali di process.
        // Dibungkus try/catch: telemetri TIDAK boleh menggagalkan startup.
        try {
            TelemetryStore.init(this)
        } catch (e: Throwable) {
            Breadcrumb.log("TELEMETRY_INIT_FAIL", e.message ?: e.javaClass.simpleName)
        }
    }
}
