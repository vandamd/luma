package com.vandam.luma.ui

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
import com.vandam.luma.R
import com.vandam.luma.data.Constants
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.StatusBarSectionType
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.SelectorButton
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.compose.SettingsItemSpacing

class LockscreenTapShortcutsFragment : Fragment() {
    private lateinit var prefs: Prefs
    private val actionState = mutableStateOf(Constants.Action.OpenApp)
    private val dateTapActionState = mutableStateOf(Constants.Action.Disabled)
    private val connectivityActionState = mutableStateOf(Constants.Action.Disabled)
    private val batteryActionState = mutableStateOf(Constants.Action.Disabled)
    private val shortcutIconState = mutableStateOf(Prefs.LockscreenShortcutIcon.Ring)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onResume() {
        super.onResume()
        actionState.value = prefs.getLockscreenShortcutAction()
        dateTapActionState.value = prefs.getLockscreenDateTapAction()
        connectivityActionState.value = prefs.getSectionAction(StatusBarSectionType.CELLULAR)
        batteryActionState.value = prefs.getSectionAction(StatusBarSectionType.BATTERY)
        shortcutIconState.value = prefs.lockscreenShortcutIcon
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
                title = stringResource(R.string.lockscreen_tap_shortcuts),
                onBack = ::goBack,
            )

            ContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(SettingsItemSpacing)) {
                    SelectorButton(
                        label = stringResource(R.string.status_bar_connectivity_tap),
                        value = actionDisplayValue(connectivityActionState.value, prefs, StatusBarSectionType.CELLULAR),
                        onClick = {
                            findNavController().navigate(
                                R.id.action_lockscreenTapShortcutsFragment_to_gestureActionFragment,
                                bundleOf(GestureActionFragment.SECTION_TYPE to StatusBarSectionType.CELLULAR.name),
                            )
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.status_bar_battery_tap),
                        value = actionDisplayValue(batteryActionState.value, prefs, StatusBarSectionType.BATTERY),
                        onClick = {
                            findNavController().navigate(
                                R.id.action_lockscreenTapShortcutsFragment_to_gestureActionFragment,
                                bundleOf(GestureActionFragment.SECTION_TYPE to StatusBarSectionType.BATTERY.name),
                            )
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.lockscreen_date_tap),
                        value = lockscreenDateTapActionDisplayValue(dateTapActionState.value, prefs),
                        onClick = {
                            findNavController().navigate(
                                R.id.action_lockscreenTapShortcutsFragment_to_gestureActionFragment,
                                bundleOf(GestureActionFragment.LOCKSCREEN_DATE_TAP to true),
                            )
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.lockscreen_shortcut),
                        value = lockscreenActionDisplayValue(actionState.value, prefs),
                        onClick = {
                            findNavController().navigate(
                                R.id.action_lockscreenTapShortcutsFragment_to_gestureActionFragment,
                                bundleOf(GestureActionFragment.LOCKSCREEN_SHORTCUT to true),
                            )
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.lockscreen_shortcut_icon),
                        value = LockscreenShortcutIconFragment.iconDisplayName(shortcutIconState.value),
                        iconRes = LockscreenShortcutIconFragment.iconDrawableRes(shortcutIconState.value),
                        onClick = {
                            findNavController().navigate(R.id.action_lockscreenTapShortcutsFragment_to_lockscreenShortcutIconFragment)
                        },
                    )
                }
            }
        }
    }
}
