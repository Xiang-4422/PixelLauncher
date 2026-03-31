package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PixelGridGeometryResolverTest {

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
    fun mapSurfaceToLogicalRejectsTouchesOutsideContent() {
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
}
