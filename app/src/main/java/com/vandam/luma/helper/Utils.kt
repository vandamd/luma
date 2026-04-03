package com.vandam.luma.helper

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import com.vandam.luma.BuildConfig
import com.vandam.luma.R
import com.vandam.luma.data.AppEntryType
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.Constants
import com.vandam.luma.data.ManagedAppCatalog
import com.vandam.luma.data.PinnedAppEntry
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.ShortcutEntry
import com.vandam.luma.data.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

private const val TAG = "Utils"
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

    if (packageName == Constants.PINNED_SHORTCUT_PACKAGE || appModel.entryType == AppEntryType.PinnedShortcut) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            showToast(appContext, appContext.getString(R.string.toast_shortcuts_require_android))
            return true
        }

        val parts = appActivityName.split("|", limit = 2)
        val shortcutPackage = parts.getOrNull(0) ?: return true
        val shortcutId = parts.getOrNull(1) ?: return true

        try {
            val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcher.startShortcut(shortcutPackage, shortcutId, null, null, android.os.Process.myUserHandle())
        } catch (_: Exception) {
            showToast(appContext, appContext.getString(R.string.toast_unable_to_launch_shortcut))
        }
        return true
    }

    val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
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
                if (appActivityName.isNotEmpty()) {
                    ComponentName(packageName, appActivityName)
                } else {
                    ComponentName(packageName, activityInfo.last().name)
                }
            }
        }

    try {
        launcher.startMainActivity(component, userHandle, null, null)
    } catch (_: SecurityException) {
        try {
            launcher.startMainActivity(component, android.os.Process.myUserHandle(), null, null)
        } catch (_: Exception) {
            showToast(appContext, appContext.getString(R.string.toast_unable_to_launch_app))
        }
    } catch (_: Exception) {
        showToast(appContext, appContext.getString(R.string.toast_unable_to_launch_app))
    }
    return true
}

suspend fun getAppsList(
    context: Context,
    includeHidden: Boolean = false,
): MutableList<AppModel> =
    withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()

        try {
            val prefs = Prefs.getInstance(context)
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val collator = Collator.getInstance()

            for (profile in userManager.userProfiles) {
                if (userManager.isQuietModeEnabled(profile)) continue

                for (app in launcherApps.getActivityList(null, profile)) {
                    if (app.applicationInfo.packageName == BuildConfig.APPLICATION_ID) continue

                    val appAlias =
                        prefs.getAppAlias(app.applicationInfo.packageName).ifEmpty {
                            prefs.getAppAlias(app.label.toString())
                        }

                    val appModel =
                        AppModel(
                            app.label.toString(),
                            collator.getCollationKey(app.label.toString()),
                            app.applicationInfo.packageName,
                            app.componentName.className,
                            profile,
                            appAlias,
                            false,
                            AppEntryType.LauncherApp,
                        )

                    appList.add(appModel)
                }
            }

            val userHandle = android.os.Process.myUserHandle()
            val hiddenShortcutIds = prefs.hiddenShortcutIds

            for (entry in prefs.pinnedShortcuts) {
                val shortcut = ShortcutEntry.parse(entry) ?: continue

                if (!includeHidden && hiddenShortcutIds.contains(shortcut.payload)) continue

                val shortcutModel =
                    AppModel(
                        appLabel = shortcut.label,
                        key = collator.getCollationKey(shortcut.label),
                        appPackage = Constants.PINNED_SHORTCUT_PACKAGE,
                        appActivityName = shortcut.payload,
                        user = userHandle,
                        appAlias = "",
                        hasNotification = false,
                        entryType = AppEntryType.PinnedShortcut,
                    )
                appList.add(shortcutModel)
            }

            for (tool in Tool.entries) {
                if (!prefs.isToolEnabled(tool) || (!includeHidden && prefs.isToolHidden(tool))) continue
                val alias = prefs.getAppAlias(tool.packageName)
                appList.add(tool.toAppModel(context, collator, alias))
            }

            val pinnedIndex = prefs.pinnedApps.withIndex().associate { it.value to it.index }
            val pinnedModels = mutableListOf<Pair<Int, AppModel>>()
            val unpinnedModels = mutableListOf<AppModel>()

            for (appModel in appList) {
                val serial = userManager.getSerialNumberForUser(appModel.user)
                val entry = PinnedAppEntry(appModel.appPackage, appModel.appActivityName, serial)
                val index = pinnedIndex[entry]
                if (index != null) {
                    pinnedModels.add(index to appModel)
                } else {
                    unpinnedModels.add(appModel)
                }
            }

            pinnedModels.sortBy { it.first }
            unpinnedModels.sortBy {
                it.displayName.lowercase()
            }

            appList.clear()
            appList.addAll(pinnedModels.map { it.second })
            appList.addAll(unpinnedModels)

            val packagesWithNotifications = LumaNotificationListener.getActiveNotificationPackages()
            appList.forEach { appModel ->
                appModel.hasNotification = packagesWithNotifications.contains(appModel.appPackage)
            }
        } catch (e: java.lang.Exception) {
            if (BuildConfig.DEBUG) {
                Log.d("backup", "$e")
            }
        }
        appList
    }

