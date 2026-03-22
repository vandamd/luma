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
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SelectorButton
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsItemSpacing

class StatusBarFragment : Fragment() {
    private lateinit var prefs: Prefs
    private val connectivityActionState = mutableStateOf(Constants.Action.Disabled)
    private val batteryActionState = mutableStateOf(Constants.Action.Disabled)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onResume() {
        super.onResume()
        connectivityActionState.value = prefs.getSectionAction(StatusBarSectionType.CELLULAR)
        batteryActionState.value = prefs.getSectionAction(StatusBarSectionType.BATTERY)
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
                        label = stringResource(R.string.status_bar_connectivity_tap),
                        value = actionDisplayValue(connectivityActionState.value, prefs, StatusBarSectionType.CELLULAR),
                        onClick = {
                            findNavController().navigate(
                                R.id.action_statusBarFragment_to_gestureActionFragment,
                                bundleOf(GestureActionFragment.SECTION_TYPE to StatusBarSectionType.CELLULAR.name),
                            )
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.status_bar_battery_tap),
                        value = actionDisplayValue(batteryActionState.value, prefs, StatusBarSectionType.BATTERY),
                        onClick = {
                            findNavController().navigate(
                                R.id.action_statusBarFragment_to_gestureActionFragment,
                                bundleOf(GestureActionFragment.SECTION_TYPE to StatusBarSectionType.BATTERY.name),
                            )
                        },
                    )
                }
            }
        }
    }
}
