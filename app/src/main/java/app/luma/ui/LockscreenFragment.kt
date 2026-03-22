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
import app.luma.data.Constants
import app.luma.data.Prefs
import app.luma.data.StatusBarSectionType
import app.luma.helper.formatLockscreenDateText
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.PrefsToggleTextButton
import app.luma.ui.compose.SettingsComposable.SelectorButton
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.ToggleSelectorButton
import app.luma.ui.compose.SettingsItemSpacing

class LockscreenFragment : Fragment() {
    private lateinit var prefs: Prefs
    private val actionState = mutableStateOf(Constants.Action.OpenApp)
    private val dateTapActionState = mutableStateOf(Constants.Action.Disabled)
    private val connectivityActionState = mutableStateOf(Constants.Action.Disabled)
    private val batteryActionState = mutableStateOf(Constants.Action.Disabled)
    private val timeFormatState = mutableStateOf(Prefs.TimeFormat.TwentyFourHour)
    private val hasNotificationPermission = mutableStateOf(false)
    private val notificationIndicatorState = mutableStateOf(true)
    private val dateFormatState = mutableStateOf(Prefs.LockscreenDateFormat.ShortWeekday)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onResume() {
        super.onResume()
        hasNotificationPermission.value = hasNotificationListenerPermission()
        actionState.value = prefs.getLockscreenShortcutAction()
        dateTapActionState.value = prefs.getLockscreenDateTapAction()
        connectivityActionState.value = prefs.getSectionAction(StatusBarSectionType.CELLULAR)
        batteryActionState.value = prefs.getSectionAction(StatusBarSectionType.BATTERY)
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
                    PrefsToggleTextButton(
                        title = stringResource(R.string.lockscreen_enabled),
                        initialValue = prefs.lockscreenGateEnabled,
                        onValueChange = { prefs.lockscreenGateEnabled = it },
                    )
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
                    PrefsToggleTextButton(
                        title = stringResource(R.string.lockscreen_show_date),
                        initialValue = prefs.lockscreenDateEnabled,
                        onValueChange = { prefs.lockscreenDateEnabled = it },
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
                        value = formatLockscreenDateText(dateFormatState.value),
                        onClick = {
                            findNavController().navigate(R.id.action_lockscreenFragment_to_lockscreenDateFormatFragment)
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.status_bar_connectivity_tap),
                        value = actionDisplayValue(connectivityActionState.value, prefs, StatusBarSectionType.CELLULAR),
                        onClick = {
                            findNavController().navigate(
                                R.id.action_lockscreenFragment_to_gestureActionFragment,
                                bundleOf(GestureActionFragment.SECTION_TYPE to StatusBarSectionType.CELLULAR.name),
                            )
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.status_bar_battery_tap),
                        value = actionDisplayValue(batteryActionState.value, prefs, StatusBarSectionType.BATTERY),
                        onClick = {
                            findNavController().navigate(
                                R.id.action_lockscreenFragment_to_gestureActionFragment,
                                bundleOf(GestureActionFragment.SECTION_TYPE to StatusBarSectionType.BATTERY.name),
                            )
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.lockscreen_date_tap),
                        value = lockscreenDateTapActionDisplayValue(dateTapActionState.value, prefs),
                        onClick = {
                            findNavController().navigate(
                                R.id.action_lockscreenFragment_to_gestureActionFragment,
                                bundleOf(GestureActionFragment.LOCKSCREEN_DATE_TAP to true),
                            )
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.lockscreen_shortcut),
                        value = lockscreenActionDisplayValue(actionState.value, prefs),
                        onClick = {
                            findNavController().navigate(
                                R.id.action_lockscreenFragment_to_gestureActionFragment,
                                bundleOf(GestureActionFragment.LOCKSCREEN_SHORTCUT to true),
                            )
                        },
                    )
                }
            }
        }
    }
}
