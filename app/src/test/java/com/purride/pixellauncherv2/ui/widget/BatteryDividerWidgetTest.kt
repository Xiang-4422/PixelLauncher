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
    fun remainingCapacityUsesCurrentLevelColorAtHalfAlpha() {
        val buffer = PixelBuffer(width = 10, height = 1)
        val renderObject = RenderBatteryDivider(
            batteryLevel = 50,
            isCharging = false,
            chargeTick = 0,
            highColor = highColor,
            mediumColor = mediumColor,
            lowColor = lowColor,
        )

        renderObject.layout(PixelRenderConstraints(maxWidth = 10, maxHeight = 1))
        renderObject.paint(PixelPaintContext(buffer), offsetX = 0, offsetY = 0)

        val remainingColor = mediumColor.withAlpha(REMAINING_BATTERY_ALPHA)
        assertEquals(mediumColor, buffer.getPixel(4, 0))
        assertEquals(remainingColor, buffer.getPixel(5, 0))
        assertEquals(remainingColor, buffer.getPixel(9, 0))
    }

    @Test
    fun chargingDotMovesOnlyInsideRemainingCapacityRange() {
        val buffer = PixelBuffer(width = 10, height = 1)
        val renderObject = RenderBatteryDivider(
            batteryLevel = 60,
            isCharging = true,
            chargeTick = 2,
            highColor = highColor,
            mediumColor = mediumColor,
            lowColor = lowColor,
        )

        renderObject.layout(PixelRenderConstraints(maxWidth = 10, maxHeight = 1))
        renderObject.paint(PixelPaintContext(buffer), offsetX = 0, offsetY = 0)

        val remainingColor = highColor.withAlpha(REMAINING_BATTERY_ALPHA)
        assertEquals(highColor, buffer.getPixel(0, 0))
        assertEquals(highColor, buffer.getPixel(5, 0))
        assertEquals(remainingColor, buffer.getPixel(6, 0))
        assertEquals(highColor, buffer.getPixel(8, 0))
        assertEquals(remainingColor, buffer.getPixel(9, 0))
    }

    private fun colorAt(level: Int): PixelColor = batteryLevelColor(
        batteryLevel = level,
        highColor = highColor,
        mediumColor = mediumColor,
        lowColor = lowColor,
    )

    private fun PixelColor.withAlpha(alpha: Int): PixelColor = PixelColor.fromArgb(
        a = alpha,
        r = red,
        g = green,
        b = blue,
    )
}
