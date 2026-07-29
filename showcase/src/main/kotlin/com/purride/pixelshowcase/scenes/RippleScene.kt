package com.purride.pixelshowcase.scenes

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelshowcase.DemoScene
import kotlin.random.Random

/**
 * 水面：真实的二维波动方程（不是画同心圆）。雨滴随机落下，
 * 波纹扩散、相互干涉、从边缘反弹——全部从 u'' = c²∇²u 自然涌现。
 */
class RippleScene : DemoScene {
    override val title = "RIPPLE"
    override val durationSeconds = 12f

    private val random = Random(3)
    private var cols = 0
    private var rows = 0
    private var current = FloatArray(0)
    private var previous = FloatArray(0)
    private var sinceStep = 0f
    private var sinceDrop = 0f

    private val palette = intArrayOf(
        PixelColor.fromRgb(10, 18, 38).argb,
        PixelColor.fromRgb(22, 40, 74).argb,
        PixelColor.fromRgb(46, 78, 124).argb,
        PixelColor.fromRgb(90, 134, 182).argb,
        PixelColor.fromRgb(150, 196, 234).argb,
        PixelColor.fromRgb(226, 244, 255).argb,
    )

    override fun reset(width: Int, height: Int) {
        cols = width / SAMPLE
        rows = height / SAMPLE
        current = FloatArray(cols * rows)
        previous = FloatArray(cols * rows)
        sinceStep = 0f
        sinceDrop = 0f
    }

    override fun update(dt: Float, elapsed: Float) {
        sinceDrop += dt
        if (sinceDrop >= DROP_INTERVAL) {
            sinceDrop = 0f
            // 一滴雨：十字软冲击——单点硬冲击会在离散网格上激起棋盘状
            // 数值振荡，波纹变成噪点；铺开成十字后波前是干净的圆环。
            val x = 2 + random.nextInt((cols - 4).coerceAtLeast(1))
            val y = 2 + random.nextInt((rows - 4).coerceAtLeast(1))
            val center = y * cols + x
            current[center] = DROP_ENERGY
            current[center - 1] += DROP_ENERGY * 0.5f
            current[center + 1] += DROP_ENERGY * 0.5f
            current[center - cols] += DROP_ENERGY * 0.5f
            current[center + cols] += DROP_ENERGY * 0.5f
        }
        sinceStep += dt
        while (sinceStep >= STEP_SECONDS) {
            sinceStep -= STEP_SECONDS
            stepWave()
        }
    }

    /** 经典两缓冲波动方程：new = (四邻均值 × 2 − old) × 阻尼。 */
    private fun stepWave() {
        for (y in 1 until rows - 1) {
            val row = y * cols
            for (x in 1 until cols - 1) {
                val index = row + x
                val neighbors = current[index - 1] + current[index + 1] +
                    current[index - cols] + current[index + cols]
                previous[index] = (neighbors / 2f - previous[index]) * DAMPING
            }
        }
        val swap = current
        current = previous
        previous = swap
    }

    override fun render(buffer: PixelBuffer) {
        val width = buffer.width
        val height = buffer.height
        for (y in 0 until rows) {
            val sourceRow = y * cols
            val targetY = y * SAMPLE
            for (x in 0 until cols) {
                // 静水落在深色档（index≈1），波峰亮、波谷更深——水面应该是暗的，
                // 亮的只有正在经过的波。
                val level = ((current[sourceRow + x] + REST_OFFSET) / LEVEL_RANGE * palette.size)
                    .toInt()
                    .coerceIn(0, palette.size - 1)
                val color = palette[level]
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
        const val STEP_SECONDS = 0.025f
        const val DROP_INTERVAL = 0.9f
        const val DROP_ENERGY = 1.6f
        const val DAMPING = 0.986f

        /** 静水(0)映射到 palette 的 1/4 处：水面常暗，波峰才亮。 */
        const val REST_OFFSET = 0.4f
        const val LEVEL_RANGE = 1.6f
    }
}
