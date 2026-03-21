package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.luma.R
import app.luma.data.Prefs
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.PrefsToggleTextButton
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

class SettingsFragment : Fragment() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Settings() }

    @Composable
    private fun Settings() {
        val versionName = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: ""
        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_title, versionName),
                onBack = ::goBack,
            )

            ContentContainer {
                    SimpleTextButton(stringResource(R.string.settings_miscellaneous)) {
                        findNavController().navigate(R.id.action_settingsFragment_to_miscellaneousFragment)
                    }
                    SimpleTextButton(stringResource(R.string.settings_homescreen)) {
                        findNavController().navigate(R.id.action_settingsFragment_to_homescreenFragment)
                    }
                    SimpleTextButton(stringResource(R.string.settings_lockscreen)) {
                        findNavController().navigate(R.id.action_settingsFragment_to_lockscreenFragment)
                    }
                    SimpleTextButton(stringResource(R.string.settings_status_bar)) {
                        findNavController().navigate(R.id.action_settingsFragment_to_statusBarFragment)
                    }
                    PrefsToggleTextButton(
                        title = stringResource(R.string.settings_show_volume_indicator),
                        initialValue = prefs.showVolumeIndicator,
                        onValueChange = { prefs.showVolumeIndicator = it },
                    )
            }
        }
    }
}
