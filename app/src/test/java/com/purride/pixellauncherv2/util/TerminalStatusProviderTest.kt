package com.purride.pixellauncherv2.util

import com.purride.pixellauncherv2.launcher.LauncherState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [TerminalStatusProvider] — the charging / low-power / night /
 * default status precedence driven off [LauncherState]. JVM-safe; no Android
 * dependencies.
 */
class TerminalStatusProviderTest {

    private val provider = TerminalStatusProvider()

    @Test
    fun buildStatus_chargingTakesPrecedenceOverLowBattery() {
        val status = provider.buildStatus(
            LauncherState(isCharging = true, batteryLevel = 10, currentTimeText = "23:00"),
        )
        assertEquals("CHARGING 10%", status)
    }

    @Test
    fun buildStatus_lowPowerWhenNotChargingAtOrBelowFifteen() {
        val status = provider.buildStatus(
            LauncherState(isCharging = false, batteryLevel = 15, currentTimeText = "14:00"),
        )
        assertEquals("LOW POWER 15%", status)
    }

    @Test
    fun buildStatus_nightModeByHourWhenBatteryHealthy() {
        assertEquals(
            "NIGHT MODE READY",
            provider.buildStatus(LauncherState(isCharging = false, batteryLevel = 80, currentTimeText = "23:00")),
        )
        assertEquals(
            "NIGHT MODE READY",
            provider.buildStatus(LauncherState(isCharging = false, batteryLevel = 80, currentTimeText = "05:30")),
        )
    }

    @Test
    fun buildStatus_systemReadyForDaytimeHealthyBattery() {
        val status = provider.buildStatus(
            LauncherState(isCharging = false, batteryLevel = 80, currentTimeText = "14:00"),
        )
        assertEquals("SYSTEM READY", status)
    }
}
