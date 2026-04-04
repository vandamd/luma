package com.vandam.luma.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.helper.isAccessibilityEnabled
import com.vandam.luma.helper.openAccessibilitySettings
import com.vandam.luma.ui.hasNotificationListenerPermission
import com.vandam.luma.ui.openNotificationListenerSettings
import com.vandam.luma.ui.compose.BottomActionText
import com.vandam.luma.ui.compose.SettingsInsetContentPaddingNoBottom
import com.vandam.luma.ui.compose.SettingsScreen
import com.vandam.luma.ui.compose.SimpleTextButton

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

        SettingsScreen(
            title = stringResource(R.string.onboarding_permissions_title),
            onBack = ::goToWelcome,
            contentPadding = SettingsInsetContentPaddingNoBottom,
            footer = {
                BottomActionText(
                    title = stringResource(R.string.onboarding_continue),
                    enabled = canContinue,
                ) {
                    Prefs.getInstance(requireContext()).onboardingLoginStarted = true
                    findNavController().navigate(R.id.action_onboardingPermissionsFragment_to_loginFragment)
                }
            },
        ) {
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
}
