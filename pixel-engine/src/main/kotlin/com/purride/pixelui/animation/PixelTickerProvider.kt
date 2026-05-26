package com.purride.pixelui.animation

import com.purride.pixelui.host.PixelFrameScheduler

public class PixelTickerProvider(
    private val frameScheduler: PixelFrameScheduler,
) {
    private val activeTickers = mutableListOf<PixelTicker>()
    private var frameScheduled = false

    public val activeTickerCount: Int
        get() = activeTickers.size

    /**
     * 创建一个 ticker。
     *
     * 当 [maxFps] 为 null（默认）时：每个 vsync 都派发 [onTick]。
     *
     * 当 [maxFps] 大于 0 时：provider 内部包装 [onTick]，只有距上次派发
     * 间隔 ≥ `1_000_000_000 / maxFps` 纳秒时才向调用方派发，中间帧被丢弃。
     * `elapsedNanos` 仍是从 ticker 启动起算的真实累积时间——
     * 这意味着 `PixelAnimationController` 在使用 fps-limited ticker 时，
     * 进度从 0→1 的"步数" ≈ `duration * maxFps`，符合像素风离散感的取向。
     *
     * 这是像素引擎专属能力：限制单个动画到 15 / 30 FPS 让风格保留离散感。
     */
    public fun createTicker(
        maxFps: Int? = null,
        onTick: (Long) -> Unit,
    ): PixelTicker {
        val callback: (Long) -> Unit = if (maxFps == null) onTick else {
            require(maxFps > 0) { "maxFps must be > 0, got $maxFps" }
            val minIntervalNanos = 1_000_000_000L / maxFps
            var lastDispatchNanos = -1L
            { elapsedNanos ->
                if (lastDispatchNanos < 0L || elapsedNanos - lastDispatchNanos >= minIntervalNanos) {
                    lastDispatchNanos = elapsedNanos
                    onTick(elapsedNanos)
                }
            }
        }
        return PixelTicker(onTick = callback, provider = this)
    }

    internal fun activate(ticker: PixelTicker) {
        activeTickers += ticker
        scheduleIfNeeded()
    }

    internal fun deactivate(ticker: PixelTicker) {
        activeTickers -= ticker
    }

    private fun scheduleIfNeeded() {
        if (frameScheduled || activeTickers.isEmpty()) return
        frameScheduled = true
        frameScheduler.scheduleFrame { frameNanos ->
            frameScheduled = false
            val snapshot = activeTickers.toList()
            for (ticker in snapshot) {
                ticker.dispatchTick(frameNanos)
            }
            scheduleIfNeeded()
        }
    }
}
