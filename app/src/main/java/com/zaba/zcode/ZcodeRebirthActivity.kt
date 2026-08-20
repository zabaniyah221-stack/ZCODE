package com.zaba.zcode

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process
import com.zaba.zcode.core.diagnostics.Breadcrumb

/** Tiny isolated-process handoff. It never initializes Chaquopy or the normal app runtime. */
class ZcodeRebirthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(BinaryRainView(this))
        val oldPid = intent.getIntExtra(EXTRA_OLD_PID, -1)
        if (oldPid <= 0 || oldPid == Process.myPid()) {
            finishAndRemoveTask()
            return
        }
        Breadcrumb.init(this)
        Breadcrumb.log("REBIRTH_HELPER_START", "oldPid=$oldPid helperPid=${Process.myPid()}")

        // post() gives Android one draw opportunity; there is no cosmetic delay.
        window.decorView.post {
            Process.killProcess(oldPid)
            val restart = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_REBIRTH_FROM_PID, oldPid)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(restart)
            finishAndRemoveTask()
            Runtime.getRuntime().exit(0)
        }
    }

    companion object {
        const val EXTRA_OLD_PID = "zcode.rebirth.old_pid"

        fun intent(activity: Activity, oldPid: Int): Intent =
            Intent(activity, ZcodeRebirthActivity::class.java).apply {
                putExtra(EXTRA_OLD_PID, oldPid)
            }
    }
}
