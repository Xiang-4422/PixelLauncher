package com.purride.pixelui.animation

import com.purride.pixelui.host.ManualFrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTickerTest {

    private val scheduler = ManualFrameScheduler()
    private val provider = PixelTickerProvider(scheduler)

    @Test
    fun startTrigersFirstOnTick() {
        val ticks = mutableListOf<Long>()
        val ticker = provider.createTicker { elapsed -> ticks += elapsed }
        ticker.start()
        scheduler.advanceFrame(1_000_000L)
        assertEquals(1, ticks.size)
        assertEquals(0L, ticks[0])
    }

    @Test
    fun elapsedAccumulatesCorrectly() {
        val ticks = mutableListOf<Long>()
        val ticker = provider.createTicker { elapsed -> ticks += elapsed }
        ticker.start()
        scheduler.advanceFrame(1_000_000L)
        scheduler.advanceFrame(17_000_000L)
        assertEquals(2, ticks.size)
        assertEquals(0L, ticks[0])
        assertEquals(16_000_000L, ticks[1])
    }

    @Test
    fun stopPreventsSubsequentOnTick() {
        val ticks = mutableListOf<Long>()
        val ticker = provider.createTicker { elapsed -> ticks += elapsed }
        ticker.start()
        scheduler.advanceFrame(1_000_000L)
        ticker.stop()
        scheduler.advanceFrame(17_000_000L)
        assertEquals(1, ticks.size)
    }

    @Test
    fun restartResetsElapsed() {
        val ticks = mutableListOf<Long>()
        val ticker = provider.createTicker { elapsed -> ticks += elapsed }
        ticker.start()
        scheduler.advanceFrame(1_000_000L)
        ticker.stop()
        ticker.start()
        scheduler.advanceFrame(50_000_000L)
        assertEquals(2, ticks.size)
        assertEquals(0L, ticks[1])
    }

    @Test
    fun isActiveReflectsState() {
        val ticker = provider.createTicker { }
        assertFalse(ticker.isActive)
        ticker.start()
        assertTrue(ticker.isActive)
        ticker.stop()
        assertFalse(ticker.isActive)
    }

    @Test
    fun disposeAfterIsNoOp() {
        val ticks = mutableListOf<Long>()
        val ticker = provider.createTicker { elapsed -> ticks += elapsed }
        ticker.start()
        ticker.dispose()
        scheduler.advanceFrame(1_000_000L)
        assertEquals(0, ticks.size)
        ticker.start()
        ticker.stop()
        ticker.dispose()
    }
}
