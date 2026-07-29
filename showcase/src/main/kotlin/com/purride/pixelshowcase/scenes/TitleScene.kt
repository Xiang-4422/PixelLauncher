package com.purride.pixelshowcase.scenes

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelshowcase.DemoScene
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 开场：\"PIXEL ENGINE\" 的字形粒子从屏幕四周飞入聚合成形，
 * 到位后整体呼吸，结尾向外爆散——字形即数据，粒子即像素。
 */
class TitleScene : DemoScene {
    override val title = "TITLE"
    override val durationSeconds = 9f

    private data class Particle(
        val targetX: Float,
        val targetY: Float,
        var x: Float,
        var y: Float,
        val delay: Float,
        var vx: Float = 0f,
        var vy: Float = 0f,
    )

    private val random = Random(42)
    private var particles: List<Particle> = emptyList()
    private var width = 0
    private var height = 0
    private var elapsed = 0f

    override fun reset(width: Int, height: Int) {
        this.width = width
        this.height = height
        this.elapsed = 0f
        particles = buildParticles(width, height)
    }

    override fun update(dt: Float, elapsed: Float) {
        this.elapsed = elapsed
        if (elapsed >= SCATTER_AT) {
            // 爆散：首帧赋随机速度，其后自由飞行。
            particles.forEach { p ->
                if (p.vx == 0f && p.vy == 0f) {
                    p.vx = (random.nextFloat() - 0.5f) * 160f
                    p.vy = (random.nextFloat() - 0.7f) * 160f
                }
                p.vy += 90f * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
            }
            return
        }
        particles.forEach { p ->
            val t = ((elapsed - p.delay) / GATHER_SECONDS).coerceIn(0f, 1f)
            val ease = 1f - (1f - t) * (1f - t) * (1f - t)
            p.x += (p.targetX - p.x) * ease * min(1f, dt * 10f)
            p.y += (p.targetY - p.y) * ease * min(1f, dt * 10f)
        }
    }

    override fun render(buffer: PixelBuffer) {
        val breath = if (elapsed in BREATH_FROM..SCATTER_AT) sin((elapsed - BREATH_FROM) * 2.4f) * 1.5f else 0f
        particles.forEach { p ->
            val x = (p.x + if (breath != 0f) breath * ((p.targetY - height / 2f) / height) * 4f else 0f).toInt()
            val y = p.y.toInt()
            buffer.fillRect(x, y, SCALE, SCALE, INK)
        }
    }

    /** 字形 → 粒子：小画布画字，墨迹像素放大映射为目标点。 */
    private fun buildParticles(width: Int, height: Int): List<Particle> {
        val font = PixelBitmapFont.Default
        val lines = listOf("PIXEL", "ENGINE")
        val textWidth = lines.maxOf { font.measureText(it) }
        val lineHeight = font.measureHeight(lines[0])
        val small = PixelBuffer(width = textWidth, height = lineHeight * lines.size + LINE_GAP)
        lines.forEachIndexed { index, line ->
            font.drawText(
                buffer = small,
                text = line,
                x = (textWidth - font.measureText(line)) / 2,
                y = index * (lineHeight + LINE_GAP),
                color = PixelColor.White,
            )
        }
        val originLeft = (width - small.width * SCALE) / 2
        val originTop = (height - small.height * SCALE) / 2
        val result = ArrayList<Particle>()
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                if (small.pixels[y * small.width + x] == 0) continue
                // 起点：随机屏幕边缘；延迟按列错开，聚合呈扫描感。
                val fromEdge = random.nextInt(4)
                val startX: Float
                val startY: Float
                when (fromEdge) {
                    0 -> { startX = -8f; startY = random.nextInt(height).toFloat() }
                    1 -> { startX = width + 8f; startY = random.nextInt(height).toFloat() }
                    2 -> { startX = random.nextInt(width).toFloat(); startY = -8f }
                    else -> { startX = random.nextInt(width).toFloat(); startY = height + 8f }
                }
                result.add(
                    Particle(
                        targetX = (originLeft + x * SCALE).toFloat(),
                        targetY = (originTop + y * SCALE).toFloat(),
                        x = startX,
                        y = startY,
                        delay = x * 0.018f + random.nextFloat() * 0.25f,
                    ),
                )
            }
        }
        return result
    }

    private companion object {
        const val SCALE = 3
        const val LINE_GAP = 2
        const val GATHER_SECONDS = 1.6f
        const val BREATH_FROM = 3.5f
        const val SCATTER_AT = 7.6f
        val INK = PixelColor.fromRgb(240, 246, 255)
    }
}
