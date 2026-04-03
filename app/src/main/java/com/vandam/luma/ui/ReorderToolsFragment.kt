package com.vandam.luma.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.vandam.luma.R
import com.vandam.luma.data.AppModel
import com.vandam.luma.data.HomeItemsManager
import com.vandam.luma.data.Prefs
import com.vandam.luma.helper.launchAppModel
import com.vandam.luma.helper.performAppTapHapticFeedback
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
        val context = requireContext()
        val items =
            remember {
                mutableStateListOf<AppModel>().apply {
                    addAll(HomeItemsManager.orderedEnabledItems(context, prefs))
                }
            }
        val hiddenStates =
            remember {
                mutableStateMapOf<String, Boolean>().apply {
                    items.forEach { item ->
                        put(prefs.homeItemKey(item), prefs.isHomeItemHidden(item))
                    }
                }
            }

        DisposableEffect(Unit) {
            val listener =
                android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    syncFromPrefs(context, items, hiddenStates)
                }
            prefs.registerListener(listener)
            onDispose {
                prefs.unregisterListener(listener)
            }
        }

        Column {
            SettingsHeader(
                title = stringResource(R.string.homescreen_reorder_tools),
                onBack = ::goBack,
            )

            ContentContainer(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items.forEachIndexed { index, appModel ->
                    val itemKey = prefs.homeItemKey(appModel)
                    ReorderRow(
                        appModel = appModel,
                        label = appModel.displayName,
                        visibleOnHome = hiddenStates[itemKey] != true,
                        canMoveUp = index > 0,
                        canMoveDown = index < items.lastIndex,
                        onOpen = {
                            performAppTapHapticFeedback(context)
                            launchAppModel(context, appModel)
                        },
                        onToggleVisibility = {
                            val visible = hiddenStates[itemKey] != true
                            hiddenStates[itemKey] = visible
                            prefs.setHomeItemHidden(appModel, hidden = visible)
                            HomeItemsManager.applyCurrentHomeLayout(context, prefs)
                        },
                        onMoveUp = {
                            swapItems(items, index, index - 1)
                        },
                        onMoveDown = {
                            swapItems(items, index, index + 1)
                        },
                    )
                }
            }
        }
    }

    private fun syncFromPrefs(
        context: android.content.Context,
        items: MutableList<AppModel>,
        hiddenStates: MutableMap<String, Boolean>,
    ) {
        val latestItems = HomeItemsManager.orderedEnabledItems(context, prefs)
        items.clear()
        items.addAll(latestItems)

        hiddenStates.clear()
        latestItems.forEach { item ->
            hiddenStates[prefs.homeItemKey(item)] = prefs.isHomeItemHidden(item)
        }
    }

    private fun swapItems(
        items: MutableList<AppModel>,
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (fromIndex !in items.indices || toIndex !in items.indices) return

        val fromItem = items[fromIndex]
        val toItem = items[toIndex]

        items[fromIndex] = toItem
        items[toIndex] = fromItem

        prefs.homeItemOrderKeys = items.map(prefs::homeItemKey)
        HomeItemsManager.applyCurrentHomeLayout(requireContext(), prefs)
    }

    @Composable
    private fun ReorderRow(
        appModel: AppModel,
        label: String,
        visibleOnHome: Boolean,
        canMoveUp: Boolean,
        canMoveDown: Boolean,
        onOpen: () -> Unit,
        onToggleVisibility: () -> Unit,
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
        ) {
            Text(
                text = label,
                style = SettingsTheme.typography.pageButton,
                modifier =
                    Modifier
                        .weight(1f)
                        .noRippleClickable(
                            enabled = appModel.appPackage.isNotBlank(),
                            onClick = onOpen,
                        ),
                maxLines = 1,
                color = textColor,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                ReorderIconButton(
                    iconRes = if (visibleOnHome) R.drawable.visibility else R.drawable.visibility_off,
                    enabled = true,
                    tint = if (visibleOnHome) textColor else disabledColor,
                    size = 30.dp,
                    contentDescription =
                        stringResource(
                            if (visibleOnHome) {
                                R.string.content_desc_hide_from_home
                            } else {
                                R.string.content_desc_show_on_home
                            },
                        ),
                    onClick = {
                        performHapticFeedback(context)
                        onToggleVisibility()
                    },
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(3.dp))
                ReorderIconButton(
                    iconRes = R.drawable.keyboard_arrow_down_32px,
                    enabled = canMoveDown,
                    tint = if (canMoveDown) textColor else disabledColor,
                    contentDescription = null,
                    onClick = {
                        performHapticFeedback(context)
                        onMoveDown()
                    },
                )
                ReorderIconButton(
                    iconRes = R.drawable.expand_less_32px,
                    enabled = canMoveUp,
                    tint = if (canMoveUp) textColor else disabledColor,
                    contentDescription = null,
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
        size: Dp = 36.dp,
        contentDescription: String?,
        onClick: () -> Unit,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .size(size)
                    .noRippleClickable(enabled = enabled, onClick = onClick),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}
