package com.purride.pixelui

import com.purride.pixelcore.ScreenProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class PixelHostLifecycleCoordinatorTest {
    @Test
    fun manualInsetsReturnsLogicalInsetsDirectly() {
        val coordinator = PixelHostLifecycleCoordinator(disposeRender = {})

        val insets = coordinator.manualInsets(left = 1, top = 2, right = 3, bottom = 4)

        assertEquals(PixelWindowInsets(left = 1, top = 2, right = 3, bottom = 4), insets)
    }

    @Test
    fun platformInsetsMapToLogicalPixels() {
        val coordinator = PixelHostLifecycleCoordinator(disposeRender = {})

        val insets = coordinator.platformInsetsToLogical(
            leftPx = 9,
            topPx = 16,
            rightPx = 0,
            bottomPx = 17,
            viewWidth = 80,
            viewHeight = 80,
            screenProfile = ScreenProfile(logicalWidth = 10, logicalHeight = 10, dotSizePx = 8),
            pixelGapEnabled = false,
            pixelGapRatio = 0f,
        )

        assertEquals(PixelWindowInsets(left = 2, top = 2, right = 0, bottom = 3), insets)
    }

    @Test
    fun detachDisposesRenderCoordinator() {
        var disposed = 0
        val coordinator = PixelHostLifecycleCoordinator(disposeRender = { disposed++ })

        coordinator.onDetachedFromWindow()

        assertEquals(1, disposed)
    }
}
