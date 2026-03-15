package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderBatteryIndicatorModelTest {

    @Test
    fun zeroPercentKeepsEntireLineDark() {
        val indicator = HeaderBatteryIndicatorModel.fromBatteryLevel(
            batteryLevel = 0,
            isCharging = false,
            logicalWidth = 72,
            chargeTick = 0,
        )

        assertNull(indicator.filledEndX)
        assertEquals(indicator.lineStartX, indicator.darkStartX)
        assertEquals(indicator.lineEndX, indicator.darkEndX)
    }

    @Test
    fun commonBatteryLevelsMapToExpectedFilledLength() {
        val onePercent = HeaderBatteryIndicatorModel.fromBatteryLevel(1, false, 72, 0)
        val fifteenPercent = HeaderBatteryIndicatorModel.fromBatteryLevel(15, false, 72, 0)
        val fiftyPercent = HeaderBatteryIndicatorModel.fromBatteryLevel(50, false, 72, 0)
        val hundredPercent = HeaderBatteryIndicatorModel.fromBatteryLevel(100, false, 72, 0)

        assertNull(onePercent.filledEndX)
        assertEquals(10, fifteenPercent.filledEndX)
        assertEquals(35, fiftyPercent.filledEndX)
        assertEquals(70, hundredPercent.filledEndX)
        assertNull(hundredPercent.darkStartX)
    }

    @Test
    fun chargingPixelStaysInsideDarkRangeAndLoopsRightToLeft() {
        val firstFrame = HeaderBatteryIndicatorModel.fromBatteryLevel(
            batteryLevel = 50,
            isCharging = true,
            logicalWidth = 72,
            chargeTick = 0,
        )
        val secondFrame = HeaderBatteryIndicatorModel.fromBatteryLevel(
            batteryLevel = 50,
            isCharging = true,
            logicalWidth = 72,
            chargeTick = 1,
        )
        val loopFrame = HeaderBatteryIndicatorModel.fromBatteryLevel(
            batteryLevel = 50,
            isCharging = true,
            logicalWidth = 72,
            chargeTick = 100,
        )

        assertNotNull(firstFrame.chargePixelX)
        assertTrue(firstFrame.chargePixelX!! in firstFrame.darkStartX!!..firstFrame.darkEndX!!)
        assertEquals(firstFrame.chargePixelX!! - 1, secondFrame.chargePixelX)
        assertTrue(loopFrame.chargePixelX!! in loopFrame.darkStartX!!..loopFrame.darkEndX!!)
    }

    @Test
    fun chargingPixelDisappearsWhenBatteryIsFull() {
        val indicator = HeaderBatteryIndicatorModel.fromBatteryLevel(
            batteryLevel = 100,
            isCharging = true,
            logicalWidth = 72,
            chargeTick = 3,
        )

        assertNull(indicator.chargePixelX)
    }
}
