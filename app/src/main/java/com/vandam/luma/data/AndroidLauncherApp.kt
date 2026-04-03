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
        alias: String = "",
    ): AppModel {
        val sortLabel = alias.ifEmpty { label }
        return AppModel(
            appLabel = label,
            key = collator.getCollationKey(sortLabel),
            appPackage = packageName,
            appActivityName = activityName,
            user = Process.myUserHandle(),
            appAlias = alias,
            hasNotification = false,
            entryType = AppEntryType.LauncherApp,
        )
    }
}
