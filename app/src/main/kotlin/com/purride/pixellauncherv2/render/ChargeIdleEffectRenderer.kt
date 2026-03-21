package com.purride.pixellauncherv2.render

/**
 * 为充电中的 Idle 页生成动态遮罩。
 *
 * 渲染器只负责输出遮罩，不处理状态切换、静态底图和显示后端。
 */
interface ChargeIdleEffectRenderer {
    fun render(
        width: Int,
        height: Int,
        batteryLevel: Int,
        isCharging: Boolean,
        gravityX: Float,
        gravityY: Float,
        nowUptimeMs: Long,
        sequence: Long,
    ): IdleMaskFrame?
}
