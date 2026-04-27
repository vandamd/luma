package com.vandam.luma.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.vandam.luma.R
import com.vandam.luma.data.HomeItemsManager
import com.vandam.luma.data.Prefs
import com.vandam.luma.data.Tool
import com.vandam.luma.ui.compose.MessageText
import com.vandam.luma.ui.compose.SettingsScreen
import com.vandam.luma.ui.compose.ToggleTextButton

class ColourAppsFragment : Fragment() {
    private val prefs by lazy { Prefs.getInstance(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        val context = requireContext()
        val colorApps = remember { mutableStateOf(prefs.colorApps) }
        val items =
            remember {
                HomeItemsManager
                    .orderedEnabledItems(context, prefs)
                    .filter { !it.appPackage.startsWith(Tool.SYNTHETIC_PACKAGE_PREFIX) }
                    .distinctBy { it.appPackage }
                    .sortedBy { it.displayName }
            }
        val hasPermission =
            remember {
                context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                    PackageManager.PERMISSION_GRANTED
            }

        SettingsScreen(
            title = stringResource(R.string.settings_colour_apps),
            onBack = ::goBack,
        ) {
            if (!hasPermission) {
                MessageText(
                    text = stringResource(R.string.colour_apps_permission_message),
                    modifier = Modifier.padding(end = 24.dp, bottom = 8.dp),
                )
            }
            items.forEach { appModel ->
                val packageName = appModel.appPackage
                ToggleTextButton(
                    title = appModel.displayName,
                    checked = colorApps.value.contains(packageName),
                    enabled = hasPermission,
                    onValueChange = { checked ->
                        val updated =
                            if (checked) {
                                colorApps.value + packageName
                            } else {
                                colorApps.value - packageName
                            }
                        colorApps.value = updated
                        prefs.colorApps = updated
                    },
                )
            }
        }
    }
}
