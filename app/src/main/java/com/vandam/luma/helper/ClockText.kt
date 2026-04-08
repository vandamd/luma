package com.vandam.luma.helper

import android.content.Context
import android.text.format.DateFormat
import com.vandam.luma.data.Prefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun formatClockText(
    prefs: Prefs,
    calendar: Calendar = Calendar.getInstance(),
    appendNotificationIndicator: Boolean = false,
    hasNotificationIndicator: Boolean = false,
): String {
    val is24Hour = prefs.timeFormat == Prefs.TimeFormat.TwentyFourHour
    val hour =
        if (is24Hour) {
            calendar.get(Calendar.HOUR_OF_DAY)
        } else {
            calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        }
    val minute = calendar.get(Calendar.MINUTE)
    val hourText =
        if (is24Hour || prefs.leadingZero) {
            "%02d".format(hour)
        } else {
            hour.toString()
        }

    return buildString {
        append("$hourText:${"%02d".format(minute)}")
        if (!is24Hour) append(if (calendar.get(Calendar.AM_PM) == Calendar.AM) " AM" else " PM")
        if (appendNotificationIndicator && hasNotificationIndicator) {
            append("*")
        }
    }
}

fun hasClockNotificationIndicator(context: Context): Boolean =
    LumaNotificationListener.getActiveNotificationPackages().isNotEmpty() ||
        PhoneSignalHelper.hasUnreadPhoneSignal(context)

fun formatLockscreenDateText(
    format: Prefs.LockscreenDateFormat,
    calendar: Calendar = Calendar.getInstance(),
    locale: Locale = Locale.getDefault(),
): String {
    when (format) {
        Prefs.LockscreenDateFormat.None -> {
            return ""
        }

        Prefs.LockscreenDateFormat.ShortWeekday -> {
            val pattern = DateFormat.getBestDateTimePattern(locale, "EEE MMM d")
            return SimpleDateFormat(pattern, locale).format(calendar.time)
        }

        Prefs.LockscreenDateFormat.LongWeekday -> {
            val pattern = DateFormat.getBestDateTimePattern(locale, "EEEE MMMM d")
            return SimpleDateFormat(pattern, locale).format(calendar.time)
        }

        Prefs.LockscreenDateFormat.SlashedDMY -> {
            return SimpleDateFormat("dd/MM/yy", locale).format(calendar.time)
        }

        Prefs.LockscreenDateFormat.SlashedMDY -> {
            return SimpleDateFormat("MM/dd/yy", locale).format(calendar.time)
        }

        Prefs.LockscreenDateFormat.ISO8601 -> {
            return SimpleDateFormat("yyyy-MM-dd", locale).format(calendar.time)
        }
    }
}
