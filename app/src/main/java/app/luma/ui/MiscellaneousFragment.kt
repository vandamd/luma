package app.luma.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.luma.MainViewModel
import app.luma.R
import app.luma.data.Constants.AppDrawerFlag
import app.luma.ui.compose.CustomScrollView
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

class MiscellaneousFragment : Fragment() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_miscellaneous),
                onBack = ::goBack,
            )

            ContentContainer {
                CustomScrollView {
                    SimpleTextButton(stringResource(R.string.settings_appearance)) {
                        findNavController().navigate(R.id.action_miscellaneousFragment_to_appearanceFragment)
                    }
                    SimpleTextButton(stringResource(R.string.settings_time)) {
                        findNavController().navigate(R.id.action_miscellaneousFragment_to_timeFragment)
                    }
                    SimpleTextButton(stringResource(R.string.settings_haptics)) {
                        findNavController().navigate(R.id.action_miscellaneousFragment_to_hapticsFragment)
                    }
                    SimpleTextButton(stringResource(R.string.settings_hidden_apps)) { showHiddenApps() }
                    SimpleTextButton(stringResource(R.string.settings_default_launcher)) { openDefaultLauncherSettings() }
                }
            }
        }
    }

    private fun openDefaultLauncherSettings() {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun showHiddenApps() {
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.appListFragment,
            bundleOf("flag" to AppDrawerFlag.HiddenApps.toString()),
        )
    }
}
