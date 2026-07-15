package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelcore.PixelBlendMode
import com.purride.pixelcore.PixelColor

/**
 * 新渲染管线里的盒模型尺寸。
 */
public data class RenderSize(
    val width: Int,
    val height: Int,
) {
    /** 集中提供 `PipelinePrimitives` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 提供 `PipelinePrimitives` 的 `Zero` 稳定默认值或常量。 */
        public val Zero: RenderSize = RenderSize(width = 0, height = 0)
    }
}

/**
 * 新渲染管线里的布局约束。
 *
 * 第一版先稳定最小盒模型协议：上下界宽高约束，以及若干常用收敛方法。
 */
public data class RenderConstraints(
    val minWidth: Int = 0,
    val maxWidth: Int,
    val minHeight: Int = 0,
    val maxHeight: Int,
) {
    init {
        require(minWidth <= maxWidth) { "minWidth 不能大于 maxWidth" }
        require(minHeight <= maxHeight) { "minHeight 不能大于 maxHeight" }
    }

    /**
     * 用当前约束收敛宽度。
     */
    public fun constrainWidth(width: Int): Int {
        return width.coerceIn(minWidth, maxWidth)
    }

    /**
     * 用当前约束收敛高度。
     */
    public fun constrainHeight(height: Int): Int {
        return height.coerceIn(minHeight, maxHeight)
    }

    /**
     * 把当前约束减去四向内边距，得到子节点可用约束。
     */
    public fun inset(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ): RenderConstraints {
        val horizontal = (left + right).coerceAtLeast(0)
        val vertical = (top + bottom).coerceAtLeast(0)
        return RenderConstraints(
            minWidth = (minWidth - horizontal).coerceAtLeast(0),
            maxWidth = (maxWidth - horizontal).coerceAtLeast(0),
            minHeight = (minHeight - vertical).coerceAtLeast(0),
            maxHeight = (maxHeight - vertical).coerceAtLeast(0),
        )
    }
}

/**
 * 新渲染管线里的绘制上下文。
 *
 * 持有目标 buffer 与共享 buffer pool。需要 scratch buffer 的 RenderObject
 * 子类应该从 [bufferPool] 借（acquire）、在 paint 结束前还（release），
 * 而不是直接 new PixelBuffer。
 *
 * 单元测试构造 PaintContext 时可省略 [bufferPool]，会创建一个独立的
 * 新池子，保证测试隔离。
 */
