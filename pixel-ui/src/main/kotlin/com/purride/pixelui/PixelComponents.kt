package com.purride.pixelui

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState

/**
 * 当前阶段的最小布局对齐方式。
 */
enum class PixelAlignment {
    TOP_START,
    CENTER,
}

data class PixelTextNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val text: String,
    val tone: PixelTone = PixelTone.ON,
    val textRasterizer: PixelTextRasterizer? = null,
) : PixelNode

data class PixelSurfaceNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val child: PixelNode? = null,
    val fillTone: PixelTone = PixelTone.OFF,
    val borderTone: PixelTone? = PixelTone.ON,
    val padding: Int = 2,
    val alignment: PixelAlignment = PixelAlignment.CENTER,
) : PixelNode

data class PixelBoxNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val children: List<PixelNode>,
    val alignment: PixelAlignment = PixelAlignment.TOP_START,
) : PixelNode

data class PixelRowNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val children: List<PixelNode>,
    val spacing: Int = 0,
) : PixelNode

data class PixelColumnNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val children: List<PixelNode>,
    val spacing: Int = 0,
) : PixelNode

data class PixelPagerNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val axis: PixelAxis,
    val state: PixelPagerState,
    val controller: PixelPagerController,
    val pages: List<PixelNode>,
) : PixelNode

fun PixelText(
    text: String,
    modifier: PixelModifier = PixelModifier.Empty,
    tone: PixelTone = PixelTone.ON,
    textRasterizer: PixelTextRasterizer? = null,
    key: Any? = null,
): PixelNode {
    return PixelTextNode(
        key = key,
        modifier = modifier,
        text = text,
        tone = tone,
        textRasterizer = textRasterizer,
    )
}

fun PixelSurface(
    child: PixelNode? = null,
    modifier: PixelModifier = PixelModifier.Empty,
    fillTone: PixelTone = PixelTone.OFF,
    borderTone: PixelTone? = PixelTone.ON,
    padding: Int = 2,
    alignment: PixelAlignment = PixelAlignment.CENTER,
    key: Any? = null,
): PixelNode {
    return PixelSurfaceNode(
        key = key,
        modifier = modifier,
        child = child,
        fillTone = fillTone,
        borderTone = borderTone,
        padding = padding,
        alignment = alignment,
    )
}

fun PixelBox(
    children: List<PixelNode>,
    modifier: PixelModifier = PixelModifier.Empty,
    alignment: PixelAlignment = PixelAlignment.TOP_START,
    key: Any? = null,
): PixelNode {
    return PixelBoxNode(
        key = key,
        modifier = modifier,
        children = children,
        alignment = alignment,
    )
}

fun PixelRow(
    children: List<PixelNode>,
    modifier: PixelModifier = PixelModifier.Empty,
    spacing: Int = 0,
    key: Any? = null,
): PixelNode {
    return PixelRowNode(
        key = key,
        modifier = modifier,
        children = children,
        spacing = spacing,
    )
}

fun PixelColumn(
    children: List<PixelNode>,
    modifier: PixelModifier = PixelModifier.Empty,
    spacing: Int = 0,
    key: Any? = null,
): PixelNode {
    return PixelColumnNode(
        key = key,
        modifier = modifier,
        children = children,
        spacing = spacing,
    )
}

fun PixelPager(
    axis: PixelAxis,
    state: PixelPagerState,
    controller: PixelPagerController,
    pages: List<PixelNode>,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): PixelNode {
    return PixelPagerNode(
        key = key,
        modifier = modifier,
        axis = axis,
        state = state,
        controller = controller,
        pages = pages,
    )
}
