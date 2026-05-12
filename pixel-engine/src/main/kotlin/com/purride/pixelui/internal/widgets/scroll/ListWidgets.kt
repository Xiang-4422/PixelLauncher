package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

/**
 * Flutter 风格 `ListView` 的 direct pipeline widget。
 */
internal data class ListViewWidget(
    val items: List<Widget>,
    val state: PixelListState,
    val controller: PixelListController,
    val spacing: Int,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时监听 controller，并返回 direct list viewport。
     */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return ListViewportWidget(
            children = items,
            state = state,
            controller = controller,
            spacing = spacing,
            key = key,
        )
    }
}

/**
 * `ListView` 对应的多子节点 render object widget。
 */
private data class ListViewportWidget(
    override val children: List<Widget>,
    val state: PixelListState,
    val controller: PixelListController,
    val spacing: Int,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(
    children = children,
    key = key,
) {
    /**
     * 创建垂直列表视口 render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderListViewport(
            state = state,
            controller = controller,
            spacing = spacing,
        )
    }

    /**
     * 同步列表视口配置。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderListViewport).updateListViewport(
            state = state,
            controller = controller,
            spacing = spacing,
        )
    }
}
