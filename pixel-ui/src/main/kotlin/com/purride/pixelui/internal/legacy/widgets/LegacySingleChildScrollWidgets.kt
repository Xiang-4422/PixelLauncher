package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollView
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

/**
 * Flutter 风格 `SingleChildScrollView` 的 legacy bridge widget。
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
     * 在 build 时监听 controller，并桥接到 legacy single child scroll 节点。
     */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return LegacySingleChildWidget(
            key = key,
            child = child,
        ) { _, childNode ->
            PixelSingleChildScrollView(
                child = childNode,
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty,
                key = key,
            )
        }
    }
}
