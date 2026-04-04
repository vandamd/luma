package com.vandam.luma.helper

import android.os.Build

object SupportedDevice {
    fun isLightPhoneIII(): Boolean =
        Build.MANUFACTURER.equals("Light", ignoreCase = true) &&
            Build.MODEL.equals("TLP301", ignoreCase = true)
}
