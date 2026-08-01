package com.purride.pixeldesign.font

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.internal.withHostTextScale
import kotlin.math.min

/**
 * 在保留字体家族字形的前提下，将栅格器等比缩小到指定逻辑边界。
 *
 * 该入口只会缩小字体，不会放大；因此宿主可以在紧凑控件中安全复用用户字体，
 * 同时避免原生大字号越过按键或安全提示区域。
 */
public fun PixelTextRasterizer.fitProductTextWithin(
    /** 用于计算水平边界的代表文本；null 表示不限制宽度。 */
    sampleText: String? = null,
    /** 代表文本允许占用的最大逻辑宽度；与 [sampleText] 必须同时提供。 */
    maxWidth: Int? = null,
    /** 字体单行允许占用的最大逻辑高度；null 表示不限制高度。 */
    maxHeight: Int? = null,
): PixelTextRasterizer {
    require((sampleText == null) == (maxWidth == null)) {
        "sampleText 与 maxWidth 必须同时提供"
    }
    require(maxWidth == null || maxWidth > 0) { "maxWidth 必须大于 0" }
    require(maxHeight == null || maxHeight > 0) { "maxHeight 必须大于 0" }
    /** 水平约束对应的最大缩放比例。 */
    val widthScale = if (sampleText != null && maxWidth != null) {
        maxWidth.toFloat() / measureText(sampleText).coerceAtLeast(1)
    } else {
        1f
    }
    /** 垂直约束对应的最大缩放比例。 */
    val heightScale = if (maxHeight != null) {
        maxHeight.toFloat() / measureHeight("Mg").coerceAtLeast(1)
    } else {
        1f
    }
    /** 最终比例禁止超过原始尺寸。 */
    val scale = min(1f, min(widthScale, heightScale))
    return if (scale >= 1f) this else withHostTextScale(scale)
}
