package com.vandam.luma.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vandam.luma.style.SettingsTheme
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomScrollView(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SettingsItemSpacing),
    content: @Composable ColumnScope.() -> Unit,
) {
    var contentHeightPx by remember { mutableStateOf(0) }
    var scrollViewHeightPx by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val maxScrollOffset = max(0, contentHeightPx - scrollViewHeightPx)

    val scrollIndicatorHeightPx =
        if (scrollViewHeightPx > 0 && contentHeightPx > scrollViewHeightPx) {
            max((scrollViewHeightPx * scrollViewHeightPx) / contentHeightPx, 20)
        } else {
            0
        }

    val scrollIndicatorPositionPx =
        if (contentHeightPx > scrollViewHeightPx && scrollIndicatorHeightPx > 0) {
            val progress = if (maxScrollOffset > 0) scrollState.value.toFloat() / maxScrollOffset else 0f
            max(0, (progress * (scrollViewHeightPx - scrollIndicatorHeightPx)).toInt())
        } else {
            0
        }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged {
                            scrollViewHeightPx = it.height
                        }.verticalScroll(scrollState),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged {
                                contentHeightPx = it.height
                            }.padding(bottom = 4.dp),
                ) {
                    Column(verticalArrangement = verticalArrangement) {
                        content()
                    }
                }
            }

            // Custom scroll indicator
            if (scrollIndicatorHeightPx > 0) {
                Box(
                    modifier =
                        Modifier
                            .width(0.8.dp)
                            .fillMaxHeight()
                            .align(Alignment.TopEnd)
                            .offset(x = -SettingsScrollIndicatorTrackInset)
                            .background(SettingsTheme.typography.item.color),
                )
                Box(
                    modifier =
                        Modifier
                            .width(4.5.dp)
                            .height(with(density) { scrollIndicatorHeightPx.toDp() })
                            .align(Alignment.TopEnd)
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = scrollIndicatorPositionPx,
                                )
                            }.offset(x = -SettingsScrollIndicatorThumbInset)
                            .background(SettingsTheme.typography.item.color),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazySettingsList(
    modifier: Modifier = Modifier,
    itemSpacing: Dp = SettingsItemSpacing,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = SettingsListContentPadding,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val layoutInfo = state.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val viewportHeightPx = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(0)
    val averageItemHeightPx =
        visibleItems
            .takeIf { it.isNotEmpty() }
            ?.sumOf { it.size }
            ?.div(visibleItems.size)
            ?: 0
    val itemSpacingPx = with(density) { itemSpacing.roundToPx() }
    val contentHeightPx =
        if (averageItemHeightPx > 0 && layoutInfo.totalItemsCount > 0) {
            val paddedContentHeight =
                averageItemHeightPx * layoutInfo.totalItemsCount +
                    itemSpacingPx * max(0, layoutInfo.totalItemsCount - 1)
            paddedContentHeight +
                with(density) { contentPadding.calculateTopPadding().roundToPx() } +
                with(density) { contentPadding.calculateBottomPadding().roundToPx() }
        } else {
            0
        }
    val scrollIndicatorHeightPx =
        if (viewportHeightPx > 0 && contentHeightPx > viewportHeightPx) {
            max((viewportHeightPx * viewportHeightPx) / contentHeightPx, 20)
        } else {
            0
        }
    val scrollOffsetPx =
        if (visibleItems.isNotEmpty() && averageItemHeightPx > 0) {
            visibleItems.first().index * (averageItemHeightPx + itemSpacingPx) + state.firstVisibleItemScrollOffset
        } else {
            0
        }
    val maxScrollOffset = max(0, contentHeightPx - viewportHeightPx)
    val scrollIndicatorPositionPx =
        if (contentHeightPx > viewportHeightPx && scrollIndicatorHeightPx > 0) {
            val progress = if (maxScrollOffset > 0) scrollOffsetPx.toFloat() / maxScrollOffset else 0f
            max(0, (progress * (viewportHeightPx - scrollIndicatorHeightPx)).toInt())
        } else {
            0
        }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = state,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
            ) {
                content()
            }

            if (scrollIndicatorHeightPx > 0) {
                Box(
                    modifier =
                        Modifier
                            .width(0.8.dp)
                            .fillMaxHeight()
                            .align(Alignment.TopEnd)
                            .offset(x = -SettingsScrollIndicatorTrackInset)
                            .background(SettingsTheme.typography.item.color),
                )
                Box(
                    modifier =
                        Modifier
                            .width(4.5.dp)
                            .height(with(density) { scrollIndicatorHeightPx.toDp() })
                            .align(Alignment.TopEnd)
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = scrollIndicatorPositionPx,
                                )
                            }.offset(x = -SettingsScrollIndicatorThumbInset)
                            .background(SettingsTheme.typography.item.color),
                )
            }
        }
    }
}
