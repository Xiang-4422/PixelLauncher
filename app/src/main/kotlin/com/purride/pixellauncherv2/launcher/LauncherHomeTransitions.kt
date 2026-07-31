package com.purride.pixellauncherv2.launcher

/**
 * Home feed 域的纯状态转换（ADR-0001 阶段 2 拆分，来源 LauncherStateTransitions）。
 *
 * 承载时间/日期、闹钟、通讯计数、媒体播放快照、天气与屏幕用量摘要的写入。
 * 对外入口仍是 [LauncherStateTransitions] facade；行为与拆分前逐字节等价。
 */
object LauncherHomeTransitions {

    /** 更新头部和 Home 固定区使用的时间相关文本。 */
    fun updateTime(
        state: LauncherState,
        currentTimeText: String,
        currentDateText: String = state.currentDateText,
        currentWeekdayText: String = state.currentWeekdayText,
    ): LauncherState {
        return state.copy(
            currentTimeText = currentTimeText,
            currentDateText = currentDateText,
            currentWeekdayText = currentWeekdayText,
        )
    }

    /** 更新下次闹钟文本。 */
    fun updateNextAlarmText(state: LauncherState, nextAlarmText: String): LauncherState {
        return state.copy(nextAlarmText = nextAlarmText)
    }

    /** 写入 Home 动态信息行使用的通话和短信计数。 */
    fun updateCommunicationStatus(
        state: LauncherState,
        missedCallCount: Int,
        unreadSmsCount: Int,
    ): LauncherState {
        return state.copy(
            missedCallCount = missedCallCount.coerceAtLeast(0),
            unreadSmsCount = unreadSmsCount.coerceAtLeast(0),
        )
    }

    /** 写入媒体播放快照。 */
    fun updateMediaPlayback(
        state: LauncherState,
        mediaPlayback: MediaPlaybackSnapshot,
    ): LauncherState {
        return state.copy(mediaPlayback = mediaPlayback)
    }

    /** 写入 Home 的天气与温度摘要文本及最近刷新时间。 */
    fun updateRainHintText(
        state: LauncherState,
        rainHintText: String,
        rainUpdatedTimeText: String = state.rainUpdatedTimeText,
    ): LauncherState {
        return state.copy(
            rainHintText = rainHintText,
            rainUpdatedTimeText = rainUpdatedTimeText,
        )
    }

    /** 更新 Home 中当天屏幕使用时长和打开次数摘要。 */
    fun updateScreenUsageSummary(
        state: LauncherState,
        screenUsageTimeText: String,
        screenOpenCountText: String,
    ): LauncherState {
        return state.copy(
            screenUsageTimeText = screenUsageTimeText,
            screenOpenCountText = screenOpenCountText,
        )
    }
}
