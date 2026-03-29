package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelGridGeometryTest {

    @Test
    fun resolveProducesExpectedCenteredGeometry() {
        val profile = ScreenProfile(
            logicalWidth = 10,
            logicalHeight = 5,
            dotSizePx = 8,
        )

        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 120,
            viewHeight = 80,
            profile = profile,
        )

        assertNotNull(geometry)
        assertEquals(12f, geometry?.cellSize ?: 0f, 1e-4f)
        assertEquals(0f, geometry?.originX ?: 0f, 1e-4f)
        assertEquals(10f, geometry?.originY ?: 0f, 1e-4f)
    }

    @Test
    fun mapSurfaceToLogicalUsesSameCenteredGridRules() {
        val profile = ScreenProfile(
            logicalWidth = 10,
            logicalHeight = 5,
            dotSizePx = 8,
        )

        val logicalPoint = PixelGridGeometryResolver.mapSurfaceToLogical(
            touchX = 25f,
            touchY = 22f,
            viewWidth = 120,
            viewHeight = 80,
            profile = profile,
        )

        assertEquals(2 to 1, logicalPoint)
    }

    @Test
    fun mapSurfaceToLogicalRejectsTouchesOutsideCenteredContent() {
        val profile = ScreenProfile(
            logicalWidth = 10,
            logicalHeight = 5,
            dotSizePx = 8,
        )

        val logicalPoint = PixelGridGeometryResolver.mapSurfaceToLogical(
            touchX = 5f,
            touchY = 5f,
            viewWidth = 120,
            viewHeight = 80,
            profile = profile,
        )

        assertNull(logicalPoint)
    }

    @Test
    fun resolveUsesCompactInsetForSmallCells() {
        val profile = ScreenProfile(
            logicalWidth = 10,
            logicalHeight = 20,
            dotSizePx = 8,
        )

        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 80,
            viewHeight = 160,
            profile = profile,
        )

        assertNotNull(geometry)
        assertEquals(8f, geometry?.cellSize ?: 0f, 1e-4f)
        assertEquals(0.5f, geometry?.dotInset ?: 0f, 1e-4f)
        assertEquals(7f, geometry?.dotSize ?: 0f, 1e-4f)
    }

    @Test
    fun resolveDisablesInsetWhenPixelGapIsOff() {
        val profile = ScreenProfile(
            logicalWidth = 10,
            logicalHeight = 20,
            dotSizePx = 8,
        )

        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 80,
            viewHeight = 160,
            profile = profile,
            pixelGapEnabled = false,
        )

        assertNotNull(geometry)
        assertEquals(8f, geometry?.cellSize ?: 0f, 1e-4f)
        assertEquals(0f, geometry?.dotInset ?: -1f, 1e-4f)
        assertEquals(8f, geometry?.dotSize ?: 0f, 1e-4f)
    }
}
