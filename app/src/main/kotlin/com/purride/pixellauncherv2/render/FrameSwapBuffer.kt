package com.purride.pixellauncherv2.render

data class FramePayload(
    val sequence: Long,
    val pixelBuffer: PixelBuffer,
    val screenProfile: ScreenProfile,
    val palette: PixelPalette,
)

data class IdleMaskUpdate(
    val sequence: Long,
    val frame: IdleMaskFrame?,
)

class FrameSwapBuffer {
    private var latestSequence: Long = 0L
    private var latestFrame: FramePayload? = null

    @Synchronized
    fun offer(
        pixelBuffer: PixelBuffer,
        screenProfile: ScreenProfile,
        palette: PixelPalette,
    ): FramePayload {
        latestSequence += 1L
        val payload = FramePayload(
            sequence = latestSequence,
            pixelBuffer = pixelBuffer,
            screenProfile = screenProfile,
            palette = palette,
        )
        latestFrame = payload
        return payload
    }

    @Synchronized
    fun consumeLatest(afterSequence: Long): FramePayload? {
        val payload = latestFrame ?: return null
        return if (payload.sequence > afterSequence) payload else null
    }

    @Synchronized
    fun latest(): FramePayload? = latestFrame
}

class IdleMaskSwapBuffer {
    private var latestSequence: Long = 0L
    private var latestFrame: IdleMaskFrame? = null

    @Synchronized
    fun offer(frame: IdleMaskFrame) {
        latestSequence = frame.sequence
        latestFrame = frame
    }

    @Synchronized
    fun clear() {
        latestSequence += 1L
        latestFrame = null
    }

    @Synchronized
    fun consumeLatest(afterSequence: Long): IdleMaskUpdate? {
        return if (latestSequence > afterSequence) {
            IdleMaskUpdate(
                sequence = latestSequence,
                frame = latestFrame,
            )
        } else {
            null
        }
    }

    @Synchronized
    fun latest(): IdleMaskFrame? = latestFrame
}
