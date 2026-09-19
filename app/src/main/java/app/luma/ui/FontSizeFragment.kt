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
import app.luma.style.FontSizeOption
import app.luma.ui.compose.CustomScrollView
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

class FontSizeFragment : Fragment() {
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
                title = stringResource(R.string.settings_font_size),
                onBack = ::goBack,
            )
            ContentContainer {
                CustomScrollView {
                    SimpleTextButton(
                        title = stringResource(R.string.font_size_system),
                        underline = prefs.fontSizeOption == FontSizeOption.System,
                        onClick = { select(FontSizeOption.System) },
                    )
                    SimpleTextButton(
                        title = stringResource(R.string.font_size_small),
                        underline = prefs.fontSizeOption == FontSizeOption.Small,
                        onClick = { select(FontSizeOption.Small) },
                    )
                    SimpleTextButton(
                        title = stringResource(R.string.font_size_medium),
                        underline = prefs.fontSizeOption == FontSizeOption.Medium,
                        onClick = { select(FontSizeOption.Medium) },
                    )
                    SimpleTextButton(
                        title = stringResource(R.string.font_size_large),
                        underline = prefs.fontSizeOption == FontSizeOption.Large,
                        onClick = { select(FontSizeOption.Large) },
                    )
                    SimpleTextButton(
                        title = stringResource(R.string.font_size_extra_large),
                        underline = prefs.fontSizeOption == FontSizeOption.ExtraLarge,
                        onClick = { select(FontSizeOption.ExtraLarge) },
                    )
                }
            }
        }
    }

    private fun select(option: FontSizeOption) {
        prefs.fontSizeOption = option
        goBack()
        requireActivity().recreate()
    }
}
