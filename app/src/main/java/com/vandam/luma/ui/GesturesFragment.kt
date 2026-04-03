package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.Constants
import com.vandam.luma.data.GestureScope
import com.vandam.luma.data.GestureType
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.SelectorButton
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader

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
        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_gestures),
                onBack = ::goBack,
            )

            ContentContainer {
                    GestureButton(stringResource(R.string.gesture_swipe_left), GestureType.SWIPE_LEFT)
                    GestureButton(stringResource(R.string.gesture_swipe_right), GestureType.SWIPE_RIGHT)
                    GestureButton(stringResource(R.string.gesture_swipe_down), GestureType.SWIPE_DOWN)
                    GestureButton(stringResource(R.string.gesture_swipe_up), GestureType.SWIPE_UP)
                    GestureButton(stringResource(R.string.gesture_double_tap), GestureType.DOUBLE_TAP)
            }
        }
    }

    @Composable
    private fun GestureButton(
        label: String,
        type: GestureType,
    ) {
        val action = prefs.getGestureAction(type, gestureScope)
        val value =
            when (action) {
                Constants.Action.OpenApp -> {
                    val appLabel = prefs.getGestureApp(type, gestureScope).displayName
                    if (appLabel.isNotEmpty()) {
                        stringResource(R.string.action_open_app_name, appLabel)
                    } else {
                        stringResource(R.string.action_open_app)
                    }
                }

                Constants.Action.Disabled -> {
                    stringResource(R.string.action_disabled)
                }

                else -> {
                    action.displayName()
                }
            }
        SelectorButton(
            label = label,
            value = value,
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
