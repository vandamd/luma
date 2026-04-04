package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import com.vandam.luma.R
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.AppSelectionTarget
import com.vandam.luma.data.Constants
import com.vandam.luma.data.Constants.Action
import com.vandam.luma.data.GestureBinding
import com.vandam.luma.data.GestureScope
import com.vandam.luma.data.GestureSelectionTarget
import com.vandam.luma.data.GestureType
import com.vandam.luma.data.HomeItemsManager
import com.vandam.luma.data.KeymapSelectionTarget
import com.vandam.luma.data.KeymapType
import com.vandam.luma.data.LockscreenDateTapSelectionTarget
import com.vandam.luma.data.LockscreenShortcutSelectionTarget
import com.vandam.luma.data.ManagedAppCatalog
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.StatusBarSectionType
import com.vandam.luma.data.StatusBarSelectionTarget
import com.vandam.luma.data.Tool
import com.vandam.luma.ui.compose.SettingsScreen
import com.vandam.luma.ui.compose.SimpleTextButton
import java.text.Collator

class GestureActionFragment : Fragment() {
    companion object {
        const val GESTURE_TYPE = "gesture_type"
        const val GESTURE_SCOPE = "gesture_scope"
        const val SECTION_TYPE = "section_type"
        const val LOCKSCREEN_SHORTCUT = "lockscreen_shortcut"
        const val LOCKSCREEN_DATE_TAP = "lockscreen_date_tap"
        const val KEYMAP_TYPE = "keymap_type"
    }

    private lateinit var prefs: Prefs
    private lateinit var selectionTarget: AppSelectionTarget

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
        selectionTarget = parseSelectionTarget()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        val currentAction = selectionTarget.getAction(prefs)
        val currentLaunchTarget = if (currentAction == Action.OpenApp) selectionTarget.getApp(prefs) else null
        val actions = availableActions()
        val launchTargets = availableLaunchTargets()
        val showActionsFirst = selectionTarget is KeymapSelectionTarget

        SettingsScreen(
            title = stringResource(selectionTarget.titleRes),
            onBack = ::goBack,
        ) {
            if (selectionTarget.allowsDisabledAction) {
                SimpleTextButton(
                    title = stringResource(R.string.action_disabled),
                    underline = currentAction == Action.Disabled,
                    onClick = { handleActionSelection(Action.Disabled) },
                )
            }

            if (showActionsFirst) {
                for (action in actions) {
                    SimpleTextButton(
                        title = action.displayName(),
                        underline = currentAction == action,
                        onClick = { handleActionSelection(action) },
                    )
                }
            }

            for (launchTarget in launchTargets) {
                SimpleTextButton(
                    title = stringResource(R.string.action_open_app_name, launchTarget.displayName),
                    underline = currentAction == Action.OpenApp && launchTargetKey(currentLaunchTarget) == launchTargetKey(launchTarget),
                    onClick = { handleLaunchTargetSelection(launchTarget) },
                )
            }

            if (!showActionsFirst) {
                for (action in actions) {
                    SimpleTextButton(
                        title = action.displayName(),
                        underline = currentAction == action,
                        onClick = { handleActionSelection(action) },
                    )
                }
            }
        }
    }

    private fun availableLaunchTargets(): List<AppModel> {
        val collator = Collator.getInstance()
        val orderedTargets = linkedMapOf<String, AppModel>()

        if (selectionTarget.includesCameraTarget) {
            val cameraTarget =
                Tool.Camera.toAppModel(
                    context = requireContext(),
                    collator = collator,
                )
            orderedTargets[launchTargetKey(cameraTarget)] = cameraTarget
        }

        HomeItemsManager
            .orderedEnabledItems(requireContext(), prefs)
            .forEach { appModel ->
                val tool = Tool.fromPackageName(appModel.appPackage)
                val managedApp = ManagedAppCatalog.fromPackageName(appModel.appPackage)
                val resolvedModel =
                    when {
                        tool != null ->
                            tool.toAppModel(
                                context = requireContext(),
                                collator = collator,
                            )

                        managedApp != null ->
                            managedApp.toAppModel(collator = collator)

                        else -> appModel
                    }
                orderedTargets[launchTargetKey(resolvedModel)] = resolvedModel
            }

        if (orderedTargets.isEmpty()) {
            val phoneTarget =
                Tool.Phone.toAppModel(
                    context = requireContext(),
                    collator = collator,
                )
            val settingsTarget =
                Tool.Settings.toAppModel(
                    context = requireContext(),
                    collator = collator,
                )
            orderedTargets[launchTargetKey(phoneTarget)] = phoneTarget
            orderedTargets[launchTargetKey(settingsTarget)] = settingsTarget
        }

        return orderedTargets.values.toList()
    }

    private fun availableActions(): Array<Action> {
        val keymapSelectionTarget = selectionTarget as? KeymapSelectionTarget
        if (keymapSelectionTarget != null) {
            return when (keymapSelectionTarget.keymapType) {
                KeymapType.ScrollwheelPress,
                KeymapType.ScrollwheelLongPress,
                -> {
                    arrayOf(Action.ToggleFlashlight)
                }

                else -> {
                    emptyArray()
                }
            }
        }

        val excludedEverywhere =
            setOf(
                Action.OpenApp,
                Action.Disabled,
            )

        return Constants.Action
            .values()
            .filterNot { it in excludedEverywhere || it in selectionTarget.disallowedActions }
            .sortedBy { if (it == Action.ShowRecents) 1 else 0 }
            .toTypedArray()
    }

    private fun handleLaunchTargetSelection(appModel: AppModel) {
        selectionTarget.setAction(prefs, Action.OpenApp)
        selectionTarget.setApp(prefs, appModel)
        goBack()
    }

    private fun handleActionSelection(action: Action) {
        selectionTarget.setAction(prefs, action)
        goBack()
    }

    private fun launchTargetKey(appModel: AppModel?): String =
        if (appModel == null) {
            ""
        } else {
            "${appModel.appPackage}|${appModel.appActivityName}"
        }

    private fun parseSelectionTarget(): AppSelectionTarget {
        arguments?.getString(GESTURE_TYPE)?.takeIf { it.isNotEmpty() }?.let { gestureName ->
            val gestureType = runCatching { GestureType.valueOf(gestureName) }.getOrNull()
            if (gestureType != null) {
                val gestureScope =
                    arguments
                        ?.getString(GESTURE_SCOPE)
                        ?.let { runCatching { GestureScope.valueOf(it) }.getOrNull() }
                        ?: GestureScope.Homescreen
                return GestureSelectionTarget(GestureBinding(gestureType, gestureScope))
            }
        }

        arguments?.getString(SECTION_TYPE)?.takeIf { it.isNotEmpty() }?.let { sectionName ->
            val sectionType = runCatching { StatusBarSectionType.valueOf(sectionName) }.getOrNull()
            if (sectionType != null) {
                return StatusBarSelectionTarget(sectionType)
            }
        }

        if (arguments?.getBoolean(LOCKSCREEN_SHORTCUT, false) == true) {
            return LockscreenShortcutSelectionTarget
        }

        if (arguments?.getBoolean(LOCKSCREEN_DATE_TAP, false) == true) {
            return LockscreenDateTapSelectionTarget
        }

        KeymapType.fromArgument(arguments?.getString(KEYMAP_TYPE))?.let { keymapType ->
            return KeymapSelectionTarget(keymapType)
        }

        error("No gesture, section, or keymap type provided")
    }
}
