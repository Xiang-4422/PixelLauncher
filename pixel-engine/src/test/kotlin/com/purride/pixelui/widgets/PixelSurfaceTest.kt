package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelSurface
import com.purride.pixelui.PixelSurfaceDecoration
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Locks pixel-exact radius, border-width, and hard-elevation rendering for theme consumers. */
class PixelSurfaceTest {
    /** Complete decoration paints stair corners and includes the hard shadow in measured output. */
    @Test
    fun completeDecorationPaintsPixelAlignedGeometry() {
        /** Sentinel fill color visible only in the main surface interior. */
        val fill = PixelColor.fromRgb(12, 130, 80)
        /** Sentinel border color used by two nested outline layers. */
        val border = PixelColor.fromRgb(230, 40, 90)
        /** Sentinel hard-shadow color visible beyond the main eight-pixel extent. */
        val shadow = PixelColor.fromRgb(35, 70, 210)
        /** Off-screen runtime that exposes exact logical-pixel output. */
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelSurface(
                decoration = PixelSurfaceDecoration(
                    fillColor = fill,
                    borderColor = border,
                    borderWidth = 2,
                    cornerRadius = 2,
                    shadowColor = shadow,
                    shadowOffset = 2,
                ),
                width = 8,
                height = 8,
            ),
            logicalWidth = 12,
            logicalHeight = 12,
        )

        assertEquals(PixelColor.Transparent, tester.pixelAt(0, 0))
        assertEquals(border, tester.pixelAt(3, 0))
        assertEquals(border, tester.pixelAt(1, 1))
        assertEquals(fill, tester.pixelAt(3, 3))
        assertEquals(shadow, tester.pixelAt(9, 5))
        assertEquals(shadow, tester.pixelAt(5, 9))
        assertEquals(PixelColor.Transparent, tester.pixelAt(10, 5))
        tester.dispose()
    }

    /** Invalid negative pixel geometry fails before a malformed surface reaches layout. */
    @Test
    fun negativeDecorationGeometryIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PixelSurfaceDecoration(borderWidth = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelSurfaceDecoration(cornerRadius = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelSurfaceDecoration(shadowOffset = -1)
        }
    }
}
