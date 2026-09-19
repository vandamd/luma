package app.luma.style

import android.content.Context
import android.provider.Settings

/**
 * Mirror the Android Display > Font size slider so Luma tracks the same relative sizes.
 */
object SystemFontScale {
    private const val SMALL_THRESHOLD = 0.8f
    private const val MEDIUM_THRESHOLD = 0.95f
    private const val LARGE_THRESHOLD = 1.1f

    fun resolveOption(context: Context): FontSizeOption {
        val rawScale =
            try {
                Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE)
            } catch (_: Settings.SettingNotFoundException) {
                1f
            }

        return when {
            rawScale < SMALL_THRESHOLD -> FontSizeOption.Small
            rawScale < MEDIUM_THRESHOLD -> FontSizeOption.Medium
            rawScale < LARGE_THRESHOLD -> FontSizeOption.Large
            else -> FontSizeOption.ExtraLarge
        }
    }
}
