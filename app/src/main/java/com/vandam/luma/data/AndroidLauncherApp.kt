package com.vandam.luma.data

import android.content.Context
import android.os.Process
import kotlinx.serialization.Serializable
import java.text.Collator

@Serializable
data class AndroidLauncherApp(
    val label: String,
    val packageName: String,
    val activityName: String,
) {
    val key: String
        get() = "$packageName|$activityName"

    fun toAppModel(
        context: Context,
        collator: Collator,
    ): AppModel {
        val resolvedLabel = Prefs.getInstance(context).resolveHomeItemLabel(packageName, activityName, label)
        return AppModel(
            appLabel = resolvedLabel,
            key = collator.getCollationKey(resolvedLabel),
            appPackage = packageName,
            appActivityName = activityName,
            user = Process.myUserHandle(),
            hasNotification = false,
            entryType = AppEntryType.LauncherApp,
        )
    }
}
