package com.vandam.luma.style

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vandam.luma.R
import com.vandam.luma.data.Prefs
import com.vandam.luma.style.CORNER_RADIUS
import com.vandam.luma.style.textDark
import com.vandam.luma.style.textGray
import com.vandam.luma.style.textLight

@Composable
fun isDarkTheme(prefs: Prefs): Boolean =
    when (prefs.themeMode) {
        Prefs.ThemeMode.Dark -> true
        Prefs.ThemeMode.Light -> false
    }

@Immutable
data class SettingsTypography(
    val title: TextStyle,
    val item: TextStyle,
    val pageButton: TextStyle,
    val button: TextStyle,
    val buttonDisabled: TextStyle,
)

private val LocalTypography =
    staticCompositionLocalOf {
        SettingsTypography(
            title = TextStyle.Default,
            item = TextStyle.Default,
            pageButton = TextStyle.Default,
            button = TextStyle.Default,
            buttonDisabled = TextStyle.Default,
        )
    }

private val LocalShape =
    staticCompositionLocalOf<Shape> {
        RoundedCornerShape(ZeroCornerSize)
    }

private val LocalBackgroundColor =
    staticCompositionLocalOf {
        Color.Unspecified
    }

@OptIn(ExperimentalTextApi::class)
@Composable
fun SettingsTheme(
    isDark: Boolean,
    content: @Composable () -> Unit,
) {
    val textColor = if (isDark) textLight else textDark
    val typography =
        SettingsTypography(
            title = TextStyle(fontSize = 20.sp, color = textColor),
            item =
                TextStyle(
                    fontFamily = FontFamily(Font(R.font.public_sans)),
                    fontWeight = FontWeight.Light,
                    fontSize = 16.sp,
                    color = textColor,
                ),
            pageButton =
                TextStyle(
                    fontFamily = FontFamily(Font(R.font.public_sans)),
                    fontSize = 32.sp,
                    color = textColor,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            button =
                TextStyle(
                    fontFamily = FontFamily(Font(R.font.public_sans)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = textColor,
                ),
            buttonDisabled =
                TextStyle(
                    fontFamily = FontFamily(Font(R.font.public_sans)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = textGray,
                ),
        )

    CompositionLocalProvider(
        LocalTypography provides typography,
        LocalShape provides RoundedCornerShape(CORNER_RADIUS),
        LocalBackgroundColor provides colorResource(if (isDark) R.color.black else R.color.white),
    ) {
        MaterialTheme(content = content)
    }
}

object SettingsTheme {
    val typography: SettingsTypography
        @Composable get() = LocalTypography.current

    val shape: Shape
        @Composable get() = LocalShape.current

    val backgroundColor: Color
        @Composable get() = LocalBackgroundColor.current
}
