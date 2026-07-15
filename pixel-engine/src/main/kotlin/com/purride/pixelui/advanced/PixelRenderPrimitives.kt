package com.purride.pixelui.advanced

import com.purride.pixelcore.PixelBlendMode
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelcore.PixelColor

/**
 * 定义 `PixelRenderSize` 在 `PixelRenderPrimitives` 中承担的数据与行为边界。
 *
 * Immutable size produced by a [PixelRenderBox] after layout.
 *
 * @property width Width in logical pixels.
 * @property height Height in logical pixels.
 */
public data class PixelRenderSize(
    public val width: Int,
    public val height: Int,
) {
    /** 集中提供 `PixelRenderPrimitives` 的 `<companion>` 共享入口。
 *
 * Shared zero-size value used before the first layout pass.
 */
    public companion object {
        /** 公开 `PixelRenderPrimitives` 的 `Zero` 配置或运行值。
 *
 * A render size whose width and height are both zero.
 */
        public val Zero: PixelRenderSize = PixelRenderSize(width = 0, height = 0)
    }
}

/**
 * 定义 `PixelRenderConstraints` 在 `PixelRenderPrimitives` 中承担的数据与行为边界。
 *
 * Box constraints passed from a parent render object to a child.
 *
 * @property minWidth Smallest permitted width in logical pixels.
 * @property maxWidth Largest permitted width in logical pixels.
 * @property minHeight Smallest permitted height in logical pixels.
 * @property maxHeight Largest permitted height in logical pixels.
 */
public data class PixelRenderConstraints(
    public val minWidth: Int = 0,
    public val maxWidth: Int,
    public val minHeight: Int = 0,
    public val maxHeight: Int,
) {
    init {
        require(minWidth >= 0) { "minWidth must not be negative." }
        require(minHeight >= 0) { "minHeight must not be negative." }
        require(minWidth <= maxWidth) { "minWidth must not exceed maxWidth." }
        require(minHeight <= maxHeight) { "minHeight must not exceed maxHeight." }
    }

    /** 执行 `PixelRenderPrimitives` 的 `constrainWidth` 公开行为；具体参数、返回和副作用见下文。
 *
 * Constrains [width] to the permitted horizontal range.
 */
    public fun constrainWidth(width: Int): Int = width.coerceIn(minWidth, maxWidth)

    /** 执行 `PixelRenderPrimitives` 的 `constrainHeight` 公开行为；具体参数、返回和副作用见下文。
 *
 * Constrains [height] to the permitted vertical range.
 */
    public fun constrainHeight(height: Int): Int = height.coerceIn(minHeight, maxHeight)

    /**
 * 执行 `PixelRenderPrimitives` 的 `inset` 公开行为；具体参数、返回和副作用见下文。
 *
     * Removes four-sided padding from this constraint while preserving non-negative bounds.
     */
    public fun inset(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ): PixelRenderConstraints {
        /** Total non-negative horizontal space reserved by the parent. */
        val horizontalInset = (left + right).coerceAtLeast(0)
        /** Total non-negative vertical space reserved by the parent. */
        val verticalInset = (top + bottom).coerceAtLeast(0)
        return PixelRenderConstraints(
            minWidth = (minWidth - horizontalInset).coerceAtLeast(0),
            maxWidth = (maxWidth - horizontalInset).coerceAtLeast(0),
            minHeight = (minHeight - verticalInset).coerceAtLeast(0),
            maxHeight = (maxHeight - verticalInset).coerceAtLeast(0),
        )
    }
}

/**
 * 定义 `PixelPaintContext` 在 `PixelRenderPrimitives` 中承担的数据与行为边界。
 *
 * Stable drawing surface passed to [PixelRenderBox.paint].
 *
 * @property buffer Destination pixel buffer for the current frame.
 * @property bufferPool Shared scratch-buffer pool. Direct use is experimental because pool ownership
 * may be replaced by a narrower capability in a future release.
 */
public class PixelPaintContext(
    public val buffer: PixelBuffer,
    @PixelExperimentalApi
    public val bufferPool: PixelBufferPool = PixelBufferPool(),
) {
    /** 更新 `PixelRenderPrimitives` 的 `setColor` 状态并保持派生数据一致。
 *
 * Writes one color into the destination buffer using [blendMode].
 */
    public fun setColor(
        x: Int,
        y: Int,
        color: PixelColor,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        buffer.setPixel(x, y, color, blendMode)
    }

    /** 执行 `PixelRenderPrimitives` 的 `fillRect` 公开行为；具体参数、返回和副作用见下文。
 *
 * Fills a rectangular destination region using [blendMode].
 */
    public fun fillRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: PixelColor,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        buffer.fillRect(x, y, width, height, color, blendMode)
    }

    /** 执行 `PixelRenderPrimitives` 的 `drawRect` 渲染或命中阶段。
 *
 * Draws a one-pixel rectangular outline using [blendMode].
 */
    public fun drawRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: PixelColor,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        buffer.drawRect(x, y, width, height, color, blendMode)
    }
}

/**
 * 表示 `PixelRenderPrimitives` 的 `PixelHitTestResult` 稳定结果或事件分支。
 *
 * Ordered collection of advanced render objects hit at one logical coordinate.
 *
 * @property hits Mutable hit path populated from the deepest target toward its ancestors.
 */
@PixelExperimentalApi
public data class PixelHitTestResult(
    public val hits: MutableList<PixelRenderObject> = mutableListOf(),
) {
    /** 向 `PixelRenderPrimitives` 注册 `add` 内容并绑定对应生命周期。
 *
 * Appends [target] to the hit path.
 */
    public fun add(target: PixelRenderObject) {
        hits += target
    }
}
