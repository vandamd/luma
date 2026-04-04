package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.ui.compose.SettingsScreen
import com.vandam.luma.ui.compose.SimpleTextButton

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Settings() }

    @Composable
    private fun Settings() {
        val versionName = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: ""
        SettingsScreen(
            title = stringResource(R.string.settings_title, versionName),
            onBack = ::goBack,
        ) {
            SimpleTextButton(stringResource(R.string.settings_miscellaneous)) {
                findNavController().navigate(R.id.action_settingsFragment_to_generalFragment)
            }
            SimpleTextButton(stringResource(R.string.settings_homescreen)) {
                findNavController().navigate(R.id.action_settingsFragment_to_homescreenFragment)
            }
            SimpleTextButton(stringResource(R.string.settings_lockscreen)) {
                findNavController().navigate(R.id.action_settingsFragment_to_lockscreenFragment)
            }
            SimpleTextButton(stringResource(R.string.settings_keymaps)) {
                findNavController().navigate(R.id.action_settingsFragment_to_keymapsFragment)
            }
            SimpleTextButton(stringResource(R.string.settings_account)) {
                findNavController().navigate(R.id.action_settingsFragment_to_accountFragment)
            }
        }
    }
}
