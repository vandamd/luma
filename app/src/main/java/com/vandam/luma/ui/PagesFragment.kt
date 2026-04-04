package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vandam.luma.R
import com.vandam.luma.data.HomeLayout
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SelectorButton
import com.vandam.luma.ui.compose.SettingsScreen

class PagesFragment : Fragment() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { PagesScreen() }

    @Composable
    fun PagesScreen() {
        val context = LocalContext.current
        val resources = context.resources
        SettingsScreen(
            title = stringResource(R.string.settings_pages),
            onBack = ::goBack,
        ) {
            SelectorButton(
                label = stringResource(R.string.pages_page_indicator_position),
                value =
                    when (prefs.pageIndicatorPosition) {
                        Prefs.PageIndicatorPosition.Left -> stringResource(R.string.position_left)
                        Prefs.PageIndicatorPosition.Right -> stringResource(R.string.position_right)
                        Prefs.PageIndicatorPosition.Hidden -> stringResource(R.string.position_hidden)
                    },
                onClick = { findNavController().navigate(R.id.action_pagesFragment_to_pageIndicatorPositionFragment) },
            )
            val pageCount = prefs.homePages.coerceIn(HomeLayout.MIN_PAGES, HomeLayout.MAX_PAGES)
            SelectorButton(
                label = stringResource(R.string.pages_number_of_pages),
                value = resources.getQuantityString(R.plurals.pages_count, pageCount, pageCount),
                onClick = { findNavController().navigate(R.id.action_pagesFragment_to_pageCountFragment) },
            )
            for (i in 1..prefs.homePages) {
                val page = i
                val appCount = prefs.getAppsPerPage(i)
                SelectorButton(
                    label = stringResource(R.string.pages_page_number_of_apps, i),
                    value = resources.getQuantityString(R.plurals.apps_count, appCount, appCount),
                    onClick = {
                        val bundle = bundleOf("pageNumber" to page)
                        findNavController().navigate(R.id.action_pagesFragment_to_appCountFragment, bundle)
                    },
                )
            }
        }
    }
}
