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
import app.luma.ui.compose.CustomScrollView
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

class StatusBarModeFragment : Fragment() {
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
                title = stringResource(R.string.settings_status_bar),
                onBack = ::goBack,
            )
            ContentContainer {
                CustomScrollView {
                    SimpleTextButton(
                        title = stringResource(R.string.status_bar_enabled),
                        underline = prefs.statusBarMode == Prefs.StatusBarMode.Enabled,
                        onClick = { select(Prefs.StatusBarMode.Enabled) },
                    )
                    SimpleTextButton(
                        title = stringResource(R.string.status_bar_mode_android),
                        underline = prefs.statusBarMode == Prefs.StatusBarMode.AndroidStatusBar,
                        onClick = { select(Prefs.StatusBarMode.AndroidStatusBar) },
                    )
                    SimpleTextButton(
                        title = stringResource(R.string.status_bar_mode_none),
                        underline = prefs.statusBarMode == Prefs.StatusBarMode.None,
                        onClick = { select(Prefs.StatusBarMode.None) },
                    )
                }
            }
        }
    }

    private fun select(mode: Prefs.StatusBarMode) {
        prefs.statusBarMode = mode
        goBack()
    }
}
