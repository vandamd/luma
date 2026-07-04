package com.vandam.luma.helper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.vandam.luma.MainActivity

class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainProcessPid = intent.getIntExtra(EXTRA_MAIN_PROCESS_PID, -1)
        if (mainProcessPid > 0 && mainProcessPid != Process.myPid()) {
            Process.killProcess(mainProcessPid)
        }

        Handler(Looper.getMainLooper()).postDelayed(
            {
                startActivity(MainActivity.createLumaHomeIntent(this, suppressLauncherIntentHandling = true))
                finish()
                Process.killProcess(Process.myPid())
            },
            RELAUNCH_DELAY_MS,
        )
    }

    companion object {
        private const val EXTRA_MAIN_PROCESS_PID = "com.vandam.luma.extra.MAIN_PROCESS_PID"
        private const val RELAUNCH_DELAY_MS = 250L

        fun createIntent(context: Context): Intent =
            Intent(context, RestartActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_MAIN_PROCESS_PID, Process.myPid())
            }
    }
}