public class PaintContext(
    /** 记录 `PipelinePrimitives` 的 `buffer` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val buffer: PixelBuffer,
    /** 记录 `PipelinePrimitives` 的 `bufferPool` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val bufferPool: PixelBufferPool = PixelBufferPool(),
    /** Global Host coordinate represented by scratch-buffer coordinate zero on the x axis. */
    internal val globalOriginX: Int = 0,
    /** Global Host coordinate represented by scratch-buffer coordinate zero on the y axis. */
    internal val globalOriginY: Int = 0,
    /** Rational x scale mapping this buffer's coordinates into Host-global coordinates. */
    private val globalScaleNumeratorX: Long = 1L,
    /** Rational x-scale denominator paired with [globalScaleNumeratorX]. */
    private val globalScaleDenominatorX: Long = 1L,
    /** Rational y scale mapping this buffer's coordinates into Host-global coordinates. */
    private val globalScaleNumeratorY: Long = 1L,
    /** Rational y-scale denominator paired with [globalScaleNumeratorY]. */
    private val globalScaleDenominatorY: Long = 1L,
) {
    /**
     * Redirects paint into [target] while preserving this scratch coordinate system exactly.
     *
     * Deferred Stack siblings use this to capture their pixels before the current clip, scale,
     * translation, or opacity scratch buffer is released by its ancestor.
     */
    internal fun redirect(target: PixelBuffer): PaintContext {
        return PaintContext(
            buffer = target,
            bufferPool = bufferPool,
            globalOriginX = globalOriginX,
            globalOriginY = globalOriginY,
            globalScaleNumeratorX = globalScaleNumeratorX,
            globalScaleDenominatorX = globalScaleDenominatorX,
            globalScaleNumeratorY = globalScaleNumeratorY,
            globalScaleDenominatorY = globalScaleDenominatorY,
        )
    }

    /** Resolves one coordinate in this paint buffer into the Host-global x axis. */
    internal fun globalX(localX: Int): Int {
        return globalOriginX + scaledCoordinate(
            coordinate = localX,
            numerator = globalScaleNumeratorX,
            denominator = globalScaleDenominatorX,
        )
    }

    /** Resolves one coordinate in this paint buffer into the Host-global y axis. */
    internal fun globalY(localY: Int): Int {
        return globalOriginY + scaledCoordinate(
            coordinate = localY,
            numerator = globalScaleNumeratorY,
            denominator = globalScaleDenominatorY,
        )
    }

    /** Resolves one local width into the corresponding Host-global width. */
    internal fun globalWidth(localWidth: Int): Int {
        return scaledExtent(localWidth, globalScaleNumeratorX, globalScaleDenominatorX)
    }

    /** Resolves one local height into the corresponding Host-global height. */
    internal fun globalHeight(localHeight: Int): Int {
        return scaledExtent(localHeight, globalScaleNumeratorY, globalScaleDenominatorY)
    }

    /**
 * 执行 `PipelinePrimitives` 的 `derive` 公开行为；具体参数、返回和副作用见下文。
 *
     * Creates a scratch context whose local origin maps to [localOriginX]/[localOriginY] here.
     */
    public fun derive(
        scratch: PixelBuffer,
        localOriginX: Int,
        localOriginY: Int,
    ): PaintContext {
        return PaintContext(
            buffer = scratch,
            bufferPool = bufferPool,
            globalOriginX = globalX(localOriginX),
            globalOriginY = globalY(localOriginY),
            globalScaleNumeratorX = globalScaleNumeratorX,
            globalScaleDenominatorX = globalScaleDenominatorX,
            globalScaleNumeratorY = globalScaleNumeratorY,
            globalScaleDenominatorY = globalScaleDenominatorY,
        )
    }

    /**
     * Creates a scratch context whose pixels are scaled before they reach this context.
     *
     * This is used by fitted content so render-only anchor links observe the same transform as
     * paint, hit testing, and semantics instead of reporting the unscaled scratch coordinates.
     */
    internal fun deriveScaled(
        scratch: PixelBuffer,
        localOriginX: Int,
        localOriginY: Int,
        scaleNumeratorX: Int,
        scaleDenominatorX: Int,
        scaleNumeratorY: Int = scaleNumeratorX,
        scaleDenominatorY: Int = scaleDenominatorX,
    ): PaintContext {
        /** Positive source denominators keep coordinate mapping total for malformed input. */
        val safeDenominatorX = scaleDenominatorX.coerceAtLeast(1).toLong()
        /** Positive source denominators keep coordinate mapping total for malformed input. */
        val safeDenominatorY = scaleDenominatorY.coerceAtLeast(1).toLong()
        /** Cross-reduced x transform avoids overflow under deeply nested fitted content. */
        val combinedScaleX = combineScale(
            outerNumerator = globalScaleNumeratorX,
            outerDenominator = globalScaleDenominatorX,
            innerNumerator = scaleNumeratorX.coerceAtLeast(0).toLong(),
            innerDenominator = safeDenominatorX,
        )
        /** Cross-reduced y transform avoids overflow under deeply nested fitted content. */
        val combinedScaleY = combineScale(
            outerNumerator = globalScaleNumeratorY,
            outerDenominator = globalScaleDenominatorY,
            innerNumerator = scaleNumeratorY.coerceAtLeast(0).toLong(),
            innerDenominator = safeDenominatorY,
        )
        return PaintContext(
            buffer = scratch,
            bufferPool = bufferPool,
            globalOriginX = globalX(localOriginX),
            globalOriginY = globalY(localOriginY),
            globalScaleNumeratorX = combinedScaleX.first,
            globalScaleDenominatorX = combinedScaleX.second,
            globalScaleNumeratorY = combinedScaleY.first,
            globalScaleDenominatorY = combinedScaleY.second,
        )
    }

    /** 更新 `PipelinePrimitives` 的 `setColor` 状态，并保持相关边界与派生状态一致。 */
    public fun setColor(
        x: Int,
        y: Int,
        color: PixelColor,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        buffer.setPixel(x, y, color, blendMode)
    }

    /** 按裁剪和混合规则把 `fillRect` 应用到 `PipelinePrimitives` 像素数据。 */
    public fun fillRect(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        color: PixelColor,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        buffer.fillRect(x, y, w, h, color, blendMode)
    }

    /** 把 `PipelinePrimitives` 按当前样式和裁剪边界执行 `drawRect` 像素绘制。 */
    public fun drawRect(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        color: PixelColor,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        buffer.drawRect(x, y, w, h, color, blendMode)
    }
}

