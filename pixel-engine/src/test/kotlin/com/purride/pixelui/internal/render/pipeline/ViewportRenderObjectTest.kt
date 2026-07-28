package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelPagerController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 直接锁定四类 viewport RenderObject 的布局与目标导出契约。 */
class ViewportRenderObjectTest {
    /** 单子节点滚动视口应同步内容高度并导出自身滚动目标。 */
    @Test
    fun singleChildViewportSynchronizesContentAndExportsListTarget() {
        /** 可滚动内容节点。 */
        val child = FixedViewportTestBox(width = 4, height = 9)
        /** 当前列表控制器及状态。 */
        val controller = PixelListController()
        /** 从零偏移开始的列表状态。 */
        val state = controller.create()
        /** 被测单子节点视口。 */
        val viewport = RenderSingleChildScrollViewport(child, state, controller)

        viewport.layout(RenderConstraints(maxWidth = 4, maxHeight = 3))
        /** 当前视口导出的列表目标。 */
        val targets = mutableListOf<PixelListTarget>()
        viewport.collectListTargets(offsetX = 2, offsetY = 5, targets = targets)

        assertEquals(RenderSize(4, 3), viewport.size)
        assertEquals(9, state.contentHeightPx)
        assertEquals(PixelRect(2, 5, 4, 3), targets.first().bounds)
        assertSame(viewport, targets.first().source)
    }

    /** Grid 视口应按列数放置子节点并同步完整内容高度。 */
    @Test
    fun gridViewportLaysOutCellsAndExportsVisibleTargets() {
        /** 两个固定网格单元。 */
        val children = listOf(
            FixedViewportTestBox(width = 2, height = 2),
            FixedViewportTestBox(width = 2, height = 2),
        )
        /** 当前网格列表控制器及状态。 */
        val controller = PixelListController()
        /** 当前网格列表状态。 */
        val state = controller.create()
        /** 被测网格视口。 */
        val viewport = RenderGridViewport(
            children = children,
            firstItemIndex = 0,
            itemCount = 2,
            cellWidth = 2,
            cellHeight = 2,
            state = state,
            controller = controller,
        )

        viewport.layout(RenderConstraints(maxWidth = 4, maxHeight = 2))
        /** 视口裁剪后导出的点击目标。 */
        val targets = mutableListOf<PixelClickTarget>()
        viewport.collectClickTargets(offsetX = 0, offsetY = 0, targets = targets)

        assertEquals(2, state.contentHeightPx)
        assertEquals(listOf(0, 2), targets.map { target -> target.bounds.left })
        assertTrue(targets.all { target -> target.bounds.height == 2 })
    }

    /** Pager 视口应把每页约束为完整视口并导出分页控制目标。 */
    @Test
    fun pagerViewportConstrainsPagesAndExportsPagerTarget() {
        /** 两个分页子节点。 */
        val children = listOf(
            FixedViewportTestBox(width = 1, height = 1),
            FixedViewportTestBox(width = 1, height = 1),
        )
        /** 当前分页控制器及状态。 */
        val controller = PixelPagerController()
        /** 两页水平分页状态。 */
        val state = controller.create(axis = PixelAxis.HORIZONTAL, pageCount = 2)
        /** 被测分页视口。 */
        val viewport = RenderPagerViewport(
            children = children,
            axis = PixelAxis.HORIZONTAL,
            state = state,
            controller = controller,
            onPageChanged = null,
            onPageDragStart = null,
        )

        viewport.layout(RenderConstraints(maxWidth = 6, maxHeight = 4))
        /** 当前分页目标。 */
        val targets = mutableListOf<PixelPagerTarget>()
        viewport.collectPagerTargets(offsetX = 3, offsetY = 7, targets = targets)

        assertTrue(children.all { child -> child.size == RenderSize(6, 4) })
        assertEquals(PixelRect(3, 7, 6, 4), targets.first().bounds)
        assertSame(viewport, targets.first().source)
    }

    /** CustomScroll 视口应使用 metadata 计算内容高度并导出自身列表目标。 */
    @Test
    fun customScrollViewportUsesMetadataForContentExtent() {
        /** 自定义滚动内容节点。 */
        val child = FixedViewportTestBox(width = 5, height = 3)
        /** 当前列表控制器及状态。 */
        val controller = PixelListController()
        /** 当前自定义滚动状态。 */
        val state = controller.create()
        /** 与唯一子节点对齐的静态 metadata。 */
        val metadata = listOf(
            CustomScrollChildEntry(
                sliverIndex = 0,
                itemIndex = 0,
                pinned = false,
                spacingAfter = 2,
                minExtent = null,
                maxExtent = null,
                contentTop = 4,
                contentEnd = 7,
                measuredItemCount = null,
                estimatedExtent = null,
            ),
        )
        /** 被测自定义滚动视口。 */
        val viewport = RenderCustomScrollViewport(
            children = listOf(child),
            metadata = metadata,
            state = state,
            controller = controller,
        )

        viewport.layout(RenderConstraints(maxWidth = 5, maxHeight = 4))
        /** 当前视口导出的列表目标。 */
        val targets = mutableListOf<PixelListTarget>()
        viewport.collectListTargets(offsetX = 1, offsetY = 2, targets = targets)

        assertEquals(9, state.contentHeightPx)
        assertEquals(PixelRect(1, 2, 5, 4), targets.first().bounds)
        assertSame(viewport, targets.first().source)
    }

    /** 为 viewport 测试提供固定尺寸、点击目标和命中行为。 */
    private class FixedViewportTestBox(
        /** 无约束时希望占用的宽度。 */
        private val width: Int,
        /** 无约束时希望占用的高度。 */
        private val height: Int,
    ) : RenderBox() {
        /** 在父约束范围内采用固定测试尺寸。 */
        override fun layout(constraints: RenderConstraints) {
            size = RenderSize(
                width = constraints.constrainWidth(width),
                height = constraints.constrainHeight(height),
            )
        }

        /** 本测试只验证布局与目标，不需要写入像素。 */
        override fun paint(context: PaintContext, offsetX: Int, offsetY: Int): Unit = Unit

        /** 导出覆盖完整节点尺寸的点击目标。 */
        override fun collectClickTargets(
            offsetX: Int,
            offsetY: Int,
            targets: MutableList<PixelClickTarget>,
        ) {
            targets += PixelClickTarget(
                bounds = PixelRect(offsetX, offsetY, size.width, size.height),
                onClick = { },
                source = this,
            )
        }
    }
}
