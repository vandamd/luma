package app.luma.helper

import android.text.format.DateFormat
import app.luma.data.Prefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun formatClockText(
    prefs: Prefs,
    calendar: Calendar = Calendar.getInstance(),
    appendNotificationIndicator: Boolean = false,
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
        if (appendNotificationIndicator && LumaNotificationListener.getActiveNotificationPackages().isNotEmpty()) {
            append("*")
        }
    }
}

fun formatLockscreenDateText(
    format: Prefs.LockscreenDateFormat,
    calendar: Calendar = Calendar.getInstance(),
    locale: Locale = Locale.getDefault(),
): String {
    val skeleton =
        when (format) {
            Prefs.LockscreenDateFormat.ShortWeekday -> "EEE MMM d"
            Prefs.LockscreenDateFormat.LongWeekday -> "EEEE MMMM d"
        }
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return SimpleDateFormat(pattern, locale).format(calendar.time)
}
