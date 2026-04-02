package com.purride.pixelui

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState

/**
 * 当前阶段的最小布局对齐方式。
 */
enum class PixelAlignment {
    TOP_START,
    TOP_CENTER,
    TOP_END,
    CENTER_START,
    CENTER,
    CENTER_END,
    BOTTOM_START,
    BOTTOM_CENTER,
    BOTTOM_END,
}

/**
 * `Row/Column` 当前阶段只支持交叉轴对齐。
 *
 * 主轴排布还没有进入这一轮实现，所以这里只先提供最常用的
 * `START / CENTER / END` 三档，先把组合布局的基础语义补稳。
 */
enum class PixelCrossAxisAlignment {
    START,
    CENTER,
    END,
    STRETCH,
}

/**
 * `Row/Column` 的主轴排布方式。
 *
 * 当前先支持最基础的起始、居中、末尾三种排布，先把常见布局场景补齐。
 */
enum class PixelMainAxisAlignment {
    START,
    CENTER,
    END,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY,
}

enum class PixelMainAxisSize {
    MIN,
    MAX,
}

data class PixelTextNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val text: String,
    val style: PixelTextStyle = PixelTextStyle.Default,
    val styleLocked: Boolean = false,
    val softWrap: Boolean = false,
    val maxLines: Int = 1,
    val overflow: PixelTextOverflow = PixelTextOverflow.CLIP,
    val textAlign: PixelTextAlign = PixelTextAlign.START,
) : PixelNode

enum class PixelTextOverflow {
    CLIP,
    ELLIPSIS,
}

enum class PixelTextAlign {
    START,
    CENTER,
    END,
}

data class PixelSurfaceNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val child: PixelNode? = null,
    val fillTone: PixelTone = PixelTone.OFF,
    val borderTone: PixelTone? = PixelTone.ON,
    val padding: Int = 2,
    val alignment: PixelAlignment = PixelAlignment.CENTER,
    val styleLocked: Boolean = false,
) : PixelNode

data class PixelBoxNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val children: List<PixelNode>,
    val alignment: PixelAlignment = PixelAlignment.TOP_START,
) : PixelNode

data class PixelPositionedNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val child: PixelNode,
    val left: Int? = null,
    val top: Int? = null,
    val right: Int? = null,
    val bottom: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
) : PixelNode

data class PixelRowNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val children: List<PixelNode>,
    val spacing: Int = 0,
    val mainAxisSize: PixelMainAxisSize = PixelMainAxisSize.MIN,
    val mainAxisAlignment: PixelMainAxisAlignment = PixelMainAxisAlignment.START,
    val crossAxisAlignment: PixelCrossAxisAlignment = PixelCrossAxisAlignment.START,
) : PixelNode

data class PixelColumnNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val children: List<PixelNode>,
    val spacing: Int = 0,
    val mainAxisSize: PixelMainAxisSize = PixelMainAxisSize.MIN,
    val mainAxisAlignment: PixelMainAxisAlignment = PixelMainAxisAlignment.START,
    val crossAxisAlignment: PixelCrossAxisAlignment = PixelCrossAxisAlignment.START,
) : PixelNode

data class PixelPagerNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val axis: PixelAxis,
    val state: PixelPagerState,
    val controller: PixelPagerController,
    val pages: List<PixelNode>,
    val onPageChanged: ((Int) -> Unit)? = null,
) : PixelNode

fun PixelText(
    text: String,
    modifier: PixelModifier = PixelModifier.Empty,
    style: PixelTextStyle = PixelTextStyle.Default,
    softWrap: Boolean = false,
    maxLines: Int = 1,
    overflow: PixelTextOverflow = PixelTextOverflow.CLIP,
    textAlign: PixelTextAlign = PixelTextAlign.START,
    key: Any? = null,
): PixelNode {
    return PixelTextNode(
        key = key,
        modifier = modifier,
        text = text,
        style = style,
        softWrap = softWrap,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
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

fun PixelPositioned(
    child: PixelNode,
    modifier: PixelModifier = PixelModifier.Empty,
    left: Int? = null,
    top: Int? = null,
    right: Int? = null,
    bottom: Int? = null,
    width: Int? = null,
    height: Int? = null,
    key: Any? = null,
): PixelNode {
    return PixelPositionedNode(
        key = key,
        modifier = modifier,
        child = child,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        width = width,
        height = height,
    )
}

fun PixelRow(
    children: List<PixelNode>,
    modifier: PixelModifier = PixelModifier.Empty,
    spacing: Int = 0,
    mainAxisSize: PixelMainAxisSize = PixelMainAxisSize.MIN,
    mainAxisAlignment: PixelMainAxisAlignment = PixelMainAxisAlignment.START,
    crossAxisAlignment: PixelCrossAxisAlignment = PixelCrossAxisAlignment.START,
    key: Any? = null,
): PixelNode {
    return PixelRowNode(
        key = key,
        modifier = modifier,
        children = children,
        spacing = spacing,
        mainAxisSize = mainAxisSize,
        mainAxisAlignment = mainAxisAlignment,
        crossAxisAlignment = crossAxisAlignment,
    )
}

fun PixelColumn(
    children: List<PixelNode>,
    modifier: PixelModifier = PixelModifier.Empty,
    spacing: Int = 0,
    mainAxisSize: PixelMainAxisSize = PixelMainAxisSize.MIN,
    mainAxisAlignment: PixelMainAxisAlignment = PixelMainAxisAlignment.START,
    crossAxisAlignment: PixelCrossAxisAlignment = PixelCrossAxisAlignment.START,
    key: Any? = null,
): PixelNode {
    return PixelColumnNode(
        key = key,
        modifier = modifier,
        children = children,
        spacing = spacing,
        mainAxisSize = mainAxisSize,
        mainAxisAlignment = mainAxisAlignment,
        crossAxisAlignment = crossAxisAlignment,
    )
}

fun PixelPager(
    axis: PixelAxis,
    state: PixelPagerState,
    controller: PixelPagerController,
    pages: List<PixelNode>,
    modifier: PixelModifier = PixelModifier.Empty,
    onPageChanged: ((Int) -> Unit)? = null,
    key: Any? = null,
): PixelNode {
    return PixelPagerNode(
        key = key,
        modifier = modifier,
        axis = axis,
        state = state,
        controller = controller,
        pages = pages,
        onPageChanged = onPageChanged,
    )
}
