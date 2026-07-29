package com.purride.pixelshowcase

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Container
import com.purride.pixelui.PixelNavigatorOperation
import com.purride.pixelui.PixelRouteTransitionBuilder
import com.purride.pixelui.PositionedFill
import com.purride.pixelui.Stack
import com.purride.pixelui.Widget
import com.purride.pixelui.advanced.PixelExperimentalApi
import com.purride.pixelui.advanced.PixelMultiChildRenderObject
import com.purride.pixelui.advanced.PixelMultiChildRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize

/**
 * 迁移式粒子路由过渡：旧页面的每一颗像素被打散、飞向新位置、
 * 颜色渐变，落定后重组出新页面——同一批粒子，物质守恒。
 *
 * 配对是全屏像素层面的双射置换：progress=0 时每颗粒子在出发位
 * 画旧色（双射保证严格重现旧页），progress=1 时全部落定重现新页，
 * 中段位置沿带鼓包的弧线插值、颜色逐通道渐变。引擎的 presentation
 * proxy 让两个页面子树可以随时画进离屏 buffer，State 不重建。
 */
@OptIn(PixelExperimentalApi::class)
object ParticleRouteTransition : PixelRouteTransitionBuilder {
    override fun build(
        progress: Float,
        operation: PixelNavigatorOperation,
        outgoing: Widget,
        incoming: Widget,
    ): Widget = Stack(
        key = "particle-transition",
        children = listOf(
            // 底衬背景：飞行中段的空洞不能露出上一帧残影。
            PositionedFill(
                key = "particle-backdrop",
                child = Container(fillColor = ShowcaseTheme.BACKGROUND),
            ),
            ParticleMigrateWidget(
                outgoing = outgoing,
                incoming = incoming,
                progress = progress,
                key = "particle-migrate",
            ),
        ),
    )
}

@OptIn(PixelExperimentalApi::class)
private class ParticleMigrateWidget(
    outgoing: Widget,
    incoming: Widget,
    private val progress: Float,
    key: Any?,
) : PixelMultiChildRenderObjectWidget(children = listOf(outgoing, incoming), key = key) {
    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        RenderParticleMigrate(progress)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as RenderParticleMigrate).update(progress)
    }
}

@OptIn(PixelExperimentalApi::class)
private class RenderParticleMigrate(
    private var progress: Float,
) : PixelMultiChildRenderObject() {

    /** 目标像素 → 出发像素的双射置换表；尺寸变化时重建。 */
    private var sourceIndex = IntArray(0)

    fun update(nextProgress: Float) {
        if (progress == nextProgress) return
        progress = nextProgress
        markNeedsPaint()
    }

    override fun layout(constraints: PixelRenderConstraints) {
        children.forEach { (it as? PixelRenderBox)?.layout(constraints) }
        size = PixelRenderSize(constraints.maxWidth, constraints.maxHeight)
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        val outgoing = children.getOrNull(0) as? PixelRenderBox
        val incoming = children.getOrNull(1) as? PixelRenderBox
        if (size.width <= 0 || size.height <= 0) return
        // 端点直画：progress 边界必须与真实页面逐像素一致。
        if (progress <= 0f) {
            outgoing?.paint(context, offsetX, offsetY)
            return
        }
        if (progress >= 1f) {
            incoming?.paint(context, offsetX, offsetY)
            return
        }
        val oldBuffer = context.bufferPool.acquire(size.width, size.height)
        val newBuffer = context.bufferPool.acquire(size.width, size.height)
        try {
            outgoing?.paint(PixelPaintContext(buffer = oldBuffer, bufferPool = context.bufferPool), 0, 0)
            incoming?.paint(PixelPaintContext(buffer = newBuffer, bufferPool = context.bufferPool), 0, 0)
            migratePixels(oldBuffer.pixels, newBuffer.pixels, context, offsetX, offsetY)
        } finally {
            context.bufferPool.release(oldBuffer)
            context.bufferPool.release(newBuffer)
        }
    }

    /** 每颗粒子：出发位 = 置换表[目标位]，位置沿弧线插值，颜色旧→新渐变。 */
    private fun migratePixels(
        old: IntArray,
        new: IntArray,
        context: PixelPaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        val width = size.width
        val height = size.height
        val total = width * height
        ensurePermutation(total)
        val target = context.buffer
        val targetWidth = target.width
        val targetHeight = target.height

        for (i in 0 until total) {
            val src = sourceIndex[i]
            val tx = i % width
            val ty = i / width
            // 黄金比例哈希：起飞时刻与弧线鼓包都由目标像素决定，逐帧稳定。
            val hash = (tx * 0x9E3779B1.toInt() + ty * 0x85EBCA77.toInt()) xor (tx shl 16)
            val delay = (hash ushr 8 and 0x3FF) / 1024f * DELAY_SPAN
            val flight = ((progress * (1f + DELAY_SPAN) - delay)).coerceIn(0f, 1f)

            val plotX: Int
            val plotY: Int
            val argb: Int
            if (flight <= 0f) {
                // 未起飞：停在出发位显示旧色——双射保证全体未起飞时就是旧页。
                plotX = offsetX + src % width
                plotY = offsetY + src / width
                argb = old[src]
            } else if (flight >= 1f) {
                plotX = offsetX + tx
                plotY = offsetY + ty
                argb = new[i]
            } else {
                // smoothstep 缓动 + 垂直于飞行的正弦鼓包，粒子走弧线不走直线。
                val eased = flight * flight * (3f - 2f * flight)
                val sx = src % width
                val sy = src / width
                val bulge = ((hash shr 20 and 0x3F) - 32) * BULGE_SCALE *
                    (4f * eased * (1f - eased))
                plotX = offsetX + (sx + (tx - sx) * eased + bulge).toInt()
                plotY = offsetY + (sy + (ty - sy) * eased - bulge).toInt()
                argb = lerpColor(old[src], new[i], eased)
            }
            if (plotX in 0 until targetWidth && plotY in 0 until targetHeight) {
                target.pixels[plotY * targetWidth + plotX] = argb
            }
        }
    }

    /**
     * 线性同余置换：j = (i × A + B) mod N，A 与 N 互质即双射。
     * 大奇数乘子把出发点撒满全屏，配上逐像素 delay 视觉上足够乱。
     */
    private fun ensurePermutation(total: Int) {
        if (sourceIndex.size == total) return
        var multiplier = PERMUTE_MULTIPLIER
        while (gcd(multiplier, total.toLong()) != 1L) multiplier += 2
        sourceIndex = IntArray(total) { i ->
            ((i.toLong() * multiplier + PERMUTE_OFFSET) % total).toInt()
        }
    }

    private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val fr = from shr 16 and 0xFF
        val fg = from shr 8 and 0xFF
        val fb = from and 0xFF
        val tr = to shr 16 and 0xFF
        val tg = to shr 8 and 0xFF
        val tb = to and 0xFF
        val r = (fr + (tr - fr) * t).toInt()
        val g = (fg + (tg - fg) * t).toInt()
        val b = (fb + (tb - fb) * t).toInt()
        return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
    }

    private companion object {
        /** 起飞时刻散布跨度：越大"逐片瓦解"的层次越明显。 */
        const val DELAY_SPAN = 0.7f

        /** 弧线鼓包幅度系数（逻辑像素/单位鼓包值）。 */
        const val BULGE_SCALE = 0.9f

        const val PERMUTE_MULTIPLIER = 2654435761L
        const val PERMUTE_OFFSET = 40503L
    }
}
