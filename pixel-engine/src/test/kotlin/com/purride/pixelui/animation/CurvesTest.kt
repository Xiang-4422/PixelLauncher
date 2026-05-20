package com.purride.pixelui.animation

import org.junit.Assert.assertEquals
import org.junit.Test

class CurvesTest {

    private fun assertNear(expected: Float, actual: Float) =
        assertEquals(expected, actual, 1e-4f)

    @Test
    fun linearPassThrough() {
        assertNear(0f, Curves.Linear.transform(0f))
        assertNear(0.5f, Curves.Linear.transform(0.5f))
        assertNear(1f, Curves.Linear.transform(1f))
    }

    @Test
    fun easeInBoundaries() {
        assertNear(0f, Curves.EaseIn.transform(0f))
        assertNear(1f, Curves.EaseIn.transform(1f))
    }

    @Test
    fun easeInSlowAtStart() {
        val mid = Curves.EaseIn.transform(0.5f)
        assert(mid < 0.5f) { "EaseIn mid=$mid should be < 0.5" }
    }

    @Test
    fun easeOutBoundaries() {
        assertNear(0f, Curves.EaseOut.transform(0f))
        assertNear(1f, Curves.EaseOut.transform(1f))
    }

    @Test
    fun easeOutFastAtStart() {
        val mid = Curves.EaseOut.transform(0.5f)
        assert(mid > 0.5f) { "EaseOut mid=$mid should be > 0.5" }
    }

    @Test
    fun easeInOutBoundaries() {
        assertNear(0f, Curves.EaseInOut.transform(0f))
        assertNear(1f, Curves.EaseInOut.transform(1f))
    }

    @Test
    fun easeInOutSymmetric() {
        val v = Curves.EaseInOut.transform(0.5f)
        assertNear(0.5f, v)
    }

    @Test
    fun step8Discrete() {
        val step = Curves.Step(8)
        assertNear(0f, step.transform(0f))
        assertNear(0f, step.transform(0.124f))
        assertNear(0.125f, step.transform(0.125f))
        assertNear(0.125f, step.transform(0.249f))
        assertNear(0.25f, step.transform(0.25f))
    }

    @Test
    fun step1SnapsAtOne() {
        val step = Curves.Step(1)
        assertNear(0f, step.transform(0f))
        assertNear(0f, step.transform(0.99f))
        assertNear(1f, step.transform(1f))
    }

    @Test
    fun intervalClamps() {
        val interval = Interval(0.2f, 0.8f)
        assertNear(0f, interval.transform(0.0f))
        assertNear(0f, interval.transform(0.199f))
        assertNear(1f, interval.transform(0.801f))
        assertNear(1f, interval.transform(1f))
    }

    @Test
    fun intervalMidpoint() {
        val interval = Interval(0.2f, 0.8f)
        assertNear(0.5f, interval.transform(0.5f))
    }
}
