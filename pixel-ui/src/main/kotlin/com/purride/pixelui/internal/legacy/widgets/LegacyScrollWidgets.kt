package com.purride.pixelui.internal

import com.purride.pixelui.Axis
import com.purride.pixelui.BuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelList
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelPager
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollView
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState

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

internal data class ListViewWidget(
    val items: List<Widget>,
    val state: PixelListState,
    val controller: PixelListController,
    val spacing: Int,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
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

internal data class SingleChildScrollViewWidget(
    val child: Widget,
    val state: PixelListState,
    val controller: PixelListController,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
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
