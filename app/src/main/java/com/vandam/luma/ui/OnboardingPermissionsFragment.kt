package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.helper.ApkInstaller
import com.vandam.luma.helper.PhoneSignalHelper
import com.vandam.luma.helper.isAccessibilityEnabled
import com.vandam.luma.helper.openAccessibilitySettings
import com.vandam.luma.ui.hasNotificationListenerPermission
import com.vandam.luma.ui.openNotificationListenerSettings
import com.vandam.luma.ui.compose.BottomActionText
import com.vandam.luma.ui.compose.MessageText
import com.vandam.luma.ui.compose.SettingsInsetStartContentPaddingNoBottom
import com.vandam.luma.ui.compose.SettingsScreen
import com.vandam.luma.ui.compose.SimpleTextButton

class OnboardingPermissionsFragment : Fragment() {
    private val hasAccessibilityPermission = mutableStateOf(false)
    private val hasNotificationPermission = mutableStateOf(false)
    private val hasPhoneToolPermission = mutableStateOf(false)
    private val hasModifySystemSettingsPermission = mutableStateOf(false)
    private val hasInstallAppsPermission = mutableStateOf(false)
    private val isPermissionRecovery: Boolean
        get() = Prefs.getInstance(requireContext()).accountNumber.isNotBlank()

    private fun goToWelcome() {
        val navController = findNavController()
        val destinationId =
            if (isPermissionRecovery) {
                R.id.mainFragment
            } else {
                R.id.onboardingWelcomeFragment
            }
        if (!navController.popBackStack()) {
            navController.navigate(
                destinationId,
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
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasPhoneToolPermission.value = PhoneSignalHelper.hasPhoneToolPermissions(requireContext())
        }

    override fun onResume() {
        super.onResume()
        hasAccessibilityPermission.value = isAccessibilityEnabled(requireContext())
        hasNotificationPermission.value = hasNotificationListenerPermission()
        hasModifySystemSettingsPermission.value = hasWriteSettingsPermission()
        hasInstallAppsPermission.value = ApkInstaller.canRequestPackageInstalls(requireContext())
        hasPhoneToolPermission.value = PhoneSignalHelper.hasPhoneToolPermissions(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = if (isPermissionRecovery) null else ::goToWelcome) { Screen() }

    @Composable
    private fun Screen() {
        val canContinue =
            hasAccessibilityPermission.value &&
                hasPhoneToolPermission.value &&
                hasNotificationPermission.value &&
                hasModifySystemSettingsPermission.value &&
                hasInstallAppsPermission.value

        SettingsScreen(
            title = stringResource(R.string.onboarding_permissions_title),
            onBack = if (isPermissionRecovery) null else ::goToWelcome,
            contentPadding = SettingsInsetStartContentPaddingNoBottom,
            footer = {
                BottomActionText(
                    title = stringResource(R.string.onboarding_continue),
                    enabled = canContinue,
                ) {
                    if (isPermissionRecovery) {
                        findNavController().navigate(
                            R.id.mainFragment,
                            null,
                            NavOptions
                                .Builder()
                                .setLaunchSingleTop(true)
                                .setPopUpTo(R.id.onboardingPermissionsFragment, true)
                                .build(),
                        )
                    } else {
                        Prefs.getInstance(requireContext()).onboardingLoginStarted = true
                        findNavController().navigate(R.id.action_onboardingPermissionsFragment_to_loginFragment)
                    }
                }
            },
        ) {
            if (!canContinue) {
                MessageText(
                    text = stringResource(R.string.onboarding_permissions_restricted_settings_note),
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
            SimpleTextButton(
                title = stringResource(R.string.onboarding_accessibility_grant),
                enabled = !hasAccessibilityPermission.value,
            ) {
                openAccessibilitySettings(requireContext())
            }
            SimpleTextButton(
                title = stringResource(R.string.onboarding_phone_grant),
                enabled = !hasPhoneToolPermission.value,
            ) {
                phonePermissionLauncher.launch(PhoneSignalHelper.phoneToolPermissions())
            }
            SimpleTextButton(
                title = stringResource(R.string.onboarding_notifications_grant),
                enabled = !hasNotificationPermission.value,
            ) {
                openNotificationListenerSettings()
            }
            SimpleTextButton(
                title = stringResource(R.string.onboarding_write_settings_grant),
                enabled = !hasModifySystemSettingsPermission.value,
            ) {
                openWriteSettingsPermissionSettings()
            }
            SimpleTextButton(
                title = stringResource(R.string.onboarding_install_apps_grant),
                enabled = !hasInstallAppsPermission.value,
            ) {
                ApkInstaller.openUnknownSourcesSettings(requireContext())
            }
        }
    }
}
