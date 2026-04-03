package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.vandam.luma.MainViewModel
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.PrefsToggleTextButton
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.compose.SettingsComposable.SimpleTextButton
import com.vandam.luma.ui.compose.SettingsItemSpacing

class MiscellaneousFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        Column {
            SettingsHeader(
                title = stringResource(R.string.settings_miscellaneous),
                onBack = ::goBack,
            )

            ContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(SettingsItemSpacing)) {
                    PrefsToggleTextButton(
                        title = stringResource(R.string.settings_invert_colours),
                        initialValue = prefs.invertColours,
                        onValueChange = {
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
                    SimpleTextButton(stringResource(R.string.settings_haptics)) {
                        runCatching {
                            val navController = findNavController()
                            if (navController.currentDestination?.id == R.id.miscellaneousFragment) {
                                navController.navigate(R.id.action_miscellaneousFragment_to_hapticsFragment)
                            }
                        }
                    }
                }
            }
        }
    }
}
