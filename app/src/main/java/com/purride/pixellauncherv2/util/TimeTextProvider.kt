package com.purride.pixellauncherv2.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class TimeTextProvider(
    locale: Locale = Locale.getDefault(),
) {

    private val formatter = SimpleDateFormat("HH:mm", locale)

    fun currentTimeText(nowMillis: Long = System.currentTimeMillis()): String {
        return formatter.format(Date(nowMillis))
    }

    fun millisUntilNextMinute(nowMillis: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1)
        }
        return max(1_000L, calendar.timeInMillis - nowMillis)
    }
}
