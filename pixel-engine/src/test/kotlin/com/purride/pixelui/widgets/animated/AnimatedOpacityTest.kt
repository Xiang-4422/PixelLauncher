package com.purride.pixelui.widgets.animated

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimatedOpacityTest {

    @Test
    fun quantizeOpacityBelowThreshold() {
        assertEquals(0f, quantizeOpacity(0f), 1e-4f)
        assertEquals(0f, quantizeOpacity(0.24f), 1e-4f)
    }

    @Test
    fun quantizeOpacityMidRange() {
        assertEquals(0.5f, quantizeOpacity(0.25f), 1e-4f)
        assertEquals(0.5f, quantizeOpacity(0.5f), 1e-4f)
        assertEquals(0.5f, quantizeOpacity(0.75f), 1e-4f)
    }

    @Test
    fun quantizeOpacityAboveThreshold() {
        assertEquals(1f, quantizeOpacity(0.76f), 1e-4f)
        assertEquals(1f, quantizeOpacity(1f), 1e-4f)
    }

    @Test
    fun quantizeOpacityOnlyThreeTiers() {
        val inputs = (0..100).map { it / 100f }
        val outputs = inputs.map { quantizeOpacity(it) }.toSet()
        assertEquals(setOf(0f, 0.5f, 1f), outputs)
    }
}
