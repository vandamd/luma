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
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.BottomActionText
import com.vandam.luma.ui.compose.MessageText
import com.vandam.luma.ui.compose.SettingsInsetContentPadding
import com.vandam.luma.ui.compose.SettingsScreen

class OnboardingWelcomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView { Screen() }

    @Composable
    private fun Screen() {
        SettingsScreen(
            title = stringResource(R.string.onboarding_welcome_title),
            scrollable = false,
            contentPadding = SettingsInsetContentPadding,
            footer = {
                BottomActionText(title = stringResource(R.string.onboarding_continue)) {
                    Prefs.getInstance(requireContext()).onboardingStarted = true
                    findNavController().navigate(R.id.action_onboardingWelcomeFragment_to_onboardingPermissionsFragment)
                }
            },
        ) {
            MessageText(text = stringResource(R.string.onboarding_welcome_message))
        }
    }
}
