package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import com.vandam.luma.R
import com.vandam.luma.data.HomeLayout
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SingleChoiceOption
import com.vandam.luma.ui.compose.SingleChoiceScreen

class PageCountFragment : Fragment() {
    private val prefs by lazy { Prefs.getInstance(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { PageCountScreen() }

    @Composable
    private fun PageCountScreen() {
        val resources = LocalContext.current.resources
        SingleChoiceScreen(
            title = stringResource(R.string.pages_number_of_pages),
            onBack = ::goBack,
            options =
                buildList {
                    for (count in HomeLayout.MIN_PAGES..HomeLayout.MAX_PAGES) {
                        add(
                            SingleChoiceOption(
                                title = resources.getQuantityString(R.plurals.pages_count, count, count),
                                selected = prefs.homePages == count,
                                onClick = { updateHomePages(count) },
                            ),
                        )
                    }
                },
        )
    }

    private fun updateHomePages(homePages: Int) {
        prefs.homePages = homePages
        goBack()
    }
}
