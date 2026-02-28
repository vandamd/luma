package app.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import app.luma.R
import app.luma.data.HomeLayout
import app.luma.data.Prefs
import app.luma.ui.compose.CustomScrollView
import app.luma.ui.compose.SettingsComposable.ContentContainer
import app.luma.ui.compose.SettingsComposable.MessageText
import app.luma.ui.compose.SettingsComposable.SettingsHeader
import app.luma.ui.compose.SettingsComposable.SimpleTextButton

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
                CustomScrollView {
                    if (prefs.statusBarMode == Prefs.StatusBarMode.Enabled && resources.configuration.fontScale >= 0.85f) {
                        MessageText(
                            stringResource(R.string.app_count_status_bar_hint),
                            modifier = Modifier.padding(end = 30.dp),
                        )
                    }
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
    }

    private fun updateAppsPerPage(
        page: Int,
        count: Int,
    ) {
        prefs.setAppsPerPage(page, count)
        goBack()
    }
}
