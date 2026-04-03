package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import com.vandam.luma.R
import com.vandam.luma.data.HomeLayout
import com.vandam.luma.data.Prefs
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader
import com.vandam.luma.ui.compose.SettingsComposable.SimpleTextButton

class AppCountFragment : Fragment() {
    private lateinit var prefs: Prefs
    private var pageNumber: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
        pageNumber = arguments?.getInt("pageNumber", 1) ?: 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { AppCountScreen() }

    @Composable
    fun AppCountScreen() {
        val resources = LocalContext.current.resources
        Column {
            SettingsHeader(
                title = stringResource(R.string.pages_page_number_of_apps, pageNumber),
                onBack = ::goBack,
            )

            ContentContainer {
                for (i in HomeLayout.MIN_APPS_PER_PAGE..HomeLayout.APPS_PER_PAGE) {
                    val isSelected = prefs.getAppsPerPage(pageNumber) == i
                    SimpleTextButton(
                        title = resources.getQuantityString(R.plurals.apps_count, i, i),
                        underline = isSelected,
                        onClick = { updateAppsPerPage(pageNumber, i) },
                    )
                }
            }
        }
    }

    private fun updateAppsPerPage(
        page: Int,
        count: Int,
    ) {
        prefs.setAppsPerPage(page, count)
        goBack()
    }
}
