package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.ScreenProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTest {

    @Test
    fun fixedAndStackAreasStaySeparated() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )

        val metrics = HomeLayout.metrics(screenProfile)

        assertEquals(true, metrics.fixedBottom < metrics.stackTop)
        assertEquals(true, metrics.fixedHeight > 0)
        assertEquals(true, metrics.stackHeight > 0)
    }

    @Test
    fun homeComponentsStayBelowSharedHeaderDivider() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )

        val metrics = HomeLayout.metrics(screenProfile)

        assertEquals(true, metrics.fixedTop > LauncherHeaderLayout.dividerY)
        assertEquals(true, metrics.stackTop > LauncherHeaderLayout.dividerY)
    }

    @Test
    fun homeLayoutKeepsReasonableInnerWidth() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )

        val metrics = HomeLayout.metrics(screenProfile)

        assertEquals(true, metrics.innerWidth >= 8)
        assertEquals(true, metrics.innerRight <= metrics.outerRight)
        assertEquals(true, metrics.innerLeft >= metrics.outerLeft)
    }
}
