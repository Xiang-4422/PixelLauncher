package com.purride.pixellauncherv2.util

import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.SmsPermissionState
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
            readyState(isCharging = true, batteryLevel = 10, currentTimeText = "23:00"),
        )
        assertEquals("CHARGING 10%", status)
    }

    @Test
    fun buildStatus_lowPowerWhenNotChargingAtOrBelowFifteen() {
        val status = provider.buildStatus(
            readyState(isCharging = false, batteryLevel = 15, currentTimeText = "14:00"),
        )
        assertEquals("LOW POWER 15%", status)
    }

    @Test
    fun buildStatus_dataIssuesAppearBeforeQuietReadyStates() {
        val status = provider.buildStatus(
            readyState(
                isCharging = false,
                batteryLevel = 80,
                currentTimeText = "14:00",
                hasLocationPermission = false,
                hasNotificationListenerAccess = false,
            ),
        )
        assertEquals("DATA 2 ISSUE", status)
    }

    @Test
    fun buildStatus_nightModeByHourWhenBatteryHealthy() {
        assertEquals(
            "NIGHT MODE READY",
            provider.buildStatus(readyState(isCharging = false, batteryLevel = 80, currentTimeText = "23:00")),
        )
        assertEquals(
            "NIGHT MODE READY",
            provider.buildStatus(readyState(isCharging = false, batteryLevel = 80, currentTimeText = "05:30")),
        )
    }

    @Test
    fun buildStatus_systemReadyForDaytimeHealthyBattery() {
        val status = provider.buildStatus(
            readyState(isCharging = false, batteryLevel = 80, currentTimeText = "14:00"),
        )
        assertEquals("SYSTEM READY", status)
    }

    private fun readyState(
        isCharging: Boolean = false,
        batteryLevel: Int = 80,
        currentTimeText: String = "14:00",
        hasUsageAccess: Boolean = true,
        hasLocationPermission: Boolean = true,
        hasCallLogPermission: Boolean = true,
        hasSmsReadPermission: Boolean = true,
        isDefaultSmsApp: Boolean = true,
        smsPermissionState: SmsPermissionState = SmsPermissionState.READY,
        hasPostNotificationPermission: Boolean = true,
        hasNotificationListenerAccess: Boolean = true,
    ): LauncherState {
        return LauncherState(
            isCharging = isCharging,
            batteryLevel = batteryLevel,
            currentTimeText = currentTimeText,
            hasUsageAccess = hasUsageAccess,
            hasLocationPermission = hasLocationPermission,
            hasCallLogPermission = hasCallLogPermission,
            hasSmsReadPermission = hasSmsReadPermission,
            isDefaultSmsApp = isDefaultSmsApp,
            smsPermissionState = smsPermissionState,
            hasPostNotificationPermission = hasPostNotificationPermission,
            hasNotificationListenerAccess = hasNotificationListenerAccess,
        )
    }
}
