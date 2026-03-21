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
                title = stringResource(R.string.status_bar_visibility),
                onBack = ::goBack,
            )
            ContentContainer {
                SimpleTextButton(
                    title = stringResource(R.string.status_bar_visibility_disabled),
                    underline = prefs.statusBarVisibility == Prefs.StatusBarVisibility.Disabled,
                    onClick = { select(Prefs.StatusBarVisibility.Disabled) },
                )
                SimpleTextButton(
                    title = stringResource(R.string.status_bar_visibility_both),
                    underline = prefs.statusBarVisibility == Prefs.StatusBarVisibility.Both,
                    onClick = { select(Prefs.StatusBarVisibility.Both) },
                )
                SimpleTextButton(
                    title = stringResource(R.string.status_bar_visibility_homescreen),
                    underline = prefs.statusBarVisibility == Prefs.StatusBarVisibility.Homescreen,
                    onClick = { select(Prefs.StatusBarVisibility.Homescreen) },
                )
                SimpleTextButton(
                    title = stringResource(R.string.status_bar_visibility_lockscreen),
                    underline = prefs.statusBarVisibility == Prefs.StatusBarVisibility.Lockscreen,
                    onClick = { select(Prefs.StatusBarVisibility.Lockscreen) },
                )
            }
        }
    }

    private fun select(mode: Prefs.StatusBarVisibility) {
        prefs.statusBarVisibility = mode
        goBack()
    }
}
