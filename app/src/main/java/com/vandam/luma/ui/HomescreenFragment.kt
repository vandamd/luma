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
import com.vandam.luma.ui.compose.NotificationIndicatorSelector
import com.vandam.luma.ui.compose.SelectorButton
import com.vandam.luma.ui.compose.SettingsScreen
import com.vandam.luma.ui.compose.SimpleTextButton

class HomescreenFragment : Fragment() {
    private val prefs by lazy { Prefs.getInstance(requireContext()) }
    private val hasNotificationPermission = mutableStateOf(false)
    private val notificationIndicatorState = mutableStateOf(true)

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
        SettingsScreen(
            title = stringResource(R.string.settings_homescreen),
            onBack = ::goBack,
        ) {
            SimpleTextButton(stringResource(R.string.settings_shortcuts)) {
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
            NotificationIndicatorSelector(
                label = stringResource(R.string.notifications_indicator),
                hasPermission = hasNotificationPermission.value,
                visible = notificationIndicatorState.value,
                visibleText = stringResource(R.string.notifications_visible_next_to_apps),
                hiddenText = stringResource(R.string.notifications_not_visible),
                permissionRequiredText = stringResource(R.string.notifications_not_visible_permission_required),
                onVisibilityChange = {
                    notificationIndicatorState.value = it
                    prefs.showNotificationIndicator = it
                },
                onRequestPermission = ::openNotificationListenerSettings,
            )
        }
    }
}
