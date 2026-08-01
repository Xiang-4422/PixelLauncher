package com.purride.pixellockscreen

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证锁屏电池广播的百分比边界和充电语义。 */
class KeyguardBatterySnapshotTest {
    /** 非 100 刻度的原始电量必须按比例换算。 */
    @Test
    fun batteryLevelUsesReportedScale() {
        assertEquals(
            50,
            KeyguardBatterySnapshot.from(128, 255, BatteryManager.BATTERY_STATUS_DISCHARGING).percent,
        )
    }

    /** 广播异常值不得突破 UI 的 `0..100` 不变式。 */
    @Test
    fun invalidAndOverflowBatteryValuesAreClamped() {
        assertEquals(
            0,
            KeyguardBatterySnapshot.from(-1, 0, BatteryManager.BATTERY_STATUS_UNKNOWN).percent,
        )
        assertEquals(
            100,
            KeyguardBatterySnapshot.from(150, 100, BatteryManager.BATTERY_STATUS_FULL).percent,
        )
    }

    /** 充电中和已充满都应显示外接电源状态。 */
    @Test
    fun chargingAndFullStatusesAreCharging() {
        assertTrue(KeyguardBatterySnapshot.from(50, 100, BatteryManager.BATTERY_STATUS_CHARGING).isCharging)
        assertTrue(KeyguardBatterySnapshot.from(100, 100, BatteryManager.BATTERY_STATUS_FULL).isCharging)
    }

    /** 放电状态不得被误标为充电。 */
    @Test
    fun dischargingStatusIsNotCharging() {
        assertFalse(
            KeyguardBatterySnapshot.from(50, 100, BatteryManager.BATTERY_STATUS_DISCHARGING).isCharging,
        )
    }

    /** 交互锁屏必须关闭 AOD 并保持零偏移。 */
    @Test
    fun interactiveStateDisablesAmbientRendering() {
        /** 当前交互状态转换出的 AOD 配置。 */
        val ambient = lockscreenAmbientUiState(isInteractive = true, epochMillis = Long.MAX_VALUE)
        assertFalse(ambient.isAmbient)
        assertEquals(0, ambient.burnInOffsetX)
        assertEquals(0, ambient.burnInOffsetY)
    }

    /** 非交互状态的分钟序列必须只产生有界且变化的防烧屏偏移。 */
    @Test
    fun ambientOffsetsRemainBoundedAndMoveAcrossMinutes() {
        /** 连续三十分钟产生的全部 AOD 状态。 */
        val states = (0L until 30L).map { minute ->
            lockscreenAmbientUiState(
                isInteractive = false,
                epochMillis = minute * 60_000L,
            )
        }
        assertTrue(states.all { state -> state.isAmbient })
        assertTrue(states.all { state -> state.burnInOffsetX in -2..2 })
        assertTrue(states.all { state -> state.burnInOffsetY in -2..2 })
        assertTrue(states.map { state -> state.burnInOffsetX to state.burnInOffsetY }.distinct().size > 5)
    }
}
