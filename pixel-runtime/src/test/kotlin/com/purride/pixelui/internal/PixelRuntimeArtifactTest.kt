package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 `pixel-runtime` 在不依赖 widgets 或 Android Host 时可独立构建和渲染。 */
public class PixelRuntimeArtifactTest {
    /** 纯 runtime RenderObject 应完成布局、绘制并产生确定像素。 */
    @Test
    public fun runtimeRendersOwnedLeafWithoutWidgetArtifact(): Unit {
        /** 本测试独占的 retained runtime。 */
        val runtime = PixelUiRuntime()
        try {
            /** 只由 runtime/core 类型构成的渲染结果。 */
            val result = runtime.render(
                root = SolidRuntimeLeaf(PixelColor.White),
                logicalWidth = 4,
                logicalHeight = 3,
            )

            assertEquals(PixelColor.White, result.buffer.getPixel(0, 0))
            assertEquals(PixelColor.White, result.buffer.getPixel(3, 2))
        } finally {
            runtime.dispose()
        }
    }

    /** 相同 retained 根的第二帧应复用完整 render cache。 */
    @Test
    public fun unchangedRuntimeLeafReusesRenderCache(): Unit {
        /** 本测试独占的 retained runtime。 */
        val runtime = PixelUiRuntime()
        /** 跨两帧保持引用相同的声明式根。 */
        val root = SolidRuntimeLeaf(PixelColor.White)
        try {
            runtime.render(root = root, logicalWidth = 2, logicalHeight = 2)
            /** 第二帧收集 cache 命中证据的 phase sink。 */
            val sink = CacheRecordingSink()
            runtime.render(root = root, logicalWidth = 2, logicalHeight = 2, framePhaseSink = sink)

            assertTrue(sink.renderCacheHit)
            assertEquals(0L, sink.paintedPixelCount)
        } finally {
            runtime.dispose()
        }
    }

    /** runtime 独立测试使用的纯色叶子 Widget。 */
    private data class SolidRuntimeLeaf(
        /** 叶子填充的确定颜色。 */
        val color: PixelColor,
    ) : LeafRenderObjectWidget() {
        /** 创建与本 Widget 一一对应的纯 runtime RenderBox。 */
        override fun createRenderObject(context: BuildContext): RenderObject = SolidRuntimeBox(color)
    }

    /** 将完整约束区域填充为单色的最小 RenderBox。 */
    private class SolidRuntimeBox(
        /** 当前帧绘制颜色。 */
        private val color: PixelColor,
    ) : RenderBox() {
        /** 采用父级允许的最大逻辑尺寸。 */
        override fun layout(constraints: RenderConstraints): Unit {
            size = RenderSize(width = constraints.maxWidth, height = constraints.maxHeight)
        }

        /** 将盒模型覆盖范围写入目标 PixelBuffer。 */
        override fun paint(context: PaintContext, offsetX: Int, offsetY: Int): Unit {
            repeat(size.height) { localY ->
                repeat(size.width) { localX ->
                    context.buffer.setPixel(offsetX + localX, offsetY + localY, color)
                }
            }
        }
    }

    /** 只记录 cache 与绘制工作量的 runtime phase sink。 */
    private class CacheRecordingSink : PixelFramePhaseSink {
        /** 第二帧是否复用了完整渲染结果。 */
        var renderCacheHit: Boolean = false

        /** 第二帧实际重绘的逻辑像素数。 */
        var paintedPixelCount: Long = 0L

        /** 忽略本测试不关心的 build 起点。 */
        override fun beginBuild(): Unit = Unit

        /** 忽略本测试不关心的 build 终点。 */
        override fun endBuild(): Unit = Unit

        /** 忽略本测试不关心的 build 工作量。 */
        override fun recordBuildWork(dirtyElementCount: Int): Unit = Unit

        /** 忽略本测试不关心的 layout 起点。 */
        override fun beginLayout(): Unit = Unit

        /** 忽略本测试不关心的 layout 终点。 */
        override fun endLayout(): Unit = Unit

        /** 忽略本测试不关心的 paint 起点。 */
        override fun beginPaint(): Unit = Unit

        /** 忽略本测试不关心的 paint 终点。 */
        override fun endPaint(): Unit = Unit

        /** 记录一帧 retained pipeline 的工作量与 cache 状态。 */
        override fun recordPipelineWork(
            dirtyRenderNodeCount: Int,
            paintedPixelCount: Long,
            renderCacheHit: Boolean,
        ): Unit {
            this.paintedPixelCount = paintedPixelCount
            this.renderCacheHit = renderCacheHit
        }

        /** 忽略本测试不关心的 buffer pool 活动。 */
        override fun recordBufferPoolActivity(hitCount: Long, missCount: Long): Unit = Unit
    }
}
