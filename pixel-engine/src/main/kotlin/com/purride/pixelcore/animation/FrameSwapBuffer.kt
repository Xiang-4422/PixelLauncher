package com.purride.pixelcore

/**
 * 主帧载荷。
 *
 * 第一版只保留对主像素帧的交换缓冲，不把 Idle 等产品专属附加帧带入内核层。
 */
public data class FramePayload(
    val sequence: Long,
    val pixelBuffer: PixelBuffer,
    val screenProfile: ScreenProfile,
    val backgroundColor: PixelColor,
)

/** 保存生产者提交的最新完整帧，并让消费者按单调序号跳过过期帧。 */
public class FrameSwapBuffer {
    private var latestSequence: Long = 0L
    private var latestFrame: FramePayload? = null

    @Synchronized
    /** 向 `FrameSwapBuffer` 提交 `offer` 数据或事件，并按所属类型的顺序与所有权规则保存。 */
    public fun offer(
        pixelBuffer: PixelBuffer,
        screenProfile: ScreenProfile,
        backgroundColor: PixelColor,
    ): FramePayload {
        latestSequence += 1L
        val payload = FramePayload(
            sequence = latestSequence,
            pixelBuffer = pixelBuffer,
            screenProfile = screenProfile,
            backgroundColor = backgroundColor,
        )
        latestFrame = payload
        return payload
    }

    @Synchronized
    /** 依据 `FrameSwapBuffer` 的公开契约执行 `consumeLatest`，并返回或提交经过边界校验的结果。 */
    public fun consumeLatest(afterSequence: Long): FramePayload? {
        val payload = latestFrame ?: return null
        return if (payload.sequence > afterSequence) payload else null
    }

    @Synchronized
    /** 查询 `FrameSwapBuffer` 的 `latest` 派生结果；该读取不会改变已保存状态。 */
    public fun latest(): FramePayload? = latestFrame
}
