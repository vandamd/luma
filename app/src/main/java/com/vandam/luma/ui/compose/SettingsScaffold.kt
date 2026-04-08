package com.vandam.luma.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vandam.luma.R
import com.vandam.luma.helper.performAppTapHapticFeedback
import com.vandam.luma.helper.performHapticFeedback
import com.vandam.luma.style.SettingsTheme
import com.vandam.luma.ui.noRippleClickable
import java.util.Locale

data class SingleChoiceOption(
    val title: String,
    val selected: Boolean,
    @param:DrawableRes val iconRes: Int? = null,
    val onClick: () -> Unit,
)

sealed interface SettingsBodyState {
    data object Content : SettingsBodyState

    data class CenteredMessage(
        val text: String,
        val onClick: (() -> Unit)? = null,
    ) : SettingsBodyState
}

@Composable
fun SettingsHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SettingsTheme.backgroundColor, SettingsTheme.shape)
                .padding(horizontal = 6.dp, vertical = 0.dp),
    ) {
        if (onBack != null) {
            HeaderIconButton(
                iconRes = R.drawable.arrow_back_ios_new_24px,
                contentDescription = stringResource(R.string.content_desc_back),
                onClick = onBack,
            )
        } else {
            Spacer(modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = title,
                style = SettingsTheme.typography.title,
                modifier = Modifier.padding(top = 9.dp, bottom = 24.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                trailingContent != null -> {
                    trailingContent()
                }

                onAction != null -> {
                    HeaderIconButton(
                        iconRes = R.drawable.check_24px,
                        contentDescription = stringResource(R.string.content_desc_save),
                        onClick = onAction,
                    )
                }

                else -> {
                    Spacer(modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Image(
        painter = painterResource(id = iconRes),
        contentDescription = contentDescription,
        modifier =
            modifier
                .size(32.dp)
                .padding(top = 9.dp, bottom = 0.dp)
                .noRippleClickable {
                    performHapticFeedback(context)
                    onClick()
                },
        colorFilter = ColorFilter.tint(SettingsTheme.typography.title.color),
    )
}

@Composable
fun ContentContainer(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SettingsItemSpacing),
    scrollable: Boolean = true,
    contentPadding: PaddingValues = SettingsListContentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val modifier =
        modifier
            .fillMaxWidth()
            .padding(contentPadding)

    if (scrollable) {
        CustomScrollView(
            modifier = modifier,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = verticalArrangement,
        ) {
            content()
        }
    }
}

@Composable
private fun HeaderScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        SettingsHeader(
            title = title,
            onBack = onBack,
            onAction = onAction,
            trailingContent = trailingContent,
        )

        if (footer != null) {
            Column(modifier = Modifier.weight(1f, fill = true)) {
                content()
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                footer()
            }
        } else {
            content()
        }
    }
}

@Composable
fun SettingsScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = SettingsListContentPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SettingsItemSpacing),
    scrollable: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    HeaderScreen(
        title = title,
        onBack = onBack,
        onAction = onAction,
        trailingContent = trailingContent,
        footer = footer,
    ) {
        ContentContainer(
            modifier = if (footer != null) Modifier.weight(1f, fill = true) else Modifier,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            scrollable = scrollable,
            content = content,
        )
    }
}

@Composable
fun SettingsStateScreen(
    title: String,
    bodyState: SettingsBodyState,
    onBack: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = SettingsListContentPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SettingsItemSpacing),
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    HeaderScreen(
        title = title,
        onBack = onBack,
        onAction = onAction,
        trailingContent = trailingContent,
    ) {
        when (bodyState) {
            SettingsBodyState.Content -> {
                ContentContainer(
                    modifier = Modifier.weight(1f, fill = true),
                    contentPadding = contentPadding,
                    verticalArrangement = verticalArrangement,
                    scrollable = scrollable,
                    content = content,
                )
            }

            is SettingsBodyState.CenteredMessage -> {
                Box(
                    modifier =
                        Modifier
                            .weight(1f, fill = true)
                            .fillMaxWidth()
                            .then(
                                if (bodyState.onClick != null) {
                                    Modifier.noRippleClickable { bodyState.onClick.invoke() }
                                } else {
                                    Modifier
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    MessageText(bodyState.text)
                }
            }
        }
    }
}

@Composable
fun SingleChoiceScreen(
    title: String,
    options: Iterable<SingleChoiceOption>,
    onBack: () -> Unit,
) {
    SettingsScreen(
        title = title,
        onBack = onBack,
    ) {
        options.forEach { option ->
            SimpleTextButton(
                title = option.title,
                underline = option.selected,
                iconRes = option.iconRes,
                onClick = option.onClick,
            )
        }
    }
}

@Composable
fun BottomActionText(
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Text(
        text = title.uppercase(Locale.getDefault()),
        style = SettingsTheme.typography.pageButton,
        fontSize = 40.sp,
        color = if (enabled) SettingsTheme.typography.pageButton.color else androidx.compose.ui.graphics.Color.Gray,
        modifier =
            Modifier
                .padding(top = 8.dp)
                .noRippleClickable(enabled = enabled) {
                    performAppTapHapticFeedback(context)
                    onClick()
                },
    )
}

@Composable
fun MessageText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = SettingsTheme.typography.item,
        fontSize = 18.sp,
        modifier = modifier,
    )
}
