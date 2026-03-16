package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerDirectionalSettlePolicyTest {

    @Test
    fun directionForDeltaYMapsUpAndDown() {
        assertEquals(1, DrawerDirectionalSettlePolicy.directionForDeltaY(-3f))
        assertEquals(-1, DrawerDirectionalSettlePolicy.directionForDeltaY(2f))
        assertEquals(0, DrawerDirectionalSettlePolicy.directionForDeltaY(0f))
    }

    @Test
    fun shouldForceAdvanceWhenNotMovedYetAndDirectionCanAdvance() {
        val force = DrawerDirectionalSettlePolicy.shouldForceAdvance(
            anchorIndex = 8,
            currentIndex = 8,
            direction = 1,
            mustAdvance = true,
            lastIndex = 20,
        )

        assertTrue(force)
    }

    @Test
    fun shouldNotForceAdvanceAtBoundary() {
        val forceForward = DrawerDirectionalSettlePolicy.shouldForceAdvance(
            anchorIndex = 20,
            currentIndex = 20,
            direction = 1,
            mustAdvance = true,
            lastIndex = 20,
        )
        val forceBackward = DrawerDirectionalSettlePolicy.shouldForceAdvance(
            anchorIndex = 0,
            currentIndex = 0,
            direction = -1,
            mustAdvance = true,
            lastIndex = 20,
        )

        assertFalse(forceForward)
        assertFalse(forceBackward)
    }

    @Test
    fun hasAdvancedTracksDirectionalMovement() {
        assertTrue(
            DrawerDirectionalSettlePolicy.hasAdvanced(
                anchorIndex = 10,
                currentIndex = 11,
                direction = 1,
            ),
        )
        assertTrue(
            DrawerDirectionalSettlePolicy.hasAdvanced(
                anchorIndex = 10,
                currentIndex = 9,
                direction = -1,
            ),
        )
        assertFalse(
            DrawerDirectionalSettlePolicy.hasAdvanced(
                anchorIndex = 10,
                currentIndex = 10,
                direction = 1,
            ),
        )
    }
}
