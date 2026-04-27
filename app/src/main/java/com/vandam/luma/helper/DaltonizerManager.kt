package com.vandam.luma.helper

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager

object DaltonizerManager {
    private const val TAG = "DaltonizerManager"
    private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val DALTONIZER_MODE = "accessibility_display_daltonizer"

    private var wasDaltonizerEnabled = false
    private var previousDaltonizerMode = 0
    private var didWeDisableDaltonizer = false
    private var currentColorApp: String? = null
    private var pendingColorApp: String? = null

    fun onAppLaunch(
        context: Context,
        packageName: String,
        colorApps: Set<String>,
    ) {
        if (colorApps.contains(packageName)) {
            disableDaltonizer(context)
            pendingColorApp = packageName
        }
    }

    fun onWindowStateChanged(
        context: Context,
        packageName: String,
        colorApps: Set<String>,
    ) {
        if (packageName == context.packageName) {
            if (currentColorApp != null || pendingColorApp != null) {
                restoreDaltonizer(context)
                currentColorApp = null
                pendingColorApp = null
            }
            return
        }

        if ((currentColorApp != null || pendingColorApp != null) && isTransientOverlay(context, packageName)) {
            return
        }

        val isColorApp = colorApps.contains(packageName)
        if (isColorApp) {
            if (currentColorApp != packageName || pendingColorApp != packageName) {
                disableDaltonizer(context)
            }
            currentColorApp = packageName
            pendingColorApp = null
        } else {
            if (currentColorApp != null || pendingColorApp != null) {
                restoreDaltonizer(context)
                currentColorApp = null
                pendingColorApp = null
            }
        }
    }

    private fun isTransientOverlay(
        context: Context,
        packageName: String,
    ): Boolean {
        if (packageName == "com.android.systemui" || packageName == "android") {
            return true
        }
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        return inputMethodManager.enabledInputMethodList.any { it.packageName == packageName }
    }

    fun restoreIfNeeded(context: Context) {
        if (currentColorApp != null || pendingColorApp != null) {
            restoreDaltonizer(context)
            currentColorApp = null
            pendingColorApp = null
        }
    }

    private fun disableDaltonizer(context: Context) {
        if (didWeDisableDaltonizer) return

        val daltonizerEnabled =
            Settings.Secure.getInt(
                context.contentResolver,
                DALTONIZER_ENABLED,
                0,
            )

        if (daltonizerEnabled == 1) {
            val daltonizerMode =
                Settings.Secure.getInt(
                    context.contentResolver,
                    DALTONIZER_MODE,
                    0,
                )

            wasDaltonizerEnabled = true
            previousDaltonizerMode = daltonizerMode
            didWeDisableDaltonizer = true

            try {
                Settings.Secure.putInt(context.contentResolver, DALTONIZER_ENABLED, 0)
                Log.d(TAG, "Disabled (was mode: $daltonizerMode)")
            } catch (exception: SecurityException) {
                Log.e(TAG, "No permission to disable daltonizer", exception)
                didWeDisableDaltonizer = false
            }
        }
    }

    private fun restoreDaltonizer(context: Context) {
        if (!didWeDisableDaltonizer) return
        if (!wasDaltonizerEnabled) {
            didWeDisableDaltonizer = false
            return
        }

        try {
            Settings.Secure.putInt(context.contentResolver, DALTONIZER_MODE, previousDaltonizerMode)
            Settings.Secure.putInt(context.contentResolver, DALTONIZER_ENABLED, 1)
            Log.d(TAG, "Restored (mode: $previousDaltonizerMode)")
        } catch (exception: SecurityException) {
            Log.e(TAG, "No permission to restore daltonizer", exception)
        }

        didWeDisableDaltonizer = false
    }
}
