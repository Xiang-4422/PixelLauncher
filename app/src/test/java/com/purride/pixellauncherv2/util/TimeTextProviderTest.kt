package com.purride.pixellauncherv2.util

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [TimeTextProvider] — injectable-now formatting of the clock line,
 * date line and the delay until the next minute boundary. Millis are built from
 * a default-timezone [Calendar] so the formatter (same default timezone) reads
 * them back consistently. JVM-safe; no Android dependencies.
 */
class TimeTextProviderTest {

    private val provider = TimeTextProvider()

    private fun millisAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millis: Int,
    ): Long = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, second)
        set(Calendar.MILLISECOND, millis)
    }.timeInMillis

    @Test
    fun currentTimeText_formatsZeroPaddedHourMinute() {
        val now = millisAt(2026, Calendar.JANUARY, 5, 7, 9, 30, 0)
        assertEquals("07:09", provider.currentTimeText(now))
    }

    @Test
    fun currentDateText_isUppercaseEnglishDateLine() {
        val now = millisAt(2026, Calendar.JANUARY, 5, 7, 9, 30, 0)
        // 2026-01-05 is a Monday.
        assertEquals("MONDAY JAN 05", provider.currentDateText(now))
    }

    @Test
    fun millisUntilNextMinute_returnsRemainderToNextBoundary() {
        val now = millisAt(2026, Calendar.JANUARY, 5, 7, 9, 30, 0)
        assertEquals(30_000L, provider.millisUntilNextMinute(now))
    }

    @Test
    fun millisUntilNextMinute_flooredToOneSecond() {
        val now = millisAt(2026, Calendar.JANUARY, 5, 7, 9, 59, 500)
        assertEquals(1_000L, provider.millisUntilNextMinute(now))
    }
}
