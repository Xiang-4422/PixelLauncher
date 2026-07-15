package com.purride.pixelui

import com.purride.pixelcore.PixelBlendMode
import com.purride.pixelcore.PixelColor

/**
 * 定义 `PixelShapeStyle` 在 `PixelShapeStyle` 中承担的数据与行为边界。
 *
 * Shared style for primitive shape widgets and canvas drawing helpers.
 *
 * [filled] is used by closed shapes such as [Circle] and [Polygon]. [strokeWidth]
 * applies to line/outline/path drawing and is clamped by callers to integer
 * pixel strokes.
 */
public data class PixelShapeStyle(
    /** 定义 `PixelShapeStyle` 绘制 `color` 时使用的颜色值。 */
    public val color: PixelColor,
    /** 表示 `PixelShapeStyle` 当前是否满足 `filled` 对应条件。 */
    public val filled: Boolean = true,
    /** 定义 `PixelShapeStyle` 布局中的 `strokeWidth` 逻辑像素度量。 */
    public val strokeWidth: Int = 1,
    /** 保存 `PixelShapeStyle` 当前的 `blendMode` 状态维度。 */
    public val blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
) {
    init {
        require(strokeWidth > 0) { "strokeWidth must be > 0 but was $strokeWidth" }
    }
}
