package com.vandam.luma.ui.compose

import androidx.compose.runtime.Composable

@Composable
fun NotificationIndicatorSelector(
    label: String,
    hasPermission: Boolean,
    visible: Boolean,
    visibleText: String,
    hiddenText: String,
    permissionRequiredText: String,
    onVisibilityChange: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
) {
    ToggleSelectorButton(
        label = label,
        value =
            when {
                !hasPermission -> permissionRequiredText
                visible -> visibleText
                else -> hiddenText
            },
        checked = hasPermission && visible,
        onCheckedChange = { nextVisible ->
            if (hasPermission) {
                onVisibilityChange(nextVisible)
            } else {
                onRequestPermission()
            }
        },
        onClick = {
            if (hasPermission) {
                onVisibilityChange(!visible)
            } else {
                onRequestPermission()
            }
        },
    )
}
