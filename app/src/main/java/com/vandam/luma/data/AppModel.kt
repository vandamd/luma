package com.vandam.luma.data

import android.os.UserHandle
import java.text.CollationKey

enum class AppEntryType {
    LauncherApp,
    Tool,
    ManagedApp,
}

data class AppModel(
    val appLabel: String,
    val key: CollationKey?,
    val appPackage: String,
    val appActivityName: String,
    val user: UserHandle,
    var hasNotification: Boolean = false,
    val entryType: AppEntryType = AppEntryType.LauncherApp,
) : Comparable<AppModel> {
    val displayName: String
        get() = appLabel

    override fun compareTo(other: AppModel): Int =
        when {
            key != null && other.key != null -> key.compareTo(other.key)
            else -> appLabel.compareTo(other.appLabel, true)
        }
}
