package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntOffset

/** 定义 `OpacityWidget` 在 `EffectWidgets` 中承担的数据与行为边界。
 *
 * Retained widget for the public opacity paint/hit-test/semantics contract.
 */
public data class OpacityWidget(
    override val child: Widget,
    val opacity: Float,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    /** Creates a render object that owns normalization and zero-opacity behavior. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderOpacity(opacity = opacity)
    }

    /** Retargets opacity without replacing the render object or retained child subtree. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderOpacity).updateOpacity(opacity)
    }
}

/**
 * 定义 `VisualOnlyWidget` 在 `EffectWidgets` 中承担的数据与行为边界。
 *
 * Retained wrapper that paints and lays out [child] while suppressing every interaction channel.
 *
 * Motion exit phases use this wrapper after logical dismissal so stale controls remain visible
 * only for the remainder of their paint transition.
 */
public data class VisualOnlyWidget(
    override val child: Widget,
    /** Whether descendant interaction and semantics channels are currently suppressed. */
    val visualOnly: Boolean = true,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    /** Creates the render boundary that deliberately exports no targets or semantics. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderVisualOnly(visualOnly = visualOnly)
    }

    /** Toggles the interaction gate without replacing the retained child subtree. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderVisualOnly).updateVisualOnly(visualOnly)
    }
}

/** 定义 `ClipRectWidget` 在 `EffectWidgets` 中承担的数据或执行职责，并保持公开不变量稳定。 */
public data class ClipRectWidget(
    override val child: Widget,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderClipRect()
    }
}

/** 定义 `TransformTranslateWidget` 在 `EffectWidgets` 中承担的数据或执行职责，并保持公开不变量稳定。 */
public data class TransformTranslateWidget(
    override val child: Widget,
    val offset: IntOffset,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderTranslate(dx = offset.x, dy = offset.y)
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderTranslate).updateOffset(dx = offset.x, dy = offset.y)
    }
}
