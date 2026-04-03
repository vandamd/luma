package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.Constants
import com.vandam.luma.data.Constants.Action
import com.vandam.luma.data.Constants.AppDrawerFlag
import com.vandam.luma.data.GestureScope
import com.vandam.luma.data.GestureType
import com.vandam.luma.data.HomeLayout
import com.vandam.luma.data.ManagedAppCatalog
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.StatusBarSectionType
import com.vandam.luma.data.Tool
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.compose.SettingsComposable.SimpleTextButton
import java.text.Collator

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
        val context = LocalContext.current
        val currentAction = getCurrentAction()
        val currentLaunchTarget = getCurrentLaunchTarget()
        Column {
            SettingsHeader(
                title = stringResource(displayInfo.titleRes),
                onBack = ::goBack,
            )

            ContentContainer {
                if (supportsDisabledAction()) {
                    SimpleTextButton(
                        title = stringResource(R.string.action_disabled),
                        underline = currentAction == Action.Disabled,
                        onClick = { handleActionSelection(Action.Disabled) },
                    )
                }

                if (supportsToolSelection()) {
                    for (target in availableLaunchTargets()) {
                        SimpleTextButton(
                            title = stringResource(R.string.action_open_app_name, target.displayName),
                            underline = currentAction == Action.OpenApp && launchTargetKey(currentLaunchTarget) == launchTargetKey(target),
                            onClick = { handleLaunchTargetSelection(target) },
                        )
                    }
                }

                for (action in availableActions()) {
                    SimpleTextButton(
                        title = action.displayName(),
                        underline = currentAction == action,
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

    private fun getCurrentLaunchTarget(): AppModel? {
        if (getCurrentAction() != Action.OpenApp) return null

        return gestureType?.let { prefs.getGestureApp(it, gestureScope) }
            ?: sectionType?.let { prefs.getSectionApp(it) }
            ?: if (lockscreenShortcut) {
                prefs.getLockscreenShortcutApp()
            } else if (lockscreenDateTap) {
                prefs.getLockscreenDateTapApp()
            } else {
                keymapType?.let { getKeymapApp(it) }
            }
    }

    private fun supportsToolSelection(): Boolean = true

    private fun supportsDisabledAction(): Boolean =
        !lockscreenShortcut &&
            (keymapType == null || keymapType == "camera_long_press" || keymapType?.startsWith("scrollwheel") == true)

    private fun availableLaunchTargets(): List<AppModel> {
        val collator = Collator.getInstance()
        val orderedTargets = linkedMapOf<String, AppModel>()

        if (keymapType?.startsWith("camera") == true) {
            val cameraTarget =
                Tool.Camera.toAppModel(
                    context = requireContext(),
                    collator = collator,
                    alias = prefs.getAppAlias(Tool.Camera.packageName),
                )
            orderedTargets[launchTargetKey(cameraTarget)] = cameraTarget
        }

        for (index in 0 until HomeLayout.TOTAL_SLOTS) {
            val appModel = prefs.getHomeAppModel(index)
            if (appModel.appPackage.isBlank()) continue
            val tool = Tool.fromPackageName(appModel.appPackage)
            val managedApp = ManagedAppCatalog.fromPackageName(appModel.appPackage)
            val resolvedModel =
                when {
                    tool != null ->
                        tool.toAppModel(
                            context = requireContext(),
                            collator = collator,
                            alias = prefs.getAppAlias(tool.packageName),
                        )

                    managedApp != null ->
                        managedApp.toAppModel(
                            collator = collator,
                            alias = prefs.getAppAlias(managedApp.packageName),
                        )

                    else -> appModel
                }
            orderedTargets[launchTargetKey(resolvedModel)] = resolvedModel
        }

        if (orderedTargets.isEmpty()) {
            if (keymapType?.startsWith("camera") == true) {
                val phoneTarget =
                    Tool.Phone.toAppModel(
                        context = requireContext(),
                        collator = collator,
                        alias = prefs.getAppAlias(Tool.Phone.packageName),
                    )
                val settingsTarget =
                    Tool.Settings.toAppModel(
                        context = requireContext(),
                        collator = collator,
                        alias = prefs.getAppAlias(Tool.Settings.packageName),
                    )
                orderedTargets[launchTargetKey(phoneTarget)] = phoneTarget
                orderedTargets[launchTargetKey(settingsTarget)] = settingsTarget
            } else if (keymapType == null) {
                val phoneTarget =
                    Tool.Phone.toAppModel(
                        context = requireContext(),
                        collator = collator,
                        alias = prefs.getAppAlias(Tool.Phone.packageName),
                    )
                val settingsTarget =
                    Tool.Settings.toAppModel(
                        context = requireContext(),
                        collator = collator,
                        alias = prefs.getAppAlias(Tool.Settings.packageName),
                    )
                orderedTargets[launchTargetKey(phoneTarget)] = phoneTarget
                orderedTargets[launchTargetKey(settingsTarget)] = settingsTarget
            }
        }

        return orderedTargets.values.toList()
    }

    private fun getKeymapApp(type: String): AppModel =
        when (type) {
            "camera_press" -> prefs.getCameraKeyPressApp()
            "camera_long_press" -> prefs.getCameraKeyLongPressApp()
            "scrollwheel_press" -> prefs.getScrollwheelButtonPressApp()
            "scrollwheel_long_press" -> prefs.getScrollwheelButtonLongPressApp()
            else -> prefs.getCameraKeyPressApp()
        }

    private fun availableActions(): Array<Action> =
        if (keymapType != null) {
            emptyArray()
        } else if (gestureType != null) {
            Constants.Action
                .values()
                .filterNot {
                    it == Action.Disabled ||
                    it == Action.ToggleFlashlight ||
                        it == Action.OpenApp ||
                        it == Action.ShowAppList ||
                        it == Action.OpenQuickSettings ||
                        it == Action.ShowNotification ||
                        it == Action.ShowRecents
                }.toTypedArray()
        } else if (lockscreenShortcut) {
            Constants.Action
                .values()
                .filterNot {
                    it == Action.Disabled ||
                        it == Action.LockScreen ||
                        it == Action.ToggleFlashlight ||
                        it == Action.OpenApp ||
                        it == Action.ShowAppList
                }.toTypedArray()
        } else if (lockscreenDateTap) {
            Constants.Action
                .values()
                .filterNot {
                    it == Action.Disabled ||
                    it == Action.LockScreen ||
                        it == Action.ToggleFlashlight ||
                        it == Action.OpenApp ||
                        it == Action.ShowAppList
                }
                .toTypedArray()
        } else {
            Constants.Action
                .values()
                .filterNot {
                    it == Action.Disabled ||
                    it == Action.ToggleFlashlight ||
                        it == Action.OpenApp ||
                        it == Action.ShowAppList
                }
                .toTypedArray()
        }

    private fun handleLaunchTargetSelection(appModel: AppModel) {
        if (gestureType != null) {
            val type = gestureType ?: return
            prefs.setGestureAction(type, Action.OpenApp, gestureScope)
            prefs.setGestureApp(type, appModel, gestureScope)
        } else if (sectionType != null) {
            val section = sectionType ?: return
            prefs.setSectionAction(section, Action.OpenApp)
            prefs.setSectionApp(section, appModel)
        } else if (lockscreenShortcut) {
            prefs.setLockscreenShortcutAction(Action.OpenApp)
            prefs.setLockscreenShortcutApp(appModel)
        } else if (lockscreenDateTap) {
            prefs.setLockscreenDateTapAction(Action.OpenApp)
            prefs.setLockscreenDateTapApp(appModel)
        } else if (keymapType != null) {
            val type = keymapType ?: return
            setKeymapAction(type, Action.OpenApp)
            setKeymapApp(type, appModel)
        }

        goBack()
    }

    private fun launchTargetKey(appModel: AppModel?): String =
        if (appModel == null) {
            ""
        } else {
            "${appModel.appPackage}|${appModel.appActivityName}"
        }

    private fun setKeymapApp(
        type: String,
        appModel: AppModel,
    ) {
        when (type) {
            "camera_press" -> prefs.setCameraKeyPressApp(appModel)
            "camera_long_press" -> prefs.setCameraKeyLongPressApp(appModel)
            "scrollwheel_press" -> prefs.setScrollwheelButtonPressApp(appModel)
            "scrollwheel_long_press" -> prefs.setScrollwheelButtonLongPressApp(appModel)
        }
    }

    private fun handleActionSelection(action: Action) {
        setCurrentAction(action)
        goBack()
    }
}
