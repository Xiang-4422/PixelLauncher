package com.purride.pixelui.internal

import com.purride.pixelui.FlexFit
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.Widget

/**
 * `Expanded / Flexible` 的 direct render object widget。
 */
internal data class FlexWrapperWidget(
    override val key: Any? = null,
    override val child: Widget,
    val flex: Int,
    val fit: FlexFit,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    /**
     * 创建带 flex parent data 的透明 render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderFlexChild(
            flex = flex.coerceAtLeast(1),
            fit = fit,
        )
    }

    /**
     * 同步新的 flex parent data。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderFlexChild).updateFlexData(
            flex = flex.coerceAtLeast(1),
            fit = fit,
        )
    }
}
