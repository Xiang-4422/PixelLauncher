package com.purride.pixelshowcase.scenes

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelshowcase.DemoScene
import kotlin.random.Random

/**
 * 星野冲屏 + 落款：3D 星点透视投影，越近越亮越快；文字随星光淡入。
 */
class StarfieldScene : DemoScene {
    override val title = "STARFIELD"
    override val durationSeconds = 9f

    private val random = Random(11)
    private var width = 0
    private var height = 0
    private var stars = Array(0) { FloatArray(3) }
    private var elapsed = 0f

    override fun reset(width: Int, height: Int) {
        this.width = width
        this.height = height
        elapsed = 0f
        stars = Array(STAR_COUNT) { newStar(randomDepth = true) }
    }

    override fun update(dt: Float, elapsed: Float) {
        this.elapsed = elapsed
        stars.forEachIndexed { index, star ->
            star[2] -= dt * WARP_SPEED
            if (star[2] <= 0.05f) {
                stars[index] = newStar(randomDepth = false)
            }
        }
    }

    override fun render(buffer: PixelBuffer) {
        val cx = width / 2f
        val cy = height / 2f
        stars.forEach { star ->
            val screenX = (cx + star[0] / star[2] * cx).toInt()
            val screenY = (cy + star[1] / star[2] * cy).toInt()
            if (screenX !in 0 until width || screenY !in 0 until height) return@forEach
            val tier = when {
                star[2] < 0.35f -> 0
                star[2] < 0.7f -> 1
                else -> 2
            }
            if (tier == 0) {
                // 近星拖出 2px 光轨，冲屏感来自这一点点长度。
                buffer.fillRect(screenX, screenY, 2, 1, STAR_TIERS[tier])
            } else {
                buffer.setPixel(screenX, screenY, STAR_TIERS[tier])
            }
        }
        if (elapsed > CREDIT_AT) {
            val font = PixelBitmapFont.Default
            val alphaTier = (((elapsed - CREDIT_AT) / 1.5f).coerceIn(0f, 1f) * (CREDIT_COLORS.size - 1)).toInt()
            val lines = listOf("PIXEL ENGINE", "FIN")
            val lineHeight = font.measureHeight(lines[0]) + 3
            lines.forEachIndexed { index, line ->
                font.drawText(
                    buffer = buffer,
                    text = line,
                    x = (width - font.measureText(line)) / 2,
                    y = (height / 2 - lineHeight) + index * lineHeight,
                    color = CREDIT_COLORS[alphaTier],
                )
            }
        }
    }

    private fun newStar(randomDepth: Boolean): FloatArray = floatArrayOf(
        (random.nextFloat() - 0.5f) * 2f,
        (random.nextFloat() - 0.5f) * 2f,
        if (randomDepth) 0.1f + random.nextFloat() * 0.9f else 1f,
    )

    private companion object {
        const val STAR_COUNT = 140
        const val WARP_SPEED = 0.35f
        const val CREDIT_AT = 3f
        val STAR_TIERS = arrayOf(
            PixelColor.fromRgb(255, 255, 255),
            PixelColor.fromRgb(150, 190, 230),
            PixelColor.fromRgb(80, 110, 160),
        )
        val CREDIT_COLORS = arrayOf(
            PixelColor.fromRgb(60, 80, 110),
            PixelColor.fromRgb(120, 150, 190),
            PixelColor.fromRgb(190, 215, 240),
            PixelColor.fromRgb(245, 250, 255),
        )
    }
}
