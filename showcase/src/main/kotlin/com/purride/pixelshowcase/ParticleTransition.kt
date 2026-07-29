package com.purride.pixelshowcase

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Container
import com.purride.pixelui.PixelNavigatorOperation
import com.purride.pixelui.PixelRouteTransitionBuilder
import com.purride.pixelui.PositionedFill
import com.purride.pixelui.Stack
import com.purride.pixelui.Widget
import com.purride.pixelui.advanced.PixelExperimentalApi
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize
import com.purride.pixelui.advanced.PixelSingleChildRenderObject
import com.purride.pixelui.advanced.PixelSingleChildRenderObjectWidget
import kotlin.math.cos
import kotlin.math.sin

/**
 * 粒子路由过渡：旧页被吹散成单像素粒子，新页从粒子聚合成形。
 *
 * 引擎为自定义过渡准备了 presentation proxy——builder 拿到的 outgoing /
 * incoming 是可以放到任意位置、任意次数重绘的真实页面子树，不重建
 * State。聚合动画就是散开动画的时间反演，一套逐像素散射公式两用。
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
            // 底衬背景：粒子飞离后的空位不能露出上一帧残影。
            PositionedFill(
                key = "particle-backdrop",
                child = Container(fillColor = ShowcaseTheme.BACKGROUND),
            ),
            ParticleScatterWidget(
                child = incoming,
                progress = progress,
                mode = ParticleMode.GATHER,
                key = "particle-incoming",
            ),
            ParticleScatterWidget(
                child = outgoing,
                progress = progress,
                mode = ParticleMode.SCATTER,
                key = "particle-outgoing",
            ),
        ),
    )
}

enum class ParticleMode { SCATTER, GATHER }

@OptIn(PixelExperimentalApi::class)
private class ParticleScatterWidget(
    child: Widget,
    private val progress: Float,
    private val mode: ParticleMode,
    key: Any?,
) : PixelSingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        RenderParticleScatter(progress, mode)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as RenderParticleScatter).update(progress, mode)
    }
}

@OptIn(PixelExperimentalApi::class)
private class RenderParticleScatter(
    private var progress: Float,
    private var mode: ParticleMode,
) : PixelSingleChildRenderObject() {

    fun update(nextProgress: Float, nextMode: ParticleMode) {
        if (progress == nextProgress && mode == nextMode) return
        progress = nextProgress
        mode = nextMode
        markNeedsPaint()
    }

    override fun layout(constraints: PixelRenderConstraints) {
        val childBox = child as? PixelRenderBox
        childBox?.layout(constraints)
        size = childBox?.size ?: PixelRenderSize.Zero
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        val childBox = child as? PixelRenderBox ?: return
        // 聚合 = 散开倒放：同一公式吃"瓦解度"，SCATTER 顺放 GATHER 反演。
        val dissolve = if (mode == ParticleMode.SCATTER) progress else 1f - progress
        if (dissolve <= 0f) {
            childBox.paint(context, offsetX, offsetY)
            return
        }
        if (dissolve >= 1f || size.width <= 0 || size.height <= 0) return

        val scratch = context.bufferPool.acquire(size.width, size.height)
        try {
            childBox.paint(PixelPaintContext(buffer = scratch, bufferPool = context.bufferPool), 0, 0)
            scatterPixels(scratch.pixels, context, offsetX, offsetY, dissolve)
        } finally {
            context.bufferPool.release(scratch)
        }
    }

    /** 逐像素确定性散射：hash 定出生时刻与飞行方向，无状态、可倒放。 */
    private fun scatterPixels(
        source: IntArray,
        context: PixelPaintContext,
        offsetX: Int,
        offsetY: Int,
        dissolve: Float,
    ) {
        val target = context.buffer
        val targetWidth = target.width
        val targetHeight = target.height
        val width = size.width
        val height = size.height
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = source[index++]
                if (argb ushr 24 == 0) continue
                // 黄金比例哈希：同一像素每帧同一命运，动画才连贯。
                val hash = (x * 0x9E3779B1.toInt() + y * 0x85EBCA77.toInt()) xor (x shl 16)
                val delay = (hash ushr 8 and 0x3FF) / 1024f * DELAY_SPAN
                val flight = ((dissolve * (1f + DELAY_SPAN) - delay) / 1f).coerceIn(0f, 1f)
                if (flight >= 1f) continue
                if (flight <= 0f) {
                    val px = offsetX + x
                    val py = offsetY + y
                    if (px in 0 until targetWidth && py in 0 until targetHeight) {
                        target.pixels[py * targetWidth + px] = argb
                    }
                    continue
                }
                // 飞行：方向由 hash 决定、整体带上飘，距离随飞行度平方加速。
                val angle = (hash and 0xFFF) / 4096f * TWO_PI
                val distance = flight * flight * MAX_DISTANCE
                val px = offsetX + x + (cos(angle) * distance).toInt()
                val py = offsetY + y + (sin(angle) * distance - flight * UPDRAFT).toInt()
                if (px in 0 until targetWidth && py in 0 until targetHeight) {
                    target.pixels[py * targetWidth + px] = argb
                }
            }
        }
    }

    private companion object {
        /** 出生时刻散布跨度：越大瓦解越有"从一角蔓延"的层次。 */
        const val DELAY_SPAN = 0.6f

        /** 粒子最大飞行距离（逻辑像素）。 */
        const val MAX_DISTANCE = 46f

        /** 整体上飘量：像素灰烬向上扬。 */
        const val UPDRAFT = 14f

        const val TWO_PI = (Math.PI * 2).toFloat()
    }
}
