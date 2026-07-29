package com.purride.pixelshowcase.scenes

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelshowcase.DemoScene
import kotlin.random.Random

/**
 * 经典火焰（fire effect）：底行随机热源，热量逐帧向上传播并衰减，
 * 映射到黑→深红→橙→黄→白的调色板。整个 demo 唯一的暖色段落。
 *
 * 与 PLASMA 同用 2×2 粗采样的连续场——采样单元不是视觉元素，
 * 离散粒子才必须保持 1 像素粒度。
 */
class FireScene : DemoScene {
    override val title = "FIRE"
    override val durationSeconds = 12f

    private val random = Random(6)
    private var cols = 0
    private var rows = 0
    private var heat = FloatArray(0)
    private var sinceStep = 0f

    private val palette = intArrayOf(
        PixelColor.fromRgb(8, 6, 10).argb,
        PixelColor.fromRgb(40, 12, 12).argb,
        PixelColor.fromRgb(96, 22, 12).argb,
        PixelColor.fromRgb(160, 48, 12).argb,
        PixelColor.fromRgb(216, 96, 16).argb,
        PixelColor.fromRgb(244, 160, 32).argb,
        PixelColor.fromRgb(252, 216, 96).argb,
        PixelColor.fromRgb(255, 250, 210).argb,
    )

    override fun reset(width: Int, height: Int) {
        cols = width / SAMPLE
        rows = height / SAMPLE
        heat = FloatArray(cols * rows)
        sinceStep = 0f
    }

    override fun update(dt: Float, elapsed: Float) {
        sinceStep += dt
        if (sinceStep < STEP_SECONDS) return
        sinceStep -= STEP_SECONDS
        // 底行热源：大部分格子烧旺，随机几处熄一下，火苗才有舞动感。
        val bottom = (rows - 1) * cols
        for (x in 0 until cols) {
            heat[bottom + x] = if (random.nextInt(10) == 0) 0.3f else 0.75f + random.nextFloat() * 0.25f
        }
        // 向上传播：每格取下方三格与更下一格的均值再衰减。
        for (y in 0 until rows - 1) {
            val row = y * cols
            val below = row + cols
            val belowFar = (row + cols * 2).coerceAtMost((rows - 1) * cols)
            for (x in 0 until cols) {
                val left = below + (x - 1 + cols) % cols
                val right = below + (x + 1) % cols
                heat[row + x] = (heat[below + x] + heat[left] + heat[right] + heat[belowFar + x]) *
                    0.25f * DECAY
            }
        }
    }

    override fun render(buffer: PixelBuffer) {
        val width = buffer.width
        val height = buffer.height
        for (y in 0 until rows) {
            val sourceRow = y * cols
            val targetY = y * SAMPLE
            for (x in 0 until cols) {
                val index = (heat[sourceRow + x].coerceIn(0f, 1f) * (palette.size - 1)).toInt()
                val color = palette[index]
                val targetX = x * SAMPLE
                for (dy in 0 until SAMPLE) {
                    val py = targetY + dy
                    if (py >= height) break
                    val base = py * width + targetX
                    for (dx in 0 until SAMPLE) {
                        if (targetX + dx < width) buffer.pixels[base + dx] = color
                    }
                }
            }
        }
    }

    private companion object {
        const val SAMPLE = 2
        const val STEP_SECONDS = 0.033f
        const val DECAY = 0.985f
    }
}
