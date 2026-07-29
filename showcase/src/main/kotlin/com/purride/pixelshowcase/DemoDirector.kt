package com.purride.pixelshowcase

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.Widget
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize
import com.purride.pixelui.animation.PixelTicker
import com.purride.pixelui.animation.PixelTickerProvider

/**
 * 演示导演：驱动场景序列（自动轮播 + 点按切换），把当帧画进自绘层。
 *
 * 动画模式与 launcher 的沙钟/贪吃蛇一致：渲染对象在 paint 时懒注册
 * markNeedsPaint 钩子，导演每帧标脏 + 请求帧——引擎的 paint 是保留式的，
 * 自驱动动画不标脏就永远停在上一帧。
 */
class DemoDirector(
    vsync: PixelTickerProvider,
    private val onFrame: () -> Unit,
    private val scenes: List<DemoScene>,
) {
    private val ticker: PixelTicker = vsync.createTicker { elapsedNanos -> onTick(elapsedNanos) }

    var repaintHook: (() -> Unit)? = null

    private var sceneIndex = 0
    private var sceneElapsedSeconds = 0f
    private var lastElapsedNanos = -1L
    private var width = 0
    private var height = 0

    val currentScene: DemoScene
        get() = scenes[sceneIndex]

    val sceneCount: Int
        get() = scenes.size

    val currentIndex: Int
        get() = sceneIndex

    /** 画布尺寸就绪/变化：重置当前场景并开跑。 */
    fun onCanvasSized(nextWidth: Int, nextHeight: Int) {
        if (nextWidth <= 0 || nextHeight <= 0) return
        if (width == nextWidth && height == nextHeight) return
        width = nextWidth
        height = nextHeight
        currentScene.reset(width, height)
        sceneElapsedSeconds = 0f
        ticker.start()
    }

    /** 点按/左滑：下一场景。 */
    fun nextScene() = jumpTo(sceneIndex + 1)

    /** 右滑：上一场景。 */
    fun previousScene() = jumpTo(sceneIndex - 1)

    private fun jumpTo(index: Int) {
        if (width <= 0) return
        sceneIndex = ((index % scenes.size) + scenes.size) % scenes.size
        sceneElapsedSeconds = 0f
        currentScene.reset(width, height)
        ticker.start()
        requestRepaint()
    }

    fun dispose() {
        ticker.dispose()
    }

    private fun onTick(elapsedNanos: Long) {
        val dt = when {
            lastElapsedNanos < 0L || elapsedNanos <= lastElapsedNanos -> FRAME_SECONDS
            else -> ((elapsedNanos - lastElapsedNanos) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.1f)
        }
        lastElapsedNanos = elapsedNanos
        sceneElapsedSeconds += dt
        // 手动切换模式：到时不跳下一场，而是重置当前场景循环演出——
        // TITLE 爆散完重新聚合、生命游戏烧完重新播种，每个节目无限返场。
        if (sceneElapsedSeconds >= currentScene.durationSeconds) {
            sceneElapsedSeconds = 0f
            currentScene.reset(width, height)
        }
        currentScene.update(dt, sceneElapsedSeconds)
        requestRepaint()
    }

    private fun requestRepaint() {
        repaintHook?.invoke()
        onFrame()
    }

    private companion object {
        const val FRAME_SECONDS = 0.016f
    }
}

/** 演示画布：全屏自绘层；点按/左滑下一场，右滑上一场。 */
fun DemoCanvas(director: DemoDirector): Widget = GestureDetector(
    child = DemoCanvasRenderWidget(director),
    onTap = { director.nextScene() },
    onSwipeLeft = { director.nextScene() },
    onSwipeRight = { director.previousScene() },
    key = "demo-canvas-gesture",
)

private class DemoCanvasRenderWidget(
    private val director: DemoDirector,
) : PixelLeafRenderObjectWidget(key = "demo-canvas") {
    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        RenderDemoCanvas(director)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as RenderDemoCanvas).update(director)
    }
}

private class RenderDemoCanvas(
    private var director: DemoDirector,
) : PixelRenderBox() {

    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(width = constraints.maxWidth, height = constraints.maxHeight)
        director.onCanvasSized(size.width, size.height)
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        director.repaintHook = { markNeedsPaint() }
        val buffer = context.buffer
        // 自己清屏：不依赖引擎对未标脏区域的保留策略，动画残影从源头杜绝。
        buffer.fillRect(0, 0, size.width, size.height, BACKGROUND)
        director.currentScene.render(buffer)
        drawChrome(buffer)
    }

    fun update(next: DemoDirector) {
        director = next
        markNeedsPaint()
    }

    /** 左上角场景名 + 右上角进度点列：demo 自己的"字幕"。 */
    private fun drawChrome(buffer: PixelBuffer) {
        val font = PixelBitmapFont.Default
        font.drawText(buffer, director.currentScene.title, x = 2, y = 2, color = CHROME_COLOR)
        val dots = director.sceneCount
        val right = size.width - 2
        for (index in 0 until dots) {
            val x = right - (dots - index) * 3
            val color = if (index == director.currentIndex) PixelColor.White else CHROME_COLOR
            buffer.setPixel(x, 3, color)
        }
    }

    private companion object {
        val CHROME_COLOR = PixelColor.fromRgb(110, 130, 160)
        val BACKGROUND = PixelColor.fromRgb(10, 14, 26)
    }
}
