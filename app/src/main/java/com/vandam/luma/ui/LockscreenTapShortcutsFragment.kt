package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.Constants
import com.vandam.luma.data.GestureScope
import com.vandam.luma.data.GestureType
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.StatusBarSectionType
import com.vandam.luma.ui.compose.SelectorButton
import com.vandam.luma.ui.compose.SettingsScreen

class LockscreenTapShortcutsFragment : Fragment() {
    private lateinit var prefs: Prefs
    private val swipeLeftActionState = mutableStateOf(Constants.Action.Disabled)
    private val swipeRightActionState = mutableStateOf(Constants.Action.Disabled)
    private val swipeDownActionState = mutableStateOf(Constants.Action.Disabled)
    private val swipeUpActionState = mutableStateOf(Constants.Action.Disabled)
    private val doubleTapActionState = mutableStateOf(Constants.Action.Disabled)
    private val clockTapActionState = mutableStateOf(Constants.Action.Disabled)
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
        swipeLeftActionState.value = prefs.getGestureAction(GestureType.SWIPE_LEFT, GestureScope.Lockscreen)
        swipeRightActionState.value = prefs.getGestureAction(GestureType.SWIPE_RIGHT, GestureScope.Lockscreen)
        swipeDownActionState.value = prefs.getGestureAction(GestureType.SWIPE_DOWN, GestureScope.Lockscreen)
        swipeUpActionState.value = prefs.getGestureAction(GestureType.SWIPE_UP, GestureScope.Lockscreen)
        doubleTapActionState.value = prefs.getGestureAction(GestureType.DOUBLE_TAP, GestureScope.Lockscreen)
        clockTapActionState.value = prefs.getLockscreenClockTapAction()
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
        SettingsScreen(
            title = stringResource(R.string.settings_shortcuts),
            onBack = ::goBack,
        ) {
            GestureButton(
                label = stringResource(R.string.gesture_swipe_left),
                type = GestureType.SWIPE_LEFT,
                action = swipeLeftActionState.value,
            )
            GestureButton(
                label = stringResource(R.string.gesture_swipe_right),
                type = GestureType.SWIPE_RIGHT,
                action = swipeRightActionState.value,
            )
            GestureButton(
                label = stringResource(R.string.gesture_swipe_down),
                type = GestureType.SWIPE_DOWN,
                action = swipeDownActionState.value,
            )
            GestureButton(
                label = stringResource(R.string.gesture_swipe_up),
                type = GestureType.SWIPE_UP,
                action = swipeUpActionState.value,
            )
            GestureButton(
                label = stringResource(R.string.gesture_double_tap),
                type = GestureType.DOUBLE_TAP,
                action = doubleTapActionState.value,
            )
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
                label = stringResource(R.string.lockscreen_clock_tap),
                value = lockscreenClockTapActionDisplayValue(clockTapActionState.value, prefs),
                onClick = {
                    findNavController().navigate(
                        R.id.action_lockscreenTapShortcutsFragment_to_gestureActionFragment,
                        bundleOf(GestureActionFragment.LOCKSCREEN_CLOCK_TAP to true),
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

    @Composable
    private fun GestureButton(
        label: String,
        type: GestureType,
        action: Constants.Action,
    ) {
        val value = actionDisplayValue(action, prefs.getGestureApp(type, GestureScope.Lockscreen).displayName)
        SelectorButton(
            label = label,
            value = value,
            onClick = {
                findNavController().navigate(
                    R.id.gestureActionFragment,
                    bundleOf(
                        GestureActionFragment.GESTURE_TYPE to type.name,
                        GestureActionFragment.GESTURE_SCOPE to GestureScope.Lockscreen.name,
                    ),
                )
            },
        )
    }
}
