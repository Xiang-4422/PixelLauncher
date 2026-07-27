package com.purride.pixellauncherv2.animation

/**
 * 启动器装饰动画的轻量状态（充能动画的帧计数节拍器）。
 *
 * 此前在此追踪的启动遮罩、开机序列、抽屉展开动画均已移除；
 * 渲染现在完全由 LauncherRootHost 中的 pixel-engine 组件负责。
 */
data class LauncherAnimationState(
    /** 充能动画当前帧计数，每次 [nextFrame] 调用递增 1。 */
    val headerChargeTick: Int = 0,
) {
    /** 返回帧计数加 1 的新状态，不修改当前实例。 */
    fun nextFrame(): LauncherAnimationState = copy(headerChargeTick = headerChargeTick + 1)

    companion object {
        /** 装饰节拍器（充能动画）的目标帧周期。 */
        const val frameDelayMs: Long = 60L

        /**
         * 实际启动应用前的等待时长（让启动遮罩动画有时间在
         * 启动器一侧播放完毕，再显示被启动的应用）。
         */
        const val launchShutterDurationMs: Long = frameDelayMs * 4   // 4 帧 ≈ 240 毫秒
    }
}
