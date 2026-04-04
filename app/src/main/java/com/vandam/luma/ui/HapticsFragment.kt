package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SettingsScreen
import com.vandam.luma.ui.compose.ToggleTextButton

private data class HapticsToggleOption(
    val title: String,
    val state: androidx.compose.runtime.MutableState<Boolean>,
    val checked: Boolean,
    val enabled: Boolean,
    val onValueChange: (Boolean) -> Unit,
)

class HapticsFragment : Fragment() {
    private val prefs by lazy { Prefs.getInstance(requireContext()) }
    private val globalEnabled = mutableStateOf(false)
    private val appTapEnabled = mutableStateOf(false)
    private val longPressEnabled = mutableStateOf(false)
    private val gestureActionsEnabled = mutableStateOf(false)
    private val statusBarPressEnabled = mutableStateOf(false)
    private val keymapsEnabled = mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        globalEnabled.value = prefs.hapticsEnabled
        appTapEnabled.value = prefs.hapticsAppTapEnabled
        longPressEnabled.value = prefs.hapticsLongPressEnabled
        gestureActionsEnabled.value = prefs.hapticsGestureActionsEnabled
        statusBarPressEnabled.value = prefs.hapticsStatusBarPressEnabled
        keymapsEnabled.value = prefs.hapticsKeymapsEnabled
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        val toggleOptions =
            listOf(
                HapticsToggleOption(
                    title = stringResource(R.string.haptics_app_tap),
                    state = appTapEnabled,
                    checked = globalEnabled.value && appTapEnabled.value,
                    enabled = globalEnabled.value,
                    onValueChange = { prefs.hapticsAppTapEnabled = it },
                ),
                HapticsToggleOption(
                    title = stringResource(R.string.haptics_long_press),
                    state = longPressEnabled,
                    checked = globalEnabled.value && longPressEnabled.value,
                    enabled = globalEnabled.value,
                    onValueChange = { prefs.hapticsLongPressEnabled = it },
                ),
                HapticsToggleOption(
                    title = stringResource(R.string.haptics_gesture_actions),
                    state = gestureActionsEnabled,
                    checked = globalEnabled.value && gestureActionsEnabled.value,
                    enabled = globalEnabled.value,
                    onValueChange = { prefs.hapticsGestureActionsEnabled = it },
                ),
                HapticsToggleOption(
                    title = stringResource(R.string.haptics_status_bar_press),
                    state = statusBarPressEnabled,
                    checked = globalEnabled.value && statusBarPressEnabled.value,
                    enabled = globalEnabled.value,
                    onValueChange = { prefs.hapticsStatusBarPressEnabled = it },
                ),
                HapticsToggleOption(
                    title = stringResource(R.string.haptics_keymaps),
                    state = keymapsEnabled,
                    checked = globalEnabled.value && keymapsEnabled.value,
                    enabled = globalEnabled.value,
                    onValueChange = { prefs.hapticsKeymapsEnabled = it },
                ),
            )

        SettingsScreen(
            title = stringResource(R.string.settings_haptics),
            onBack = ::goBack,
        ) {
            ToggleTextButton(
                title = stringResource(R.string.haptics_enabled),
                checked = globalEnabled.value,
                onValueChange = {
                    globalEnabled.value = it
                    prefs.hapticsEnabled = it
                },
            )
            toggleOptions.forEach { option ->
                ToggleTextButton(
                    title = option.title,
                    checked = option.checked,
                    enabled = option.enabled,
                    onValueChange = {
                        option.state.value = it
                        option.onValueChange(it)
                    },
                )
            }
        }
    }
}