suspend fun getHiddenAppsList(context: Context): MutableList<AppModel> =
    withContext(Dispatchers.IO) {
        val prefs = Prefs.getInstance(context)
        val hiddenAppsSet = prefs.hiddenApps
        val hiddenShortcutIds = prefs.hiddenShortcutIds
        val appList: MutableList<AppModel> = mutableListOf()

        val collator = Collator.getInstance()
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val myHandle = android.os.Process.myUserHandle()
        val mySerial = userManager.getSerialNumberForUser(myHandle)

        for (entry in hiddenAppsSet) {
            val parts = entry.split("|")
            val packageName = parts[0]
            val serial = if (parts.size == 2) parts[1].toLongOrNull() ?: mySerial else mySerial
            val tool = Tool.fromPackageName(packageName)
            if (tool != null) {
                if (!prefs.isToolEnabled(tool)) continue
                val alias = prefs.getAppAlias(packageName)
                appList.add(tool.toAppModel(context, collator, alias))
                continue
            }
            val userHandle = userManager.getUserForSerialNumber(serial) ?: continue
            val activities = launcherApps.getActivityList(packageName, userHandle)
            if (activities.isEmpty()) continue
            val app = activities[0]
            val appName = app.label.toString()
            val appKey = collator.getCollationKey(appName)
            val alias = prefs.getAppAlias(packageName).ifEmpty { prefs.getAppAlias(appName) }
            appList.add(AppModel(appName, appKey, packageName, "", userHandle, alias, false, AppEntryType.LauncherApp))
        }

        for (entry in prefs.pinnedShortcuts) {
            val shortcut = ShortcutEntry.parse(entry) ?: continue

            if (!hiddenShortcutIds.contains(shortcut.payload)) continue

            val shortcutModel =
                AppModel(
                    appLabel = shortcut.label,
                    key = collator.getCollationKey(shortcut.label),
                    appPackage = Constants.PINNED_SHORTCUT_PACKAGE,
                    appActivityName = shortcut.payload,
                    user = myHandle,
                    appAlias = "",
                    hasNotification = false,
                    entryType = AppEntryType.PinnedShortcut,
                )
            appList.add(shortcutModel)
        }

        appList.sort()
        appList
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

fun openAppInfo(
    context: Context,
    userHandle: UserHandle,
    packageName: String,
) {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val component = launcher.getActivityList(packageName, userHandle).firstOrNull()?.componentName
    if (component != null) {
        launcher.startAppDetailsActivity(component, userHandle, null, null)
    } else {
        showToast(context, context.getString(R.string.toast_unable_to_open_app_info))
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

@Suppress("SpellCheckingInspection")
@SuppressLint("WrongConstant")
fun expandNotificationDrawer(context: Context) {
    // Source: https://stackoverflow.com/a/51132142
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandNotificationsPanel")
        method.invoke(statusBarService)
    } catch (e: Exception) {
        Log.e(TAG, "Error expanding notification drawer", e)
    }
}

@Suppress("SpellCheckingInspection")
@SuppressLint("WrongConstant")
fun expandQuickSettings(context: Context) {
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandSettingsPanel")
        method.invoke(statusBarService)
    } catch (e: Exception) {
        Log.e(TAG, "Error expanding quick settings", e)
    }
}
