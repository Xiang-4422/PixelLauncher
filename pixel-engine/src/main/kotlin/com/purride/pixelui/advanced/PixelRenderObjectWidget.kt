package com.purride.pixelui.advanced

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget

/**
 * 定义 `PixelRenderObjectWidget` 在 `PixelRenderObjectWidget` 中承担的数据与行为边界。
 *
 * Stable immutable widget configuration that creates one [PixelRenderObject].
 *
 * The retained Element and adapter lifecycle are deliberately created by the internal inflater, so
 * consumers only depend on this public Widget-to-RenderObject contract.
 *
 * @property key Optional identity used by the retained reconciliation algorithm.
 */
public abstract class PixelRenderObjectWidget(
    public override val key: Any? = null,
) : Widget {
    /** 创建或解析 `PixelRenderObjectWidget` 的 `createRenderObject` 结果，并在返回前校验输入。
 *
 * Creates the retained render object for the first mounted instance of this widget.
 */
    public abstract fun createRenderObject(context: BuildContext): PixelRenderObject

    /** 更新 `PixelRenderObjectWidget` 的 `updateRenderObject` 状态并保持派生数据一致。
 *
 * Synchronizes a rebuilt widget configuration into an existing [renderObject].
 */
    public open fun updateRenderObject(
        context: BuildContext,
        renderObject: PixelRenderObject,
    ): Unit = Unit
}

/**
 * 定义 `PixelLeafRenderObjectWidget` 在 `PixelRenderObjectWidget` 中承担的数据与行为边界。
 *
 * Stable specialization for a consumer-defined render object with no Widget children.
 *
 * @property key Optional identity used by retained reconciliation.
 */
public abstract class PixelLeafRenderObjectWidget(
    key: Any? = null,
) : PixelRenderObjectWidget(key = key)

/**
 * 定义 `PixelSingleChildRenderObjectWidget` 在 `PixelRenderObjectWidget` 中承担的数据与行为边界。
 *
 * Experimental widget specialization with one retained Widget child.
 *
 * @property child Current child Widget, or null when the render object is empty.
 * @property key Optional identity used by retained reconciliation.
 */
@PixelExperimentalApi
public abstract class PixelSingleChildRenderObjectWidget(
    public open val child: Widget?,
    key: Any? = null,
) : PixelRenderObjectWidget(key = key)

/**
 * 定义 `PixelMultiChildRenderObjectWidget` 在 `PixelRenderObjectWidget` 中承担的数据与行为边界。
 *
 * Experimental widget specialization with an ordered list of retained Widget children.
 *
 * @property children Current ordered Widget children.
 * @property key Optional identity used by retained reconciliation.
 */
@PixelExperimentalApi
public abstract class PixelMultiChildRenderObjectWidget(
    public open val children: List<Widget>,
    key: Any? = null,
) : PixelRenderObjectWidget(key = key)
