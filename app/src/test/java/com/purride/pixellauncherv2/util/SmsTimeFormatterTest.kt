package com.purride.pixellauncherv2.util

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [SmsTimeFormatter] — invalid-timestamp placeholder, same-day
 * "HH:MM" and other-day "M/D" formatting. Timestamps are built relative to the
 * same system clock/timezone the formatter reads, so assertions stay timezone
 * self-consistent. JVM-safe; no Android dependencies.
 */
class SmsTimeFormatterTest {

    @Test
    fun format_nonPositiveReturnsPlaceholder() {
        assertEquals("--:--", SmsTimeFormatter.format(0L))
        assertEquals("--:--", SmsTimeFormatter.format(-1L))
    }

    @Test
    fun format_sameDayReturnsZeroPaddedHourMinute() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals("09:05", SmsTimeFormatter.format(cal.timeInMillis))
    }

    @Test
    fun format_otherDayReturnsMonthSlashDay() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.YEAR, -1)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 7)
        }
        assertEquals("3/7", SmsTimeFormatter.format(cal.timeInMillis))
    }
}
