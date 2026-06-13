package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.helper.formatLockscreenDateText
import com.vandam.luma.ui.compose.NotificationIndicatorSelector
import com.vandam.luma.ui.compose.SelectorButton
import com.vandam.luma.ui.compose.SettingsScreen
import com.vandam.luma.ui.compose.SimpleTextButton

class LockscreenFragment : Fragment() {
    private val prefs by lazy { Prefs.getInstance(requireContext()) }
    private val timeFormatState = mutableStateOf(Prefs.TimeFormat.TwentyFourHour)
    private val hasNotificationPermission = mutableStateOf(false)
    private val notificationIndicatorState = mutableStateOf(true)
    private val dateFormatState = mutableStateOf(Prefs.LockscreenDateFormat.None)

    override fun onResume() {
        super.onResume()
        hasNotificationPermission.value = hasNotificationListenerPermission()
        timeFormatState.value = prefs.timeFormat
        notificationIndicatorState.value = prefs.lockscreenClockNotificationIndicator
        dateFormatState.value = prefs.lockscreenDateFormat
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    fun Screen() {
        SettingsScreen(
            title = stringResource(R.string.settings_lockscreen),
            onBack = ::goBack,
        ) {
            SimpleTextButton(stringResource(R.string.settings_shortcuts)) {
                findNavController().navigate(R.id.action_lockscreenFragment_to_lockscreenTapShortcutsFragment)
            }
            NotificationIndicatorSelector(
                label = stringResource(R.string.notifications_indicator),
                hasPermission = hasNotificationPermission.value,
                visible = notificationIndicatorState.value,
                visibleText = stringResource(R.string.notifications_visible_next_to_clock),
                hiddenText = stringResource(R.string.notifications_not_visible),
                permissionRequiredText = stringResource(R.string.notifications_not_visible_permission_required),
                onVisibilityChange = {
                    notificationIndicatorState.value = it
                    prefs.lockscreenClockNotificationIndicator = it
                },
                onRequestPermission = ::openNotificationListenerSettings,
            )
            SelectorButton(
                label = stringResource(R.string.status_bar_time_format),
                value =
                    when (timeFormatState.value) {
                        Prefs.TimeFormat.Standard -> stringResource(R.string.status_bar_time_standard)
                        Prefs.TimeFormat.TwelveHour -> stringResource(R.string.status_bar_time_twelve)
                        Prefs.TimeFormat.TwentyFourHour -> stringResource(R.string.status_bar_time_24h)
                    },
                onClick = {
                    findNavController().navigate(R.id.action_lockscreenFragment_to_timeFormatFragment)
                },
            )
            SelectorButton(
                label = stringResource(R.string.lockscreen_date_format),
                value =
                    if (dateFormatState.value == Prefs.LockscreenDateFormat.None) {
                        stringResource(R.string.option_none)
                    } else {
                        formatLockscreenDateText(dateFormatState.value)
                    },
                onClick = {
                    findNavController().navigate(R.id.action_lockscreenFragment_to_lockscreenDateFormatFragment)
                },
            )
        }
    }
}
