package com.vandam.luma.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vandam.luma.R

object HomeLayout {
    const val APPS_PER_PAGE = 6
    const val MAX_PAGES = 5
    const val MIN_PAGES = 1
    const val MIN_APPS_PER_PAGE = 1
    const val TOTAL_SLOTS = APPS_PER_PAGE * MAX_PAGES
}

object Constants {
    enum class Action(
        @field:StringRes val displayNameRes: Int,
    ) {
        Disabled(R.string.action_disabled),
        OpenApp(R.string.action_open_app),
        NetworkShortcutLight(R.string.action_network_shortcut_light),
        ShowNotificationList(R.string.action_show_notification_list),
        ShowRecents(R.string.action_show_recents),
        LockScreen(R.string.action_lock_screen),
        ToggleFlashlight(R.string.action_toggle_flashlight),
        ;

        fun displayName(context: Context): String = context.getString(displayNameRes)

        @Composable
        fun displayName(): String = stringResource(displayNameRes)
    }
}
