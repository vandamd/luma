package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.helper.LumaUpdateManager
import com.vandam.luma.ui.compose.BottomActionText
import com.vandam.luma.ui.compose.MessageText
import com.vandam.luma.ui.compose.SettingsInsetContentPadding
import com.vandam.luma.ui.compose.SettingsScreen
import kotlinx.coroutines.launch

class LumaUpdateFragment : Fragment() {
    private val versionName: String by lazy { arguments?.getString("versionName").orEmpty() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        val scope = rememberCoroutineScope()
        var isInstallingUpdate by remember { mutableStateOf(false) }

        SettingsScreen(
            title = stringResource(R.string.settings_update_luma),
            onBack = ::goBack,
            scrollable = false,
            contentPadding = SettingsInsetContentPadding,
            footer = {
                BottomActionText(
                    title = stringResource(R.string.settings_update_luma),
                    enabled = versionName.isNotBlank() && !isInstallingUpdate,
                ) {
                    if (versionName.isBlank() || isInstallingUpdate) {
                        return@BottomActionText
                    }
                    isInstallingUpdate = true
                    scope.launch {
                        LumaUpdateManager.installUpdate(
                            requireContext(),
                            LumaUpdateManager.AvailableUpdate(versionName = versionName),
                        )
                        isInstallingUpdate = false
                    }
                }
            },
        ) {
            MessageText(text = stringResource(R.string.settings_update_luma_message, versionName))
        }
    }

    private fun goBack() {
        findNavController().popBackStack()
    }
}
