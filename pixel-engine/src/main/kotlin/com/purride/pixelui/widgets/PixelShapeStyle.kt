package com.purride.pixelui

import com.purride.pixelcore.PixelBlendMode
import com.purride.pixelcore.PixelColor

/**
 * Shared style for primitive shape widgets and canvas drawing helpers.
 *
 * [filled] is used by closed shapes such as [Circle] and [Polygon]. [strokeWidth]
 * applies to line/outline/path drawing and is clamped by callers to integer
 * pixel strokes.
 */
public data class PixelShapeStyle(
    public val color: PixelColor,
    public val filled: Boolean = true,
    public val strokeWidth: Int = 1,
    public val blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
) {
    init {
        require(strokeWidth > 0) { "strokeWidth must be > 0 but was $strokeWidth" }
    }
}
