package app.luma.helper

import app.luma.data.Prefs
import java.util.Calendar

fun formatClockText(
    prefs: Prefs,
    calendar: Calendar = Calendar.getInstance(),
    appendNotificationIndicator: Boolean = false,
): String {
    val is24Hour = prefs.timeFormat == Prefs.TimeFormat.TwentyFourHour
    val showSeconds = prefs.showSeconds
    val hour =
        if (is24Hour) {
            calendar.get(Calendar.HOUR_OF_DAY)
        } else {
            calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        }
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    val hourText =
        if (is24Hour || prefs.leadingZero) {
            "%02d".format(hour)
        } else {
            hour.toString()
        }

    return buildString {
        append("$hourText:${"%02d".format(minute)}")
        if (showSeconds) append(":${"%02d".format(second)}")
        if (!is24Hour) append(if (calendar.get(Calendar.AM_PM) == Calendar.AM) " AM" else " PM")
        if (appendNotificationIndicator && LumaNotificationListener.getActiveNotificationPackages().isNotEmpty()) {
            append("*")
        }
    }
}
