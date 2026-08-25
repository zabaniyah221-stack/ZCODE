package com.zaba.zcode.core.update

import android.content.Context

/**
 * UpdateSession — hasil proses receipt start-up, dimiliki [ZcodeApp]
 * (satu consumer: [UpdateReceipt.processOnStartup] bersifat destruktif
 * — membuang receipt/APK untuk INSTALLED & STALE — jadi tidak boleh
 * dipanggil ulang oleh ViewModel).
 *
 * ViewModel baru (setiap Activity) HANYA membaca [startupOutcome].
 */
object UpdateSession {

    @Volatile
    var startupOutcome: UpdateReceipt.Outcome = UpdateReceipt.Outcome.None
        private set

    /** Dipanggil sekali dari ZcodeApp.onCreate (process utama). */
    fun onStartup(context: Context) {
        startupOutcome = UpdateReceipt.processOnStartup(context)
    }
}
