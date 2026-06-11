package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelSliver
import com.purride.pixelui.PixelSliverAppBar
import com.purride.pixelui.PixelSliverList
import com.purride.pixelui.PixelSliverPinnedHeader
import com.purride.pixelui.SizedBox
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

internal data class CustomScrollViewWidget(
    val slivers: List<PixelSliver>,
    val state: PixelListState,
    val controller: PixelListController,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val entries = buildList {
            slivers.forEachIndexed { sliverIndex, sliver ->
                when (sliver) {
                    is PixelSliverList -> sliver.items.forEachIndexed { itemIndex, item ->
                        add(
                            CustomScrollChildEntry(
                                sliverIndex = sliverIndex,
                                itemIndex = itemIndex,
                                pinned = false,
                                spacingAfter = if (itemIndex < sliver.items.lastIndex) sliver.spacing.coerceAtLeast(0) else 0,
                                minExtent = null,
                                maxExtent = null,
                            ),
                        )
                        add(item)
                    }
                    is PixelSliverPinnedHeader -> {
                        add(
                            CustomScrollChildEntry(
                                sliverIndex = sliverIndex,
                                itemIndex = 0,
                                pinned = true,
                                spacingAfter = 0,
                                minExtent = null,
                                maxExtent = null,
                            ),
                        )
                        add(sliver.child)
                    }
                    is PixelSliverAppBar -> {
                        val expandedHeight = sliver.expandedHeight.coerceAtLeast(0)
                        val collapsedHeight = sliver.collapsedHeight.coerceIn(0, expandedHeight.coerceAtLeast(0))
                        add(
                            CustomScrollChildEntry(
                                sliverIndex = sliverIndex,
                                itemIndex = 0,
                                pinned = true,
                                spacingAfter = 0,
                                minExtent = collapsedHeight,
                                maxExtent = expandedHeight,
                            ),
                        )
                        add(SizedBox(height = expandedHeight, child = sliver.child, key = sliver.key))
                    }
                }
            }
        }
        val metadata = entries.filterIsInstance<CustomScrollChildEntry>()
        val children = entries.filterIsInstance<Widget>()
        return CustomScrollViewportWidget(
            children = children,
            metadata = metadata,
            state = state,
            controller = controller,
            key = key,
        )
    }
}

internal data class CustomScrollChildEntry(
    val sliverIndex: Int,
    val itemIndex: Int,
    val pinned: Boolean,
    val spacingAfter: Int,
    val minExtent: Int?,
    val maxExtent: Int?,
)

private data class CustomScrollViewportWidget(
    override val children: List<Widget>,
    val metadata: List<CustomScrollChildEntry>,
    val state: PixelListState,
    val controller: PixelListController,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(children = children, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderCustomScrollViewport(
            metadata = metadata,
            state = state,
            controller = controller,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderCustomScrollViewport).updateCustomScrollViewport(
            metadata = metadata,
            state = state,
            controller = controller,
        )
    }
}
