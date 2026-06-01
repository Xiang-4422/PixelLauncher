package com.purride.pixellauncherv2.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for [ThrottleClickHelper] — first-click acceptance, suppression
 * within the interval, recovery afterwards and a custom interval. The clock is
 * injected via nowMs so the test never touches SystemClock. JVM-safe; no Android
 * dependencies.
 */
class ThrottleClickHelperTest {

    @Test
    fun canClick_allowsClickOnceIntervalHasElapsedSinceStart() {
        val helper = ThrottleClickHelper(intervalMs = 500L)
        assertTrue(helper.canClick(nowMs = 1_000L))
    }

    @Test
    fun canClick_blocksSecondClickWithinInterval() {
        val helper = ThrottleClickHelper(intervalMs = 500L)
        assertTrue(helper.canClick(nowMs = 1_000L))
        assertFalse(helper.canClick(nowMs = 1_200L))
    }

    @Test
    fun canClick_allowsAgainAfterIntervalElapses() {
        val helper = ThrottleClickHelper(intervalMs = 500L)
        assertTrue(helper.canClick(nowMs = 1_000L))
        assertFalse(helper.canClick(nowMs = 1_200L))
        assertTrue(helper.canClick(nowMs = 1_600L))
    }

    @Test
    fun canClick_respectsCustomInterval() {
        val helper = ThrottleClickHelper(intervalMs = 100L)
        assertTrue(helper.canClick(nowMs = 1_000L))
        assertFalse(helper.canClick(nowMs = 1_050L))
        assertTrue(helper.canClick(nowMs = 1_100L))
    }
}
