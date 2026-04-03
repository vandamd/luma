package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.helper.performAppTapHapticFeedback
import com.vandam.luma.style.SettingsTheme
import com.vandam.luma.ui.compose.SettingsComposable.MessageText
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.noRippleClickable
import java.util.Locale

class OnboardingWelcomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView { Screen() }

    @Composable
    private fun Screen() {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(
                title = stringResource(R.string.onboarding_welcome_title),
                onBack = null,
            )

            MessageText(
                text = stringResource(R.string.onboarding_welcome_message),
                modifier = Modifier.padding(horizontal = 37.dp),
            )

            val context = LocalContext.current
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(bottom = 14.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_continue).uppercase(Locale.getDefault()),
                    style = SettingsTheme.typography.pageButton,
                    fontSize = 40.sp,
                    modifier =
                        Modifier.noRippleClickable {
                            performAppTapHapticFeedback(context)
                            Prefs.getInstance(requireContext()).onboardingStarted = true
                            findNavController().navigate(R.id.action_onboardingWelcomeFragment_to_onboardingPermissionsFragment)
                        },
                )
            }
        }
    }
}
