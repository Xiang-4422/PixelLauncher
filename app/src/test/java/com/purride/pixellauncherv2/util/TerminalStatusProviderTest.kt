package com.purride.pixellauncherv2.util

import com.purride.pixellauncherv2.launcher.LauncherState
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalStatusProviderTest {

    private val provider = TerminalStatusProvider()

    @Test
    fun chargingStateTakesPriority() {
        val status = provider.buildStatus(
            LauncherState(
                batteryLevel = 81,
                isCharging = true,
            ),
        )

        assertEquals("CHARGING 81%", status)
    }

    @Test
    fun lowBatteryStateOverridesRecentLaunchText() {
        val status = provider.buildStatus(
            LauncherState(
                batteryLevel = 12,
                isCharging = false,
                lastLaunchPackageName = "com.demo.camera",
            ),
        )

        assertEquals("LOW POWER 12%", status)
    }

    @Test
    fun systemReadyIsUsedWhenThereIsNoPowerStateOverride() {
        val status = provider.buildStatus(
            LauncherState(
                batteryLevel = 80,
                lastLaunchPackageName = "com.demo.camera",
            ),
        )

        assertEquals("SYSTEM READY", status)
    }

    @Test
    fun nightReadyOverridesLaunchMetadata() {
        val status = provider.buildStatus(
            LauncherState(
                batteryLevel = 80,
                currentTimeText = "23:10",
                recentApps = listOf("pkg.camera"),
                lastLaunchPackageName = "com.demo.camera",
            ),
        )

        assertEquals("NIGHT MODE READY", status)
    }
}
