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
}
