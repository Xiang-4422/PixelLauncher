package com.purride.pixelui.host

import com.purride.pixelui.PixelHostFrameStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelHostFrameLoopTest {

    private class FakeClock(var ms: Long = 1_000L, var ns: Long = 1_000_000L) : MonotonicClock {
        override fun uptimeMillis(): Long = ms
        override fun nanoTime(): Long = ns
    }

    @Test
    fun snapshotBeforeAnyFrameReportsZeroFps() {
        val loop = PixelHostFrameLoop(clock = FakeClock())
        val stats = loop.snapshotStats()
        assertEquals(0L, stats.frameCount)
        assertEquals(0f, stats.fpsAvg, 0.001f)
    }

    @Test
    fun consumeFrameDeltaMsRecordsFrameCount() {
        val clock = FakeClock()
        val loop = PixelHostFrameLoop(clock = clock)
        loop.consumeFrameDeltaMs()
        clock.ms += 16
        loop.consumeFrameDeltaMs()
        clock.ms += 16
        loop.consumeFrameDeltaMs()
        assertEquals(3L, loop.snapshotStats().frameCount)
    }

    @Test
    fun firstFrameDeltaIsBootstrapped() {
        val loop = PixelHostFrameLoop(clock = FakeClock())
        val delta = loop.consumeFrameDeltaMs()
        assertEquals("first frame should bootstrap to ~16ms", 16L, delta)
    }

    @Test
    fun subsequentFramesReportRealDelta() {
        val clock = FakeClock(ms = 1_000L)
        val loop = PixelHostFrameLoop(clock = clock)
        loop.consumeFrameDeltaMs()           // bootstrap = 16
        clock.ms = 1_032L                    // +32ms
        val delta = loop.consumeFrameDeltaMs()
        assertEquals(32L, delta)
    }

    @Test
    fun fpsAverageReflectsLastWindowOfDeltas() {
        val clock = FakeClock(ms = 0L)
        val loop = PixelHostFrameLoop(clock = clock)
        // 模拟 5 帧，每帧间隔 20ms → 平均 50 FPS
        repeat(5) {
            clock.ms += 20
            loop.consumeFrameDeltaMs()
        }
        val stats = loop.snapshotStats()
        assertEquals(5L, stats.frameCount)
        // 第一帧 bootstrap=16，后续都是 20；窗口=5 帧。
        // 平均 delta = (16 + 20*4) / 5 = 96/5 = 19.2 → fps = 1000/19.2 ≈ 52.08
        assertTrue("fps should be ~52, got ${stats.fpsAvg}", stats.fpsAvg in 50f..55f)
    }

    @Test
    fun fpsAverageWindowSlidesPastBootstrap() {
        val clock = FakeClock(ms = 0L)
        val loop = PixelHostFrameLoop(clock = clock)
        // 远超窗口大小后，bootstrap 帧应当被挤出
        repeat(PixelHostFrameStats.FPS_WINDOW + 5) {
            clock.ms += 20
            loop.consumeFrameDeltaMs()
        }
        val fps = loop.snapshotStats().fpsAvg
        // 全部 20ms delta → 50 fps
        assertTrue("fps should be ~50, got $fps", fps in 49f..51f)
    }

    @Test
    fun beginAndEndPaintRecordsPaintTime() {
        val clock = FakeClock(ns = 1_000_000L)
        val loop = PixelHostFrameLoop(clock = clock)
        loop.consumeFrameDeltaMs()
        loop.beginPaint()
        clock.ns += 2_500_000L  // 2.5ms paint
        loop.endPaint()
        assertEquals(2_500_000L, loop.snapshotStats().paintTimeNanos)
    }

    @Test
    fun windowSizeIsExposedAsConstant() {
        assertEquals(30, PixelHostFrameStats.FPS_WINDOW)
    }
}
