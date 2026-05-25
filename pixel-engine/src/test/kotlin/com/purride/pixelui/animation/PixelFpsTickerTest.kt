package com.purride.pixelui.animation

import com.purride.pixelui.host.ManualFrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `PixelTickerProvider.createTicker(maxFps=...)` 限速行为的回归测试。
 *
 * 注意：ManualFrameScheduler.advanceFrame 接收的是**绝对时间戳**而非增量，
 * 所以测试自己维护 cumulativeNanos 并每次累加。
 */
class PixelFpsTickerTest {

    private val scheduler = ManualFrameScheduler()
    private val provider = PixelTickerProvider(scheduler)
    private var cumulativeNanos = 0L

    private fun advanceMs(ms: Long) {
        cumulativeNanos += ms * 1_000_000L
        scheduler.advanceFrame(cumulativeNanos)
    }

    @Test
    fun unlimitedTickerReceivesEveryFrame() {
        val received = mutableListOf<Long>()
        val ticker = provider.createTicker { elapsed -> received += elapsed }
        ticker.start()
        repeat(5) { advanceMs(10) }
        assertEquals("unlimited should dispatch every frame", 5, received.size)
    }

    @Test
    fun fpsLimitedTickerDropsIntermediateFrames() {
        val received = mutableListOf<Long>()
        // 30 FPS → min interval ≈ 33.33ms。每 10ms 推一帧，应只大约每 4 帧派发一次。
        val ticker = provider.createTicker(maxFps = 30) { elapsed -> received += elapsed }
        ticker.start()
        repeat(20) { advanceMs(10) }
        // 200ms 总 elapsed，30fps → 期望 6 次派发左右
        assertTrue("fps-limited should dispatch fewer frames, got ${received.size}", received.size in 5..7)
    }

    @Test
    fun fpsLimitedFirstFrameAlwaysDispatched() {
        val received = mutableListOf<Long>()
        val ticker = provider.createTicker(maxFps = 30) { elapsed -> received += elapsed }
        ticker.start()
        advanceMs(1) // 第一帧时间 <33ms 也应当 dispatch（bootstrap）
        assertEquals(1, received.size)
    }

    @Test
    fun fpsLimitedElapsedReflectsRealTime() {
        // 限速不应改变 elapsedNanos 的实际值——仍是从 ticker 启动起累积。
        val received = mutableListOf<Long>()
        val ticker = provider.createTicker(maxFps = 10) { elapsed -> received += elapsed }
        ticker.start()
        repeat(10) { advanceMs(50) }
        // 500ms 总累积，10fps → 5 次派发；每次 elapsed 应大致是 100ms 的倍数
        assertTrue("should have multiple dispatches, got ${received.size}", received.size >= 4)
        // 最后一次 elapsed 应接近 500ms
        val lastNanos = received.last()
        assertTrue("last elapsed ${lastNanos}ns should be near 500ms", lastNanos in 400_000_000L..550_000_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun maxFpsZeroIsRejected() {
        provider.createTicker(maxFps = 0) { /* never called */ }
    }

    @Test(expected = IllegalArgumentException::class)
    fun maxFpsNegativeIsRejected() {
        provider.createTicker(maxFps = -1) { /* never called */ }
    }

    @Test
    fun stopPreventsFurtherDispatch() {
        val received = mutableListOf<Long>()
        val ticker = provider.createTicker(maxFps = 60) { elapsed -> received += elapsed }
        ticker.start()
        advanceMs(20)
        val before = received.size
        ticker.stop()
        advanceMs(100)
        assertEquals("no dispatch after stop", before, received.size)
    }
}
