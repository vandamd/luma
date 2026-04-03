package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.helper.formatLockscreenDateText
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.compose.SettingsComposable.SimpleTextButton

class LockscreenDateFormatFragment : Fragment() {
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
                title = stringResource(R.string.lockscreen_date_format),
                onBack = ::goBack,
            )

            ContentContainer {
                for (format in Prefs.LockscreenDateFormat.values()) {
                    val label =
                        if (format == Prefs.LockscreenDateFormat.None) {
                            stringResource(R.string.option_none)
                        } else {
                            formatLockscreenDateText(format)
                        }
                    SimpleTextButton(
                        title = label,
                        underline = prefs.lockscreenDateFormat == format,
                        onClick = { select(format) },
                    )
                }
            }
        }
    }

    private fun select(format: Prefs.LockscreenDateFormat) {
        prefs.lockscreenDateFormat = format
        goBack()
    }
}
