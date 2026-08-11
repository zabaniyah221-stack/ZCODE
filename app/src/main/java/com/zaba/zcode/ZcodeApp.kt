package com.zaba.zcode

import android.app.Application
import com.zaba.zcode.core.packageengine.TelemetryStore
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZcodeApp : Application() {
    // Fase 0: ServiceContainer will be bootstrapped here (port zabacode/web_app.py get_service_container)
    // No AI/Oracle in Fase 0 skeleton

    override fun onCreate() {
        super.onCreate()
        // Telemetri lokal (SPEC-001 dashboard metric) — init sekali di process
        TelemetryStore.init(this)
    }
}
