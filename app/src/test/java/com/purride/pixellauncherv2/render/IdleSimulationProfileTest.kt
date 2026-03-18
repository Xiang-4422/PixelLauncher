package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleSimulationProfileTest {

    @Test
    fun preservesSizeWhenLogicalGridAlreadySmallEnough() {
        val profile = IdleSimulationProfile.fromLogicalSize(
            logicalWidth = 40,
            logicalHeight = 60,
            maxLongestSide = 64,
        )

        assertEquals(40, profile.width)
        assertEquals(60, profile.height)
    }

    @Test
    fun scalesLongestSideDownToConfiguredBudget() {
        val profile = IdleSimulationProfile.fromLogicalSize(
            logicalWidth = 120,
            logicalHeight = 96,
            maxLongestSide = 64,
        )

        assertEquals(64, profile.width)
        assertTrue(profile.height in 50..52)
    }

    @Test
    fun keepsPortraitAspectWhenHeightIsLongestSide() {
        val profile = IdleSimulationProfile.fromLogicalSize(
            logicalWidth = 72,
            logicalHeight = 144,
            maxLongestSide = 64,
        )

        assertEquals(64, profile.height)
        assertEquals(32, profile.width)
    }
}
