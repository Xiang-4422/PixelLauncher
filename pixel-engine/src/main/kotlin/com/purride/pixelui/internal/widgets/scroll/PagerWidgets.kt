package com.purride.pixelui.internal

import com.purride.pixelui.Axis
import com.purride.pixelui.BuildContext
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState

/**
 * Flutter 风格 `PageView` 的 direct pipeline widget。
 */
internal data class PageViewWidget(
    val axis: Axis,
    val controller: PixelPagerController,
    val state: PixelPagerState,
    val pages: List<Widget>,
    val onPageChanged: ((Int) -> Unit)?,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时监听 controller，并返回 direct pager viewport。
     */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return PagerViewportWidget(
            children = pages,
            axis = axis,
            state = state,
            controller = controller,
            onPageChanged = onPageChanged,
            key = key,
        )
    }
}

/**
 * `PageView` 对应的多子节点 render object widget。
 */
private data class PagerViewportWidget(
    override val children: List<Widget>,
    val axis: Axis,
    val state: PixelPagerState,
    val controller: PixelPagerController,
    val onPageChanged: ((Int) -> Unit)?,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(
    children = children,
    key = key,
) {
    /**
     * 创建分页视口 render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderPagerViewport(
            axis = axis,
            state = state,
            controller = controller,
            onPageChanged = onPageChanged,
        )
    }

    /**
     * 同步分页视口配置。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderPagerViewport).updatePagerViewport(
            axis = axis,
            state = state,
            controller = controller,
            onPageChanged = onPageChanged,
        )
    }
}
