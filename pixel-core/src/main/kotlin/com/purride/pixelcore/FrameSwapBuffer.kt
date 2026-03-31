package com.purride.pixelcore

/**
 * 一帧完整显示数据的封装。
 *
 * 它表示已经准备好提交到显示层的一次逻辑帧，
 * 包含像素缓冲、屏幕配置和调色板三部分信息。
 */
data class FramePayload(
    val sequence: Long,
    val pixelBuffer: PixelBuffer,
    val screenProfile: ScreenProfile,
    val palette: PixelPalette,
)

/**
 * 主帧交换缓冲。
 *
 * 这层只负责“生产最新帧、消费比某个序号更新的帧”，
 * 不关心业务页面，也不关心具体显示实现。
 */
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
