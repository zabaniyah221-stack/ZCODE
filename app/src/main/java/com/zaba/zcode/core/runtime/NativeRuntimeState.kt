package com.zaba.zcode.core.runtime

import android.content.Context
import android.os.Process
import com.zaba.zcode.core.diagnostics.Breadcrumb

/**
 * Persistent contract for a Chaquopy process which has loaded or changed native code.
 * Native extensions cannot be safely unloaded by deleting entries from sys.modules.
 */
object NativeRuntimeState {
    private const val PREFS = "zcode_native_runtime"
    private const val REQUIRED = "restart_required"
    private const val PACKAGES = "restart_packages"
    private const val REQUESTED_AT = "restart_requested_at"
    private const val FROM_PID = "restart_from_pid"
    private const val RECEIPT = "restart_receipt"

    fun isRequired(context: Context): Boolean = prefs(context).getBoolean(REQUIRED, false)

    fun packages(context: Context): Set<String> =
        prefs(context).getStringSet(PACKAGES, emptySet())?.toSet().orEmpty()

    /** Synchronous commit: the stale receipt must reach disk before any process can die. */
    fun markRequired(context: Context, packageNames: Collection<String>, reason: String) {
        val merged = packages(context) + packageNames.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val ok = prefs(context).edit()
            .putBoolean(REQUIRED, true)
            .putStringSet(PACKAGES, merged)
            .commit()
        if (ok) Breadcrumb.log("RUNTIME_RESTART_REQUIRED", "${merged.joinToString(",")} reason=$reason")
        else Breadcrumb.log("RUNTIME_RESTART_STATE_FAIL", "reason=$reason")
    }

    /** Returns false if the restart receipt could not be durably persisted. */
    fun prepareRestart(context: Context): Boolean {
        if (!isRequired(context)) return false
        val pid = Process.myPid()
        val now = System.currentTimeMillis()
        val ok = prefs(context).edit()
            .putLong(REQUESTED_AT, now)
            .putInt(FROM_PID, pid)
            .putBoolean(RECEIPT, true)
            .commit()
        if (ok) Breadcrumb.log("RUNTIME_RESTART_REQUEST", "oldPid=$pid")
        return ok
    }

    /** Called only by the explicit fresh MainActivity launched by the helper process. */
    fun completeRestart(context: Context, previousPid: Int): Boolean {
        val p = prefs(context)
        val valid = p.getBoolean(RECEIPT, false) &&
            p.getBoolean(REQUIRED, false) &&
            previousPid > 0 && previousPid != Process.myPid() &&
            p.getInt(FROM_PID, -1) == previousPid
        if (!valid) return false
        val ok = p.edit()
            .putBoolean(REQUIRED, false)
            .remove(PACKAGES)
            .remove(RECEIPT)
            .remove(REQUESTED_AT)
            .remove(FROM_PID)
            .commit()
        if (ok) Breadcrumb.log("RUNTIME_RESTART_OK", "previousPid=$previousPid newPid=${Process.myPid()}")
        return ok
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
