package com.purride.pixellauncherv2.model

/**
 * 设备电池状态：电量百分比及是否正在充电。
 */
data class DeviceStatus(
    /** 当前电量百分比（0-100）。 */
    val batteryLevel: Int,
    /** 是否正在充电（含 AC/USB/无线/底座）。 */
    val isCharging: Boolean,
)
