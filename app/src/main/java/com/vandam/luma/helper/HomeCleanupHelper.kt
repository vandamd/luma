package com.vandam.luma.helper

import android.content.Context
import android.os.UserHandle
import com.vandam.luma.data.AppEntryType
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.Constants
import com.vandam.luma.data.GestureScope
import com.vandam.luma.data.GestureType
import com.vandam.luma.data.HomeLayout
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.ShortcutEntry

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
        var needsHomeRefresh = false
        var needsAppListRefresh = false

        for (i in 0 until HomeLayout.TOTAL_SLOTS) {
            val appModel = prefs.getHomeAppModel(i)
            if (shouldClear(appModel, packageName, userHandle, prefs)) {
                prefs.setHomeAppModel(i, emptyAppModel())
                needsHomeRefresh = true
            }
        }

        for (gestureScope in GestureScope.entries) {
            for (gestureType in GestureType.entries) {
                val appModel = prefs.getGestureApp(gestureType, gestureScope)
                if (shouldClear(appModel, packageName, userHandle, prefs)) {
                    prefs.setGestureApp(gestureType, emptyAppModel(), gestureScope)
                    if (gestureScope == GestureScope.Homescreen) {
                        needsHomeRefresh = true
                    }
                }
            }
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
        prefs: Prefs,
    ): Boolean {
        if (appModel.appLabel.isEmpty()) return false
        if (appModel.entryType == AppEntryType.ManagedApp && prefs.isManagedAppEnabled(packageName)) {
            return false
        }
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
            appActivityName = "",
            user = android.os.Process.myUserHandle(),
            key = null,
        )
}
