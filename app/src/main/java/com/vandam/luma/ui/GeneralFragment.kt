package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SimpleTextButton
import com.vandam.luma.ui.compose.SettingsScreen
import com.vandam.luma.ui.compose.ToggleTextButton

class GeneralFragment : Fragment() {
    private val prefs by lazy { Prefs.getInstance(requireContext()) }
    private val invertColoursState = mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        invertColoursState.value = prefs.invertColours
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        SettingsScreen(
            title = stringResource(R.string.settings_miscellaneous),
            onBack = ::goBack,
        ) {
            ToggleTextButton(
                title = stringResource(R.string.settings_invert_colours),
                checked = invertColoursState.value,
                onValueChange = {
                    invertColoursState.value = it
                    prefs.invertColours = it
                    AppCompatDelegate.setDefaultNightMode(
                        if (it) {
                            AppCompatDelegate.MODE_NIGHT_NO
                        } else {
                            AppCompatDelegate.MODE_NIGHT_YES
                        },
                    )
                },
            )
            SimpleTextButton(stringResource(R.string.settings_colour_apps)) {
                val navController = findNavController()
                if (navController.currentDestination?.id == R.id.generalFragment) {
                    navController.navigate(R.id.action_generalFragment_to_colourAppsFragment)
                }
            }
            SimpleTextButton(stringResource(R.string.settings_haptics)) {
                val navController = findNavController()
                if (navController.currentDestination?.id == R.id.generalFragment) {
                    navController.navigate(R.id.action_generalFragment_to_hapticsFragment)
                }
            }
        }
    }
}
