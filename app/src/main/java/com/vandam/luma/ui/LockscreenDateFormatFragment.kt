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
import com.vandam.luma.helper.formatLockscreenDateText
import com.vandam.luma.ui.compose.SingleChoiceOption
import com.vandam.luma.ui.compose.SingleChoiceScreen

class LockscreenDateFormatFragment : Fragment() {
    private val prefs by lazy { Prefs.getInstance(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() =
        SingleChoiceScreen(
            title = stringResource(R.string.lockscreen_date_format),
            onBack = ::goBack,
            options =
                Prefs.LockscreenDateFormat.values().map { format ->
                    SingleChoiceOption(
                        title =
                            if (format == Prefs.LockscreenDateFormat.None) {
                                stringResource(R.string.option_none)
                            } else {
                                formatLockscreenDateText(format)
                            },
                        selected = prefs.lockscreenDateFormat == format,
                        onClick = { select(format) },
                    )
                },
        )

    private fun select(format: Prefs.LockscreenDateFormat) {
        prefs.lockscreenDateFormat = format
        goBack()
    }
}
