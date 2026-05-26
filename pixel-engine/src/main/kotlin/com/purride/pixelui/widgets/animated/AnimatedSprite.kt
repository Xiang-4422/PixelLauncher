package com.purride.pixelui.widgets.animated

import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Sprite
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTicker
import com.purride.pixelui.animation.PixelTickerProvider

public fun AnimatedSprite(
    sheet: PixelSpriteSheet,
    fps: Int,
    loop: Boolean = true,
    playing: Boolean = true,
    vsync: PixelTickerProvider,
    key: Any? = null,
): Widget = AnimatedSpriteWidget(
    sheet = sheet,
    fps = fps,
    loop = loop,
    playing = playing,
    vsync = vsync,
    key = key,
)

private class AnimatedSpriteWidget(
    val sheet: PixelSpriteSheet,
    val fps: Int,
    val loop: Boolean,
    val playing: Boolean,
    val vsync: PixelTickerProvider,
    override val key: Any?,
) : StatefulWidget(key = key) {
    init {
        require(fps > 0) { "fps must be > 0, got $fps" }
    }

    override fun createState(): State<out StatefulWidget> = AnimatedSpriteState()
}

private class AnimatedSpriteState : State<AnimatedSpriteWidget>() {
    private var ticker: PixelTicker? = null
    private var currentFrame = 0
    private var lastElapsedNanos = -1L
    private var carryNanos = 0L

    override fun initState() {
        currentFrame = 0
        createTicker()
        syncPlaying()
    }

    override fun didUpdateWidget(oldWidget: AnimatedSpriteWidget) {
        if (widget.fps != oldWidget.fps || widget.vsync !== oldWidget.vsync) {
            ticker?.dispose()
            ticker = null
            lastElapsedNanos = -1L
            carryNanos = 0L
            createTicker()
        }
        if (widget.sheet !== oldWidget.sheet) {
            currentFrame = currentFrame.coerceIn(0, widget.sheet.frames.lastIndex)
        }
        syncPlaying()
    }

    private fun createTicker() {
        ticker = widget.vsync.createTicker { elapsedNanos ->
            if (!widget.playing) return@createTicker
            val delta = if (lastElapsedNanos < 0L) 0L else elapsedNanos - lastElapsedNanos
            lastElapsedNanos = elapsedNanos
            if (delta <= 0L) return@createTicker
            advance(delta)
        }
    }

    private fun advance(deltaNanos: Long) {
        val frameNanos = 1_000_000_000L / widget.fps
        carryNanos += deltaNanos
        var advanced = false
        while (carryNanos >= frameNanos) {
            carryNanos -= frameNanos
            if (widget.loop) {
                currentFrame = (currentFrame + 1) % widget.sheet.frames.size
                advanced = true
            } else if (currentFrame < widget.sheet.frames.lastIndex) {
                currentFrame += 1
                advanced = true
            }
        }
        if (advanced) {
            setState { }
        }
    }

    private fun syncPlaying() {
        val activeTicker = ticker ?: return
        if (widget.playing) {
            activeTicker.start()
        } else {
            activeTicker.stop()
            lastElapsedNanos = -1L
        }
    }

    override fun dispose() {
        ticker?.dispose()
    }

    override fun build(context: BuildContext): Widget {
        return Sprite(sheet = widget.sheet, frameIndex = currentFrame, key = widget.key?.let { "$it-frame" })
    }
}
