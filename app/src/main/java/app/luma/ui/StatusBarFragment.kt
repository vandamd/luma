package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.luma.R
import app.luma.data.Prefs
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SelectorButton
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton
import app.luma.ui.compose.SettingsItemSpacing

class StatusBarFragment : Fragment() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
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
                title = stringResource(R.string.settings_status_bar),
                onBack = ::goBack,
            )

            ContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(SettingsItemSpacing)) {
                    SelectorButton(
                        label = stringResource(R.string.status_bar_visibility),
                        value =
                            when (prefs.statusBarVisibility) {
                                Prefs.StatusBarVisibility.Disabled -> stringResource(R.string.status_bar_visibility_disabled)
                                Prefs.StatusBarVisibility.Both -> stringResource(R.string.status_bar_visibility_both)
                                Prefs.StatusBarVisibility.Homescreen -> stringResource(R.string.status_bar_visibility_homescreen)
                                Prefs.StatusBarVisibility.Lockscreen -> stringResource(R.string.status_bar_visibility_lockscreen)
                            },
                        onClick = { findNavController().navigate(R.id.action_statusBarFragment_to_statusBarModeFragment) },
                    )
                    SelectorButton(
                        label = stringResource(R.string.status_bar_type),
                        value =
                            when (prefs.statusBarType) {
                                Prefs.StatusBarType.Luma -> stringResource(R.string.status_bar_type_luma)
                                Prefs.StatusBarType.Android -> stringResource(R.string.status_bar_type_android)
                            },
                        onClick = { findNavController().navigate(R.id.action_statusBarFragment_to_statusBarTypeFragment) },
                    )
                    SimpleTextButton(stringResource(R.string.status_bar_notification_indicator)) {
                        findNavController().navigate(R.id.action_statusBarFragment_to_statusBarNotificationIndicatorFragment)
                    }
                    SimpleTextButton(stringResource(R.string.status_bar_connectivity)) {
                        findNavController().navigate(R.id.action_statusBarFragment_to_statusBarConnectivityFragment)
                    }
                    SimpleTextButton(stringResource(R.string.status_bar_time)) {
                        findNavController().navigate(R.id.action_statusBarFragment_to_statusBarTimeFragment)
                    }
                    SimpleTextButton(stringResource(R.string.status_bar_battery)) {
                        findNavController().navigate(R.id.action_statusBarFragment_to_statusBarBatteryFragment)
                    }
                }
            }
        }
    }
}
