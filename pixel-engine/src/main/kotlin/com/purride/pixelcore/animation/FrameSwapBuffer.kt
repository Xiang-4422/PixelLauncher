package com.purride.pixelcore

/**
 * 主帧载荷。
 *
 * 第一版只保留对主像素帧的交换缓冲，不把 Idle 等产品专属附加帧带入内核层。
 */
data class FramePayload(
    val sequence: Long,
    val pixelBuffer: PixelBuffer,
    val screenProfile: ScreenProfile,
    val palette: PixelPalette,
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
