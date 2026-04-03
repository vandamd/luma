package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.compose.SettingsComposable.ToggleTextButton

class HapticsFragment : Fragment() {
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
    private fun Screen() {
        val globalEnabled = remember { mutableStateOf(prefs.hapticsEnabled) }
        val appTapEnabled = remember { mutableStateOf(prefs.hapticsAppTapEnabled) }
        val longPressEnabled = remember { mutableStateOf(prefs.hapticsLongPressEnabled) }
        val gestureActionsEnabled = remember { mutableStateOf(prefs.hapticsGestureActionsEnabled) }
        val statusBarPressEnabled = remember { mutableStateOf(prefs.hapticsStatusBarPressEnabled) }
        val keymapsEnabled = remember { mutableStateOf(prefs.hapticsKeymapsEnabled) }

        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_haptics),
                onBack = ::goBack,
            )

            ContentContainer {
                ToggleTextButton(
                    title = stringResource(R.string.haptics_enabled),
                    checked = globalEnabled.value,
                    onCheckedChange = {
                        globalEnabled.value = it
                        prefs.hapticsEnabled = it
                    },
                    onClick = {
                        globalEnabled.value = !globalEnabled.value
                        prefs.hapticsEnabled = globalEnabled.value
                    },
                )
                ToggleTextButton(
                    title = stringResource(R.string.haptics_app_tap),
                    checked = globalEnabled.value && appTapEnabled.value,
                    onCheckedChange = {
                        appTapEnabled.value = it
                        prefs.hapticsAppTapEnabled = it
                    },
                    onClick = {
                        appTapEnabled.value = !appTapEnabled.value
                        prefs.hapticsAppTapEnabled = appTapEnabled.value
                    },
                    enabled = globalEnabled.value,
                )
                ToggleTextButton(
                    title = stringResource(R.string.haptics_long_press),
                    checked = globalEnabled.value && longPressEnabled.value,
                    onCheckedChange = {
                        longPressEnabled.value = it
                        prefs.hapticsLongPressEnabled = it
                    },
                    onClick = {
                        longPressEnabled.value = !longPressEnabled.value
                        prefs.hapticsLongPressEnabled = longPressEnabled.value
                    },
                    enabled = globalEnabled.value,
                )
                ToggleTextButton(
                    title = stringResource(R.string.haptics_gesture_actions),
                    checked = globalEnabled.value && gestureActionsEnabled.value,
                    onCheckedChange = {
                        gestureActionsEnabled.value = it
                        prefs.hapticsGestureActionsEnabled = it
                    },
                    onClick = {
                        gestureActionsEnabled.value = !gestureActionsEnabled.value
                        prefs.hapticsGestureActionsEnabled = gestureActionsEnabled.value
                    },
                    enabled = globalEnabled.value,
                )
                ToggleTextButton(
                    title = stringResource(R.string.haptics_status_bar_press),
                    checked = globalEnabled.value && statusBarPressEnabled.value,
                    onCheckedChange = {
                        statusBarPressEnabled.value = it
                        prefs.hapticsStatusBarPressEnabled = it
                    },
                    onClick = {
                        statusBarPressEnabled.value = !statusBarPressEnabled.value
                        prefs.hapticsStatusBarPressEnabled = statusBarPressEnabled.value
                    },
                    enabled = globalEnabled.value,
                )
                ToggleTextButton(
                    title = stringResource(R.string.haptics_keymaps),
                    checked = globalEnabled.value && keymapsEnabled.value,
                    onCheckedChange = {
                        keymapsEnabled.value = it
                        prefs.hapticsKeymapsEnabled = it
                    },
                    onClick = {
                        keymapsEnabled.value = !keymapsEnabled.value
                        prefs.hapticsKeymapsEnabled = keymapsEnabled.value
                    },
                    enabled = globalEnabled.value,
                )
            }
        }
    }
}
