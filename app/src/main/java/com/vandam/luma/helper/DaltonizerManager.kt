package com.vandam.luma.helper

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager

object DaltonizerManager {
    private const val TAG = "DaltonizerManager"
    private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val DALTONIZER_MODE = "accessibility_display_daltonizer"

    private const val PREFS_FILENAME = "com.vandam.luma.daltonizer"
    private const val PREF_DID_WE_DISABLE = "did_we_disable"
    private const val PREF_WAS_ENABLED = "was_enabled"
    private const val PREF_PREVIOUS_MODE = "previous_mode"

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

    fun recoverFromProcessDeath(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_FILENAME, 0)
        if (!prefs.getBoolean(PREF_DID_WE_DISABLE, false)) return

        val wasEnabled = prefs.getBoolean(PREF_WAS_ENABLED, false)
        val previousMode = prefs.getInt(PREF_PREVIOUS_MODE, 0)

        if (wasEnabled) {
            try {
                Settings.Secure.putInt(context.contentResolver, DALTONIZER_MODE, previousMode)
                Settings.Secure.putInt(context.contentResolver, DALTONIZER_ENABLED, 1)
                Log.d(TAG, "Restored after process death (mode: $previousMode)")
            } catch (exception: SecurityException) {
                Log.e(TAG, "No permission to restore daltonizer after process death", exception)
            }
        }

        prefs.edit().clear().apply()
        wasDaltonizerEnabled = false
        previousDaltonizerMode = 0
        didWeDisableDaltonizer = false
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
                persistState(context, wasEnabled = true, previousMode = daltonizerMode)
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
            clearPersistedState(context)
            return
        }

        try {
            Settings.Secure.putInt(context.contentResolver, DALTONIZER_MODE, previousDaltonizerMode)
            Settings.Secure.putInt(context.contentResolver, DALTONIZER_ENABLED, 1)
            Log.d(TAG, "Restored (mode: $previousDaltonizerMode)")
            didWeDisableDaltonizer = false
            clearPersistedState(context)
        } catch (exception: SecurityException) {
            Log.e(TAG, "No permission to restore daltonizer", exception)
        }
    }

    private fun persistState(
        context: Context,
        wasEnabled: Boolean,
        previousMode: Int,
    ) {
        context
            .getSharedPreferences(PREFS_FILENAME, 0)
            .edit()
            .putBoolean(PREF_DID_WE_DISABLE, true)
            .putBoolean(PREF_WAS_ENABLED, wasEnabled)
            .putInt(PREF_PREVIOUS_MODE, previousMode)
            .commit()
    }

    private fun clearPersistedState(context: Context) {
        context.getSharedPreferences(PREFS_FILENAME, 0).edit().clear().commit()
    }
}
