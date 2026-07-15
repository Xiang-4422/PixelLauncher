package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Container
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies that the retained runtime connects build and buffer metrics to the render pipeline. */
class PixelUiRuntimeFrameDiagnosticsTest {
    /** Captures real build, layout, paint, cache, and PixelBuffer pool activity across two frames. */
    @Test
    fun runtimeEmitsCompletePlatformNeutralFramePhases() {
        /** Runtime retained across a dirty first frame and an unchanged cache-hit frame. */
        val runtime = PixelUiRuntime()
        /** Stable root identity preventing the second frame from manufacturing widget changes. */
        val root = Container(width = 4, height = 3, fillColor = PixelColor.White)
        try {
            /** Sink receiving phase and workload evidence from the first complete render. */
            val firstFrame = RuntimeRecordingFramePhaseSink()
            runtime.render(root = root, logicalWidth = 8, logicalHeight = 6, framePhaseSink = firstFrame)

            assertEquals(1, firstFrame.buildBeginCount)
            assertEquals(1, firstFrame.buildEndCount)
            assertTrue(firstFrame.dirtyElementCount > 0)
            assertEquals(1, firstFrame.layoutBeginCount)
            assertEquals(1, firstFrame.layoutEndCount)
            assertEquals(1, firstFrame.paintBeginCount)
            assertEquals(1, firstFrame.paintEndCount)
            assertTrue(firstFrame.dirtyRenderNodeCount > 0)
            assertEquals(48L, firstFrame.paintedPixelCount)
            assertTrue(firstFrame.bufferMissCount > 0L)

            /** Sink receiving evidence from the unchanged retained frame. */
            val cachedFrame = RuntimeRecordingFramePhaseSink()
            runtime.render(root = root, logicalWidth = 8, logicalHeight = 6, framePhaseSink = cachedFrame)

            assertEquals(1, cachedFrame.buildBeginCount)
            assertEquals(1, cachedFrame.buildEndCount)
            assertEquals(0, cachedFrame.layoutBeginCount)
            assertEquals(0, cachedFrame.paintBeginCount)
            assertEquals(0, cachedFrame.dirtyRenderNodeCount)
            assertEquals(0L, cachedFrame.paintedPixelCount)
            assertTrue(cachedFrame.renderCacheHit)
        } finally {
            runtime.dispose()
        }
    }

    /** Primitive-only sink used to inspect runtime callbacks without a clock dependency. */
    private class RuntimeRecordingFramePhaseSink : PixelFramePhaseSink {
        /** Opened retained build segment count. */
        var buildBeginCount: Int = 0

        /** Closed retained build segment count. */
        var buildEndCount: Int = 0

        /** Rebuilt Element count. */
        var dirtyElementCount: Int = 0

        /** Opened RenderObject layout segment count. */
        var layoutBeginCount: Int = 0

        /** Closed RenderObject layout segment count. */
        var layoutEndCount: Int = 0

        /** Opened logical paint segment count. */
        var paintBeginCount: Int = 0

        /** Closed logical paint segment count. */
        var paintEndCount: Int = 0

        /** Dirty RenderObject subtree size. */
        var dirtyRenderNodeCount: Int = 0

        /** Repainted logical pixel count. */
        var paintedPixelCount: Long = 0L

        /** Whether a complete retained render result was reused. */
        var renderCacheHit: Boolean = false

        /** Runtime PixelBuffer pool hit count. */
        var bufferHitCount: Long = 0L

        /** Runtime PixelBuffer pool miss count. */
        var bufferMissCount: Long = 0L

        /** Records the start of retained reconciliation. */
        override fun beginBuild() {
            buildBeginCount += 1
        }

        /** Records the end of retained reconciliation. */
        override fun endBuild() {
            buildEndCount += 1
        }

        /** Accumulates rebuilt Elements from retained reconciliation. */
        override fun recordBuildWork(dirtyElementCount: Int) {
            this.dirtyElementCount += dirtyElementCount
        }

        /** Records the start of RenderObject layout. */
        override fun beginLayout() {
            layoutBeginCount += 1
        }

        /** Records the end of RenderObject layout. */
        override fun endLayout() {
            layoutEndCount += 1
        }

        /** Records the start of logical buffer paint. */
        override fun beginPaint() {
            paintBeginCount += 1
        }

        /** Records the end of logical buffer paint. */
        override fun endPaint() {
            paintEndCount += 1
        }

        /** Accumulates retained pipeline work from one render request. */
        override fun recordPipelineWork(
            dirtyRenderNodeCount: Int,
            paintedPixelCount: Long,
            renderCacheHit: Boolean,
        ) {
            this.dirtyRenderNodeCount += dirtyRenderNodeCount
            this.paintedPixelCount += paintedPixelCount
            this.renderCacheHit = this.renderCacheHit || renderCacheHit
        }

        /** Accumulates runtime PixelBuffer pool activity. */
        override fun recordBufferPoolActivity(hitCount: Long, missCount: Long) {
            bufferHitCount += hitCount
            bufferMissCount += missCount
        }
    }
}
