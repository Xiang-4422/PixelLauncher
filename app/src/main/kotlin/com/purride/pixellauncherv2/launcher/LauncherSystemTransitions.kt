package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.DeviceStatus

/**
 * System / Capabilities 域的纯状态转换（ADR-0001 阶段 2 拆分，来源 LauncherStateTransitions）。
 *
 * 承载电量/充电与跨业务共享的平台能力快照写入。对外入口仍是
 * [LauncherStateTransitions] facade；行为与拆分前逐字节等价。
 */
object LauncherSystemTransitions {

    /** 写入电量与充电状态快照。 */
    fun updateDeviceStatus(state: LauncherState, deviceStatus: DeviceStatus): LauncherState {
        return state.copy(
            batteryLevel = deviceStatus.batteryLevel,
            isCharging = deviceStatus.isCharging,
        )
    }

    /** 写入六项平台能力快照；仅用户显式刷新时更新时间戳。 */
    fun updateDataHealth(
        state: LauncherState,
        hasUsageAccess: Boolean,
        hasLocationPermission: Boolean,
        hasCallLogPermission: Boolean,
        hasSmsReadPermission: Boolean,
        hasPostNotificationPermission: Boolean,
        hasNotificationListenerAccess: Boolean,
        dataHealthUpdatedTimeText: String = state.dataHealthUpdatedTimeText,
    ): LauncherState {
        return state.copy(
            hasUsageAccess = hasUsageAccess,
            hasLocationPermission = hasLocationPermission,
            hasCallLogPermission = hasCallLogPermission,
            hasSmsReadPermission = hasSmsReadPermission,
            hasPostNotificationPermission = hasPostNotificationPermission,
            hasNotificationListenerAccess = hasNotificationListenerAccess,
            dataHealthUpdatedTimeText = dataHealthUpdatedTimeText,
        )
    }
}
