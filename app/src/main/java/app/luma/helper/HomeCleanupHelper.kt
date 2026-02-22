package app.luma.helper

import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import app.luma.data.AppModel
import app.luma.data.Constants
import app.luma.data.GestureType
import app.luma.data.HomeLayout
import app.luma.data.Prefs
import app.luma.data.ShortcutEntry

object HomeCleanupHelper {
    private var onHomeCleanupCallback: (() -> Unit)? = null
    private var onAppListCleanupCallback: (() -> Unit)? = null

    fun setOnHomeCleanupCallback(callback: (() -> Unit)?) {
        onHomeCleanupCallback = callback
    }

    fun setOnAppListCleanupCallback(callback: (() -> Unit)?) {
        onAppListCleanupCallback = callback
    }

    fun cleanupRemovedPackage(
        context: Context,
        packageName: String,
        userHandle: UserHandle = android.os.Process.myUserHandle(),
    ) {
        val prefs = Prefs.getInstance(context)
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val userSerial = userManager.getSerialNumberForUser(userHandle)
        var needsHomeRefresh = false
        var needsAppListRefresh = false

        for (i in 0 until HomeLayout.TOTAL_SLOTS) {
            val appModel = prefs.getHomeAppModel(i)
            if (shouldClear(appModel, packageName, userHandle)) {
                prefs.setHomeAppModel(i, emptyAppModel())
                needsHomeRefresh = true
            }
        }

        for (gestureType in GestureType.entries) {
            val appModel = prefs.getGestureApp(gestureType)
            if (shouldClear(appModel, packageName, userHandle)) {
                prefs.setGestureApp(gestureType, emptyAppModel())
                needsHomeRefresh = true
            }
        }

        val hiddenApps = prefs.hiddenApps
        val hiddenKey = prefs.getHiddenAppKey(packageName, userSerial)
        if (hiddenApps.contains(hiddenKey)) {
            prefs.hiddenApps = hiddenApps.filterNot { it == hiddenKey }.toMutableSet()
            needsAppListRefresh = true
        }

        val pinnedShortcuts = prefs.pinnedShortcuts
        val filteredShortcuts =
            pinnedShortcuts
                .filterNot { entry ->
                    ShortcutEntry.parse(entry)?.packageName == packageName
                }.toSet()
        if (filteredShortcuts.size != pinnedShortcuts.size) {
            prefs.pinnedShortcuts = filteredShortcuts
            needsAppListRefresh = true
        }

        val pinnedApps = prefs.pinnedApps
        val serial = if (userSerial < 0L) prefs.mySerial else userSerial
        val filtered =
            pinnedApps
                .filterNot { it.packageName == packageName && it.userSerial == serial }
                .filterNot {
                    it.packageName == Constants.PINNED_SHORTCUT_PACKAGE &&
                        it.activityName.startsWith("$packageName|")
                }
        if (filtered.size != pinnedApps.size) {
            prefs.pinnedApps = filtered
            needsAppListRefresh = true
        }

        val hiddenShortcutIds = prefs.hiddenShortcutIds
        val filteredHiddenIds =
            hiddenShortcutIds
                .filterNot { it.startsWith("$packageName|") }
                .toSet()
        if (filteredHiddenIds.size != hiddenShortcutIds.size) {
            prefs.hiddenShortcutIds = filteredHiddenIds
        }

        if (needsHomeRefresh) {
            onHomeCleanupCallback?.invoke()
        }
        if (needsAppListRefresh) {
            onAppListCleanupCallback?.invoke()
        }
    }

    private fun shouldClear(
        appModel: AppModel,
        packageName: String,
        userHandle: UserHandle,
    ): Boolean {
        if (appModel.appLabel.isEmpty()) return false
        if (appModel.appPackage == packageName && appModel.user == userHandle) return true

        if (appModel.appPackage == Constants.PINNED_SHORTCUT_PACKAGE) {
            val activityName = appModel.appActivityName
            if (activityName.contains("|")) {
                val shortcutPackage = activityName.substringBefore("|")
                if (shortcutPackage == packageName) return true
            }
        }

        return false
    }

    private fun emptyAppModel(): AppModel =
        AppModel(
            appLabel = "",
            appPackage = "",
            appAlias = "",
            appActivityName = "",
            user = android.os.Process.myUserHandle(),
            key = null,
        )
}
