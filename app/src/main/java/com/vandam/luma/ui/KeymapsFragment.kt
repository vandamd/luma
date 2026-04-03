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
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.Constants
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.SelectorButton
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.compose.SettingsItemSpacing

class KeymapsFragment : Fragment() {
    private lateinit var prefs: Prefs

    private val cameraPressState = mutableStateOf(Constants.Action.Disabled)
    private val cameraLongPressState = mutableStateOf(Constants.Action.Disabled)
    private val scrollwheelPressState = mutableStateOf(Constants.Action.Disabled)
    private val scrollwheelLongPressState = mutableStateOf(Constants.Action.Disabled)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onResume() {
        super.onResume()
        cameraPressState.value = prefs.getCameraKeyPressAction()
        cameraLongPressState.value = prefs.getCameraKeyLongPressAction()
        scrollwheelPressState.value = prefs.getScrollwheelButtonPressAction()
        scrollwheelLongPressState.value = prefs.getScrollwheelButtonLongPressAction()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_keymaps),
                onBack = ::goBack,
            )

            ContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(SettingsItemSpacing)) {
                    SelectorButton(
                        label = stringResource(R.string.keymaps_camera_press),
                        value = actionDisplayValue(cameraPressState.value, prefs.getCameraKeyPressApp()),
                        onClick = { navigateToKeymap("camera_press") },
                    )
                    SelectorButton(
                        label = stringResource(R.string.keymaps_camera_long_press),
                        value = actionDisplayValue(cameraLongPressState.value, prefs.getCameraKeyLongPressApp()),
                        onClick = { navigateToKeymap("camera_long_press") },
                    )
                    SelectorButton(
                        label = stringResource(R.string.keymaps_scrollwheel_press),
                        value = actionDisplayValue(scrollwheelPressState.value, prefs.getScrollwheelButtonPressApp()),
                        onClick = { navigateToKeymap("scrollwheel_press") },
                    )
                    SelectorButton(
                        label = stringResource(R.string.keymaps_scrollwheel_long_press),
                        value = actionDisplayValue(scrollwheelLongPressState.value, prefs.getScrollwheelButtonLongPressApp()),
                        onClick = { navigateToKeymap("scrollwheel_long_press") },
                    )
                }
            }
        }
    }

    private fun navigateToKeymap(type: String) {
        findNavController().navigate(
            R.id.gestureActionFragment,
            bundleOf(GestureActionFragment.KEYMAP_TYPE to type),
        )
    }

    @Composable
    private fun actionDisplayValue(
        action: Constants.Action,
        appModel: AppModel,
    ): String =
        when (action) {
            Constants.Action.OpenApp -> {
                val appLabel = appModel.displayName
                if (appLabel.isNotEmpty()) {
                    stringResource(R.string.action_open_app_name, appLabel)
                } else {
                    stringResource(R.string.action_open_app)
                }
            }

            Constants.Action.ToggleFlashlight -> {
                stringResource(R.string.action_toggle_flashlight)
            }

            Constants.Action.Disabled -> {
                stringResource(R.string.action_disabled)
            }

            else -> {
                action.displayName()
            }
        }
}
