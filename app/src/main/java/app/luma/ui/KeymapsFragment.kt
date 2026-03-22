package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.luma.R
import app.luma.data.Prefs
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.PrefsToggleTextButton
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

class KeymapsFragment : Fragment() {
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
        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_keymaps),
                onBack = ::goBack,
            )

            ContentContainer {
                SimpleTextButton(stringResource(R.string.keymaps_camera_shortcut)) {
                    findNavController().navigate(R.id.action_keymapsFragment_to_cameraShortcutFragment)
                }
                SimpleTextButton(stringResource(R.string.keymaps_scrollwheel_button)) {
                    findNavController().navigate(R.id.action_keymapsFragment_to_scrollwheelButtonFragment)
                }
                PrefsToggleTextButton(
                    title = stringResource(R.string.settings_scrollwheel_brightness),
                    initialValue = prefs.scrollwheelBrightnessEnabled,
                    onValueChange = { prefs.scrollwheelBrightnessEnabled = it },
                )
            }
        }
    }
}