/** Maps a signed local coordinate through one positive rational scale with Int saturation. */
private fun scaledCoordinate(coordinate: Int, numerator: Long, denominator: Long): Int {
    val safeDenominator = denominator.coerceAtLeast(1L)
    val scaled = coordinate.toDouble() * numerator.coerceAtLeast(0L).toDouble() / safeDenominator.toDouble()
    return scaled.coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt()
}

/** Maps a non-negative local extent through one rational scale while retaining non-empty boxes. */
private fun scaledExtent(extent: Int, numerator: Long, denominator: Long): Int {
    if (extent <= 0 || numerator <= 0L) return 0
    val scaled = extent.toDouble() * numerator.toDouble() / denominator.coerceAtLeast(1L).toDouble()
    return scaled.coerceIn(1.0, Int.MAX_VALUE.toDouble()).toInt()
}

/** Cross-reduces two positive rational transforms before multiplying their factors. */
private fun combineScale(
    outerNumerator: Long,
    outerDenominator: Long,
    innerNumerator: Long,
    innerDenominator: Long,
): Pair<Long, Long> {
    if (outerNumerator <= 0L || innerNumerator <= 0L) return 0L to 1L
    /** Cancels the outer numerator against the inner denominator before multiplication. */
    val firstDivisor = positiveGreatestCommonDivisor(outerNumerator, innerDenominator)
    /** Cancels the inner numerator against the outer denominator before multiplication. */
    val secondDivisor = positiveGreatestCommonDivisor(innerNumerator, outerDenominator)
    /** Reduced numerator factor originating from the outer paint transform. */
    val reducedOuterNumerator = outerNumerator / firstDivisor
    /** Reduced numerator factor originating from the newly derived transform. */
    val reducedInnerNumerator = innerNumerator / secondDivisor
    /** Reduced denominator factor originating from the outer paint transform. */
    val reducedOuterDenominator = outerDenominator.coerceAtLeast(1L) / secondDivisor
    /** Reduced denominator factor originating from the newly derived transform. */
    val reducedInnerDenominator = innerDenominator.coerceAtLeast(1L) / firstDivisor
    return saturatingMultiply(reducedOuterNumerator, reducedInnerNumerator) to
        saturatingMultiply(reducedOuterDenominator, reducedInnerDenominator).coerceAtLeast(1L)
}

/** Returns the positive greatest common divisor used to normalize rational paint transforms. */
private fun positiveGreatestCommonDivisor(first: Long, second: Long): Long {
    var left = first.coerceAtLeast(1L)
    var right = second.coerceAtLeast(1L)
    while (right != 0L) {
        val remainder = left % right
        left = right
        right = remainder
    }
    return left
}

/** Multiplies positive transform factors without overflowing the retained coordinate model. */
private fun saturatingMultiply(first: Long, second: Long): Long {
    if (first <= 0L || second <= 0L) return 0L
    return if (first > Long.MAX_VALUE / second) Long.MAX_VALUE else first * second
}

/**
 * 新渲染管线里的命中测试结果。
 */
public data class HitTestResult(
    val hits: MutableList<RenderObject> = mutableListOf(),
) {
    /**
     * 追加一个命中节点。
     */
    public fun add(target: RenderObject) {
        hits += target
    }
}
