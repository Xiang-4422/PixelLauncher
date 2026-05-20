package com.purride.pixelui.animation

public class PixelTicker internal constructor(
    private val onTick: (elapsedNanos: Long) -> Unit,
    private val provider: PixelTickerProvider,
) {
    private var startNanos: Long = -1L
    private var disposed = false

    public var isActive: Boolean = false
        private set

    public fun start() {
        if (disposed || isActive) return
        isActive = true
        startNanos = -1L
        provider.activate(this)
    }

    public fun stop() {
        if (disposed || !isActive) return
        isActive = false
        provider.deactivate(this)
    }

    internal fun dispatchTick(frameNanos: Long) {
        if (!isActive) return
        if (startNanos < 0L) startNanos = frameNanos
        onTick(frameNanos - startNanos)
    }

    public fun dispose() {
        if (disposed) return
        stop()
        disposed = true
    }
}
