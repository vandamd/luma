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
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.luma.R
import app.luma.data.Prefs
import app.luma.ui.compose.CustomScrollView
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.PrefsToggleTextButton
import app.luma.ui.compose.SettingsComposable.SelectorButton
import app.luma.ui.compose.SettingsComposable.SettingsHeader

class AppearanceFragment : Fragment() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { AppearanceScreen() }

    @Composable
    private fun AppearanceScreen() {
        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_appearance),
                onBack = ::goBack,
            )

            ContentContainer {
                CustomScrollView(verticalArrangement = Arrangement.spacedBy(33.5.dp)) {
                    PrefsToggleTextButton(
                        title = stringResource(R.string.settings_auto_rotate),
                        initialValue = prefs.autoRotateEnabled,
                        onValueChange = {
                            prefs.autoRotateEnabled = it
                            requireActivity().recreate()
                        },
                    )
                    SelectorButton(
                        label = stringResource(R.string.settings_invert_colours),
                        value =
                            when (prefs.themeMode) {
                                Prefs.ThemeMode.Dark -> stringResource(R.string.settings_theme_dark)
                                Prefs.ThemeMode.Light -> stringResource(R.string.settings_theme_light)
                                Prefs.ThemeMode.Automatic -> stringResource(R.string.settings_theme_automatic)
                            },
                        onClick = { findNavController().navigate(R.id.action_appearanceFragment_to_themeModeFragment) },
                    )
                    PrefsToggleTextButton(
                        title = stringResource(R.string.settings_show_volume_indicator),
                        initialValue = prefs.showVolumeIndicator,
                        onValueChange = { prefs.showVolumeIndicator = it },
                    )
                    PrefsToggleTextButton(
                        title = stringResource(R.string.settings_show_tool_icons),
                        initialValue = prefs.showAppDrawerToolIcons,
                        onValueChange = { prefs.showAppDrawerToolIcons = it },
                    )
                    PrefsToggleTextButton(
                        title = stringResource(R.string.settings_show_pin_icons),
                        initialValue = prefs.showAppDrawerPinIcons,
                        onValueChange = { prefs.showAppDrawerPinIcons = it },
                    )
                }
            }
        }
    }
}
