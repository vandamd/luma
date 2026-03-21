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
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.PrefsToggleTextButton
import app.luma.ui.compose.SettingsComposable.SelectorButton
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.ToggleSelectorButton
import app.luma.ui.compose.SettingsItemSpacing

class LockscreenFragment : Fragment() {
    private lateinit var prefs: Prefs
    private val actionState = mutableStateOf(Constants.Action.OpenApp)
    private val hasNotificationPermission = mutableStateOf(false)
    private val notificationIndicatorState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onResume() {
        super.onResume()
        hasNotificationPermission.value = hasNotificationListenerPermission()
        actionState.value = prefs.getLockscreenShortcutAction()
        notificationIndicatorState.value = prefs.lockscreenClockNotificationIndicator
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
