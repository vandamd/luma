package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.helper.LumaUpdateManager
import com.vandam.luma.ui.compose.SettingsScreen
import com.vandam.luma.ui.compose.SimpleTextButton
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Settings() }

    @Composable
    private fun Settings() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var isInstallingUpdate by remember { mutableStateOf(false) }
        val versionName = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: ""
        val availableUpdate by produceState<LumaUpdateManager.AvailableUpdate?>(initialValue = null) {
            value = LumaUpdateManager.fetchAvailableUpdate(context)
        }
        SettingsScreen(
            title = stringResource(R.string.settings_title, versionName),
            onBack = ::goBack,
        ) {
            availableUpdate?.let { update ->
                SimpleTextButton(
                    stringResource(R.string.settings_update_luma, update.versionName),
                ) {
                    if (isInstallingUpdate) {
                        return@SimpleTextButton
                    }
                    isInstallingUpdate = true
                    scope.launch {
                        LumaUpdateManager.installUpdate(context, update)
                        isInstallingUpdate = false
                    }
                }
            }
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
