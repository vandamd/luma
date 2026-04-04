package com.vandam.luma.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.vandam.luma.R

class PackageInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = intent.getInstallConfirmationIntent() ?: return
                confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmationIntent)
            }

            PackageInstaller.STATUS_SUCCESS -> Unit

            else -> {
                val appLabel =
                    intent.getStringExtra(EXTRA_APP_LABEL).orEmpty().ifBlank {
                        context.getString(R.string.app_name)
                    }
                val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                showToast(
                    context,
                    statusMessage.ifBlank {
                        context.getString(R.string.toast_unable_to_install_release, appLabel)
                    },
                    Toast.LENGTH_LONG,
                )
            }
        }
    }

    private fun Intent.getInstallConfirmationIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_INTENT)
        }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.vandam.luma.action.INSTALL_STATUS"
        const val EXTRA_APP_LABEL = "app_label"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}
