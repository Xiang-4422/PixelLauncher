package com.purride.pixelshowcase.scenes

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelshowcase.DemoScene
import kotlin.math.PI
import kotlin.math.cos
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
        /** 呼吸分裂时的专属飘散向量（方向 × 幅度，出生时定死）。 */
        val driftX: Float,
        val driftY: Float,
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
            if (t >= 1f) {
                // 到位即吸附：渐近插值永远差一点点，toInt 截断后就是字形边缘
                // 上随机的 ±1px 毛刺。
                p.x = p.targetX
                p.y = p.targetY
                return@forEach
            }
            val ease = 1f - (1f - t) * (1f - t) * (1f - t)
            p.x += (p.targetX - p.x) * ease * min(1f, dt * 10f)
            p.y += (p.targetY - p.y) * ease * min(1f, dt * 10f)
        }
    }

    override fun render(buffer: PixelBuffer) {
        // 呼吸 = 定格 → 分裂 → 聚合的循环。每轮先让完整字形停留 HOLD 秒
        // （没有平台期的正弦呼吸里，字形完整只存在于周期端点的一瞬，根本
        // 来不及读），随后 sin(π·t) 驱动一次雾化往返。偏移只做在渲染层，
        // 粒子逻辑位置钉在目标位——定格期与聚合端点都是零误差字形。
        val burst = if (elapsed in BREATH_FROM..SCATTER_AT) {
            val cycleT = ((elapsed - BREATH_FROM) % BREATH_CYCLE_SECONDS)
            if (cycleT < BREATH_HOLD_SECONDS) {
                0f
            } else {
                sin((cycleT - BREATH_HOLD_SECONDS) / BREATH_BURST_SECONDS * PI.toFloat())
            }
        } else {
            0f
        }
        particles.forEach { p ->
            buffer.setPixel(
                (p.x + p.driftX * burst).toInt(),
                (p.y + p.driftY * burst).toInt(),
                INK,
            )
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
                // 放大后的每个像素独立成粒：粒子 = 1 逻辑像素。
                // 块状粒子雾化时是方块糖，单像素粒子雾化时才是沙。
                for (dy in 0 until SCALE) {
                    for (dx in 0 until SCALE) {
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
                        // 飘散向量：随机方向、4~14 像素幅度。幅度刻意压在"散而可辨"
                        // 区间——峰值时字应该像雾化而不是消失，聚合的瞬间才有魔力。
                        val driftAngle = random.nextFloat() * 2f * PI.toFloat()
                        val driftRadius = 4f + random.nextFloat() * 10f
                        result.add(
                            Particle(
                                targetX = (originLeft + x * SCALE + dx).toFloat(),
                                targetY = (originTop + y * SCALE + dy).toFloat(),
                                x = startX,
                                y = startY,
                                delay = (x * SCALE + dx) * 0.006f + random.nextFloat() * 0.25f,
                                driftX = cos(driftAngle) * driftRadius,
                                driftY = sin(driftAngle) * driftRadius,
                            ),
                        )
                    }
                }
            }
        }
        return result
    }

    private companion object {
        const val SCALE = 3
        const val LINE_GAP = 2
        const val GATHER_SECONDS = 1.6f
        const val BREATH_FROM = 2.6f
        const val SCATTER_AT = 7.6f

        /** 每轮先定格（字可读）再雾化往返；两轮正好铺满呼吸窗口。 */
        const val BREATH_HOLD_SECONDS = 1.3f
        const val BREATH_BURST_SECONDS = 1.2f
        const val BREATH_CYCLE_SECONDS = BREATH_HOLD_SECONDS + BREATH_BURST_SECONDS
        val INK = PixelColor.fromRgb(240, 246, 255)
    }
}
