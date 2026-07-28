package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies every orthogonal viewport policy resolves one shared paint/touch geometry. */
class PixelGridGeometryResolverTest {

    /** canonical 默认策略保持 contain + integer + center。 */
    @Test
    fun resolveProducesExpectedCenteredGeometry() {
        /** 2:1 grid limited by the physical width. */
        val profile = ScreenProfile(
            logicalWidth = 10,
            logicalHeight = 5,
            dotSizePx = 8,
        )

        /** Geometry returned by the canonical default policy. */
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 120,
            viewHeight = 80,
            profile = profile,
            viewportPolicy = PixelViewportPolicy(),
        )

        assertNotNull(geometry)
        assertEquals(12f, geometry?.cellSize ?: 0f, 1e-4f)
        assertEquals(0f, geometry?.originX ?: 0f, 1e-4f)
        assertEquals(10f, geometry?.originY ?: 0f, 1e-4f)
    }

    /** canonical 默认策略下的反向映射仍然拒绝居中留白区域。 */
    @Test
    fun mapSurfaceToLogicalRejectsTouchesOutsideContent() {
        /** Profile shared by both paint and pointer mapping. */
        val profile = ScreenProfile(
            logicalWidth = 10,
            logicalHeight = 5,
            dotSizePx = 8,
        )

        /** Point inside the Host but outside the centered logical content. */
        val logicalPoint = PixelGridGeometryResolver.mapSurfaceToLogical(
            touchX = 5f,
            touchY = 5f,
            viewWidth = 120,
            viewHeight = 80,
            profile = profile,
            viewportPolicy = PixelViewportPolicy(),
        )

        assertNull(logicalPoint)
    }

    /** canonical 默认策略与显式 contain+integer+center 组合逐字段等价。 */
    @Test
    fun defaultPolicyEqualsExplicitContainIntegerCenter() {
        /** Odd physical remainders exercise the integer origin floor behavior. */
        val profile = ScreenProfile(logicalWidth = 10, logicalHeight = 5, dotSizePx = 8)
        /** Geometry resolved through the canonical default policy. */
        val default = PixelGridGeometryResolver.resolve(
            viewWidth = 125,
            viewHeight = 83,
            profile = profile,
            viewportPolicy = PixelViewportPolicy(),
        )
        /** Geometry resolved through a fully spelled-out equivalent policy. */
        val explicit = PixelGridGeometryResolver.resolve(
            viewWidth = 125,
            viewHeight = 83,
            profile = profile,
            viewportPolicy = PixelViewportPolicy(
                fit = PixelViewportFit.CONTAIN,
                quantization = PixelViewportQuantization.INTEGER,
                alignment = PixelViewportAlignment.CENTER,
            ),
        )

        assertEquals(default, explicit)
        assertEquals(12f, explicit?.cellSize ?: 0f, 0f)
        assertEquals(2f, explicit?.originX ?: 0f, 0f)
        assertEquals(11f, explicit?.originY ?: 0f, 0f)
    }

    /** Fractional contain preserves the exact uniform scale and exact centered origin. */
    @Test
    fun fractionalContainDoesNotStretchOrRoundTheGrid() {
        /** Logical 2:1 grid whose physical width resolves to a half-pixel cell. */
        val profile = ScreenProfile(logicalWidth = 10, logicalHeight = 5, dotSizePx = 8)
        /** Explicit fractional contain policy under test. */
        val policy = PixelViewportPolicy(
            fit = PixelViewportFit.CONTAIN,
            quantization = PixelViewportQuantization.FRACTIONAL,
            alignment = PixelViewportAlignment.CENTER,
        )
        /** Exact fractional geometry shared by painting and touch inversion. */
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 125,
            viewHeight = 83,
            profile = profile,
            viewportPolicy = policy,
            pixelGapEnabled = false,
        )

        assertNotNull(geometry)
        assertEquals(12.5f, geometry?.cellSize ?: 0f, 1e-4f)
        assertEquals(0f, geometry?.originX ?: -1f, 1e-4f)
        assertEquals(10.25f, geometry?.originY ?: 0f, 1e-4f)
        assertEquals(2f, (geometry?.contentWidth ?: 0f) / (geometry?.contentHeight ?: 1f), 1e-4f)
    }

    /** Integer contain honors trailing-axis alignment without changing scale selection. */
    @Test
    fun integerContainSupportsBottomRightAlignment() {
        /** Logical grid with positive free space on both physical axes. */
        val profile = ScreenProfile(logicalWidth = 10, logicalHeight = 5, dotSizePx = 8)
        /** Bottom-right contained geometry. */
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 125,
            viewHeight = 83,
            profile = profile,
            viewportPolicy = PixelViewportPolicy(
                fit = PixelViewportFit.CONTAIN,
                quantization = PixelViewportQuantization.INTEGER,
                alignment = PixelViewportAlignment.BOTTOM_RIGHT,
            ),
        )

        assertEquals(12f, geometry?.cellSize ?: 0f, 0f)
        assertEquals(5f, geometry?.originX ?: 0f, 0f)
        assertEquals(23f, geometry?.originY ?: 0f, 0f)
    }

    /** Integer cover rounds up and exposes the same cropped transform to pointer inversion. */
    @Test
    fun integerCoverFillsViewportAndMapsThroughNegativeOrigin() {
        /** Logical grid whose vertical scale forces horizontal cover cropping. */
        val profile = ScreenProfile(logicalWidth = 10, logicalHeight = 5, dotSizePx = 8)
        /** Top-right cover policy placing all overflow before logical column zero. */
        val policy = PixelViewportPolicy(
            fit = PixelViewportFit.COVER,
            quantization = PixelViewportQuantization.INTEGER,
            alignment = PixelViewportAlignment.TOP_RIGHT,
        )
        /** Cover geometry used to assert crop placement. */
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 100,
            viewHeight = 80,
            profile = profile,
            viewportPolicy = policy,
        )
        /** Physical top-left point mapped through the same negative x origin. */
        val mapped = PixelGridGeometryResolver.mapSurfaceToLogical(
            touchX = 0f,
            touchY = 0f,
            viewWidth = 100,
            viewHeight = 80,
            profile = profile,
            viewportPolicy = policy,
        )

        assertEquals(16f, geometry?.cellSize ?: 0f, 0f)
        assertEquals(-60f, geometry?.originX ?: 0f, 0f)
        assertEquals(0f, geometry?.originY ?: -1f, 0f)
        assertEquals(3 to 0, mapped)
    }

    /** Fractional cover retains exact fill scale and centered crop for paint and touch. */
    @Test
    fun fractionalCoverRetainsExactCenteredCrop() {
        /** Logical grid whose physical height selects a non-integer cover scale. */
        val profile = ScreenProfile(logicalWidth = 10, logicalHeight = 5, dotSizePx = 8)
        /** Centered fractional cover policy under test. */
        val policy = PixelViewportPolicy(
            fit = PixelViewportFit.COVER,
            quantization = PixelViewportQuantization.FRACTIONAL,
            alignment = PixelViewportAlignment.CENTER,
        )
        /** Exact cover geometry with horizontal overflow. */
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 101,
            viewHeight = 81,
            profile = profile,
            viewportPolicy = policy,
        )
        /** Viewport center mapped back through the same fractional geometry. */
        val mappedCenter = PixelGridGeometryResolver.mapSurfaceToLogical(
            touchX = 50.5f,
            touchY = 40.5f,
            viewWidth = 101,
            viewHeight = 81,
            profile = profile,
            viewportPolicy = policy,
        )

        assertEquals(16.2f, geometry?.cellSize ?: 0f, 1e-4f)
        assertEquals(-30.5f, geometry?.originX ?: 0f, 1e-4f)
        assertEquals(0f, geometry?.originY ?: -1f, 1e-4f)
        assertEquals(5 to 2, mappedCenter)
    }
}
