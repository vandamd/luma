package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.luma.R
import app.luma.data.Constants
import app.luma.data.GestureType
import app.luma.data.Prefs
import app.luma.ui.compose.CustomScrollView
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.PrefsToggleTextButton
import app.luma.ui.compose.SettingsComposable.SelectorButton
import app.luma.ui.compose.SettingsComposable.SettingsHeader

class GesturesFragment : Fragment() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
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
                CustomScrollView(verticalArrangement = Arrangement.spacedBy(33.5.dp)) {
                    GestureButton(stringResource(R.string.gesture_swipe_left), GestureType.SWIPE_LEFT)
                    GestureButton(stringResource(R.string.gesture_swipe_right), GestureType.SWIPE_RIGHT)
                    GestureButton(stringResource(R.string.gesture_swipe_down), GestureType.SWIPE_DOWN)
                    GestureButton(stringResource(R.string.gesture_swipe_up), GestureType.SWIPE_UP)
                    GestureButton(stringResource(R.string.gesture_double_tap), GestureType.DOUBLE_TAP)
                }
            }
        }
    }

    @Composable
    private fun GestureButton(
        label: String,
        type: GestureType,
    ) {
        val action = prefs.getGestureAction(type)
        val value =
            when (action) {
                Constants.Action.OpenApp -> {
                    val appLabel = prefs.getGestureApp(type).appLabel
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
                    bundleOf(GestureActionFragment.GESTURE_TYPE to type.name),
                )
            },
        )
    }
}
