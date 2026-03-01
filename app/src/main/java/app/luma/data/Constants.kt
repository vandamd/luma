package app.luma.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.luma.R

object HomeLayout {
    const val APPS_PER_PAGE = 6
    const val MAX_PAGES = 5
    const val MIN_PAGES = 1
    const val MIN_APPS_PER_PAGE = 1
    const val TOTAL_SLOTS = APPS_PER_PAGE * MAX_PAGES
}

object Constants {
    const val REQUEST_CONFIRM_PIN_SHORTCUT = "android.content.pm.action.CONFIRM_PIN_SHORTCUT"
    const val PINNED_SHORTCUT_PACKAGE = "__pinned_shortcut__"

    enum class AppDrawerFlag {
        LaunchApp,
        HiddenApps,
        SetHomeApp,
        SetSwipeLeft,
        SetSwipeRight,
        SetSwipeUp,
        SetSwipeDown,
        SetDoubleTap,
        SetStatusBarCellular,
        SetStatusBarTime,
        SetStatusBarBattery,
    }

    enum class Action(
        @field:StringRes val displayNameRes: Int,
    ) {
        Disabled(R.string.action_disabled),
        OpenApp(R.string.action_open_app),
        ShowAppList(R.string.action_show_app_list),
        ShowNotificationList(R.string.action_show_notification_list),
        ShowRecents(R.string.action_show_recents),
        LockScreen(R.string.action_lock_screen),
        OpenQuickSettings(R.string.action_quick_settings),
        ShowNotification(R.string.action_show_notifications),
        ;

        fun displayName(context: Context): String = context.getString(displayNameRes)

        @Composable
        fun displayName(): String = stringResource(displayNameRes)
    }
}
