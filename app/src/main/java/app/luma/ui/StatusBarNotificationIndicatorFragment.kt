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
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.luma.R
import app.luma.data.Prefs
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SelectorButton
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.ToggleSelectorButton
import app.luma.ui.compose.SettingsItemSpacing

class StatusBarNotificationIndicatorFragment : Fragment() {
    private lateinit var prefs: Prefs
    private val hasNotificationPermission = mutableStateOf(false)
    private val enabledState = mutableStateOf(true)
    private val sectionState = mutableStateOf(Prefs.NotificationIndicatorSection.Time)
    private val alignmentState = mutableStateOf(Prefs.NotificationIndicatorAlignment.After)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onResume() {
        super.onResume()
        hasNotificationPermission.value = hasNotificationListenerPermission()
        enabledState.value = prefs.showStatusBarNotificationIndicator
        sectionState.value = prefs.notificationIndicatorSection
        alignmentState.value = prefs.notificationIndicatorAlignment
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
                title = stringResource(R.string.status_bar_notification_indicator),
                onBack = ::goBack,
            )

            ContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(SettingsItemSpacing)) {
                    ToggleSelectorButton(
                        label = stringResource(R.string.status_bar_enabled),
                        value =
                            if (hasNotificationPermission.value) {
                                stringResource(if (enabledState.value) R.string.notifications_visible else R.string.notifications_not_visible)
                            } else {
                                stringResource(R.string.notifications_not_visible_permission_required)
                            },
                        checked = hasNotificationPermission.value && enabledState.value,
                        onCheckedChange = {
                            if (hasNotificationPermission.value) {
                                enabledState.value = it
                                prefs.showStatusBarNotificationIndicator = it
                            } else {
                                openNotificationListenerSettings()
                            }
                        },
                        onClick = {
                            if (hasNotificationPermission.value) {
                                enabledState.value = !enabledState.value
                                prefs.showStatusBarNotificationIndicator = enabledState.value
                            } else {
                                openNotificationListenerSettings()
                            }
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.status_bar_notif_section),
                        value =
                            when (sectionState.value) {
                                Prefs.NotificationIndicatorSection.Connectivity -> {
                                    stringResource(
                                        R.string.status_bar_notif_section_cellular,
                                    )
                                }

                                Prefs.NotificationIndicatorSection.Time -> {
                                    stringResource(R.string.status_bar_notif_section_time)
                                }

                                Prefs.NotificationIndicatorSection.Battery -> {
                                    stringResource(R.string.status_bar_notif_section_battery)
                                }
                            },
                        onClick = {
                            findNavController().navigate(
                                R.id.action_statusBarNotificationIndicatorFragment_to_notificationIndicatorSectionFragment,
                            )
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.status_bar_notif_alignment),
                        value =
                            when (alignmentState.value) {
                                Prefs.NotificationIndicatorAlignment.Before -> stringResource(R.string.status_bar_notif_alignment_before)
                                Prefs.NotificationIndicatorAlignment.After -> stringResource(R.string.status_bar_notif_alignment_after)
                            },
                        onClick = {
                            findNavController().navigate(
                                R.id.action_statusBarNotificationIndicatorFragment_to_notificationIndicatorAlignmentFragment,
                            )
                        },
                    )
                }
            }
        }
    }
}
