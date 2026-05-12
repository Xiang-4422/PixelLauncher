package com.purride.pixelui.internal

/**
 * 通用像素组件修饰器。
 *
 * 当前阶段保留尺寸、填充、点击、权重等最小布局语义，供 direct pipeline widget 复用。
 */
internal data class PixelModifier(
    val elements: List<PixelModifierElement> = emptyList(),
) {
    /**
     * 追加单个修饰器元素。
     */
    fun then(element: PixelModifierElement): PixelModifier = copy(elements = elements + element)

    /**
     * 追加另一组修饰器。
     */
    fun then(other: PixelModifier): PixelModifier = copy(elements = elements + other.elements)

    companion object {
        /**
         * 空修饰器。
         */
        val Empty = PixelModifier()
    }
}

/**
 * 修饰器元素标记接口。
 */
internal interface PixelModifierElement

/**
 * padding 修饰器元素。
 */
internal data class PixelPaddingElement(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) : PixelModifierElement

/**
 * 固定尺寸修饰器元素。
 */
internal data class PixelSizeElement(
    val width: Int? = null,
    val height: Int? = null,
) : PixelModifierElement

/**
 * 最大宽度填充修饰器元素。
 */
internal data class PixelFillMaxWidthElement(
    val enabled: Boolean = true,
) : PixelModifierElement

/**
 * 最大高度填充修饰器元素。
 */
internal data class PixelFillMaxHeightElement(
    val enabled: Boolean = true,
) : PixelModifierElement

/**
 * 点击修饰器元素。
 */
internal data class PixelClickableElement(
    val onClick: () -> Unit,
) : PixelModifierElement

/**
 * flex 权重修饰器元素。
 */
internal data class PixelWeightElement(
    val weight: Float,
    val fit: PixelFlexFit = PixelFlexFit.TIGHT,
) : PixelModifierElement

/**
 * 追加四边相同 padding。
 */
internal fun PixelModifier.padding(all: Int): PixelModifier {
    return then(PixelPaddingElement(left = all, top = all, right = all, bottom = all))
}

/**
 * 追加水平/垂直 padding。
 */
internal fun PixelModifier.padding(
    horizontal: Int = 0,
    vertical: Int = 0,
): PixelModifier {
    return then(
        PixelPaddingElement(
            left = horizontal,
            top = vertical,
            right = horizontal,
            bottom = vertical,
        ),
    )
}

/**
 * 追加独立四边 padding。
 */
internal fun PixelModifier.padding(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
): PixelModifier {
    return then(
        PixelPaddingElement(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        ),
    )
}

/**
 * 追加固定宽高。
 */
internal fun PixelModifier.size(width: Int, height: Int): PixelModifier {
    return then(PixelSizeElement(width = width, height = height))
}

/**
 * 追加固定宽度。
 */
internal fun PixelModifier.width(width: Int): PixelModifier {
    return then(PixelSizeElement(width = width))
}

/**
 * 追加固定高度。
 */
internal fun PixelModifier.height(height: Int): PixelModifier {
    return then(PixelSizeElement(height = height))
}

/**
 * 追加最大宽度填充。
 */
internal fun PixelModifier.fillMaxWidth(): PixelModifier {
    return then(PixelFillMaxWidthElement())
}

/**
 * 追加最大高度填充。
 */
internal fun PixelModifier.fillMaxHeight(): PixelModifier {
    return then(PixelFillMaxHeightElement())
}

/**
 * 追加最大宽高填充。
 */
internal fun PixelModifier.fillMaxSize(): PixelModifier {
    return fillMaxWidth().fillMaxHeight()
}

/**
 * 追加点击回调。
 */
internal fun PixelModifier.clickable(onClick: () -> Unit): PixelModifier {
    return then(PixelClickableElement(onClick))
}

/**
 * 追加 flex 权重。
 */
internal fun PixelModifier.weight(
    weight: Float,
    fit: PixelFlexFit = PixelFlexFit.TIGHT,
): PixelModifier {
    return then(
        PixelWeightElement(
            weight = weight.coerceAtLeast(0f),
            fit = fit,
        ),
    )
}
