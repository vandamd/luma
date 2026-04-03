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

class PageCountFragment : Fragment() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { PageCountScreen() }

    @Composable
    fun PageCountScreen() {
        val resources = LocalContext.current.resources
        Column {
            SettingsHeader(
                title = stringResource(R.string.pages_number_of_pages),
                onBack = ::goBack,
            )

            ContentContainer {
                    for (i in HomeLayout.MIN_PAGES..HomeLayout.MAX_PAGES) {
                        val isSelected = prefs.homePages == i
                        SimpleTextButton(
                            title = resources.getQuantityString(R.plurals.pages_count, i, i),
                            underline = isSelected,
                            onClick = { updateHomePages(i) },
                        )
                    }
            }
        }
    }

    private fun updateHomePages(homePages: Int) {
        prefs.homePages = homePages
        goBack()
    }
}
