package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SingleChoiceOption
import com.vandam.luma.ui.compose.SingleChoiceScreen

class LightOSMediaRouteFragment : Fragment() {
    private val prefs by lazy { Prefs.getInstance(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        val currentRoute = prefs.getLightOsMediaRoute()
        SingleChoiceScreen(
            title = stringResource(R.string.keymaps_lightos_media_playing),
            onBack = ::goBack,
            options =
                listOf(
                    SingleChoiceOption(
                        title = stringResource(R.string.tools_music),
                        selected = currentRoute == "music",
                        onClick = { selectRoute("music") },
                    ),
                    SingleChoiceOption(
                        title = stringResource(R.string.tools_podcasts),
                        selected = currentRoute == "podcasts",
                        onClick = { selectRoute("podcasts") },
                    ),
                ),
        )
    }

    private fun selectRoute(route: String) {
        prefs.setLightOsMediaRoute(route)
        goBack()
    }
}
