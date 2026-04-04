package com.vandam.luma.data

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
        collator: Collator,
    ): AppModel {
        return AppModel(
            appLabel = label,
            key = collator.getCollationKey(label),
            appPackage = packageName,
            appActivityName = activityName,
            user = Process.myUserHandle(),
            hasNotification = false,
            entryType = AppEntryType.LauncherApp,
        )
    }
}
