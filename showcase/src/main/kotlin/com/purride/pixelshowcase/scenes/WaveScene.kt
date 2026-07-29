package com.purride.pixelshowcase.scenes

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelshowcase.DemoScene
import kotlin.math.sin

/**
 * 正弦滚字（sine scroller）：demoscene 的署名艺能。
 * 每个字符独立取波形相位，文字像一条缎带流过屏幕。
 */
class WaveScene : DemoScene {
    override val title = "SCROLLER"

    private var width = 0
    private var height = 0
    private var scroll = 0f
    private var charWidth = 6

    override fun reset(width: Int, height: Int) {
        this.width = width
        this.height = height
        scroll = width.toFloat()
        charWidth = PixelBitmapFont.Default.measureText("W").coerceAtLeast(4) + 1
    }

    override fun update(dt: Float, elapsed: Float) {
        scroll -= dt * SPEED
        val total = TEXT.length * charWidth
        if (scroll < -total) scroll = width.toFloat()
    }

    override fun render(buffer: PixelBuffer) {
        val font = PixelBitmapFont.Default
        val baseline = height / 2f
        TEXT.forEachIndexed { index, char ->
            val x = scroll + index * charWidth
            if (x < -charWidth || x > width) return@forEachIndexed
            val phase = x / 14f
            val y = baseline + sin(phase) * AMPLITUDE
            // 波峰亮、波谷暗：亮度跟随相位，缎带有了光泽。
            val brightness = ((sin(phase) + 1f) / 2f * (COLORS.size - 1)).toInt()
            font.drawText(
                buffer = buffer,
                text = char.toString(),
                x = x.toInt(),
                y = y.toInt(),
                color = COLORS[brightness],
            )
        }
        // 底部参考线：三条渐隐横线，衬出波幅。
        for (offset in 0 until 3) {
            val y = (baseline + AMPLITUDE + 12 + offset * 3).toInt()
            if (y < height) {
                buffer.fillRect(0, y, width, 1, GUIDE[offset])
            }
        }
    }

    private companion object {
        const val TEXT = "POWERED BY PIXEL-ENGINE ... RETRO SOUL / MODERN CORE ... EVERY DOT IS A LOGICAL PIXEL ... "
        const val SPEED = 46f
        const val AMPLITUDE = 22f
        val COLORS = arrayOf(
            PixelColor.fromRgb(70, 110, 170),
            PixelColor.fromRgb(120, 165, 215),
            PixelColor.fromRgb(180, 215, 245),
            PixelColor.fromRgb(240, 250, 255),
        )
        val GUIDE = arrayOf(
            PixelColor.fromRgb(60, 84, 120),
            PixelColor.fromRgb(40, 58, 88),
            PixelColor.fromRgb(26, 40, 64),
        )
    }
}
