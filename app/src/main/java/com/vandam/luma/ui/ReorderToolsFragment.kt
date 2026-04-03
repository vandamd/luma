package com.vandam.luma.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material.Text
import com.vandam.luma.R
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.HomeLayout
import com.vandam.luma.data.Prefs
import com.vandam.luma.helper.performHapticFeedback
import com.vandam.luma.style.SettingsTheme
import com.vandam.luma.ui.compose.SettingsComposable.ContentContainer
import com.vandam.luma.ui.compose.SettingsComposable.SettingsHeader

class ReorderToolsFragment : Fragment() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView(onSwipeBack = ::goBack) { Screen() }

    @Composable
    private fun Screen() {
        val items =
            remember {
                mutableStateListOf<AppModel>().apply {
                    addAll(loadHomeItems())
                }
            }

        Column {
            SettingsHeader(
                title = stringResource(R.string.homescreen_reorder_tools),
                onBack = ::goBack,
            )

            ContentContainer(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items.forEachIndexed { index, appModel ->
                    ReorderRow(
                        label = appModel.displayName,
                        canMoveUp = index > 0,
                        canMoveDown = index < items.lastIndex,
                        onMoveUp = {
                            swapHomeItems(items, index, index - 1)
                        },
                        onMoveDown = {
                            swapHomeItems(items, index, index + 1)
                        },
                    )
                }
            }
        }
    }

    private fun loadHomeItems(): List<AppModel> =
        buildList {
            for (index in 0 until HomeLayout.TOTAL_SLOTS) {
                val item = prefs.getHomeAppModel(index)
                if (item.appPackage.isBlank()) continue
                add(item)
            }
        }

    private fun swapHomeItems(
        items: MutableList<AppModel>,
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (fromIndex !in items.indices || toIndex !in items.indices) return

        val fromItem = items[fromIndex]
        val toItem = items[toIndex]

        items[fromIndex] = toItem
        items[toIndex] = fromItem

        prefs.setHomeAppModel(fromIndex, toItem)
        prefs.setHomeAppModel(toIndex, fromItem)
    }

    @Composable
    private fun ReorderRow(
        label: String,
        canMoveUp: Boolean,
        canMoveDown: Boolean,
        onMoveUp: () -> Unit,
        onMoveDown: () -> Unit,
    ) {
        val context = LocalContext.current
        val textColor = SettingsTheme.typography.pageButton.color
        val disabledColor = Color.Gray

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(end = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = SettingsTheme.typography.pageButton,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                color = textColor,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReorderIconButton(
                    iconRes = R.drawable.keyboard_arrow_down_32px,
                    enabled = canMoveDown,
                    tint = if (canMoveDown) textColor else disabledColor,
                    onClick = {
                        performHapticFeedback(context)
                        onMoveDown()
                    },
                )
                ReorderIconButton(
                    iconRes = R.drawable.expand_less_32px,
                    enabled = canMoveUp,
                    tint = if (canMoveUp) textColor else disabledColor,
                    onClick = {
                        performHapticFeedback(context)
                        onMoveUp()
                    },
                )
            }
        }
    }

    @Composable
    private fun ReorderIconButton(
        iconRes: Int,
        enabled: Boolean,
        tint: Color,
        onClick: () -> Unit,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier =
                Modifier
                    .size(36.dp)
                    .padding(start = 8.dp)
                    .noRippleClickable(enabled = enabled, onClick = onClick),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}
