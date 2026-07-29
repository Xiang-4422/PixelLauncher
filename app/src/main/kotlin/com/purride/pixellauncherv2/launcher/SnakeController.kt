package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTextRasterizer
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
import kotlin.random.Random

/**
 * 贪吃蛇的运行时编排：ticker 驱动逻辑帧，纯规则在 [SnakeModel]。
 *
 * 渲染走与沙钟相同的自驱动动画模式：渲染对象在 paint 时懒注册 markNeedsPaint
 * 钩子，控制器每逻辑帧标脏 + 请求帧（保留式 paint 下缺一动画都不可见）。
 * 分数与终局提示直接画进自绘层——不经 LauncherState，游戏对宿主状态零侵入。
 */
internal class SnakeController(
    vsync: PixelTickerProvider,
    private val onFrame: () -> Unit,
    private val random: Random = Random.Default,
) {
    private val ticker: PixelTicker = vsync.createTicker { elapsedNanos ->
        onTick(elapsedNanos)
    }

    var state: SnakeModel.State? = null
        private set

    /** 进程级最高分；持久化留待后续（游戏本体先行）。 */
    var bestScore: Int = 0
        private set

    var repaintHook: (() -> Unit)? = null

    private var lastElapsedNanos = -1L
    private var sinceLastStepMs = 0L

    fun isRunning(): Boolean = state != null

    /**
     * 场地尺寸就绪（渲染层首次 layout 时回调）。已在局中且尺寸未变则忽略；
     * 尺寸变化（转屏/点距调整）重开新局——旧坐标系在新场地上没有意义。
     */
    fun onFieldSized(cols: Int, rows: Int) {
        val current = state
        if (current != null && current.cols == cols && current.rows == rows) return
        if (cols < SnakeModel.MIN_GRID || rows < SnakeModel.MIN_GRID) return
        startGame(cols, rows)
    }

    fun turn(direction: SnakeModel.Direction) {
        val current = state ?: return
        state = SnakeModel.turn(current, direction)
    }

    /** 终局后点按场地重开；局中无效（误触不应清空进度）。 */
    fun restartIfGameOver() {
        val current = state ?: return
        if (current.isGameOver) {
            startGame(current.cols, current.rows)
        }
    }

    /** 离开游戏页：停帧并丢弃本局。 */
    fun clear() {
        ticker.stop()
        state = null
        lastElapsedNanos = -1L
        sinceLastStepMs = 0L
    }

    fun dispose() {
        clear()
        ticker.dispose()
    }

    private fun startGame(cols: Int, rows: Int) {
        state = SnakeModel.initial(cols, rows, random)
        lastElapsedNanos = -1L
        sinceLastStepMs = 0L
        ticker.start()
        requestRepaint()
    }

    private fun onTick(elapsedNanos: Long) {
        val current = state ?: run {
            ticker.stop()
            return
        }
        if (current.isGameOver) {
            ticker.stop()
            return
        }
        val deltaMs = when {
            lastElapsedNanos < 0L || elapsedNanos <= lastElapsedNanos -> FRAME_DELAY_MS
            else -> ((elapsedNanos - lastElapsedNanos) / 1_000_000L).coerceIn(1L, 100L)
        }
        lastElapsedNanos = elapsedNanos
        sinceLastStepMs += deltaMs
        val interval = SnakeModel.tickIntervalMs(current.score)
        if (sinceLastStepMs < interval) return
        sinceLastStepMs -= interval

        val next = SnakeModel.step(current, random)
        state = next
        if (next.isGameOver) {
            bestScore = maxOf(bestScore, next.score)
            ticker.stop()
        }
        requestRepaint()
    }

    private fun requestRepaint() {
        repaintHook?.invoke()
        onFrame()
    }

    private companion object {
        const val FRAME_DELAY_MS = 16L
    }
}

/**
 * 游戏场地：自绘蛇/食物/边框/HUD/终局提示；点按在终局时重开。
 */
internal fun SnakeFieldLayer(
    controller: SnakeController,
    rasterizer: PixelTextRasterizer,
    fieldColor: PixelColor,
    dimColor: PixelColor,
    dangerColor: PixelColor,
    key: Any? = null,
): Widget = GestureDetector(
    child = SnakeFieldRenderWidget(
        controller = controller,
        style = SnakeFieldStyle(rasterizer, fieldColor, dimColor, dangerColor),
        key = key,
    ),
    onTap = { controller.restartIfGameOver() },
    key = key?.let { "$it-gesture" },
)

