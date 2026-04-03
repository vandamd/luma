package com.vandam.luma.style

import android.content.Context
import android.provider.Settings

/**
 * Mirror the Android Display > Font size slider so Luma tracks the same relative sizes.
 */
object SystemFontScale {
    private const val SMALL_THRESHOLD = 0.9f

    fun resolveOption(context: Context): FontSizeOption {
        val rawScale =
            try {
                Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE)
            } catch (_: Settings.SettingNotFoundException) {
                1f
            }

        return if (rawScale <= SMALL_THRESHOLD) FontSizeOption.Small else FontSizeOption.Medium
    }
}
