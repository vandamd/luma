package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.luma.R
import app.luma.data.Constants
import app.luma.data.Prefs
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.PrefsToggleTextButton
import app.luma.ui.compose.SettingsComposable.SelectorButton
import app.luma.ui.compose.SettingsComposable.SettingsHeader

class CameraShortcutFragment : Fragment() {
    private lateinit var prefs: Prefs
    private val actionState = mutableStateOf(Constants.Action.Disabled)
    private val durationState = mutableStateOf(Prefs.KeymapDuration.ShortPress)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onResume() {
        super.onResume()
        actionState.value = prefs.getCameraKeyAction()
        durationState.value = prefs.cameraKeyDuration
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
                title = stringResource(R.string.keymaps_camera_shortcut),
                onBack = ::goBack,
            )

            ContentContainer {
                SelectorButton(
                    label = stringResource(R.string.keymaps_action),
                    value = actionDisplayValue(),
                    onClick = {
                        findNavController().navigate(
                            R.id.gestureActionFragment,
                            bundleOf(GestureActionFragment.CAMERA_KEY to true),
                        )
                    },
                )
                SelectorButton(
                    label = stringResource(R.string.keymaps_duration),
                    value = durationDisplayValue(),
                    onClick = {
                        findNavController().navigate(R.id.action_cameraShortcutFragment_to_cameraShortcutDurationFragment)
                    },
                )
                PrefsToggleTextButton(
                    title = stringResource(R.string.keymaps_vibrate),
                    initialValue = prefs.cameraKeyVibrate,
                    onValueChange = { prefs.cameraKeyVibrate = it },
                )
            }
        }
    }

    @Composable
    private fun actionDisplayValue(): String =
        when (actionState.value) {
            Constants.Action.OpenApp -> {
                val appLabel = prefs.getCameraKeyApp().displayName
                if (appLabel.isNotEmpty()) {
                    stringResource(R.string.action_open_app_name, appLabel)
                } else {
                    stringResource(R.string.action_open_app)
                }
            }

            Constants.Action.Disabled -> stringResource(R.string.action_disabled)

            else -> actionState.value.displayName()
        }

    @Composable
    private fun durationDisplayValue(): String =
        when (durationState.value) {
            Prefs.KeymapDuration.ShortPress -> stringResource(R.string.keymaps_duration_short_press)
            Prefs.KeymapDuration.LongPress -> stringResource(R.string.keymaps_duration_long_press)
        }
}
