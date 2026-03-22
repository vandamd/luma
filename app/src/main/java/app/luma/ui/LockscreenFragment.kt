package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.luma.R
import app.luma.data.GestureScope
import app.luma.data.Prefs
import app.luma.helper.formatLockscreenDateText
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SelectorButton
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton
import app.luma.ui.compose.SettingsComposable.ToggleSelectorButton
import app.luma.ui.compose.SettingsItemSpacing

class LockscreenFragment : Fragment() {
    private lateinit var prefs: Prefs
    private val timeFormatState = mutableStateOf(Prefs.TimeFormat.TwentyFourHour)
    private val hasNotificationPermission = mutableStateOf(false)
    private val notificationIndicatorState = mutableStateOf(true)
    private val dateFormatState = mutableStateOf(Prefs.LockscreenDateFormat.None)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

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
        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_lockscreen),
                onBack = ::goBack,
            )

            ContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(SettingsItemSpacing)) {
                    SimpleTextButton(stringResource(R.string.settings_gestures)) {
                        findNavController().navigate(
                            R.id.action_lockscreenFragment_to_gesturesFragment,
                            bundleOf(GestureActionFragment.GESTURE_SCOPE to GestureScope.Lockscreen.name),
                        )
                    }
                    SimpleTextButton(stringResource(R.string.lockscreen_tap_shortcuts)) {
                        findNavController().navigate(R.id.action_lockscreenFragment_to_lockscreenTapShortcutsFragment)
                    }
                    ToggleSelectorButton(
                        label = stringResource(R.string.notifications_indicator),
                        value =
                            if (hasNotificationPermission.value) {
                                if (notificationIndicatorState.value) {
                                    stringResource(R.string.notifications_visible_next_to_clock)
                                } else {
                                    stringResource(R.string.notifications_not_visible)
                                }
                            } else {
                                stringResource(R.string.notifications_not_visible_permission_required)
                            },
                        checked = hasNotificationPermission.value && notificationIndicatorState.value,
                        onCheckedChange = {
                            if (hasNotificationPermission.value) {
                                notificationIndicatorState.value = it
                                prefs.lockscreenClockNotificationIndicator = it
                            } else {
                                openNotificationListenerSettings()
                            }
                        },
                        onClick = {
                            if (hasNotificationPermission.value) {
                                notificationIndicatorState.value = !notificationIndicatorState.value
                                prefs.lockscreenClockNotificationIndicator = notificationIndicatorState.value
                            } else {
                                openNotificationListenerSettings()
                            }
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.status_bar_time_format),
                        value =
                            when (timeFormatState.value) {
                                Prefs.TimeFormat.Standard -> stringResource(R.string.status_bar_time_standard)
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
    }
}
