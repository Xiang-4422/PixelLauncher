package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.viewmodel.LauncherUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class IdleStatusModelTest {

    @Test
    fun notificationHasHighestPriority() {
        val line = IdleStatusModel.line(
            LauncherUiState(
                missedCallCount = 1,
                unreadSmsCount = 2,
                isCharging = true,
                batteryLevel = 7,
                rainHintText = "RAIN",
            ),
        )

        assertEquals(IdleStatusKind.NOTIFICATION, line.kind)
        assertEquals("NOTIFY", line.title)
        assertEquals("CALL 1  SMS 2", line.body)
    }

    @Test
    fun listenerNotificationSummaryCountsAsNotificationState() {
        val line = IdleStatusModel.line(
            LauncherUiState(
                notificationSummaryText = "BANK OTP",
                notificationCount = 1,
                isCharging = true,
                batteryLevel = 80,
            ),
        )

        assertEquals(IdleStatusKind.NOTIFICATION, line.kind)
        assertEquals("NOTIFY", line.title)
        assertEquals("BANK OTP", line.body)
    }

    @Test
    fun chargingShowsBatteryAndEffect() {
        val line = IdleStatusModel.line(
            LauncherUiState(
                isCharging = true,
                batteryLevel = 73,
                chargeIdleEffect = ChargeIdleEffect.HORIZON,
            ),
        )

        assertEquals(IdleStatusKind.CHARGING, line.kind)
        assertEquals("CHARGING", line.title)
        assertEquals("BAT 73%  HORIZON", line.body)
    }

    @Test
    fun lowBatteryShowsBeforeWeatherWhenNotCharging() {
        val line = IdleStatusModel.line(
            LauncherUiState(
                isCharging = false,
                batteryLevel = 12,
                rainHintText = "RAIN",
            ),
        )

        assertEquals(IdleStatusKind.LOW_BATTERY, line.kind)
        assertEquals("LOW BAT", line.title)
        assertEquals("BAT 12%", line.body)
    }

    @Test
    fun attentionWeatherShowsWhenNoHigherPriorityStateExists() {
        val line = IdleStatusModel.line(
            LauncherUiState(
                batteryLevel = 80,
                rainHintText = "RAIN 20C",
            ),
        )

        assertEquals(IdleStatusKind.WEATHER, line.kind)
        assertEquals("WEATHER", line.title)
        assertEquals("RAIN 20C", line.body)
    }

    @Test
    fun ordinaryWeatherDoesNotOverrideDefaultIdleState() {
        val line = IdleStatusModel.line(
            LauncherUiState(
                batteryLevel = 80,
                rainHintText = "CLEAR 22C",
            ),
        )

        assertEquals(IdleStatusKind.DEFAULT, line.kind)
        assertEquals("IDLE", line.title)
        assertEquals("NO PRIORITY STATE", line.body)
    }

    @Test
    fun defaultShowsNextAlarmWhenAvailable() {
        val line = IdleStatusModel.line(
            LauncherUiState(
                batteryLevel = 80,
                nextAlarmText = "07:30",
            ),
        )

        assertEquals(IdleStatusKind.DEFAULT, line.kind)
        assertEquals("IDLE", line.title)
        assertEquals("ALARM 07:30", line.body)
    }
}
