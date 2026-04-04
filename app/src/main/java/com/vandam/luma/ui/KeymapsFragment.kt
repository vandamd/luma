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
import com.vandam.luma.data.KeymapType
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SelectorButton
import com.vandam.luma.ui.compose.SettingsScreen

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
        SettingsScreen(
            title = stringResource(R.string.settings_keymaps),
            onBack = ::goBack,
        ) {
            SelectorButton(
                label = stringResource(R.string.keymaps_camera_press),
                value = actionDisplayValue(cameraPressState.value, prefs.getCameraKeyPressApp().displayName),
                onClick = { navigateToKeymap(KeymapType.CameraPress) },
            )
            SelectorButton(
                label = stringResource(R.string.keymaps_camera_long_press),
                value = actionDisplayValue(cameraLongPressState.value, prefs.getCameraKeyLongPressApp().displayName),
                onClick = { navigateToKeymap(KeymapType.CameraLongPress) },
            )
            SelectorButton(
                label = stringResource(R.string.keymaps_scrollwheel_press),
                value = actionDisplayValue(scrollwheelPressState.value, prefs.getScrollwheelButtonPressApp().displayName),
                onClick = { navigateToKeymap(KeymapType.ScrollwheelPress) },
            )
            SelectorButton(
                label = stringResource(R.string.keymaps_scrollwheel_long_press),
                value = actionDisplayValue(scrollwheelLongPressState.value, prefs.getScrollwheelButtonLongPressApp().displayName),
                onClick = { navigateToKeymap(KeymapType.ScrollwheelLongPress) },
            )
        }
    }

    private fun navigateToKeymap(type: KeymapType) {
        findNavController().navigate(
            R.id.gestureActionFragment,
            bundleOf(GestureActionFragment.KEYMAP_TYPE to type.argumentValue),
        )
    }
}
