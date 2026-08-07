package com.zaba.zcode

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZcodeApp : Application() {
    // Fase 0: ServiceContainer will be bootstrapped here (port zabacode/web_app.py get_service_container)
    // No AI/Oracle in Fase 0 skeleton
}
