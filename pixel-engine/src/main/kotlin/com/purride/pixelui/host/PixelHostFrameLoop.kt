package com.purride.pixelui.host

import android.os.SystemClock
import com.purride.pixelui.PixelHostFrameStats

/**
 * 单调时钟抽象。生产环境用 [AndroidUptimeClock]，单测注入 [FakeMonotonicClock]。
 */
internal interface MonotonicClock {
    fun uptimeMillis(): Long
    fun nanoTime(): Long
}

/**
 * 生产实现：包装 Android `SystemClock.uptimeMillis()` 和 `System.nanoTime()`。
 */
internal object AndroidUptimeClock : MonotonicClock {
    override fun uptimeMillis(): Long = SystemClock.uptimeMillis()
    override fun nanoTime(): Long = System.nanoTime()
}

/**
 * PixelHostView 内部帧节拍状态。
 *
 * 负责：
 *  - 把 Android uptime 转成当前帧 delta
 *  - 维护最近 [PixelHostFrameStats.FPS_WINDOW] 帧的滑动平均 FPS
 *  - 记录 onDraw 体内 paint 耗时（由 PixelHostView 在 onDraw 前后调用
 *    [beginPaint] / [endPaint]）
 *
 * 仅在 PixelHostView 持有 `frameStatsObserver` 时才构造 [PixelHostFrameStats]，
 * 其余路径只更新累积变量，避免在 paint 热路径分配。
 *
 * 时钟通过 [MonotonicClock] 注入，便于 JVM 单测脱离 Android `SystemClock`。
 */
internal class PixelHostFrameLoop(
    private val clock: MonotonicClock = AndroidUptimeClock,
) {
    private var lastFrameUptimeMs: Long = 0L
    private var lastDeltaMs: Long = 16L
    private var frameCount: Long = 0L

    // FPS_WINDOW 帧的 delta 滚动缓冲（毫秒）；初始全 0 = 还没数据
    private val deltaWindow: LongArray = LongArray(PixelHostFrameStats.FPS_WINDOW)
    private var windowIndex: Int = 0
    private var windowFilled: Int = 0

    private var paintStartNanos: Long = 0L
    private var paintTimeNanos: Long = 0L

    fun consumeFrameDeltaMs(): Long {
        val now = clock.uptimeMillis()
        val deltaMs = if (lastFrameUptimeMs == 0L) {
            16L
        } else {
            (now - lastFrameUptimeMs).coerceAtLeast(1L)
        }
        lastFrameUptimeMs = now
        lastDeltaMs = deltaMs
        deltaWindow[windowIndex] = deltaMs
        windowIndex = (windowIndex + 1) % deltaWindow.size
        if (windowFilled < deltaWindow.size) windowFilled += 1
        frameCount += 1
        return deltaMs
    }

    fun beginPaint() {
        paintStartNanos = clock.nanoTime()
    }

    fun endPaint() {
        if (paintStartNanos != 0L) {
            paintTimeNanos = clock.nanoTime() - paintStartNanos
            paintStartNanos = 0L
        }
    }

    /**
     * 构造当前帧的统计快照。
     *
     * 仅在调用方明确需要时调用（PixelHostView 在 observer 非空时才走此路径），
     * 否则不分配新对象。
     */
    fun snapshotStats(): PixelHostFrameStats {
        val filled = windowFilled
        val fps = if (filled <= 0) 0f else {
            var sum = 0L
            for (i in 0 until filled) sum += deltaWindow[i]
            if (sum <= 0L) 0f else 1000f * filled / sum
        }
        return PixelHostFrameStats(
            deltaMs = lastDeltaMs,
            fpsAvg = fps,
            paintTimeNanos = paintTimeNanos,
            frameCount = frameCount,
        )
    }
}
