package com.purride.pixelui

/**
 * 单帧的运行时统计快照。
 *
 * 由 [PixelHostView] 在每次 `onDraw` 结束时构造，传给可选的
 * `frameStatsObserver`。配合 [PixelDebugOverlay] 与 `ValueNotifier` 可
 * 在调试模式下实时显示 FPS / 帧时间。
 *
 * 不在 paint 热路径分配：[PixelHostView] 内部用预分配的累积变量计算，
 * 仅在 observer 非空时构造一次 [PixelHostFrameStats]。
 */
public data class PixelHostFrameStats(
    /** 距上一帧的时间（毫秒），>= 1。 */
    val deltaMs: Long,
    /** 最近 [FPS_WINDOW] 帧的平均 FPS。 */
    val fpsAvg: Float,
    /** 当前帧 onDraw 体内绘制（不含 invalidate）的耗时（纳秒）。 */
    val paintTimeNanos: Long,
    /** 已观测到的 onDraw 调用总次数。 */
    val frameCount: Long,
) {
    public companion object {
        /** FPS 滑动平均窗口长度。 */
        public const val FPS_WINDOW: Int = 30
    }
}
