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

class NotificationIndicatorAlignmentFragment : Fragment() {
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
                title = stringResource(R.string.status_bar_notif_alignment),
                onBack = ::goBack,
            )
            ContentContainer {
                    SimpleTextButton(
                        title = stringResource(R.string.status_bar_notif_alignment_before),
                        underline = prefs.notificationIndicatorAlignment == Prefs.NotificationIndicatorAlignment.Before,
                        onClick = { select(Prefs.NotificationIndicatorAlignment.Before) },
                    )
                    SimpleTextButton(
                        title = stringResource(R.string.status_bar_notif_alignment_after),
                        underline = prefs.notificationIndicatorAlignment == Prefs.NotificationIndicatorAlignment.After,
                        onClick = { select(Prefs.NotificationIndicatorAlignment.After) },
                    )
            }
        }
    }

    private fun select(alignment: Prefs.NotificationIndicatorAlignment) {
        prefs.notificationIndicatorAlignment = alignment
        goBack()
    }
}
