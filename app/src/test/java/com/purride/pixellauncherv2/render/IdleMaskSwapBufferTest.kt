package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IdleMaskSwapBufferTest {

    @Test
    fun consumeLatestReturnsNewMaskAndClearSignals() {
        val swapBuffer = IdleMaskSwapBuffer()
        val maskFrame = IdleMaskFrame(
            sequence = 5L,
            width = 2,
            height = 2,
            mask = byteArrayOf(0x00, 0x7F, 0x00, 0x7F),
        )

        swapBuffer.offer(maskFrame)
        val firstUpdate = swapBuffer.consumeLatest(afterSequence = 0L)
        assertNotNull(firstUpdate)
        assertEquals(maskFrame.sequence, firstUpdate?.sequence)
        assertEquals(maskFrame, firstUpdate?.frame)

        swapBuffer.clear()
        val clearUpdate = swapBuffer.consumeLatest(afterSequence = maskFrame.sequence)
        assertNotNull(clearUpdate)
        assertNull(clearUpdate?.frame)
        assertEquals(maskFrame.sequence + 1L, clearUpdate?.sequence)
    }
}
