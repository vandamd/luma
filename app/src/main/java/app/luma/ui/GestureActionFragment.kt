package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.luma.MainViewModel
import app.luma.R
import app.luma.data.AppModel
import app.luma.data.Constants
import app.luma.data.Constants.Action
import app.luma.data.Constants.AppDrawerFlag
import app.luma.data.GestureScope
import app.luma.data.GestureType
import app.luma.data.Prefs
import app.luma.data.StatusBarSectionType
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

class GestureActionFragment : Fragment() {
    companion object {
        const val GESTURE_TYPE = "gesture_type"
        const val GESTURE_SCOPE = "gesture_scope"
        const val SECTION_TYPE = "section_type"
        const val LOCKSCREEN_SHORTCUT = "lockscreen_shortcut"
        const val LOCKSCREEN_DATE_TAP = "lockscreen_date_tap"
        const val KEYMAP_TYPE = "keymap_type"

        private val gestureTitleRes =
            mapOf(
                GestureType.SWIPE_LEFT to R.string.gesture_swipe_left,
                GestureType.SWIPE_RIGHT to R.string.gesture_swipe_right,
                GestureType.SWIPE_UP to R.string.gesture_swipe_up,
                GestureType.SWIPE_DOWN to R.string.gesture_swipe_down,
                GestureType.DOUBLE_TAP to R.string.gesture_double_tap,
            )

        private val sectionDisplayInfo =
            mapOf(
                StatusBarSectionType.CELLULAR to
                    ActionDisplayInfo(
                        R.string.status_bar_connectivity_tap,
                        AppDrawerFlag.SetStatusBarCellular,
                    ),
                StatusBarSectionType.TIME to ActionDisplayInfo(R.string.status_bar_time, AppDrawerFlag.SetStatusBarTime),
                StatusBarSectionType.BATTERY to ActionDisplayInfo(R.string.status_bar_battery_tap, AppDrawerFlag.SetStatusBarBattery),
            )

        private val keymapDisplayInfo =
            mapOf(
                "camera_press" to ActionDisplayInfo(R.string.keymaps_camera_press, AppDrawerFlag.SetCameraKeyPress),
                "camera_long_press" to ActionDisplayInfo(R.string.keymaps_camera_long_press, AppDrawerFlag.SetCameraKeyLongPress),
                "scrollwheel_press" to ActionDisplayInfo(R.string.keymaps_scrollwheel_press, AppDrawerFlag.SetScrollwheelButtonPress),
                "scrollwheel_long_press" to
                    ActionDisplayInfo(R.string.keymaps_scrollwheel_long_press, AppDrawerFlag.SetScrollwheelButtonLongPress),
            )
    }

    private data class ActionDisplayInfo(
        @field:StringRes val titleRes: Int,
        val appDrawerFlag: AppDrawerFlag,
    )

