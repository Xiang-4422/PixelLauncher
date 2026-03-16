package com.purride.pixellauncherv2.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class TimeTextProvider(
    locale: Locale = Locale.getDefault(),
) {

    private val timeFormatter = SimpleDateFormat("HH:mm", locale)
    private val dateLineFormatter = SimpleDateFormat("EEEE MMM dd", Locale.ENGLISH)

    fun currentTimeText(nowMillis: Long = System.currentTimeMillis()): String {
        return timeFormatter.format(Date(nowMillis))
    }

    fun currentDateText(nowMillis: Long = System.currentTimeMillis()): String {
        return dateLineFormatter.format(Date(nowMillis)).uppercase(Locale.ENGLISH)
    }

    fun currentWeekdayText(nowMillis: Long = System.currentTimeMillis()): String {
        return ""
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
