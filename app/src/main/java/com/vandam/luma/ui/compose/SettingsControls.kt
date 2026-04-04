package com.vandam.luma.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vandam.luma.helper.performHapticFeedback
import com.vandam.luma.style.SettingsTheme
import com.vandam.luma.ui.noRippleClickable

private fun Modifier.underline(
    color: Color,
    yOffset: Dp = 2.dp,
): Modifier =
    drawBehind {
        val strokeWidth = 2.dp.toPx()
        val y = size.height + yOffset.toPx()
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth,
        )
    }

@Composable
fun ToggleTextButton(
    title: String,
    checked: Boolean,
    onValueChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    Row(
        modifier =
            Modifier
                .padding(top = 2.dp, bottom = 0.dp)
                .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CustomToggleSwitch(checked = checked, onCheckedChange = onValueChange, enabled = enabled)
        Box(modifier = Modifier.offset(y = 5.dp)) {
            SimpleTextButton(
                title = title,
                fontSize = fontSize,
                enabled = enabled,
                onClick = { onValueChange(!checked) },
            )
        }
    }
}

@Composable
fun CustomToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val circleDiameter = 8.dp
    val circleBorder = 2.dp
    val lineWidth = 12.dp
    val lineHeight = 2.22.dp

    val switchColor = if (enabled) SettingsTheme.typography.title.color else Color.Gray

    Row(
        modifier =
            Modifier
                .noRippleClickable(enabled = enabled) {
                    performHapticFeedback(context)
                    onCheckedChange(!checked)
                }.padding(7.4.dp, 10.dp, 13.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!checked) {
            Box(
                modifier =
                    Modifier
                        .size(circleDiameter)
                        .border(circleBorder, switchColor, CircleShape),
            )
            Box(
                modifier =
                    Modifier
                        .width(lineWidth)
                        .height(lineHeight)
                        .background(switchColor),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .width(lineWidth)
                        .height(lineHeight)
                        .background(switchColor),
            )
            Box(
                modifier =
                    Modifier
                        .size(circleDiameter)
                        .background(switchColor, CircleShape),
            )
        }
    }
}

@Composable
fun SimpleTextButton(
    title: String,
    fontSize: TextUnit = TextUnit.Unspecified,
    underline: Boolean = false,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val underlineColor = SettingsTheme.typography.pageButton.color
    val textColor = if (enabled) SettingsTheme.typography.pageButton.color else Color.Gray
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 0.dp)) {
        Row(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(y = (-5.5).dp)
                    .noRippleClickable(enabled = enabled) {
                        performHapticFeedback(context)
                        onClick()
                    }.then(
                        if (underline) Modifier.underline(underlineColor) else Modifier,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = SettingsTheme.typography.pageButton,
                fontSize = fontSize,
                color = textColor,
            )
            if (iconRes != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "(",
                    style = SettingsTheme.typography.pageButton,
                    fontSize = fontSize,
                    color = textColor,
                )
                Spacer(modifier = Modifier.width(2.dp))
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(textColor),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    ")",
                    style = SettingsTheme.typography.pageButton,
                    fontSize = fontSize,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
fun SelectorButton(
    label: String,
    value: String,
    isSelected: Boolean = false,
    @DrawableRes iconRes: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val selectedColor = SettingsTheme.typography.button.color
    val valueColor = SettingsTheme.typography.item.color
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 0.dp)
                .noRippleClickable(enabled = enabled) {
                    performHapticFeedback(context)
                    onClick()
                },
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            label,
            style = SettingsTheme.typography.item,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .padding(bottom = 0.dp)
                    .then(
                        if (isSelected) Modifier.underline(selectedColor, yOffset = (-5).dp) else Modifier,
                    ),
        ) {
            Text(
                value,
                style = SettingsTheme.typography.item,
                fontSize = 30.sp,
            )
            if (iconRes != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "(",
                    style = SettingsTheme.typography.item,
                    fontSize = 30.sp,
                )
                Spacer(modifier = Modifier.width(2.dp))
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(valueColor),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    ")",
                    style = SettingsTheme.typography.item,
                    fontSize = 30.sp,
                )
            }
        }
    }
}

@Composable
fun ToggleSelectorButton(
    label: String,
    value: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    @DrawableRes labelIconRes: Int? = null,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val labelColor = if (enabled) SettingsTheme.typography.item.color else Color.Gray
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 0.dp)
                .noRippleClickable(enabled = enabled) {
                    performHapticFeedback(context)
                    onClick()
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CustomToggleSwitch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else { _ -> },
            enabled = enabled,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = SettingsTheme.typography.item,
                    fontSize = 30.sp,
                    color = labelColor,
                )
                if (labelIconRes != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "(",
                        style = SettingsTheme.typography.item,
                        fontSize = 30.sp,
                        color = labelColor,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Image(
                        painter = painterResource(id = labelIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(labelColor),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        ")",
                        style = SettingsTheme.typography.item,
                        fontSize = 30.sp,
                        color = labelColor,
                    )
                }
            }
            Text(
                value,
                style = SettingsTheme.typography.item,
                fontSize = 16.sp,
                color = if (enabled) Color.Unspecified else Color.Gray,
                modifier = Modifier.padding(top = 0.dp),
            )
        }
    }
}
