package com.purride.pixelshowcase.scenes

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelshowcase.DemoScene
import kotlin.random.Random

/**
 * 字符雨：每列一条下落的字符流，头亮尾暗。
 * 展示点：内置点阵字体的字形可以当纯图形素材使用，逐字符自由摆放。
 */
class RainScene : DemoScene {
    override val title = "RAIN"

    private val random = Random(9)
    private var width = 0
    private var height = 0
    private var columnWidth = 6
    private var lineHeight = 8
    private var heads = FloatArray(0)
    private var speeds = FloatArray(0)
    private var glyphs: Array<CharArray> = emptyArray()

    override fun reset(width: Int, height: Int) {
        this.width = width
        this.height = height
        val font = PixelBitmapFont.Default
        columnWidth = font.measureText("W").coerceAtLeast(4) + 1
        lineHeight = font.measureHeight("W").coerceAtLeast(6)
        val columns = (width / columnWidth).coerceAtLeast(1)
        val rows = (height / lineHeight) + TRAIL + 2
        heads = FloatArray(columns) { random.nextFloat() * rows }
        speeds = FloatArray(columns) { 6f + random.nextFloat() * 14f }
        glyphs = Array(columns) { CharArray(rows) { randomGlyph() } }
    }

    override fun update(dt: Float, elapsed: Float) {
        for (index in heads.indices) {
            heads[index] += speeds[index] * dt
            val rows = glyphs[index].size
            if (heads[index] >= rows + TRAIL) {
                heads[index] = 0f
                speeds[index] = 6f + random.nextFloat() * 14f
            }
            // 头部经过的行随机换字，雨看起来一直在"变"。
            val headRow = heads[index].toInt()
            if (headRow in glyphs[index].indices && random.nextInt(3) == 0) {
                glyphs[index][headRow] = randomGlyph()
            }
        }
    }

    override fun render(buffer: PixelBuffer) {
        val font = PixelBitmapFont.Default
        for (column in heads.indices) {
            val headRow = heads[column].toInt()
            val x = column * columnWidth
            for (offset in 0 until TRAIL) {
                val row = headRow - offset
                if (row < 0 || row >= glyphs[column].size) continue
                val y = row * lineHeight
                if (y > height) continue
                font.drawText(
                    buffer = buffer,
                    text = glyphs[column][row].toString(),
                    x = x,
                    y = y,
                    color = TRAIL_COLORS[offset],
                )
            }
        }
    }

    private fun randomGlyph(): Char = GLYPHS[random.nextInt(GLYPHS.length)]

    private companion object {
        const val TRAIL = 7
        const val GLYPHS = "01ABCDEFGHIJKLMNOPQRSTUVWXYZ#*+<>=%"

        /** 头白，尾部逐级熄灭。 */
        val TRAIL_COLORS = arrayOf(
            PixelColor.fromRgb(255, 255, 255),
            PixelColor.fromRgb(170, 220, 255),
            PixelColor.fromRgb(110, 170, 230),
            PixelColor.fromRgb(70, 120, 190),
            PixelColor.fromRgb(48, 84, 140),
            PixelColor.fromRgb(32, 56, 100),
            PixelColor.fromRgb(20, 36, 66),
        )
    }
}
