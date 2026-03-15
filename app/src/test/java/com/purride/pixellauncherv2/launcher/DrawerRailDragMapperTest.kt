package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerRailDragMapperTest {

    @Test
    fun consumeDragDoesNotStepBelowThreshold() {
        val result = DrawerRailDragMapper.consumeDrag(
            accumulatedPx = 0f,
            deltaPx = 0.9f,
            pixelsPerApp = 1f,
        )

        assertEquals(0, result.stepDelta)
        assertEquals(0.9f, result.accumulatedPx, 0.0001f)
    }

    @Test
    fun consumeDragStepsOneByOneAcrossThresholds() {
        val result = DrawerRailDragMapper.consumeDrag(
            accumulatedPx = 0.4f,
            deltaPx = 2.2f,
            pixelsPerApp = 1f,
        )

        assertEquals(2, result.stepDelta)
        assertEquals(0.6f, result.accumulatedPx, 0.0001f)
    }

    @Test
    fun consumeDragSupportsReverseDirection() {
        val result = DrawerRailDragMapper.consumeDrag(
            accumulatedPx = -0.3f,
            deltaPx = -2.1f,
            pixelsPerApp = 1f,
        )

        assertEquals(-2, result.stepDelta)
        assertEquals(-0.4f, result.accumulatedPx, 0.0001f)
    }
}
