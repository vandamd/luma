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

class AppCountFragment : Fragment() {
    private val prefs by lazy { Prefs.getInstance(requireContext()) }
    private var pageNumber: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageNumber = arguments?.getInt("pageNumber", 1) ?: 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { AppCountScreen() }

    @Composable
    private fun AppCountScreen() {
        val resources = LocalContext.current.resources
        SingleChoiceScreen(
            title = stringResource(R.string.pages_page_number_of_apps, pageNumber),
            onBack = ::goBack,
            options =
                buildList {
                    for (count in HomeLayout.MIN_APPS_PER_PAGE..HomeLayout.APPS_PER_PAGE) {
                        add(
                            SingleChoiceOption(
                                title = resources.getQuantityString(R.plurals.apps_count, count, count),
                                selected = prefs.getAppsPerPage(pageNumber) == count,
                                onClick = { updateAppsPerPage(pageNumber, count) },
                            ),
                        )
                    }
                },
        )
    }

    private fun updateAppsPerPage(
        page: Int,
        count: Int,
    ) {
        prefs.setAppsPerPage(page, count)
        goBack()
    }
}
