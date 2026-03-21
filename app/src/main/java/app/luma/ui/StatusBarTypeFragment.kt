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

class StatusBarTypeFragment : Fragment() {
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
                title = stringResource(R.string.status_bar_type),
                onBack = ::goBack,
            )
            ContentContainer {
                SimpleTextButton(
                    title = stringResource(R.string.status_bar_type_luma),
                    underline = prefs.statusBarType == Prefs.StatusBarType.Luma,
                    onClick = { select(Prefs.StatusBarType.Luma) },
                )
                SimpleTextButton(
                    title = stringResource(R.string.status_bar_type_android),
                    underline = prefs.statusBarType == Prefs.StatusBarType.Android,
                    onClick = { select(Prefs.StatusBarType.Android) },
                )
            }
        }
    }

    private fun select(type: Prefs.StatusBarType) {
        prefs.statusBarType = type
        goBack()
    }
}
