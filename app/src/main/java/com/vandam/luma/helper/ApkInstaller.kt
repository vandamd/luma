package com.vandam.luma.helper

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.io.FileInputStream

object ApkInstaller {
    fun canRequestPackageInstalls(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun openUnknownSourcesSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        context.startActivity(intent)
    }

    fun openInstallPrompt(
        context: Context,
        apkFile: File,
        packageName: String,
        appLabel: String,
    ) {
        val packageInstaller = context.packageManager.packageInstaller
        val sessionParams =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }

        val sessionId = packageInstaller.createSession(sessionParams)
        try {
            packageInstaller.openSession(sessionId).use { session ->
                FileInputStream(apkFile).use { input ->
                    session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val statusIntent =
                    Intent(context, PackageInstallStatusReceiver::class.java).apply {
                        action = PackageInstallStatusReceiver.ACTION_INSTALL_STATUS
                        putExtra(PackageInstallStatusReceiver.EXTRA_APP_LABEL, appLabel)
                        putExtra(PackageInstallStatusReceiver.EXTRA_PACKAGE_NAME, packageName)
                    }
                val pendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        statusIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )

                session.commit(pendingIntent.intentSender)
            }
        } catch (exception: Exception) {
            packageInstaller.abandonSession(sessionId)
            showToast(
                context,
                context.getString(
                    com.vandam.luma.R.string.toast_unable_to_install_release,
                    appLabel,
                ),
            )
        }
    }
}
