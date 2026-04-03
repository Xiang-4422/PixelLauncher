package com.purride.pixelui.internal

import com.purride.pixelui.Axis
import com.purride.pixelui.BuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelPager
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState

/**
 * Flutter 风格 `PageView` 的 legacy bridge widget。
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
     * 在 build 时监听 controller，并桥接到 legacy pager 节点。
     */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return LegacyMultiChildWidget(
            key = key,
            children = pages,
        ) { _, childNodes ->
            PixelPager(
                axis = axis,
                state = state,
                controller = controller,
                pages = childNodes,
                modifier = PixelModifier.Empty,
                onPageChanged = onPageChanged,
                key = key,
            )
        }
    }
}
