package com.vandam.luma.data

import android.content.Context
import androidx.annotation.StringRes
import com.vandam.luma.R
import java.text.Collator

enum class Tool(
    @param:StringRes val labelRes: Int,
    val prefKey: String,
    val lightOsRoute: String,
) {
    Phone(R.string.tools_phone, "light_app_phone_enabled", "recentsindex"),
    Settings(R.string.tools_settings, "light_app_settings_enabled", "settingsindex"),
    Album(R.string.tools_album, "light_app_album_enabled", "album"),
    Alarm(R.string.tools_alarm, "light_app_alarm_enabled", "alarm"),
    Calculator(R.string.tools_calculator, "light_app_calculator_enabled", "calculator"),
    Directions(R.string.tools_directions, "light_app_directions_enabled", "directions"),
    Directory(R.string.tools_directory, "light_app_directory_enabled", "directory"),
    Camera(R.string.tools_camera, "light_app_camera_enabled", "camera"),
    Notes(R.string.tools_notes, "light_app_notes_enabled", "notes"),
    Calendar(R.string.tools_calendar, "light_app_calendar_enabled", "calendar"),
    Timer(R.string.tools_timer, "light_app_timer_enabled", "timer"),
    Music(R.string.tools_music, "light_app_music_enabled", "music"),
    Podcasts(R.string.tools_podcasts, "light_app_podcasts_enabled", "podcasts"),
    Hotspot(R.string.tools_hotspot, "light_app_hotspot_enabled", "hotspot"),
    ;

    val id: String
        get() = name.lowercase()

    val packageName: String
        get() = "$SYNTHETIC_PACKAGE_PREFIX$id"

    fun defaultLabel(context: Context): String = context.getString(labelRes)

    fun toAppModel(
        context: Context,
        collator: Collator,
    ): AppModel {
        val label = defaultLabel(context)
        return AppModel(
            appLabel = label,
            key = collator.getCollationKey(label),
            appPackage = packageName,
            appActivityName = id,
            user = android.os.Process.myUserHandle(),
            hasNotification = false,
            entryType = AppEntryType.Tool,
        )
    }

    companion object {
        const val SYNTHETIC_PACKAGE_PREFIX = "__tool__."

        fun fromId(id: String?): Tool? = entries.firstOrNull { it.id == id }

        fun fromPackageName(packageName: String?): Tool? = entries.firstOrNull { it.packageName == packageName }
    }
}
