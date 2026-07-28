package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelcore.PixelClusterTextRasterizer
import com.purride.pixelcore.PixelFontMetrics
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelcore.internal.PixelCoreArtifactAccess
import com.purride.pixelui.PixelTextStyle
import kotlin.math.roundToInt

/** Applies one positive Host text scale to rasterizer metrics and nearest-neighbor paint. */
internal class PixelTextScaleRasterizer(
    /** Unscaled consumer or engine rasterizer. */
    private val delegate: PixelTextRasterizer,
    /** Positive finite Host multiplier captured for this immutable adapter. */
    private val scaleFactor: Float,
) : PixelClusterTextRasterizer {
    /** 适配器生命周期内复用的原生 glyph 缓冲池；Host 文本操作受主线程约束。 */
    private val bufferPool = PixelBufferPool()

    /** Rejects malformed environment values before measurement or buffer allocation. */
    init {
        require(scaleFactor.isFinite() && scaleFactor > 0f) {
            "scaleFactor must be finite and greater than zero"
        }
    }

    /** Scales horizontal advance while retaining zero-width ignorable clusters. */
    override fun measureText(text: String): Int {
        return scaleDimension(delegate.measureText(text))
    }

    /** 连续缩放两个相邻片段的原生总宽度，避免在适配层生成拼接文本。 */
    internal fun measureAdjacentText(first: String, second: String): Int {
        return scaleDimension(delegate.measureAdjacentText(first, second))
    }

    /** Scales total rasterizer height to the same deterministic destination extent as paint. */
    override fun measureHeight(text: String): Int {
        return scaleDimension(delegate.measureHeight(text))
    }

    /** Scales every vertical metric and clamps dependent positions to the scaled cell. */
    override fun fontMetrics(text: String): PixelFontMetrics {
        /** Original rasterizer metrics before Host scaling. */
        val original = delegate.fontMetrics(text)
        /** Positive scaled cell height defining the valid vertical coordinate range. */
        val cellHeight = scaleDimension(original.cellHeight).coerceAtLeast(1)
        return PixelFontMetrics(
            cellHeight = cellHeight,
            baseline = scaleCoordinate(original.baseline, cellHeight),
            ascent = scaleDimension(original.ascent).coerceAtMost(cellHeight),
            descent = scaleDimension(original.descent).coerceAtMost(cellHeight),
            inkTop = scaleCoordinate(original.inkTop, cellHeight),
            inkBottom = scaleCoordinate(original.inkBottom, cellHeight),
        )
    }

    /** 支持 grapheme cluster，同时保留单 code point 的确定性回落路径。 */
    override fun canRasterizeCluster(cluster: String): Boolean {
        /** Optional atomic cluster capability implemented by the wrapped rasterizer. */
        val clusterDelegate = delegate as? PixelClusterTextRasterizer
        return clusterDelegate?.canRasterizeCluster(cluster)
            ?: (cluster.isNotEmpty() && Character.codePointCount(cluster, 0, cluster.length) == 1)
    }

    /** Draws once at native size and scales the resulting pixels into the destination buffer. */
    override fun drawText(
        buffer: PixelBuffer,
        text: String,
        x: Int,
        y: Int,
        color: com.purride.pixelcore.PixelColor,
    ) {
        /** Native source width, retaining one scratch column for zero-advance control payloads. */
        val sourceWidth = delegate.measureText(text).coerceAtLeast(1)
        /** Native source height, retaining one scratch row for malformed consumer rasterizers. */
        val sourceHeight = delegate.measureHeight(text).coerceAtLeast(1)
        /** 从适配器池借出的原生 glyph 缓冲，避免每次绘制分配像素数组。 */
        val source = bufferPool.acquire(width = sourceWidth, height = sourceHeight)
        try {
            delegate.drawText(
                buffer = source,
                text = text,
                x = 0,
                y = 0,
                color = color,
            )
            /** Destination width exactly matching [measureText] for positive native advances. */
            val destinationWidth = scaleDimension(sourceWidth).coerceAtLeast(1)
            /** Destination height exactly matching [measureHeight] for positive native heights. */
            val destinationHeight = scaleDimension(sourceHeight).coerceAtLeast(1)
            for (destinationY in 0 until destinationHeight) {
                /** Native row sampled by normalized nearest-neighbor position. */
                val sourceY = ((destinationY.toLong() * sourceHeight) / destinationHeight)
                    .toInt()
                    .coerceIn(0, sourceHeight - 1)
                for (destinationX in 0 until destinationWidth) {
                    /** Native column sampled by normalized nearest-neighbor position. */
                    val sourceX = ((destinationX.toLong() * sourceWidth) / destinationWidth)
                        .toInt()
                        .coerceIn(0, sourceWidth - 1)
                    /** Exact source color, including transparency, emitted only for visible ink. */
                    val pixel = source.getPixel(sourceX, sourceY)
                    if (pixel.alpha > 0) {
                        buffer.setPixel(
                            x = x + destinationX,
                            y = y + destinationY,
                            color = pixel,
                        )
                    }
                }
            }
        } finally {
            bufferPool.release(source)
        }
    }

    /** Structural equality prevents identical immutable adapters from invalidating every frame. */
    override fun equals(other: Any?): Boolean {
        return other is PixelTextScaleRasterizer &&
            delegate === other.delegate &&
            scaleFactor == other.scaleFactor
    }

    /** Identity-based delegate hashing matches the equality contract. */
    override fun hashCode(): Int {
        return 31 * System.identityHashCode(delegate) + scaleFactor.hashCode()
    }

    /** Scales a non-negative extent and preserves zero exactly. */
    private fun scaleDimension(value: Int): Int {
        if (value <= 0) return 0
        return (value * scaleFactor).roundToInt().coerceAtLeast(1)
    }

    /** Scales one coordinate and clamps it inside the scaled cell. */
    private fun scaleCoordinate(value: Int, cellHeight: Int): Int {
        return (value * scaleFactor).roundToInt().coerceIn(0, cellHeight - 1)
    }
}

