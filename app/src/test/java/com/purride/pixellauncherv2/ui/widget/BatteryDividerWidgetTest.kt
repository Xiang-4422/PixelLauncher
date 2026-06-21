package com.purride.pixellauncherv2.ui.widget

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderConstraints
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryDividerWidgetTest {

    private val highColor = PixelColor.fromRgb(0, 255, 0)
    private val mediumColor = PixelColor.fromRgb(255, 255, 0)
    private val lowColor = PixelColor.fromRgb(255, 0, 0)

    @Test
    fun batteryColorUsesConfiguredLevelThresholds() {
        assertEquals(lowColor, colorAt(0))
        assertEquals(lowColor, colorAt(20))
        assertEquals(mediumColor, colorAt(21))
        assertEquals(mediumColor, colorAt(50))
        assertEquals(highColor, colorAt(51))
        assertEquals(highColor, colorAt(100))
    }

    @Test
    fun positiveBatteryLevelAlwaysPaintsAtLeastOnePixel() {
        assertEquals(0, batteryFilledWidth(width = 10, batteryLevel = 0))
        assertEquals(1, batteryFilledWidth(width = 10, batteryLevel = 1))
        assertEquals(5, batteryFilledWidth(width = 10, batteryLevel = 50))
        assertEquals(10, batteryFilledWidth(width = 10, batteryLevel = 100))
    }

    @Test
    fun chargingPaintsHalfTransparentTrackAndSolidDotInsideLevelRange() {
        val buffer = PixelBuffer(width = 10, height = 1)
        val renderObject = RenderBatteryDivider(
            batteryLevel = 50,
            isCharging = true,
            chargeTick = 7,
            highColor = highColor,
            mediumColor = mediumColor,
            lowColor = lowColor,
        )

        renderObject.layout(PixelRenderConstraints(maxWidth = 10, maxHeight = 1))
        renderObject.paint(PixelPaintContext(buffer), offsetX = 0, offsetY = 0)

        assertEquals(CHARGING_TRACK_ALPHA, buffer.getPixel(0, 0).alpha)
        assertEquals(mediumColor, buffer.getPixel(2, 0))
        assertEquals(CHARGING_TRACK_ALPHA, buffer.getPixel(4, 0).alpha)
        assertEquals(PixelColor.Transparent, buffer.getPixel(5, 0))
    }

    private fun colorAt(level: Int): PixelColor = batteryLevelColor(
        batteryLevel = level,
        highColor = highColor,
        mediumColor = mediumColor,
        lowColor = lowColor,
    )
}
