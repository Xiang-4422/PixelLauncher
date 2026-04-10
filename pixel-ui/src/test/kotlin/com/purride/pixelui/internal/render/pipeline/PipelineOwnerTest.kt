package com.purride.pixelui.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * `PipelineOwner` 与基础 render object 生命周期的回归测试。
 */
class PipelineOwnerTest {
    /**
     * owner 挂载 root 时应该递归 attach 整棵 render object 子树。
     */
    @Test
    fun attachRootAttachesWholeRenderTree() {
        val child = CountingRenderBox()
        val root = CountingSingleChildRenderBox(child)

        PipelineOwner(root = root)

        assertEquals(1, root.attachCount)
        assertEquals(1, child.attachCount)
        assertSame(root, child.parent)
    }

    /**
     * owner 替换为空 root 时应该递归 detach 旧 render object 子树。
     */
    @Test
    fun attachRootNullDetachesPreviousRenderTree() {
        val child = CountingRenderBox()
        val root = CountingSingleChildRenderBox(child)
        val owner = PipelineOwner(root = root)

        owner.attachRoot(null)

        assertEquals(1, root.detachCount)
        assertEquals(1, child.detachCount)
        assertNull(child.parent)
    }

    /**
     * 未标脏时重复 render 不应该重复 layout，但仍会绘制到新的输出 buffer。
     */
    @Test
    fun renderSkipsLayoutUntilRenderObjectMarksLayoutDirty() {
        val root = CountingRenderBox()
        val owner = PipelineOwner(root = root)

        owner.render(logicalWidth = 8, logicalHeight = 6)
        owner.render(logicalWidth = 8, logicalHeight = 6)

        assertEquals(1, root.layoutCount)
        assertEquals(2, root.paintCount)

        root.requestLayout()
        owner.render(logicalWidth = 8, logicalHeight = 6)

        assertEquals(2, root.layoutCount)
        assertEquals(3, root.paintCount)
    }

    /**
     * 已挂载 parent 后新增 child 应该自动 attach child，并触发下一次 layout。
     */
    @Test
    fun adoptedChildAfterAttachReceivesOwnerAndMarksParentDirty() {
        val root = CountingSingleChildRenderBox()
        val owner = PipelineOwner(root = root)
        owner.render(logicalWidth = 8, logicalHeight = 6)
        val child = CountingRenderBox()

        root.setRenderObjectChild(child)
        owner.render(logicalWidth = 8, logicalHeight = 6)

        assertEquals(1, child.attachCount)
        assertSame(root, child.parent)
        assertEquals(2, root.layoutCount)
        assertEquals(1, child.layoutCount)
    }

    /**
     * 测试用的可计数单 child render box。
     */
    private class CountingSingleChildRenderBox(
        child: RenderBox? = null,
    ) : SingleChildRenderObject() {
        var attachCount = 0
            private set
        var detachCount = 0
            private set
        var layoutCount = 0
            private set
        var paintCount = 0
            private set

        init {
            setRenderObjectChild(child)
        }

        /**
         * 记录 attach 次数。
         */
        override fun onAttach() {
            attachCount += 1
        }

        /**
         * 记录 detach 次数。
         */
        override fun onDetach() {
            detachCount += 1
        }

        /**
         * 记录 layout 次数，并布局唯一子节点。
         */
        override fun layout(constraints: RenderConstraints) {
            layoutCount += 1
            size = RenderSize(
                width = constraints.constrainWidth(4),
                height = constraints.constrainHeight(3),
            )
            (child as? RenderBox)?.layout(constraints)
        }

        /**
         * 记录 paint 次数，并透传绘制唯一子节点。
         */
        override fun paint(
            context: PaintContext,
            offsetX: Int,
            offsetY: Int,
        ) {
            paintCount += 1
            (child as? RenderBox)?.paint(
                context = context,
                offsetX = offsetX,
                offsetY = offsetY,
            )
        }
    }

    /**
     * 测试用的可计数叶子 render box。
     */
    private class CountingRenderBox : RenderBox() {
        var attachCount = 0
            private set
        var detachCount = 0
            private set
        var layoutCount = 0
            private set
        var paintCount = 0
            private set

        /**
         * 对外暴露 layout 脏标记入口。
         */
        fun requestLayout() {
            markNeedsLayout()
        }

        /**
         * 记录 attach 次数。
         */
        override fun onAttach() {
            attachCount += 1
        }

        /**
         * 记录 detach 次数。
         */
        override fun onDetach() {
            detachCount += 1
        }

        /**
         * 记录 layout 次数。
         */
        override fun layout(constraints: RenderConstraints) {
            layoutCount += 1
            size = RenderSize(
                width = constraints.constrainWidth(2),
                height = constraints.constrainHeight(2),
            )
        }

        /**
         * 记录 paint 次数。
         */
        override fun paint(
            context: PaintContext,
            offsetX: Int,
            offsetY: Int,
        ) {
            paintCount += 1
            context.buffer.setPixel(
                x = offsetX,
                y = offsetY,
                value = 1,
            )
        }
    }

}
