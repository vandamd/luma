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

class NotificationIndicatorSectionFragment : Fragment() {
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
                title = stringResource(R.string.status_bar_notif_section),
                onBack = ::goBack,
            )
            ContentContainer {
                    SimpleTextButton(
                        title = stringResource(R.string.status_bar_notif_section_cellular),
                        underline = prefs.notificationIndicatorSection == Prefs.NotificationIndicatorSection.Connectivity,
                        onClick = { select(Prefs.NotificationIndicatorSection.Connectivity) },
                    )
                    SimpleTextButton(
                        title = stringResource(R.string.status_bar_notif_section_time),
                        underline = prefs.notificationIndicatorSection == Prefs.NotificationIndicatorSection.Time,
                        onClick = { select(Prefs.NotificationIndicatorSection.Time) },
                    )
                    SimpleTextButton(
                        title = stringResource(R.string.status_bar_notif_section_battery),
                        underline = prefs.notificationIndicatorSection == Prefs.NotificationIndicatorSection.Battery,
                        onClick = { select(Prefs.NotificationIndicatorSection.Battery) },
                    )
            }
        }
    }

    private fun select(section: Prefs.NotificationIndicatorSection) {
        prefs.notificationIndicatorSection = section
        goBack()
    }
}
