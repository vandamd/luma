package com.vandam.luma.helper

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.LauncherApps
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import com.vandam.luma.R
import com.vandam.luma.data.AppEntryType
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.ManagedAppCatalog
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.Tool

private const val LIGHT_OS_PACKAGE = "com.lightos"
private const val LIGHT_OS_MAIN_ACTIVITY = "com.lightos.MainActivity"

fun performHapticFeedback(context: Context) {
    try {
        if (!Prefs.getInstance(context).hapticsEnabled) return
        vibrateDevice(context)
    } catch (e: Exception) {
        // Continue if haptic feedback fails
    }
}

fun vibrateDevice(
    context: Context,
    durationMs: Long = 42L,
): Boolean =
    runCatching {
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        true
    }.getOrDefault(false)

fun performAppTapHapticFeedback(context: Context) {
    if (Prefs.getInstance(context).hapticsAppTapEnabled) {
        performHapticFeedback(context)
    }
}

fun performLongPressHapticFeedback(context: Context) {
    if (Prefs.getInstance(context).hapticsLongPressEnabled) {
        performHapticFeedback(context)
    }
}

fun performGestureActionHapticFeedback(context: Context) {
    if (Prefs.getInstance(context).hapticsGestureActionsEnabled) {
        performHapticFeedback(context)
    }
}

fun performStatusBarPressHapticFeedback(context: Context) {
    if (Prefs.getInstance(context).hapticsStatusBarPressEnabled) {
        performHapticFeedback(context)
    }
}

fun showToast(
    context: Context,
    message: String,
    duration: Int = Toast.LENGTH_SHORT,
) {
    Toast
        .makeText(context.applicationContext, message, duration)
        .apply {
            setGravity(Gravity.CENTER, 0, 0)
        }.show()
}

fun launchLightOsRoute(
    context: Context,
    route: String,
): Boolean {
    val appContext = context.applicationContext
    val actionService = ActionService.instance()
    val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("lightos://$route")).apply {
            component = ComponentName(LIGHT_OS_PACKAGE, LIGHT_OS_MAIN_ACTIVITY)
            // LightOS is a singleTask launcher activity. Reusing its existing task avoids
            // a cold React Native boot, which otherwise flashes the default app screen
            // before the deep link is applied.
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

    try {
        actionService?.showToolLaunchMask(Prefs.getInstance(appContext).isDarkTheme())
        context.startActivity(intent)
    } catch (_: Exception) {
        actionService?.cancelToolLaunchMask()
        showToast(appContext, appContext.getString(R.string.toast_unable_to_launch_app))
    }
    return true
}

fun launchAppModel(
    context: Context,
    appModel: AppModel,
): Boolean {
    val appContext = context.applicationContext
    val packageName = appModel.appPackage
    val appActivityName = appModel.appActivityName
    val userHandle = appModel.user

    val tool = Tool.fromPackageName(packageName)
    if (appModel.entryType == AppEntryType.Tool || tool != null) {
        return launchLightOsRoute(context, (tool ?: Tool.fromId(appActivityName) ?: return true).lightOsRoute)
    }

    val managedApp = ManagedAppCatalog.fromPackageName(packageName)
    if (appModel.entryType == AppEntryType.ManagedApp || managedApp != null) {
        if (managedApp != null && ManagedAppManager.handleManagedAppLaunch(context, managedApp)) {
            return true
        }
    }

    val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    if (appActivityName.isNotEmpty()) {
        val storedComponent = ComponentName(packageName, appActivityName)
        if (startMainActivity(launcher, storedComponent, userHandle)) {
            return true
        }
    }

    val activityInfo = launcher.getActivityList(packageName, userHandle)
    val component =
        when (activityInfo.size) {
            0 -> {
                showToast(appContext, appContext.getString(R.string.toast_app_not_found))
                return true
            }

            1 -> {
                ComponentName(packageName, activityInfo[0].name)
            }

            else -> {
                ComponentName(packageName, activityInfo.last().name)
            }
        }

    if (!startMainActivity(launcher, component, userHandle)) {
        showToast(appContext, appContext.getString(R.string.toast_unable_to_launch_app))
    }
    return true
}

private fun startMainActivity(
    launcher: LauncherApps,
    component: ComponentName,
    userHandle: UserHandle,
): Boolean =
    try {
        launcher.startMainActivity(component, userHandle, null, null)
        true
    } catch (_: SecurityException) {
        try {
            launcher.startMainActivity(component, android.os.Process.myUserHandle(), null, null)
            true
        } catch (_: Exception) {
            false
        }
    } catch (_: Exception) {
        false
    }

fun getDefaultLauncherPackage(context: Context): String {
    val intent = Intent()
    intent.action = Intent.ACTION_MAIN
    intent.addCategory(Intent.CATEGORY_HOME)
    val packageManager = context.packageManager
    val result = packageManager.resolveActivity(intent, 0)
    return if (result?.activityInfo != null) {
        result.activityInfo.packageName
    } else {
        "android"
    }
}

fun initActionService(context: Context): ActionService? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val actionService = ActionService.instance()
        if (actionService != null) {
            return actionService
        } else {
            openAccessibilitySettings(context)
        }
    } else {
        showToast(context, context.getString(R.string.toast_action_requires_android_p), Toast.LENGTH_LONG)
    }

    return null
}

fun isAccessibilityEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabledServices.any { it.resolveInfo.serviceInfo.name == ActionService::class.java.name }
}

fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    val cs = ComponentName(context.packageName, ActionService::class.java.name).flattenToString()
    val bundle = Bundle()
    bundle.putString(":settings:fragment_args_key", cs)
    intent.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(":settings:fragment_args_key", cs)
        putExtra(":settings:show_fragment_args", bundle)
    }
    context.startActivity(intent)
}

fun openNotificationPolicyAccessSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallback =
            Intent(Settings.ACTION_SETTINGS).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(fallback)
    }
}

fun hideStatusBar(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.hide(WindowInsets.Type.statusBars())
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.apply {
            systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }
}

fun showStatusBar(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.show(WindowInsets.Type.statusBars())
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.apply {
            systemUiVisibility = systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
        }
    }
}

fun uninstallApp(
    context: Context,
    appPackage: String,
) {
    val intent = Intent(Intent.ACTION_DELETE)
    intent.data = Uri.parse("package:$appPackage")
    context.startActivity(intent)
}

fun dp2px(
    resources: Resources,
    dp: Int,
): Int =
    TypedValue
        .applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics,
        ).toInt()
