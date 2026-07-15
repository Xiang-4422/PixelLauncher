package com.purride.pixelcompat.external

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runtime behavior proof for a consumer compiled only against the published pixel-engine artifact. */
class ExternalDotWidgetTest {
    /** Verifies layout, paint, retained update, and paint invalidation through the external SPI. */
    @Test
    fun publishedAarRunsExternalRenderObject() {
        /** Counters shared by both immutable Widget configurations. */
        val stats = ExternalDotStats()
        /** First color rendered by the external retained object. */
        val firstColor = PixelColor.fromRgb(15, 60, 120)
        /** Updated color proving the same retained object repaints. */
        val secondColor = PixelColor.fromRgb(230, 80, 25)
        /** Public off-screen harness loaded from the published AAR. */
        val tester = PixelTester()

        tester.pumpWidget(
            widget = ExternalDotWidget(firstColor, stats, key = "external-dot"),
            logicalWidth = 6,
            logicalHeight = 5,
        )
        assertEquals(1, stats.createCount)
        assertEquals(1, stats.updateCount)
        assertEquals(firstColor, tester.pixelAt(0, 0))
        assertEquals(firstColor, tester.pixelAt(3, 2))
        assertEquals(PixelColor.Transparent, tester.pixelAt(4, 2))

        tester.pumpWidget(
            widget = ExternalDotWidget(secondColor, stats, key = "external-dot"),
            logicalWidth = 6,
            logicalHeight = 5,
        )
        assertEquals(1, stats.createCount)
        assertEquals(2, stats.updateCount)
        assertEquals(secondColor, tester.pixelAt(0, 0))
        assertEquals(secondColor, tester.pixelAt(3, 2))
        tester.dispose()
    }
}
