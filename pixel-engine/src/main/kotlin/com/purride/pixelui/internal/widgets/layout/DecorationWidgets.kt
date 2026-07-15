package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.toPixelAlignment

/**
 * Flutter 风格 `GestureDetector` 的直接 render object widget。
 */
internal data class GestureDetectorWidget(
    override val child: Widget,
    val onTap: () -> Unit,
    /** Optional captured press-state observer used by retained standard controls. */
    val onPressedChanged: ((Boolean) -> Unit)? = null,
    /** Optional mouse or stylus hover observer used by retained standard controls. */
    val onHoveredChanged: ((Boolean) -> Unit)? = null,
    val onLongPress: (() -> Unit)?,
    val onDoubleTap: (() -> Unit)?,
    val onSwipeStart: (() -> Unit)?,
    val onSwipeUpdate: ((Int) -> Unit)?,
    val onSwipeEnd: ((Int) -> Unit)?,
    val onSwipeLeft: (() -> Unit)?,
    val onSwipeRight: (() -> Unit)?,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSurface(
            alignment = PixelAlignment.TOP_START,
            onClick = onTap,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
            onLongPress = onLongPress,
            onDoubleTap = onDoubleTap,
            onSwipeStart = onSwipeStart,
            onSwipeUpdate = onSwipeUpdate,
            onSwipeEnd = onSwipeEnd,
            onSwipeLeft = onSwipeLeft,
            onSwipeRight = onSwipeRight,
            preserveChildMinConstraints = true,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSurface).updateSurface(
            alignment = PixelAlignment.TOP_START,
            onClick = onTap,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
            onLongPress = onLongPress,
            onDoubleTap = onDoubleTap,
            onSwipeStart = onSwipeStart,
            onSwipeUpdate = onSwipeUpdate,
            onSwipeEnd = onSwipeEnd,
            onSwipeLeft = onSwipeLeft,
            onSwipeRight = onSwipeRight,
            preserveChildMinConstraints = true,
        )
    }
}

/**
 * 额外暴露按压与悬停微状态的 SDK 内部手势检测器。
 *
 * 公开的 [com.purride.pixelui.GestureDetector] 签名保持稳定；标准控件需要交互视觉反馈时使用
 * 这个 retained widget。该类型仅用于 Pixel SDK 兄弟 artifact 之间互操作。
 */
@PixelArtifactInternalApi
public data class InteractionDetector(
    /** 被手势检测器包裹的可视子组件。 */
    public override val child: Widget,
    /** 在一次完整且未发生拖动的指针序列结束后执行的点击动作。 */
    public val onTap: () -> Unit,
    /** 按下时接收 true；抬起、取消、接管、暂停或分离时接收 false。 */
    public val onPressedChanged: ((Boolean) -> Unit)? = null,
    /** 接收鼠标或触控笔进入、离开悬停区域的状态变化。 */
    public val onHoveredChanged: ((Boolean) -> Unit)? = null,
    /** retained 元素与渲染对象复用的稳定标识。 */
    public override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    /** 创建把点击和交互回调合并为单一命中目标的渲染表面。 */
    public override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSurface(
            alignment = PixelAlignment.TOP_START,
            onClick = onTap,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
            preserveChildMinConstraints = true,
        )
    }

    /** 在不替换 retained 渲染对象及其目标标识的前提下刷新回调。 */
    public override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSurface).updateSurface(
            alignment = PixelAlignment.TOP_START,
            onClick = onTap,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
            preserveChildMinConstraints = true,
        )
    }
}

/**
 * Flutter 风格 `DecoratedBox` 的直接 render object widget。
 */
internal data class DecoratedBoxWidget(
    override val child: Widget?,
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    val padding: Int,
    val alignment: Alignment,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSurface(
            fillColor = fillColor,
            borderColor = borderColor,
            alignment = alignment.toPixelAlignment(),
            fillMaxWidth = child == null,
            fillMaxHeight = child == null,
            contentPaddingLeft = padding,
            contentPaddingTop = padding,
            contentPaddingRight = padding,
            contentPaddingBottom = padding,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSurface).updateSurface(
            fillColor = fillColor,
            borderColor = borderColor,
            alignment = alignment.toPixelAlignment(),
            fillMaxWidth = child == null,
            fillMaxHeight = child == null,
            contentPaddingLeft = padding,
            contentPaddingTop = padding,
            contentPaddingRight = padding,
            contentPaddingBottom = padding,
        )
    }
}
