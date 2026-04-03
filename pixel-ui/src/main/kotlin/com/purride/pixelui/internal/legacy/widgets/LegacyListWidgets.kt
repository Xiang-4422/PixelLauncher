package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelList
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

/**
 * Flutter 风格 `ListView` 的 legacy bridge widget。
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
     * 在 build 时监听 controller，并桥接到 legacy list 节点。
     */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return LegacyMultiChildWidget(
            key = key,
            children = items,
        ) { _, childNodes ->
            PixelList(
                items = childNodes,
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty,
                spacing = spacing,
                key = key,
            )
        }
    }
}
