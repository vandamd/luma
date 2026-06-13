package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SingleChoiceOption
import com.vandam.luma.ui.compose.SingleChoiceScreen

class TimeFormatFragment : Fragment() {
    private val prefs by lazy { Prefs.getInstance(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() =
        SingleChoiceScreen(
            title = stringResource(R.string.status_bar_time_format),
            onBack = ::goBack,
            options =
                listOf(
                    SingleChoiceOption(
                        title = stringResource(R.string.status_bar_time_standard),
                        selected = prefs.timeFormat == Prefs.TimeFormat.Standard,
                        onClick = { select(Prefs.TimeFormat.Standard) },
                    ),
                    SingleChoiceOption(
                        title = stringResource(R.string.status_bar_time_twelve),
                        selected = prefs.timeFormat == Prefs.TimeFormat.TwelveHour,
                        onClick = { select(Prefs.TimeFormat.TwelveHour) },
                    ),
                    SingleChoiceOption(
                        title = stringResource(R.string.status_bar_time_24h),
                        selected = prefs.timeFormat == Prefs.TimeFormat.TwentyFourHour,
                        onClick = { select(Prefs.TimeFormat.TwentyFourHour) },
                    ),
                ),
        )

    private fun select(format: Prefs.TimeFormat) {
        prefs.timeFormat = format
        goBack()
    }
}
