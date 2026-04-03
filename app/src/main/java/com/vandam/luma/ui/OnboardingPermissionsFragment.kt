package com.vandam.luma.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.helper.isAccessibilityEnabled
import com.vandam.luma.helper.openAccessibilitySettings
import com.vandam.luma.helper.performAppTapHapticFeedback
import com.vandam.luma.style.SettingsTheme
import com.vandam.luma.ui.hasNotificationListenerPermission
import com.vandam.luma.ui.openNotificationListenerSettings
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.compose.SettingsComposable.SimpleTextButton
import com.vandam.luma.ui.compose.SettingsItemSpacing
import com.vandam.luma.ui.noRippleClickable
import java.util.Locale

class OnboardingPermissionsFragment : Fragment() {
    private val hasAccessibilityPermission = mutableStateOf(false)
    private val hasNotificationPermission = mutableStateOf(false)
    private val hasPhonePermission = mutableStateOf(false)
    private val hasModifySystemSettingsPermission = mutableStateOf(false)

    private fun goToWelcome() {
        val navController = findNavController()
        if (!navController.popBackStack()) {
            navController.navigate(
                R.id.onboardingWelcomeFragment,
                null,
                NavOptions
                    .Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.onboardingPermissionsFragment, true)
                    .build(),
            )
        }
    }

    private val phonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPhonePermission.value = granted
        }

    override fun onResume() {
        super.onResume()
        hasAccessibilityPermission.value = isAccessibilityEnabled(requireContext())
        hasNotificationPermission.value = hasNotificationListenerPermission()
        hasModifySystemSettingsPermission.value = hasWriteSettingsPermission()
        hasPhonePermission.value =
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_PHONE_STATE,
            ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goToWelcome) { Screen() }

    @Composable
    private fun Screen() {
        val canContinue =
            hasAccessibilityPermission.value &&
                hasPhonePermission.value &&
                hasNotificationPermission.value &&
                hasModifySystemSettingsPermission.value

        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(
                title = stringResource(R.string.onboarding_permissions_title),
                onBack = ::goToWelcome,
            )

            Column(modifier = Modifier.padding(horizontal = 37.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(SettingsItemSpacing)) {
                    SimpleTextButton(
                        title =
                            if (hasAccessibilityPermission.value) {
                                stringResource(R.string.onboarding_accessibility_granted)
                            } else {
                                stringResource(R.string.onboarding_accessibility_grant)
                            },
                        enabled = !hasAccessibilityPermission.value,
                    ) {
                        openAccessibilitySettings(requireContext())
                    }
                    SimpleTextButton(
                        title =
                            if (hasPhonePermission.value) {
                                stringResource(R.string.onboarding_phone_granted)
                            } else {
                                stringResource(R.string.onboarding_phone_grant)
                            },
                        enabled = !hasPhonePermission.value,
                    ) {
                        phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    }
                    SimpleTextButton(
                        title =
                            if (hasNotificationPermission.value) {
                                stringResource(R.string.onboarding_notifications_granted)
                            } else {
                                stringResource(R.string.onboarding_notifications_grant)
                            },
                        enabled = !hasNotificationPermission.value,
                    ) {
                        openNotificationListenerSettings()
                    }
                    SimpleTextButton(
                        title =
                            if (hasModifySystemSettingsPermission.value) {
                                stringResource(R.string.onboarding_write_settings_granted)
                            } else {
                                stringResource(R.string.onboarding_write_settings_grant)
                            },
                        enabled = !hasModifySystemSettingsPermission.value,
                    ) {
                        openWriteSettingsPermissionSettings()
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(bottom = 14.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                PermissionAction(
                    title = stringResource(R.string.onboarding_continue),
                    enabled = canContinue,
                ) {
                    Prefs.getInstance(requireContext()).onboardingLoginStarted = true
                    findNavController().navigate(R.id.action_onboardingPermissionsFragment_to_loginFragment)
                }
            }
        }
    }

    @Composable
    private fun PermissionAction(
        title: String,
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        val context = LocalContext.current

        Text(
            text = title.uppercase(Locale.getDefault()),
            style = SettingsTheme.typography.pageButton,
            fontSize = 40.sp,
            color = if (enabled) SettingsTheme.typography.pageButton.color else Color.Gray,
            modifier =
                Modifier
                    .noRippleClickable(enabled = enabled) {
                        performAppTapHapticFeedback(context)
                        onClick()
                    },
        )
    }
}
