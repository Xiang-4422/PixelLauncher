package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
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
     * 重复挂载同一个 root 应该保持 owner/root 关系，不重复 detach/attach。
     */
    @Test
    fun attachRootSkipsWhenRootIsUnchanged() {
        val root = CountingRenderBox()
        val owner = PipelineOwner(root = root)

        owner.attachRoot(root)

        assertEquals(1, root.attachCount)
        assertEquals(0, root.detachCount)
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
     * 替换 root 时应该 detach 旧树并 attach 新树，后续 render 只驱动新 root。
     */
    @Test
    fun attachRootReplacementDetachesOldRootAndRendersNewRoot() {
        val oldRoot = CountingRenderBox()
        val newRoot = CountingRenderBox()
        val owner = PipelineOwner(root = oldRoot)

        owner.attachRoot(newRoot)
        owner.render(logicalWidth = 8, logicalHeight = 6)

        assertEquals(1, oldRoot.detachCount)
        assertEquals(1, newRoot.attachCount)
        assertEquals(0, oldRoot.layoutCount)
        assertEquals(1, newRoot.layoutCount)
        assertEquals(1, newRoot.paintCount)
    }

    /**
     * 未标脏时重复 render 既不重复 layout，也不重复 paint：
     * PipelineOwner 直接返回上一帧缓存的 PixelRenderResult。
     */
    @Test
    fun renderSkipsLayoutUntilRenderObjectMarksLayoutDirty() {
        val root = CountingRenderBox()
        val owner = PipelineOwner(root = root)

        owner.render(logicalWidth = 8, logicalHeight = 6)
        owner.render(logicalWidth = 8, logicalHeight = 6)

        assertEquals(1, root.layoutCount)
        assertEquals(1, root.paintCount)

        root.requestLayout()
        owner.render(logicalWidth = 8, logicalHeight = 6)

        assertEquals(2, root.layoutCount)
        assertEquals(2, root.paintCount)
    }

    /** A changed logical viewport invalidates layout even when no RenderObject marked itself dirty. */
    @Test
    fun renderRelayoutsWhenLogicalViewportChanges() {
        /** Root whose counters expose the owner's cache and invalidation decisions. */
        val root = CountingRenderBox()
        /** Pipeline owner retained across the simulated window resize. */
        val owner = PipelineOwner(root = root)

        owner.render(logicalWidth = 8, logicalHeight = 6)
        owner.render(logicalWidth = 5, logicalHeight = 4)

        assertEquals(2, root.layoutCount)
        assertEquals(2, root.paintCount)
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
     * 已挂载 parent 移除 child 时应该 detach child，并触发 parent 下一次 layout。
     */
    @Test
    fun droppedChildAfterAttachDetachesChildAndMarksParentDirty() {
        val child = CountingRenderBox()
        val root = CountingSingleChildRenderBox(child)
        val owner = PipelineOwner(root = root)
        owner.render(logicalWidth = 8, logicalHeight = 6)

        root.setRenderObjectChild(null)
        owner.render(logicalWidth = 8, logicalHeight = 6)

        assertEquals(1, child.detachCount)
        assertNull(child.parent)
        assertEquals(2, root.layoutCount)
    }

    /**
     * 只标记 paint dirty 时不应该重复 layout，但每次 render 都应该重新导出 targets。
     */
    @Test
    fun paintDirtyDoesNotForceLayoutAndTargetsStayConsistent() {
        val root = CountingRenderBox(clickable = true)
        val owner = PipelineOwner(root = root)

        val first = owner.render(logicalWidth = 8, logicalHeight = 6)
        root.requestPaint()
        val second = owner.render(logicalWidth = 8, logicalHeight = 6)

        assertEquals(1, root.layoutCount)
        assertEquals(2, root.paintCount)
        assertEquals(1, first.clickTargets.size)
        assertEquals(1, second.clickTargets.size)
        assertEquals(first.clickTargets.single().bounds, second.clickTargets.single().bounds)
    }

    /**
     * diagnostics 应该导出当前 render subtree 的层级、子节点数和布局尺寸。
     */
    @Test
    fun collectDiagnosticsReturnsRenderTreeSnapshot() {
        val child = CountingRenderBox()
        val root = CountingSingleChildRenderBox(child)
        val owner = PipelineOwner(root = root)

        owner.render(logicalWidth = 8, logicalHeight = 6)
        val diagnostics = owner.collectDiagnostics()

        assertEquals(2, diagnostics.size)
        assertEquals("CountingSingleChildRenderBox", diagnostics[0].name)
        assertEquals(0, diagnostics[0].depth)
        assertEquals(1, diagnostics[0].childCount)
        assertEquals(RenderSize(width = 4, height = 3), diagnostics[0].size)
        assertEquals("CountingRenderBox", diagnostics[1].name)
        assertEquals(1, diagnostics[1].depth)
        assertEquals(0, diagnostics[1].childCount)
        assertEquals(RenderSize(width = 2, height = 2), diagnostics[1].size)
    }

    /**
     * pipeline diagnostics 应该记录 layout/paint 次数和最近一次 target 导出数量。
     */
    @Test
    fun collectPipelineDiagnosticsReturnsRenderCountersAndTargets() {
        val root = CountingRenderBox(clickable = true)
        val owner = PipelineOwner(root = root)

        val beforeRender = owner.collectPipelineDiagnostics()
        val first = owner.render(logicalWidth = 8, logicalHeight = 6)
        val afterFirstRender = owner.collectPipelineDiagnostics()
        root.requestPaint()
        owner.render(logicalWidth = 8, logicalHeight = 6)
        val afterSecondRender = owner.collectPipelineDiagnostics()

        assertEquals(true, beforeRender.hasRoot)
        assertEquals(true, beforeRender.needsLayout)
        assertEquals(0, beforeRender.renderCount)
        assertEquals(1, first.clickTargets.size)
        assertEquals(false, afterFirstRender.needsLayout)
        assertEquals(false, afterFirstRender.needsPaint)
        assertEquals(1, afterFirstRender.renderCount)
        assertEquals(1, afterFirstRender.layoutPassCount)
        assertEquals(1, afterFirstRender.paintPassCount)
        assertEquals(1, afterFirstRender.targetDiagnostics.clickTargets)
        assertEquals(2, afterSecondRender.renderCount)
        assertEquals(1, afterSecondRender.layoutPassCount)
        assertEquals(2, afterSecondRender.paintPassCount)
    }

    /** 纯文本光标显隐变化只能增加 paint 次数，不能增加 layout 次数。 */
    @Test
    fun textInputCursorVisibilityMarksPaintWithoutLayout() {
        /** 可直接验证光标 dirty 分类的 retained surface。 */
        val root = RenderSurface(alignment = PixelAlignment.TOP_START)
        /** 记录 owner 级 layout 与 paint pass 的测试管线。 */
        val owner = PipelineOwner(root = root)
        owner.render(logicalWidth = 8, logicalHeight = 6)
        /** 首帧完成后的稳定 pass 计数。 */
        val beforeCursorChange = owner.collectPipelineDiagnostics()

        root.updateTextInputCursorVisibility(visible = false)
        /** 重绘前应当只有 paint 被标脏。 */
        val dirtyAfterCursorChange = owner.collectPipelineDiagnostics()
        owner.render(logicalWidth = 8, logicalHeight = 6)
        /** 光标帧完成后的最终 pass 计数。 */
        val afterCursorChange = owner.collectPipelineDiagnostics()

        assertEquals(false, dirtyAfterCursorChange.needsLayout)
        assertEquals(true, dirtyAfterCursorChange.needsPaint)
        assertEquals(beforeCursorChange.layoutPassCount, afterCursorChange.layoutPassCount)
        assertEquals(beforeCursorChange.paintPassCount + 1, afterCursorChange.paintPassCount)
    }

    /**
     * terminal dispose 应该清除 cached targets，并递归 detach owner 持有的 render tree。
     */
    @Test
    fun disposeDetachesRenderTreeAndClearsTargetDiagnostics() {
        val child = CountingRenderBox(clickable = true)
        val root = CountingSingleChildRenderBox(child)
        val owner = PipelineOwner(root = root)
        owner.render(logicalWidth = 8, logicalHeight = 6)

        owner.dispose()

        val diagnostics = owner.collectPipelineDiagnostics()
        assertEquals(false, diagnostics.hasRoot)
        assertEquals(0, diagnostics.targetDiagnostics.clickTargets)
        assertEquals(1, root.detachCount)
        assertEquals(1, child.detachCount)
        assertNull(child.parent)
        assertEquals(emptyList<RenderDiagnosticsNode>(), owner.collectDiagnostics())
    }

    /** Emits layout, paint, dirty-node, pixel, and cache evidence from the real pipeline path. */
    @Test
    fun renderEmitsFramePhaseAndWorkloadDiagnostics() {
        /** Two-node retained render tree used to verify subtree workload counting. */
        val root = CountingSingleChildRenderBox(CountingRenderBox())
        /** Owner under test, retained across a dirty frame and a complete cache-hit frame. */
        val owner = PipelineOwner(root = root)
        /** Sink receiving the first frame's real pipeline phase callbacks. */
        val firstFrame = RecordingFramePhaseSink()

        owner.render(logicalWidth = 8, logicalHeight = 6, framePhaseSink = firstFrame)

        assertEquals(1, firstFrame.layoutBeginCount)
        assertEquals(1, firstFrame.layoutEndCount)
        assertEquals(1, firstFrame.paintBeginCount)
        assertEquals(1, firstFrame.paintEndCount)
        assertEquals(2, firstFrame.dirtyRenderNodeCount)
        assertEquals(48L, firstFrame.paintedPixelCount)
        assertEquals(false, firstFrame.renderCacheHit)

        /** Independent sink proving an unchanged second render is attributed as a cache hit. */
        val cachedFrame = RecordingFramePhaseSink()
        owner.render(logicalWidth = 8, logicalHeight = 6, framePhaseSink = cachedFrame)

        assertEquals(0, cachedFrame.layoutBeginCount)
        assertEquals(0, cachedFrame.paintBeginCount)
        assertEquals(0, cachedFrame.dirtyRenderNodeCount)
        assertEquals(0L, cachedFrame.paintedPixelCount)
        assertEquals(true, cachedFrame.renderCacheHit)
    }

    /** Test sink retaining primitive callback counts without measuring wall-clock time. */
    private class RecordingFramePhaseSink : PixelFramePhaseSink {
        /** Number of retained build segments opened by the tested layer. */
        var buildBeginCount: Int = 0

        /** Number of retained build segments closed by the tested layer. */
        var buildEndCount: Int = 0

        /** Number of rebuilt Elements reported by the tested layer. */
        var dirtyElementCount: Int = 0

        /** Number of layout segments opened by the pipeline. */
        var layoutBeginCount: Int = 0

        /** Number of layout segments closed by the pipeline. */
        var layoutEndCount: Int = 0

        /** Number of engine-paint segments opened by the pipeline. */
        var paintBeginCount: Int = 0

        /** Number of engine-paint segments closed by the pipeline. */
        var paintEndCount: Int = 0

        /** Dirty RenderObjects participating in the most recent pipeline result. */
        var dirtyRenderNodeCount: Int = 0

        /** Logical pixels repainted by the most recent pipeline result. */
        var paintedPixelCount: Long = 0L

        /** Whether the most recent pipeline result reused its complete cache. */
        var renderCacheHit: Boolean = false

        /** PixelBuffer pool hits reported by a surrounding runtime. */
        var bufferHitCount: Long = 0L

        /** PixelBuffer pool misses reported by a surrounding runtime. */
        var bufferMissCount: Long = 0L

        /** Records one opened retained build phase. */
        override fun beginBuild() {
            buildBeginCount += 1
        }

        /** Records one closed retained build phase. */
        override fun endBuild() {
            buildEndCount += 1
        }

        /** Accumulates rebuilt Element work. */
        override fun recordBuildWork(dirtyElementCount: Int) {
            this.dirtyElementCount += dirtyElementCount
        }

        /** Records one opened layout phase. */
        override fun beginLayout() {
            layoutBeginCount += 1
        }

        /** Records one closed layout phase. */
        override fun endLayout() {
            layoutEndCount += 1
        }

        /** Records one opened logical paint phase. */
        override fun beginPaint() {
            paintBeginCount += 1
        }

        /** Records one closed logical paint phase. */
        override fun endPaint() {
            paintEndCount += 1
        }

        /** Retains the pipeline workload emitted by the tested render call. */
        override fun recordPipelineWork(
            dirtyRenderNodeCount: Int,
            paintedPixelCount: Long,
            renderCacheHit: Boolean,
        ) {
            this.dirtyRenderNodeCount += dirtyRenderNodeCount
            this.paintedPixelCount += paintedPixelCount
            this.renderCacheHit = this.renderCacheHit || renderCacheHit
        }

        /** Retains buffer-pool deltas when a surrounding runtime emits them. */
        override fun recordBufferPoolActivity(hitCount: Long, missCount: Long) {
            bufferHitCount += hitCount
            bufferMissCount += missCount
        }
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
    private class CountingRenderBox(
        private val clickable: Boolean = false,
    ) : RenderBox() {
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

        /** 对外暴露 paint 脏标记入口。 */
        fun requestPaint() {
            markNeedsPaint()
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
                color = PixelColor.White,
            )
        }

        override fun collectClickTargets(
            offsetX: Int,
            offsetY: Int,
            targets: MutableList<PixelClickTarget>,
        ) {
            if (!clickable) {
                return
            }
            targets += PixelClickTarget(
                bounds = PixelRect(
                    left = offsetX,
                    top = offsetY,
                    width = size.width,
                    height = size.height,
                ),
                onClick = { },
            )
        }
    }

}
