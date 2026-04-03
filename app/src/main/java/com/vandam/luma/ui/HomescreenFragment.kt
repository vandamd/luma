package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.MessageText
import com.vandam.luma.ui.compose.SettingsComposable.SelectorButton
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.compose.SettingsComposable.SimpleTextButton
import com.vandam.luma.ui.compose.SettingsComposable.ToggleSelectorButton
import com.vandam.luma.ui.compose.SettingsItemSpacing

class HomescreenFragment : Fragment() {
    private lateinit var prefs: Prefs
    private val hasNotificationPermission = mutableStateOf(false)
    private val notificationIndicatorState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onResume() {
        super.onResume()
        hasNotificationPermission.value = hasNotificationListenerPermission()
        notificationIndicatorState.value = prefs.showNotificationIndicator
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
                Column(verticalArrangement = Arrangement.spacedBy(SettingsItemSpacing)) {
                    SimpleTextButton(stringResource(R.string.settings_gestures)) {
                        findNavController().navigate(R.id.action_homescreenFragment_to_gesturesFragment)
                    }
                    SimpleTextButton(stringResource(R.string.homescreen_reorder_tools)) {
                        findNavController().navigate(R.id.action_homescreenFragment_to_reorderToolsFragment)
                    }
                    SelectorButton(
                        label = stringResource(R.string.pages_page_indicator_position),
                        value =
                            when (prefs.pageIndicatorPosition) {
                                Prefs.PageIndicatorPosition.Left -> stringResource(R.string.position_left)
                                Prefs.PageIndicatorPosition.Right -> stringResource(R.string.position_right)
                                Prefs.PageIndicatorPosition.Hidden -> stringResource(R.string.position_hidden)
                            },
                        onClick = {
                            findNavController().navigate(R.id.pageIndicatorPositionFragment)
                        },
                    )
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
                }
            }
        }
    }
}
