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

class TimeFormatFragment : Fragment() {
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
                title = stringResource(R.string.status_bar_time_format),
                onBack = ::goBack,
            )
            ContentContainer {
                    SimpleTextButton(
                        title = stringResource(R.string.status_bar_time_24h),
                        underline = prefs.timeFormat == Prefs.TimeFormat.TwentyFourHour,
                        onClick = { select(Prefs.TimeFormat.TwentyFourHour) },
                    )
                    SimpleTextButton(
                        title = stringResource(R.string.status_bar_time_standard),
                        underline = prefs.timeFormat == Prefs.TimeFormat.Standard,
                        onClick = { select(Prefs.TimeFormat.Standard) },
                    )
            }
        }
    }

    private fun select(format: Prefs.TimeFormat) {
        prefs.timeFormat = format
        goBack()
    }
}
