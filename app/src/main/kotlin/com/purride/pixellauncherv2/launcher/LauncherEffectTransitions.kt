package com.purride.pixellauncherv2.launcher

/**
 * Effect / Idle 域的纯状态转换（ADR-0001 阶段 2 拆分，来源 LauncherStateTransitions）。
 *
 * 目前只承载交互时刻记录；Idle/Pixel Matter 偏好经 updateUiBehavior 归 Settings 域写入，
 * Idle 路由流程（showIdle/hideIdle）归 Shell 流。对外入口仍是 [LauncherStateTransitions]
 * facade；行为与拆分前逐字节等价。
 */
object LauncherEffectTransitions {

    /** 记录最近一次用户交互的单调时钟时刻，驱动 Idle 自动进入判定。 */
    fun recordInteraction(state: LauncherState, uptimeMs: Long): LauncherState {
        return state.copy(lastInteractionUptimeMs = uptimeMs)
    }
}
