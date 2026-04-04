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

class PageIndicatorPositionFragment : Fragment() {
    private val prefs by lazy { Prefs.getInstance(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() =
        SingleChoiceScreen(
            title = stringResource(R.string.pages_page_indicator_position),
            onBack = ::goBack,
            options =
                listOf(
                    SingleChoiceOption(
                        title = stringResource(R.string.position_left),
                        selected = prefs.pageIndicatorPosition == Prefs.PageIndicatorPosition.Left,
                        onClick = { selectPosition(Prefs.PageIndicatorPosition.Left) },
                    ),
                    SingleChoiceOption(
                        title = stringResource(R.string.position_right),
                        selected = prefs.pageIndicatorPosition == Prefs.PageIndicatorPosition.Right,
                        onClick = { selectPosition(Prefs.PageIndicatorPosition.Right) },
                    ),
                    SingleChoiceOption(
                        title = stringResource(R.string.position_hidden),
                        selected = prefs.pageIndicatorPosition == Prefs.PageIndicatorPosition.Hidden,
                        onClick = { selectPosition(Prefs.PageIndicatorPosition.Hidden) },
                    ),
                ),
        )

    private fun selectPosition(position: Prefs.PageIndicatorPosition) {
        prefs.pageIndicatorPosition = position
        goBack()
    }
}
