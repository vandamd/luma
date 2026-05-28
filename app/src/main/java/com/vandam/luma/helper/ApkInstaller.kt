package com.vandam.luma.helper

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.vandam.luma.R
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
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                        if (context !is Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    },
                )
            } catch (_: Exception) {
                showToast(context, context.getString(R.string.toast_unable_to_launch_app), Toast.LENGTH_LONG)
            }
        }
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

        var sessionId = -1
        try {
            sessionId = packageInstaller.createSession(sessionParams)
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
            if (sessionId != -1) {
                packageInstaller.abandonSession(sessionId)
            }
            showToast(
                context,
                context.getString(
                    com.vandam.luma.R.string.toast_unable_to_install_release,
                    appLabel,
                ),
            )
        }
    }

    fun isValidCachedApk(
        context: Context,
        apkFile: File,
        expectedPackageName: String,
        expectedVersionName: String? = null,
    ): Boolean {
        if (!apkFile.exists() || apkFile.length() <= 0) return false

        val packageInfo =
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageArchiveInfo(
                        apkFile.absolutePath,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                }
            } catch (_: Exception) {
                null
            } ?: return false

        val packageName = packageInfo.packageName ?: return false
        if (packageName != expectedPackageName) return false

        val versionName = expectedVersionName?.trim()?.removePrefix("v")?.takeIf { it.isNotBlank() }
        return versionName == null || packageInfo.versionName?.trim()?.removePrefix("v") == versionName
    }
}
