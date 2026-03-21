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

class TimeFragment : Fragment() {
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
                title = stringResource(R.string.settings_time),
                onBack = ::goBack,
            )

            ContentContainer {
                CustomScrollView(verticalArrangement = Arrangement.spacedBy(33.5.dp)) {
                    SelectorButton(
                        label = stringResource(R.string.status_bar_time_format),
                        value =
                            when (prefs.timeFormat) {
                                Prefs.TimeFormat.Standard -> stringResource(R.string.status_bar_time_standard)
                                Prefs.TimeFormat.TwentyFourHour -> stringResource(R.string.status_bar_time_24h)
                            },
                        onClick = { findNavController().navigate(R.id.action_timeFragment_to_timeFormatFragment) },
                    )
                    PrefsToggleTextButton(
                        title = stringResource(R.string.status_bar_show_seconds),
                        initialValue = prefs.showSeconds,
                        onValueChange = { prefs.showSeconds = it },
                    )
                }
            }
        }
    }
}