internal data class SnakeFieldStyle(
    val rasterizer: PixelTextRasterizer,
    val fieldColor: PixelColor,
    val dimColor: PixelColor,
    val dangerColor: PixelColor,
)

private class SnakeFieldRenderWidget(
    private val controller: SnakeController,
    private val style: SnakeFieldStyle,
    override val key: Any?,
) : PixelLeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        RenderSnakeField(controller, style)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as RenderSnakeField).update(controller, style)
    }
}

private class RenderSnakeField(
    private var controller: SnakeController,
    private var style: SnakeFieldStyle,
) : PixelRenderBox() {

    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        )
        // HUD 一行文字 + 场地网格；余数留白由居中吸收。
        val hudHeight = style.rasterizer.measureHeight(HUD_SAMPLE) + HUD_GAP
        val cols = (size.width - BORDER * 2) / CELL
        val rows = (size.height - hudHeight - BORDER * 2) / CELL
        controller.onFieldSized(cols, rows)
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        controller.repaintHook = { markNeedsPaint() }
        val state = controller.state ?: return
        val buffer = context.buffer

        val hudHeight = style.rasterizer.measureHeight(HUD_SAMPLE) + HUD_GAP
        style.rasterizer.drawText(
            buffer = buffer,
            text = "SCORE ${state.score}   BEST ${maxOf(controller.bestScore, state.score)}",
            x = offsetX,
            y = offsetY,
            color = style.dimColor,
        )

        // 场地边框：网格外扩 1px，居中吸收余数。
        val fieldWidth = state.cols * CELL
        val fieldHeight = state.rows * CELL
        val fieldLeft = offsetX + (size.width - fieldWidth) / 2
        val fieldTop = offsetY + hudHeight
        drawBorder(buffer, fieldLeft - BORDER, fieldTop - BORDER, fieldWidth + BORDER * 2, fieldHeight + BORDER * 2)

        state.body.forEach { cell ->
            fillCell(buffer, fieldLeft, fieldTop, cell, style.fieldColor)
        }
        // 食物：内缩一圈的小块，与蛇身形成形状区分（单色屏没有颜色可用）。
        fillCellInset(buffer, fieldLeft, fieldTop, state.food, style.fieldColor)

        if (state.isGameOver) {
            val line1 = "GAME OVER  SCORE ${state.score}"
            val line2 = "TAP FIELD TO RESTART"
            val y1 = fieldTop + fieldHeight / 2 - style.rasterizer.measureHeight(line1)
            drawCenteredText(buffer, line1, offsetX, y1, style.dangerColor)
            drawCenteredText(buffer, line2, offsetX, y1 + style.rasterizer.measureHeight(line1) + HUD_GAP, style.dimColor)
        }
    }

    fun update(nextController: SnakeController, nextStyle: SnakeFieldStyle) {
        controller = nextController
        style = nextStyle
        markNeedsPaint()
    }

    private fun drawCenteredText(buffer: PixelBuffer, text: String, offsetX: Int, y: Int, color: PixelColor) {
        val textWidth = style.rasterizer.measureText(text)
        style.rasterizer.drawText(
            buffer = buffer,
            text = text,
            x = offsetX + ((size.width - textWidth) / 2).coerceAtLeast(0),
            y = y,
            color = color,
        )
    }

    private fun fillCell(buffer: PixelBuffer, left: Int, top: Int, cell: SnakeModel.Cell, color: PixelColor) {
        buffer.fillRect(left + cell.x * CELL, top + cell.y * CELL, CELL, CELL, color)
    }

    private fun fillCellInset(buffer: PixelBuffer, left: Int, top: Int, cell: SnakeModel.Cell, color: PixelColor) {
        buffer.fillRect(left + cell.x * CELL + 1, top + cell.y * CELL + 1, CELL - 2, CELL - 2, color)
    }

    private fun drawBorder(buffer: PixelBuffer, left: Int, top: Int, width: Int, height: Int) {
        buffer.fillRect(left, top, width, BORDER, style.dimColor)
        buffer.fillRect(left, top + height - BORDER, width, BORDER, style.dimColor)
        buffer.fillRect(left, top, BORDER, height, style.dimColor)
        buffer.fillRect(left + width - BORDER, top, BORDER, height, style.dimColor)
    }

    private companion object {
        /** 一格的边长（逻辑像素）。 */
        const val CELL = 6

        /** 场地边框厚度。 */
        const val BORDER = 1

        /** HUD 与场地的间隙。 */
        const val HUD_GAP = 3

        /** 测 HUD 行高用的样本。 */
        const val HUD_SAMPLE = "SCORE 0"
    }
}
