package com.purride.pixelui

/**
 * 通用像素组件修饰器。
 *
 * 设计方向参考 Compose 的 Modifier，但当前阶段只做最小可运行能力：
 * 尺寸、填充、点击、权重。
 */
internal data class PixelModifier(
    val elements: List<PixelModifierElement> = emptyList(),
) {
    fun then(element: PixelModifierElement): PixelModifier = copy(elements = elements + element)

    fun then(other: PixelModifier): PixelModifier = copy(elements = elements + other.elements)

    companion object {
        val Empty = PixelModifier()
    }
}

/**
 * 修饰器元素标记接口。
 *
 * 后续尺寸、间距、点击、焦点、滚动等能力都会拆成独立 element，
 * 运行时再基于 element 类型做布局和事件能力注入。
 */
internal interface PixelModifierElement

internal data class PixelPaddingElement(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) : PixelModifierElement

internal data class PixelSizeElement(
    val width: Int? = null,
    val height: Int? = null,
) : PixelModifierElement

internal data class PixelFillMaxWidthElement(
    val enabled: Boolean = true,
) : PixelModifierElement

internal data class PixelFillMaxHeightElement(
    val enabled: Boolean = true,
) : PixelModifierElement

internal data class PixelClickableElement(
    val onClick: () -> Unit,
) : PixelModifierElement

internal data class PixelWeightElement(
    val weight: Float,
    val fit: PixelFlexFit = PixelFlexFit.TIGHT,
) : PixelModifierElement

internal enum class PixelFlexFit {
    TIGHT,
    LOOSE,
}

internal fun PixelModifier.padding(all: Int): PixelModifier {
    return then(PixelPaddingElement(left = all, top = all, right = all, bottom = all))
}

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

internal fun PixelModifier.size(width: Int, height: Int): PixelModifier {
    return then(PixelSizeElement(width = width, height = height))
}

internal fun PixelModifier.width(width: Int): PixelModifier {
    return then(PixelSizeElement(width = width))
}

internal fun PixelModifier.height(height: Int): PixelModifier {
    return then(PixelSizeElement(height = height))
}

internal fun PixelModifier.fillMaxWidth(): PixelModifier {
    return then(PixelFillMaxWidthElement())
}

internal fun PixelModifier.fillMaxHeight(): PixelModifier {
    return then(PixelFillMaxHeightElement())
}

internal fun PixelModifier.fillMaxSize(): PixelModifier {
    return fillMaxWidth().fillMaxHeight()
}

internal fun PixelModifier.clickable(onClick: () -> Unit): PixelModifier {
    return then(PixelClickableElement(onClick))
}

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