/** 优先使用 SDK 栅格器的无拼接相邻测量能力，第三方实现保持原协议回退。 */
internal fun PixelTextRasterizer.measureAdjacentText(first: String, second: String): Int {
    return when (this) {
        is PixelTextScaleRasterizer -> measureAdjacentText(first, second)
        else -> PixelCoreArtifactAccess.measureAdjacentText(this, first, second)
            ?: measureText(first + second)
    }
}

/** 执行 `PixelTextScaleRasterizer` 的 `withHostTextScale` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns an immutable scaled adapter, avoiding a wrapper for the exact identity scale.
 */
public fun PixelTextRasterizer.withHostTextScale(scaleFactor: Float): PixelTextRasterizer {
    return if (scaleFactor == 1f) this else PixelTextScaleRasterizer(this, scaleFactor)
}

/** 执行 `PixelTextScaleRasterizer` 的 `withHostTextScale` 公开行为；具体参数、返回和副作用见下文。
 *
 * Scales explicit typography metrics and any style-owned rasterizer for one Host snapshot.
 */
public fun PixelTextStyle.withHostTextScale(scaleFactor: Float): PixelTextStyle {
    if (scaleFactor == 1f) return this
    /** Scales a non-negative style metric while retaining zero as an absent spacing value. */
    fun scaleMetric(value: Int): Int {
        if (value <= 0) return 0
        return (value * scaleFactor).roundToInt().coerceAtLeast(1)
    }
    return copy(
        textRasterizer = textRasterizer?.withHostTextScale(scaleFactor),
        lineSpacing = scaleMetric(lineSpacing),
        letterSpacing = scaleMetric(letterSpacing),
        lineHeight = lineHeight?.let(::scaleMetric)?.coerceAtLeast(1),
    )
}