    private lateinit var prefs: Prefs
    private var gestureScope = GestureScope.Homescreen
    private var gestureType: GestureType? = null
    private var sectionType: StatusBarSectionType? = null
    private var lockscreenShortcut = false
    private var lockscreenDateTap = false
    private var keymapType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
        gestureScope =
            arguments
                ?.getString(GESTURE_SCOPE)
                ?.let { runCatching { GestureScope.valueOf(it) }.getOrNull() }
                ?: GestureScope.Homescreen
        arguments?.getString(GESTURE_TYPE)?.takeIf { it.isNotEmpty() }?.let {
            gestureType = runCatching { GestureType.valueOf(it) }.getOrNull()
        }
        arguments?.getString(SECTION_TYPE)?.takeIf { it.isNotEmpty() }?.let {
            sectionType = runCatching { StatusBarSectionType.valueOf(it) }.getOrNull()
        }
        lockscreenShortcut = arguments?.getBoolean(LOCKSCREEN_SHORTCUT, false) == true
        lockscreenDateTap = arguments?.getBoolean(LOCKSCREEN_DATE_TAP, false) == true
        keymapType = arguments?.getString(KEYMAP_TYPE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    private fun getDisplayInfo(): ActionDisplayInfo =
        gestureType?.let { gestureDisplayInfo(it) }
            ?: sectionType?.let { sectionDisplayInfo[it] }
            ?: if (lockscreenShortcut) {
                ActionDisplayInfo(R.string.lockscreen_shortcut, AppDrawerFlag.SetLockscreenShortcut)
            } else if (lockscreenDateTap) {
                ActionDisplayInfo(R.string.lockscreen_date_tap, AppDrawerFlag.SetLockscreenDateTap)
            } else {
                keymapType?.let { keymapDisplayInfo[it] }
            }
                ?: error("No gesture, section, or keymap type provided")

    private fun gestureDisplayInfo(type: GestureType): ActionDisplayInfo =
        ActionDisplayInfo(
            titleRes = gestureTitleRes.getValue(type),
            appDrawerFlag =
                when (gestureScope) {
                    GestureScope.Homescreen -> {
                        when (type) {
                            GestureType.SWIPE_LEFT -> AppDrawerFlag.SetSwipeLeft
                            GestureType.SWIPE_RIGHT -> AppDrawerFlag.SetSwipeRight
                            GestureType.SWIPE_UP -> AppDrawerFlag.SetSwipeUp
                            GestureType.SWIPE_DOWN -> AppDrawerFlag.SetSwipeDown
                            GestureType.DOUBLE_TAP -> AppDrawerFlag.SetDoubleTap
                        }
                    }

                    GestureScope.Lockscreen -> {
                        when (type) {
                            GestureType.SWIPE_LEFT -> AppDrawerFlag.SetLockscreenSwipeLeft
                            GestureType.SWIPE_RIGHT -> AppDrawerFlag.SetLockscreenSwipeRight
                            GestureType.SWIPE_UP -> AppDrawerFlag.SetLockscreenSwipeUp
                            GestureType.SWIPE_DOWN -> AppDrawerFlag.SetLockscreenSwipeDown
                            GestureType.DOUBLE_TAP -> AppDrawerFlag.SetLockscreenDoubleTap
                        }
                    }
                },
        )

    @Composable
    fun Screen() {
        val displayInfo = getDisplayInfo()
        Column {
            SettingsHeader(
                title = stringResource(displayInfo.titleRes),
                onBack = ::goBack,
            )

            ContentContainer {
                for (action in availableActions()) {
                    val isSelected = getCurrentAction() == action
                    val buttonText =
                        when {
                            action == Constants.Action.OpenApp && isSelected -> {
                                val appLabel = getAppLabel()
                                if (appLabel.isNotEmpty()) {
                                    stringResource(R.string.action_open_app_name, appLabel)
                                } else {
                                    stringResource(R.string.action_open_app)
                                }
                            }

                            action == Constants.Action.OpenApp -> {
                                stringResource(R.string.action_open_app)
                            }

                            else -> {
                                action.displayName()
                            }
                        }
                    SimpleTextButton(
                        title = buttonText,
                        underline = isSelected,
                        onClick = { handleActionSelection(action) },
                    )
                }
            }
        }
    }

    private fun getCurrentAction(): Action =
        gestureType?.let { prefs.getGestureAction(it, gestureScope) }
            ?: sectionType?.let { prefs.getSectionAction(it) }
            ?: if (lockscreenShortcut) {
                prefs.getLockscreenShortcutAction()
            } else if (lockscreenDateTap) {
                prefs.getLockscreenDateTapAction()
            } else {
                keymapType?.let { getKeymapAction(it) }
            }
                ?: Action.Disabled

    private fun getKeymapAction(type: String): Action =
        when (type) {
            "camera_press" -> prefs.getCameraKeyPressAction()
            "camera_long_press" -> prefs.getCameraKeyLongPressAction()
            "scrollwheel_press" -> prefs.getScrollwheelButtonPressAction()
            "scrollwheel_long_press" -> prefs.getScrollwheelButtonLongPressAction()
            else -> Action.Disabled
        }

    private fun setCurrentAction(action: Action) {
        gestureType?.let { prefs.setGestureAction(it, action, gestureScope) }
            ?: sectionType?.let { prefs.setSectionAction(it, action) }
            ?: if (lockscreenShortcut) {
                prefs.setLockscreenShortcutAction(action)
            } else if (lockscreenDateTap) {
                prefs.setLockscreenDateTapAction(action)
            } else {
                keymapType?.let { setKeymapAction(it, action) }
            }
    }

    private fun setKeymapAction(
        type: String,
        action: Action,
    ) {
        when (type) {
            "camera_press" -> prefs.setCameraKeyPressAction(action)
            "camera_long_press" -> prefs.setCameraKeyLongPressAction(action)
            "scrollwheel_press" -> prefs.setScrollwheelButtonPressAction(action)
            "scrollwheel_long_press" -> prefs.setScrollwheelButtonLongPressAction(action)
        }
    }

    private fun getAppLabel(): String =
        gestureType?.let { prefs.getGestureApp(it, gestureScope).displayName }
            ?: sectionType?.let { prefs.getSectionApp(it).displayName }
            ?: if (lockscreenShortcut) {
                prefs.getLockscreenShortcutApp().displayName
            } else if (lockscreenDateTap) {
                prefs.getLockscreenDateTapApp().displayName
            } else {
                keymapType?.let { getKeymapApp(it).displayName }
            }
                ?: ""

    private fun getKeymapApp(type: String): AppModel =
        when (type) {
            "camera_press" -> prefs.getCameraKeyPressApp()
            "camera_long_press" -> prefs.getCameraKeyLongPressApp()
            "scrollwheel_press" -> prefs.getScrollwheelButtonPressApp()
            "scrollwheel_long_press" -> prefs.getScrollwheelButtonLongPressApp()
            else -> prefs.getCameraKeyPressApp()
        }

    private fun availableActions(): Array<Action> =
        if (keymapType?.startsWith("camera") == true) {
            arrayOf(Action.Disabled, Action.OpenApp)
        } else if (keymapType?.startsWith("scrollwheel") == true) {
            arrayOf(Action.Disabled, Action.OpenApp, Action.ToggleFlashlight)
        } else if (lockscreenShortcut) {
            Constants.Action
                .values()
                .filterNot { it == Action.Disabled || it == Action.LockScreen || it == Action.ToggleFlashlight }
                .toTypedArray()
        } else if (lockscreenDateTap) {
            Constants.Action
                .values()
                .filterNot { it == Action.LockScreen || it == Action.ToggleFlashlight }
                .toTypedArray()
        } else {
            Constants.Action
                .values()
                .filterNot { it == Action.ToggleFlashlight }
                .toTypedArray()
        }

    private fun handleActionSelection(action: Action) {
        if (action == Action.OpenApp) {
            val viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
            viewModel.getAppList()
            val displayInfo = getDisplayInfo()
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf("flag" to displayInfo.appDrawerFlag.toString()),
            )
        } else {
            setCurrentAction(action)
            goBack()
        }
    }
}
