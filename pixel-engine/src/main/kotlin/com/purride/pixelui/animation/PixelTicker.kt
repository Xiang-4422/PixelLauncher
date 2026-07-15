package com.purride.pixelui.animation

/** 定义 `PixelTicker` 在 `PixelTicker` 中承担的数据或执行职责，并保持公开不变量稳定。 */
public class PixelTicker internal constructor(
    /** Consumer callback receiving pause-excluding elapsed active time. */
    private val onTick: (elapsedNanos: Long) -> Unit,
    /** Provider that owns scheduling and terminal ticker registration. */
    private val provider: PixelTickerProvider,
) {
    /** Active-time timestamp captured by the first dispatched frame after [start]. */
    private var startNanos: Long = -1L

    /** Whether terminal ticker disposal has already completed. */
    private var disposed = false

    /** 表示 `PixelTicker` 当前是否满足 `isActive` 对应条件。
 *
 * Whether this ticker currently requests provider frames.
 */
    public var isActive: Boolean = false
        private set

    /** 表示 `PixelTicker` 当前是否满足 `isDisposed` 对应条件。
 *
 * Whether this ticker can no longer be started.
 */
    public val isDisposed: Boolean
        get() = disposed

    /** 执行 `PixelTicker` 的 `start` 公开行为；具体参数、返回和副作用见下文。
 *
 * Starts this ticker from elapsed zero unless it is active or disposed.
 */
    public fun start() {
        if (disposed || isActive) return
        isActive = true
        startNanos = -1L
        provider.activate(this)
    }

    /** 执行 `PixelTicker` 的 `stop` 公开行为；具体参数、返回和副作用见下文。
 *
 * Stops frame delivery while keeping this ticker available for a fresh restart.
 */
    public fun stop() {
        if (disposed || !isActive) return
        isActive = false
        provider.deactivate(this)
    }

    /** Delivers one provider-owned active timestamp to this ticker. */
    internal fun dispatchTick(frameNanos: Long) {
        if (!isActive) return
        if (startNanos < 0L) startNanos = frameNanos
        onTick(frameNanos - startNanos)
    }

    /** 从 `PixelTicker` 释放 `dispose` 内容并收敛相关所有权。
 *
 * Stops and releases this ticker from its provider exactly once.
 */
    public fun dispose() {
        if (disposed) return
        stop()
        disposed = true
        provider.unregister(this)
    }
}
