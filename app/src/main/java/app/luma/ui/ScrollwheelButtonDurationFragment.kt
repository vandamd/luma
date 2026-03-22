package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.fragment.app.Fragment
import androidx.compose.ui.res.stringResource
import app.luma.R
import app.luma.data.Prefs
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

class ScrollwheelButtonDurationFragment : Fragment() {
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
                title = stringResource(R.string.keymaps_duration),
                onBack = ::goBack,
            )

            ContentContainer {
                SimpleTextButton(
                    title = stringResource(R.string.keymaps_duration_short_press),
                    underline = prefs.scrollwheelButtonDuration == Prefs.KeymapDuration.ShortPress,
                    onClick = { select(Prefs.KeymapDuration.ShortPress) },
                )
                SimpleTextButton(
                    title = stringResource(R.string.keymaps_duration_long_press),
                    underline = prefs.scrollwheelButtonDuration == Prefs.KeymapDuration.LongPress,
                    onClick = { select(Prefs.KeymapDuration.LongPress) },
                )
            }
        }
    }

    private fun select(duration: Prefs.KeymapDuration) {
        prefs.scrollwheelButtonDuration = duration
        goBack()
    }
}
