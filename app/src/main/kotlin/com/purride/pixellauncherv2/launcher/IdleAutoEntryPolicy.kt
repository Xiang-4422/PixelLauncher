package com.purride.pixellauncherv2.launcher

/**
 * Idle 自动进入规则的纯函数策略。
 *
 * Activity 负责读取系统时间、充电状态和 Handler 调度；这里只定义哪些状态允许自动进入
 * Idle，以及无操作调度还需要等待多久，方便用 JVM 单元测试覆盖行为边界。
 */
object IdleAutoEntryPolicy {

    fun canAutoEnter(
        state: LauncherState,
        launchPending: Boolean,
    ): Boolean {
        return state.isIdlePageEnabled &&
            !launchPending &&
            (state.mode == LauncherMode.HOME || state.mode == LauncherMode.APP_DRAWER)
    }

    fun shouldEnterForCharging(
        wasCharging: Boolean,
        isCharging: Boolean,
        state: LauncherState,
        launchPending: Boolean,
    ): Boolean {
        return !wasCharging &&
            isCharging &&
            state.chargeAutoIdleEnabled &&
            canAutoEnter(state, launchPending)
    }

    fun shouldEnterForCurrentCharging(
        state: LauncherState,
        launchPending: Boolean,
    ): Boolean {
        return state.isCharging &&
            state.chargeAutoIdleEnabled &&
            canAutoEnter(state, launchPending)
    }

    fun nextInactivityDelayMs(
        state: LauncherState,
        nowUptimeMs: Long,
        launchPending: Boolean,
    ): Long? {
        if (!canAutoEnter(state, launchPending) || !state.inactivityAutoIdleEnabled) {
            return null
        }
        val idleForMs = (nowUptimeMs - state.lastInteractionUptimeMs).coerceAtLeast(0L)
        return (idleTimeoutMs(state) - idleForMs).coerceAtLeast(0L)
    }

    fun idleTimeoutMs(state: LauncherState): Long {
        return state.idleTimeoutSeconds.coerceAtLeast(1).toLong() * 1_000L
    }
}
