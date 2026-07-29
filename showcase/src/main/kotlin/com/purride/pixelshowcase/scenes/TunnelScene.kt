package com.purride.pixelshowcase.scenes

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelshowcase.DemoScene
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 纹理隧道：极坐标棋盘无限前飞。角度与深度都预计算成查找表，
 * 每帧只做整数纹理寻址——90 年代 demo 在 486 上就是这么跑满帧的。
 */
class TunnelScene : DemoScene {
    override val title = "TUNNEL"
    override val durationSeconds = 12f

    private var width = 0
    private var height = 0
    private var angleTable = IntArray(0)
    private var depthTable = IntArray(0)
    private var time = 0f

    private val palette = intArrayOf(
        PixelColor.fromRgb(14, 20, 40).argb,
        PixelColor.fromRgb(40, 64, 110).argb,
        PixelColor.fromRgb(96, 140, 190).argb,
        PixelColor.fromRgb(196, 224, 250).argb,
    )

    override fun reset(width: Int, height: Int) {
        this.width = width
        this.height = height
        time = 0f
        buildTables()
    }

    override fun update(dt: Float, elapsed: Float) {
        time = elapsed
    }

    override fun render(buffer: PixelBuffer) {
        // 前飞 = 深度纹理坐标随时间平移；缓慢自旋 = 角度坐标平移。
        val depthShift = (time * 26f).toInt()
        val angleShift = (time * 6f).toInt()
        var index = 0
        val pixels = buffer.pixels
        while (index < pixels.size) {
            val angle = angleTable[index] + angleShift
            val depth = depthTable[index] + depthShift
            // 棋盘：角度段与深度段的奇偶异或；深度衰减压暗远处。
            val checker = (angle / ANGLE_CELL + depth / DEPTH_CELL) and 1
            val fade = (depthTable[index] / FADE_STEP).coerceAtMost(2)
            val level = if (checker == 1) 3 - fade else (2 - fade).coerceAtLeast(0)
            pixels[index] = palette[level]
            index++
        }
    }

    /** 逐像素预计算极坐标：角度 0..1023、深度 = k/r。 */
    private fun buildTables() {
        angleTable = IntArray(width * height)
        depthTable = IntArray(width * height)
        val cx = width / 2f
        val cy = height / 2f
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - cx
                val dy = y - cy
                val radius = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                angleTable[index] = ((atan2(dy, dx) / (2 * Math.PI) + 0.5) * 1024).toInt()
                depthTable[index] = (DEPTH_SCALE / radius).toInt()
                index++
            }
        }
    }

    private companion object {
        const val DEPTH_SCALE = 4200f
        const val ANGLE_CELL = 64
        const val DEPTH_CELL = 24
        const val FADE_STEP = 90
    }
}
