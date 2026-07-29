package com.purride.pixelshowcase.scenes

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelshowcase.DemoScene
import kotlin.random.Random

/**
 * 康威生命游戏：用 \"LIFE\" 的字形墨迹播种，加一圈随机噪声让它烧起来。
 * 展示点：字形当数据用——文字既是初始 pattern，也是这块画布的世界观。
 */
class LifeScene : DemoScene {
    override val title = "GAME OF LIFE"
    override val durationSeconds = 10f

    private val random = Random(4)
    private var cols = 0
    private var rows = 0
    private var cells = BooleanArray(0)
    private var next = BooleanArray(0)
    private var age = IntArray(0)
    private var sinceStep = 0f

    override fun reset(width: Int, height: Int) {
        cols = width / CELL
        rows = height / CELL
        cells = BooleanArray(cols * rows)
        next = BooleanArray(cols * rows)
        age = IntArray(cols * rows)
        sinceStep = 0f
        seedFromGlyphs()
        // 随机噪声铺底：纯字形太稳定，几代就烧完；噪声保证持续演化。
        repeat(cols * rows / 12) {
            cells[random.nextInt(cells.size)] = true
        }
    }

    override fun update(dt: Float, elapsed: Float) {
        sinceStep += dt
        if (sinceStep < STEP_SECONDS) return
        sinceStep -= STEP_SECONDS
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val index = y * cols + x
                val neighbors = countNeighbors(x, y)
                val alive = cells[index]
                next[index] = if (alive) neighbors == 2 || neighbors == 3 else neighbors == 3
                age[index] = if (next[index]) (age[index] + 1).coerceAtMost(AGE_COLORS.size - 1) else 0
            }
        }
        val swap = cells
        cells = next
        next = swap
    }

    override fun render(buffer: PixelBuffer) {
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val index = y * cols + x
                if (!cells[index]) continue
                // 新生亮白，长寿沉为深蓝：一眼看出哪里在剧烈演化。
                buffer.fillRect(x * CELL, y * CELL, CELL, CELL, AGE_COLORS[age[index]])
            }
        }
    }

    private fun countNeighbors(x: Int, y: Int): Int {
        var count = 0
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                // 环面世界：边缘相接，滑翔机可以永远飞。
                val nx = (x + dx + cols) % cols
                val ny = (y + dy + rows) % rows
                if (cells[ny * cols + nx]) count++
            }
        }
        return count
    }

    private fun seedFromGlyphs() {
        val font = PixelBitmapFont.Default
        val text = "LIFE"
        val textWidth = font.measureText(text)
        val textHeight = font.measureHeight(text)
        val small = PixelBuffer(width = textWidth, height = textHeight)
        font.drawText(small, text, x = 0, y = 0, color = PixelColor.White)
        val scale = 2
        val left = (cols - textWidth * scale) / 2
        val top = (rows - textHeight * scale) / 2
        for (y in 0 until textHeight) {
            for (x in 0 until textWidth) {
                if (small.pixels[y * textWidth + x] == 0) continue
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        val cx = left + x * scale + dx
                        val cy = top + y * scale + dy
                        if (cx in 0 until cols && cy in 0 until rows) {
                            cells[cy * cols + cx] = true
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val CELL = 3
        const val STEP_SECONDS = 0.12f
        val AGE_COLORS = arrayOf(
            PixelColor.fromRgb(255, 255, 255),
            PixelColor.fromRgb(190, 220, 250),
            PixelColor.fromRgb(130, 175, 225),
            PixelColor.fromRgb(90, 130, 190),
            PixelColor.fromRgb(60, 95, 150),
            PixelColor.fromRgb(42, 68, 112),
        )
    }
}
