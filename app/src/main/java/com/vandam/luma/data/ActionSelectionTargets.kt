package com.vandam.luma.data

import androidx.annotation.StringRes
import com.vandam.luma.R
import com.vandam.luma.data.Constants.Action

data class GestureBinding(
    val type: GestureType,
    val scope: GestureScope,
)

sealed interface AppSelectionTarget {
    @get:StringRes val titleRes: Int
    val allowsDisabledAction: Boolean get() = true
    val includesCameraTarget: Boolean get() = false
    val disallowedActions: Set<Action> get() = emptySet()

    fun getAction(prefs: Prefs): Action

    fun setAction(
        prefs: Prefs,
        action: Action,
    )

    fun getApp(prefs: Prefs): AppModel

    fun setApp(
        prefs: Prefs,
        appModel: AppModel,
    )
}

data class GestureSelectionTarget(
    val binding: GestureBinding,
) : AppSelectionTarget {
    override val titleRes: Int = binding.type.titleRes

    override fun getAction(prefs: Prefs): Action = prefs.getGestureAction(binding.type, binding.scope)

    override fun setAction(
        prefs: Prefs,
        action: Action,
    ) {
        prefs.setGestureAction(binding.type, action, binding.scope)
    }

    override fun getApp(prefs: Prefs): AppModel = prefs.getGestureApp(binding.type, binding.scope)

    override fun setApp(
        prefs: Prefs,
        appModel: AppModel,
    ) {
        prefs.setGestureApp(binding.type, appModel, binding.scope)
    }
}

data class StatusBarSelectionTarget(
    val sectionType: StatusBarSectionType,
) : AppSelectionTarget {
    override val titleRes: Int = sectionType.titleRes

    override fun getAction(prefs: Prefs): Action = prefs.getSectionAction(sectionType)

    override fun setAction(
        prefs: Prefs,
        action: Action,
    ) {
        prefs.setSectionAction(sectionType, action)
    }

    override fun getApp(prefs: Prefs): AppModel = prefs.getSectionApp(sectionType)

    override fun setApp(
        prefs: Prefs,
        appModel: AppModel,
    ) {
        prefs.setSectionApp(sectionType, appModel)
    }
}

data class KeymapSelectionTarget(
    val keymapType: KeymapType,
) : AppSelectionTarget {
    override val titleRes: Int = keymapType.titleRes
    override val allowsDisabledAction: Boolean = keymapType.allowsDisabledAction
    override val includesCameraTarget: Boolean = keymapType.includesCameraTarget

    override fun getAction(prefs: Prefs): Action =
        when (keymapType) {
            KeymapType.CameraPress -> prefs.getCameraKeyPressAction()
            KeymapType.CameraLongPress -> prefs.getCameraKeyLongPressAction()
            KeymapType.ScrollwheelPress -> prefs.getScrollwheelButtonPressAction()
            KeymapType.ScrollwheelLongPress -> prefs.getScrollwheelButtonLongPressAction()
        }

    override fun setAction(
        prefs: Prefs,
        action: Action,
    ) {
        when (keymapType) {
            KeymapType.CameraPress -> prefs.setCameraKeyPressAction(action)
            KeymapType.CameraLongPress -> prefs.setCameraKeyLongPressAction(action)
            KeymapType.ScrollwheelPress -> prefs.setScrollwheelButtonPressAction(action)
            KeymapType.ScrollwheelLongPress -> prefs.setScrollwheelButtonLongPressAction(action)
        }
    }

    override fun getApp(prefs: Prefs): AppModel =
        when (keymapType) {
            KeymapType.CameraPress -> prefs.getCameraKeyPressApp()
            KeymapType.CameraLongPress -> prefs.getCameraKeyLongPressApp()
            KeymapType.ScrollwheelPress -> prefs.getScrollwheelButtonPressApp()
            KeymapType.ScrollwheelLongPress -> prefs.getScrollwheelButtonLongPressApp()
        }

    override fun setApp(
        prefs: Prefs,
        appModel: AppModel,
    ) {
        when (keymapType) {
            KeymapType.CameraPress -> prefs.setCameraKeyPressApp(appModel)
            KeymapType.CameraLongPress -> prefs.setCameraKeyLongPressApp(appModel)
            KeymapType.ScrollwheelPress -> prefs.setScrollwheelButtonPressApp(appModel)
            KeymapType.ScrollwheelLongPress -> prefs.setScrollwheelButtonLongPressApp(appModel)
        }
    }
}

data object LockscreenShortcutSelectionTarget : AppSelectionTarget {
    override val titleRes: Int = R.string.lockscreen_shortcut
    override val allowsDisabledAction: Boolean = false
    override val disallowedActions: Set<Action> = setOf(Action.LockScreen)

    override fun getAction(prefs: Prefs): Action = prefs.getLockscreenShortcutAction()

    override fun setAction(
        prefs: Prefs,
        action: Action,
    ) {
        prefs.setLockscreenShortcutAction(action)
    }

    override fun getApp(prefs: Prefs): AppModel = prefs.getLockscreenShortcutApp()

    override fun setApp(
        prefs: Prefs,
        appModel: AppModel,
    ) {
        prefs.setLockscreenShortcutApp(appModel)
    }
}

data object LockscreenDateTapSelectionTarget : AppSelectionTarget {
    override val titleRes: Int = R.string.lockscreen_date_tap
    override val disallowedActions: Set<Action> = setOf(Action.LockScreen)

    override fun getAction(prefs: Prefs): Action = prefs.getLockscreenDateTapAction()

    override fun setAction(
        prefs: Prefs,
        action: Action,
    ) {
        prefs.setLockscreenDateTapAction(action)
    }

    override fun getApp(prefs: Prefs): AppModel = prefs.getLockscreenDateTapApp()

    override fun setApp(
        prefs: Prefs,
        appModel: AppModel,
    ) {
        prefs.setLockscreenDateTapApp(appModel)
    }
}

enum class KeymapType(
    val argumentValue: String,
    @param:StringRes val titleRes: Int,
) {
    CameraPress("camera_press", R.string.keymaps_camera_press),
    CameraLongPress("camera_long_press", R.string.keymaps_camera_long_press),
    ScrollwheelPress("scrollwheel_press", R.string.keymaps_scrollwheel_press),
    ScrollwheelLongPress("scrollwheel_long_press", R.string.keymaps_scrollwheel_long_press),
    ;

    val includesCameraTarget: Boolean
        get() = this == CameraPress || this == CameraLongPress

    val allowsDisabledAction: Boolean
        get() = this == CameraLongPress || this == ScrollwheelPress || this == ScrollwheelLongPress

    companion object {
        fun fromArgument(value: String?): KeymapType? = values().firstOrNull { it.argumentValue == value }
    }
}

private val GestureType.titleRes: Int
    @StringRes
    get() =
        when (this) {
            GestureType.SWIPE_LEFT -> R.string.gesture_swipe_left
            GestureType.SWIPE_RIGHT -> R.string.gesture_swipe_right
            GestureType.SWIPE_UP -> R.string.gesture_swipe_up
            GestureType.SWIPE_DOWN -> R.string.gesture_swipe_down
            GestureType.DOUBLE_TAP -> R.string.gesture_double_tap
        }

private val StatusBarSectionType.titleRes: Int
    @StringRes
    get() =
        when (this) {
            StatusBarSectionType.CELLULAR -> R.string.status_bar_connectivity_tap
            StatusBarSectionType.BATTERY -> R.string.status_bar_battery_tap
        }
