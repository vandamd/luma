package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import app.luma.R
import app.luma.data.Prefs
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

class ThemeModeFragment : Fragment() {
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
    fun Screen() {
        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_invert_colours),
                onBack = ::goBack,
            )
            ContentContainer {
                    SimpleTextButton(
                        title = stringResource(R.string.settings_theme_dark),
                        underline = prefs.themeMode == Prefs.ThemeMode.Dark,
                        onClick = { select(Prefs.ThemeMode.Dark) },
                    )
                    SimpleTextButton(
                        title = stringResource(R.string.settings_theme_light),
                        underline = prefs.themeMode == Prefs.ThemeMode.Light,
                        onClick = { select(Prefs.ThemeMode.Light) },
                    )
                    SimpleTextButton(
                        title = stringResource(R.string.settings_theme_automatic),
                        underline = prefs.themeMode == Prefs.ThemeMode.Automatic,
                        onClick = { select(Prefs.ThemeMode.Automatic) },
                    )
            }
        }
    }

    private fun select(themeMode: Prefs.ThemeMode) {
        prefs.themeMode = themeMode
        goBack()
        requireActivity().recreate()
    }
}
