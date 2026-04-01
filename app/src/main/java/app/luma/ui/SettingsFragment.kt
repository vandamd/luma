package app.luma.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.luma.R
import app.luma.data.Prefs
import app.luma.helper.isAccessibilityEnabled
import app.luma.helper.openAccessibilitySettings
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.MessageText
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

class SettingsFragment : Fragment() {
    private lateinit var prefs: Prefs
    private val hasAccessibilityPermission = mutableStateOf(false)
    private val hasPhonePermission = mutableStateOf(false)

    private val phonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPhonePermission.value = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onResume() {
        super.onResume()
        hasAccessibilityPermission.value = isAccessibilityEnabled(requireContext())
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
                if (!hasAccessibilityPermission.value) {
                    MessageText(
                        stringResource(R.string.accessibility_hint),
                        modifier = Modifier.padding(end = 30.dp),
                    )
                    SimpleTextButton(stringResource(R.string.accessibility_grant)) {
                        openAccessibilitySettings(requireContext())
                    }
                }
                if (!hasPhonePermission.value) {
                    SimpleTextButton("Grant Phone Permission") {
                        phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    }
                }
                SimpleTextButton(stringResource(R.string.settings_miscellaneous)) {
                    findNavController().navigate(R.id.action_settingsFragment_to_miscellaneousFragment)
                }
                SimpleTextButton(stringResource(R.string.settings_tools)) {
                    findNavController().navigate(R.id.action_settingsFragment_to_toolsFragment)
                }
                SimpleTextButton(stringResource(R.string.settings_homescreen)) {
                    findNavController().navigate(R.id.action_settingsFragment_to_homescreenFragment)
                }
                SimpleTextButton(stringResource(R.string.settings_lockscreen), enabled = hasAccessibilityPermission.value) {
                    findNavController().navigate(R.id.action_settingsFragment_to_lockscreenFragment)
                }
                SimpleTextButton(stringResource(R.string.settings_keymaps), enabled = hasAccessibilityPermission.value) {
                    findNavController().navigate(R.id.action_settingsFragment_to_keymapsFragment)
                }
            }
        }
    }
}
