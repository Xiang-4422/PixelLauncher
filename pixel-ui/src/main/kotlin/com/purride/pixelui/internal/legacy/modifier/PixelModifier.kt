package com.purride.pixelui.internal.legacy

/**
 * legacy 包里的修饰器兼容别名。
 */
internal typealias PixelModifier = com.purride.pixelui.internal.PixelModifier

/**
 * legacy 包里的修饰器元素兼容别名。
 */
internal typealias PixelModifierElement = com.purride.pixelui.internal.PixelModifierElement

/**
 * legacy 包里的 padding 元素兼容别名。
 */
internal typealias PixelPaddingElement = com.purride.pixelui.internal.PixelPaddingElement

/**
 * legacy 包里的尺寸元素兼容别名。
 */
internal typealias PixelSizeElement = com.purride.pixelui.internal.PixelSizeElement

/**
 * legacy 包里的最大宽度填充元素兼容别名。
 */
internal typealias PixelFillMaxWidthElement = com.purride.pixelui.internal.PixelFillMaxWidthElement

/**
 * legacy 包里的最大高度填充元素兼容别名。
 */
internal typealias PixelFillMaxHeightElement = com.purride.pixelui.internal.PixelFillMaxHeightElement

/**
 * legacy 包里的点击元素兼容别名。
 */
internal typealias PixelClickableElement = com.purride.pixelui.internal.PixelClickableElement

/**
 * legacy 包里的权重元素兼容别名。
 */
internal typealias PixelWeightElement = com.purride.pixelui.internal.PixelWeightElement

/**
 * legacy 包里的 flex fit 兼容别名。
 */
internal typealias PixelFlexFit = com.purride.pixelui.internal.PixelFlexFit

/**
 * legacy padding 兼容入口。
 */
internal fun PixelModifier.padding(all: Int): PixelModifier {
    return then(PixelPaddingElement(left = all, top = all, right = all, bottom = all))
}

/**
 * legacy 水平/垂直 padding 兼容入口。
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
 * legacy 四边 padding 兼容入口。
 */
internal fun PixelModifier.padding(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
): PixelModifier {
    return then(PixelPaddingElement(left = left, top = top, right = right, bottom = bottom))
}

/**
 * legacy 固定宽高兼容入口。
 */
internal fun PixelModifier.size(width: Int, height: Int): PixelModifier {
    return then(PixelSizeElement(width = width, height = height))
}

/**
 * legacy 固定宽度兼容入口。
 */
internal fun PixelModifier.width(width: Int): PixelModifier {
    return then(PixelSizeElement(width = width))
}

/**
 * legacy 固定高度兼容入口。
 */
internal fun PixelModifier.height(height: Int): PixelModifier {
    return then(PixelSizeElement(height = height))
}

/**
 * legacy 最大宽度填充兼容入口。
 */
internal fun PixelModifier.fillMaxWidth(): PixelModifier {
    return then(PixelFillMaxWidthElement())
}

/**
 * legacy 最大高度填充兼容入口。
 */
internal fun PixelModifier.fillMaxHeight(): PixelModifier {
    return then(PixelFillMaxHeightElement())
}

/**
 * legacy 最大尺寸填充兼容入口。
 */
internal fun PixelModifier.fillMaxSize(): PixelModifier {
    return fillMaxWidth().fillMaxHeight()
}

/**
 * legacy 点击兼容入口。
 */
internal fun PixelModifier.clickable(onClick: () -> Unit): PixelModifier {
    return then(PixelClickableElement(onClick))
}

/**
 * legacy 权重兼容入口。
 */
internal fun PixelModifier.weight(
    weight: Float,
    fit: PixelFlexFit = PixelFlexFit.TIGHT,
): PixelModifier {
    return then(PixelWeightElement(weight = weight.coerceAtLeast(0f), fit = fit))
}
