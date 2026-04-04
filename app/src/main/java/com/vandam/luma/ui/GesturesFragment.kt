package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.GestureScope
import com.vandam.luma.data.GestureType
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SelectorButton
import com.vandam.luma.ui.compose.SettingsScreen

class GesturesFragment : Fragment() {
    private lateinit var prefs: Prefs
    private var gestureScope = GestureScope.Homescreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
        gestureScope =
            arguments
                ?.getString(GestureActionFragment.GESTURE_SCOPE)
                ?.let { runCatching { GestureScope.valueOf(it) }.getOrNull() }
                ?: GestureScope.Homescreen
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { GesturesScreen() }

    @Composable
    fun GesturesScreen() {
        SettingsScreen(
            title =
                if (gestureScope == GestureScope.Homescreen) {
                    stringResource(R.string.settings_shortcuts)
                } else {
                    stringResource(R.string.settings_gestures)
                },
            onBack = ::goBack,
        ) {
            GestureButton(stringResource(R.string.gesture_swipe_left), GestureType.SWIPE_LEFT)
            GestureButton(stringResource(R.string.gesture_swipe_right), GestureType.SWIPE_RIGHT)
            GestureButton(stringResource(R.string.gesture_swipe_down), GestureType.SWIPE_DOWN)
            GestureButton(stringResource(R.string.gesture_swipe_up), GestureType.SWIPE_UP)
            GestureButton(stringResource(R.string.gesture_double_tap), GestureType.DOUBLE_TAP)
        }
    }

    @Composable
    private fun GestureButton(
        label: String,
        type: GestureType,
    ) {
        val action = prefs.getGestureAction(type, gestureScope)
        SelectorButton(
            label = label,
            value = actionDisplayValue(action, prefs.getGestureApp(type, gestureScope).displayName),
            onClick = {
                findNavController().navigate(
                    R.id.gestureActionFragment,
                    bundleOf(
                        GestureActionFragment.GESTURE_TYPE to type.name,
                        GestureActionFragment.GESTURE_SCOPE to gestureScope.name,
                    ),
                )
            },
        )
    }
}
