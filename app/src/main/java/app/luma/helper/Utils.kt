package app.luma.helper

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
import app.luma.BuildConfig
import app.luma.R
import app.luma.data.AppDrawerItem
import app.luma.data.AppModel
import app.luma.data.Constants
import app.luma.data.FolderModel
import app.luma.data.PinnedAppEntry
import app.luma.data.Prefs
import app.luma.data.ShortcutEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

private const val TAG = "Utils"

fun performHapticFeedback(context: Context) {
    try {
        if (!Prefs.getInstance(context).hapticsEnabled) return
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        vibrator.vibrate(VibrationEffect.createOneShot(42, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (e: Exception) {
        // Continue if haptic feedback fails
    }
}

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

suspend fun getAppsList(
    context: Context,
    grouped: Boolean = true,
): MutableList<AppDrawerItem> =
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
                        )

                    appList.add(appModel)
                }
            }

            val userHandle = android.os.Process.myUserHandle()
            val hiddenShortcutIds = prefs.hiddenShortcutIds

            for (entry in prefs.pinnedShortcuts) {
                val shortcut = ShortcutEntry.parse(entry) ?: continue

                if (hiddenShortcutIds.contains(shortcut.payload)) continue

                val shortcutModel =
                    AppModel(
                        appLabel = shortcut.label,
                        key = collator.getCollationKey(shortcut.label),
                        appPackage = Constants.PINNED_SHORTCUT_PACKAGE,
                        appActivityName = shortcut.payload,
                        user = userHandle,
                        appAlias = "",
                        hasNotification = false,
                    )
                appList.add(shortcutModel)
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
                val label = it.appAlias.ifEmpty { it.appLabel }
                label.lowercase()
            }

            val packagesWithNotifications = LumaNotificationListener.getActiveNotificationPackages()
            val allApps = (pinnedModels.map { it.second } + unpinnedModels).onEach {
                it.hasNotification = packagesWithNotifications.contains(it.appPackage)
            }

            if (prefs.foldersEnabled && grouped) {
                val visibleFolders = prefs.folders.filter { !prefs.isFolderHidden(it.id) }
                val items = mutableListOf<AppDrawerItem>()
                val appsInFolders = mutableSetOf<PinnedAppEntry>()

                prefs.folders.forEach { folder ->
                    folder.apps.forEach { appsInFolders.add(it) }
                }

                // Add folders
                visibleFolders.forEach { folder ->
                    items.add(AppDrawerItem.Folder(folder))
                }

                // Add apps not in folders
                allApps.forEach { app ->
                    val serial = userManager.getSerialNumberForUser(app.user)
                    val entry = PinnedAppEntry(app.appPackage, app.appActivityName, serial)
                    if (!appsInFolders.contains(entry)) {
                        items.add(AppDrawerItem.App(app))
                    }
                }

                // Sort items: folders first (alphabetical), then apps (pinned first, then alphabetical)
                // Wait, Luma usually sorts pinned apps first. Let's stick to that but maybe folders are also special.
                // For now, let's keep folders at the top, sorted by name.
                val sortedItems = items.sortedWith(compareBy({
                    it is AppDrawerItem.App
                }, {
                    if (it is AppDrawerItem.Folder) it.folderModel.name.lowercase() else ""
                }))

                // Actually, let's just return them as they are for now, or refine sorting.
                // The current sorting in Luma is: pinned apps, then alphabetical unpinned apps.
                // With folders: Pinned Apps (not in folders), then Folders (alphabetical), then Unpinned Apps (not in folders).

                val finalItems = mutableListOf<AppDrawerItem>()
                // Pinned apps not in folders
                allApps.filter { app ->
                    val serial = userManager.getSerialNumberForUser(app.user)
                    val entry = PinnedAppEntry(app.appPackage, app.appActivityName, serial)
                    prefs.isPinned(entry) && !appsInFolders.contains(entry)
                }.forEach { finalItems.add(AppDrawerItem.App(it)) }

                // Folders
                visibleFolders.forEach { finalItems.add(AppDrawerItem.Folder(it)) }

                // Unpinned apps not in folders
                allApps.filter { app ->
                    val serial = userManager.getSerialNumberForUser(app.user)
                    val entry = PinnedAppEntry(app.appPackage, app.appActivityName, serial)
                    !prefs.isPinned(entry) && !appsInFolders.contains(entry)
                }.forEach { finalItems.add(AppDrawerItem.App(it)) }

                return@withContext finalItems.toMutableList()
            } else {
                return@withContext allApps.map { AppDrawerItem.App(it) }.toMutableList()
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d("AppList", "$e")
            }
            mutableListOf()
        }
    }

suspend fun getHiddenItemsList(context: Context): MutableList<AppDrawerItem> =
    withContext(Dispatchers.IO) {
        val prefs = Prefs.getInstance(context)
        val hiddenAppsSet = prefs.hiddenApps
        val hiddenFoldersSet = prefs.hiddenFolders
        val hiddenShortcutIds = prefs.hiddenShortcutIds
        val itemList: MutableList<AppDrawerItem> = mutableListOf()

        val collator = Collator.getInstance()
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val myHandle = android.os.Process.myUserHandle()
        val mySerial = userManager.getSerialNumberForUser(myHandle)

        // Hidden folders
        val folders = prefs.folders
        for (folderId in hiddenFoldersSet) {
            folders.find { it.id == folderId }?.let {
                itemList.add(AppDrawerItem.Folder(it))
            }
        }

        // Hidden apps
        val hiddenAppsList = mutableListOf<AppModel>()
        for (entry in hiddenAppsSet) {
            val parts = entry.split("|")
            val packageName = parts[0]
            val serial = if (parts.size == 2) parts[1].toLongOrNull() ?: mySerial else mySerial
            val userHandle = userManager.getUserForSerialNumber(serial) ?: continue
            val activities = launcherApps.getActivityList(packageName, userHandle)
            if (activities.isEmpty()) continue
            val app = activities[0]
            val appName = app.label.toString()
            val appKey = collator.getCollationKey(appName)
            hiddenAppsList.add(AppModel(appName, appKey, packageName, "", userHandle, prefs.getAppAlias(appName), false))
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
                )
            hiddenAppsList.add(shortcutModel)
        }

        hiddenAppsList.sort()
        itemList.addAll(hiddenAppsList.map { AppDrawerItem.App(it) })
        itemList
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
