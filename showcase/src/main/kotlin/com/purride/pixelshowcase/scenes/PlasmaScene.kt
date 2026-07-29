package com.purride.pixelshowcase.scenes

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelshowcase.DemoScene
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 经典等离子（demoscene 祖传艺能）：多个正弦场叠加，量化成离散色阶。
 * 低分辨率 + 色阶量化正是点阵屏的主场——同样的数学在高分屏上只是渐变，
 * 在这里是流动的色块地形。
 */
class PlasmaScene : DemoScene {
    override val title = "PLASMA"

    private var width = 0
    private var height = 0
    private var time = 0f

    /** 深海 → 冰蓝 → 白的 6 级阶梯，与点阵夜色调一脉相承。 */
    private val palette = intArrayOf(
        PixelColor.fromRgb(16, 24, 48).argb,
        PixelColor.fromRgb(28, 52, 96).argb,
        PixelColor.fromRgb(44, 92, 148).argb,
        PixelColor.fromRgb(90, 150, 200).argb,
        PixelColor.fromRgb(160, 208, 240).argb,
        PixelColor.fromRgb(236, 246, 255).argb,
    )

    override fun reset(width: Int, height: Int) {
        this.width = width
        this.height = height
        time = 0f
    }

    override fun update(dt: Float, elapsed: Float) {
        time = elapsed
    }

    override fun render(buffer: PixelBuffer) {
        // 2×2 粗采样：帧预算减到 1/4，颗粒感反而更"硬件"。
        val t = time
        var y = 0
        while (y < height) {
            val fy = y.toFloat()
            var x = 0
            val row0 = y * width
            val row1 = if (y + 1 < height) (y + 1) * width else row0
            while (x < width) {
                val fx = x.toFloat()
                val v = sin(fx / 11f + t * 1.4f) +
                    sin(fy / 7f - t) +
                    sin((fx + fy) / 13f + t * 0.7f) +
                    sin(sqrt(fx * fx + fy * fy) / 9f - t * 1.8f)
                // v ∈ [-4,4] → 色阶下标
                val index = (((v + 4f) / 8f) * palette.size).toInt().coerceIn(0, palette.size - 1)
                val color = palette[index]
                buffer.pixels[row0 + x] = color
                if (x + 1 < width) buffer.pixels[row0 + x + 1] = color
                buffer.pixels[row1 + x] = color
                if (x + 1 < width) buffer.pixels[row1 + x + 1] = color
                x += 2
            }
            y += 2
        }
    }
}
