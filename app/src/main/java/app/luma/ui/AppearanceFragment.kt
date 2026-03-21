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
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_appearance),
                onBack = ::goBack,
            )

            ContentContainer {
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
                        title = stringResource(R.string.settings_auto_rotate),
                        initialValue = prefs.autoRotateEnabled,
                        onValueChange = {
                            prefs.autoRotateEnabled = it
                            requireActivity().recreate()
                        },
                    )
            }
        }
    }
}
