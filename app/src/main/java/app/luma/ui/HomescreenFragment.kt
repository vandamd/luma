package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.luma.R
import app.luma.data.Prefs
import app.luma.ui.compose.CustomScrollView
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton
import app.luma.ui.compose.SettingsComposable.ToggleSelectorButton

class HomescreenFragment : Fragment() {
    private lateinit var prefs: Prefs
    private val hasNotificationPermission = mutableStateOf(false)
    private val notificationIndicatorState = mutableStateOf(true)
    private val toolIconState = mutableStateOf(true)
    private val pinIconState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onResume() {
        super.onResume()
        hasNotificationPermission.value = hasNotificationListenerPermission()
        notificationIndicatorState.value = prefs.showNotificationIndicator
        toolIconState.value = prefs.showAppDrawerToolIcons
        pinIconState.value = prefs.showAppDrawerPinIcons
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_homescreen),
                onBack = ::goBack,
            )

            ContentContainer {
                CustomScrollView {
                    SimpleTextButton(stringResource(R.string.settings_tools)) {
                        findNavController().navigate(R.id.action_homescreenFragment_to_toolsFragment)
                    }
                    SimpleTextButton(stringResource(R.string.settings_pages)) {
                        findNavController().navigate(R.id.action_homescreenFragment_to_pagesFragment)
                    }
                    SimpleTextButton(stringResource(R.string.settings_gestures)) {
                        findNavController().navigate(R.id.action_homescreenFragment_to_gesturesFragment)
                    }
                    ToggleSelectorButton(
                        label = stringResource(R.string.notifications_indicator),
                        value =
                            if (hasNotificationPermission.value) {
                                if (notificationIndicatorState.value) {
                                    stringResource(R.string.notifications_visible_next_to_apps)
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
                                prefs.showNotificationIndicator = it
                            } else {
                                openNotificationListenerSettings()
                            }
                        },
                        onClick = {
                            if (hasNotificationPermission.value) {
                                notificationIndicatorState.value = !notificationIndicatorState.value
                                prefs.showNotificationIndicator = notificationIndicatorState.value
                            } else {
                                openNotificationListenerSettings()
                            }
                        },
                    )
                    ToggleSelectorButton(
                        label = stringResource(R.string.settings_show_tool_icons),
                        value = stringResource(if (toolIconState.value) R.string.notifications_visible else R.string.notifications_not_visible),
                        checked = toolIconState.value,
                        labelIconRes = R.drawable.tool_bulb_24px,
                        onCheckedChange = {
                            toolIconState.value = it
                            prefs.showAppDrawerToolIcons = it
                        },
                        onClick = {
                            toolIconState.value = !toolIconState.value
                            prefs.showAppDrawerToolIcons = toolIconState.value
                        },
                    )
                    ToggleSelectorButton(
                        label = stringResource(R.string.settings_show_pin_icons),
                        value = stringResource(if (pinIconState.value) R.string.notifications_visible else R.string.notifications_not_visible),
                        checked = pinIconState.value,
                        labelIconRes = R.drawable.pin_24px,
                        onCheckedChange = {
                            pinIconState.value = it
                            prefs.showAppDrawerPinIcons = it
                        },
                        onClick = {
                            pinIconState.value = !pinIconState.value
                            prefs.showAppDrawerPinIcons = pinIconState.value
                        },
                    )
                }
            }
        }
    }
}
