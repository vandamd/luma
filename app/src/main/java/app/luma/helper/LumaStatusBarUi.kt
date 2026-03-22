package app.luma.helper

import android.content.Intent
import android.os.BatteryManager
import android.telephony.TelephonyManager
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.luma.R
import app.luma.data.Prefs

object LumaStatusBarUi {
    fun showTinted(
        imageView: ImageView,
        icon: Int,
        tintColor: Int,
    ) {
        imageView.visibility = View.VISIBLE
        imageView.setImageResource(icon)
        imageView.setColorFilter(tintColor)
    }

    fun updateSectionBaseline(layout: LinearLayout) {
        for (index in 0 until layout.childCount) {
            if (layout.getChildAt(index).visibility != View.GONE) {
                layout.baselineAlignedChildIndex = index
                return
            }
        }
    }

    fun clockPlaceholder(prefs: Prefs): String {
        val is24Hour = prefs.timeFormat == Prefs.TimeFormat.TwentyFourHour
        val showSeconds = prefs.showSeconds
        val hour = if (is24Hour || prefs.leadingZero) "00" else "12"
        return buildString {
            append("$hour:00")
            if (showSeconds) append(":00")
            if (!is24Hour) append(" AM")
        }
    }

    fun signalDrawableForLevel(level: Int): Int =
        when (level.coerceIn(0, 4)) {
            0 -> R.drawable.signal_0
            1 -> R.drawable.signal_1
            2 -> R.drawable.signal_2
            3 -> R.drawable.signal_3
            else -> R.drawable.signal_4
        }

    fun wifiDrawableForLevel(level: Int): Int =
        when {
            level <= 1 -> R.drawable.wifi_1
            level == 2 -> R.drawable.wifi_2
            else -> R.drawable.wifi_full
        }

    fun networkLabelForType(type: Int?): String =
        when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS,
            -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "E"
            TelephonyManager.NETWORK_TYPE_GPRS -> "G"
            else -> ""
        }

    fun batteryIconRes(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level < 0 || scale <= 0) 0 else level * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging =
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return if (charging) {
            R.drawable.battery_charging
        } else {
            when {
                pct >= 95 -> R.drawable.battery_full
                pct >= 60 -> R.drawable.battery_75
                pct >= 40 -> R.drawable.battery_50
                pct >= 20 -> R.drawable.battery_low
                pct >= 5 -> R.drawable.battery_very_low
                else -> R.drawable.battery_empty
            }
        }
    }
}
