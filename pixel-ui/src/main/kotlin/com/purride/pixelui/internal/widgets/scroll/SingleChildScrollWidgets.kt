package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

/**
 * Flutter 风格 `SingleChildScrollView` 的 direct pipeline widget。
 */
internal data class SingleChildScrollViewWidget(
    val child: Widget,
    val state: PixelListState,
    val controller: PixelListController,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时监听 controller，并返回 direct single child viewport。
     */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return SingleChildScrollViewportWidget(
            child = child,
            state = state,
            controller = controller,
            key = key,
        )
    }
}

/**
 * `SingleChildScrollView` 对应的单子节点 render object widget。
 */
private data class SingleChildScrollViewportWidget(
    override val child: Widget,
    val state: PixelListState,
    val controller: PixelListController,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    /**
     * 创建单子节点滚动视口 render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderSingleChildScrollViewport(
            state = state,
            controller = controller,
        )
    }

    /**
     * 同步单子节点滚动视口配置。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderSingleChildScrollViewport).updateScrollViewport(
            state = state,
            controller = controller,
        )
    }
}
