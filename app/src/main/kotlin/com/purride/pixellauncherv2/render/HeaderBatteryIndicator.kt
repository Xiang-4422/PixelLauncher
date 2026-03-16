package com.purride.pixellauncherv2.render

data class HeaderBatteryIndicator(
    val lineStartX: Int,
    val lineEndX: Int,
    val filledEndX: Int?,
    val darkStartX: Int?,
    val darkEndX: Int?,
    val chargePixelX: Int?,
)

object HeaderBatteryIndicatorModel {

    fun fromBatteryLevel(
        batteryLevel: Int,
        isCharging: Boolean,
        logicalWidth: Int,
        chargeTick: Int,
    ): HeaderBatteryIndicator {
        val lineStartX = 1
        val lineEndX = (logicalWidth - 2).coerceAtLeast(lineStartX)
        val usableWidth = (lineEndX - lineStartX + 1).coerceAtLeast(1)
        val safeBatteryLevel = batteryLevel.coerceIn(0, 100)
        val filledPixelCount = ((usableWidth * safeBatteryLevel) / 100f).toInt().coerceIn(0, usableWidth)

        val filledEndX = if (filledPixelCount > 0) {
            lineStartX + filledPixelCount - 1
        } else {
            null
        }
        val darkStartX = if (filledPixelCount < usableWidth) {
            lineStartX + filledPixelCount
        } else {
            null
        }
        val darkEndX = if (filledPixelCount < usableWidth) {
            lineEndX
        } else {
            null
        }

        val chargePixelX = if (isCharging && darkStartX != null && darkEndX != null) {
            val darkWidth = (darkEndX - darkStartX + 1).coerceAtLeast(1)
            darkEndX - (chargeTick % darkWidth)
        } else {
            null
        }

        return HeaderBatteryIndicator(
            lineStartX = lineStartX,
            lineEndX = lineEndX,
            filledEndX = filledEndX,
            darkStartX = darkStartX,
            darkEndX = darkEndX,
            chargePixelX = chargePixelX,
        )
    }
}
