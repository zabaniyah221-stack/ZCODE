package com.zaba.zcode.core.library

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object DeviceProbe {
    fun getSupportedAbis(): List<String> {
        return Build.SUPPORTED_ABIS.toList()
    }

    fun getTotalRamBytes(context: Context): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
        } catch (e: Exception) {
            0L
        }
    }

    fun isLowRam(context: Context): Boolean {
        val total = getTotalRamBytes(context)
        // Heuristik RAM minimal 4GB (kurang dari 4GB dianggap Low RAM / ampas)
        val limit4Gb = 4L * 1024L * 1024L * 1024L
        return total > 0 && total < limit4Gb
    }
}
