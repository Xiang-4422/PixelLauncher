package com.purride.pixelui.animation

import com.purride.pixelui.host.PixelFrameScheduler

public class PixelTickerProvider(
    private val frameScheduler: PixelFrameScheduler,
) {
    private val activeTickers = mutableListOf<PixelTicker>()
    private var frameScheduled = false

    public fun createTicker(onTick: (Long) -> Unit): PixelTicker {
        return PixelTicker(onTick = onTick, provider = this)
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
