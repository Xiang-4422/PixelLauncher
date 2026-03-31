package com.purride.pixellauncherv2.render

/**
 * 兼容层别名。
 *
 * 主帧载荷的真实定义已经迁到 `:pixel-core`，
 * 当前保留旧包名，降低第一轮拆分的改动面。
 */
typealias FramePayload = com.purride.pixelcore.FramePayload

data class IdleMaskUpdate(
    val sequence: Long,
    val frame: IdleMaskFrame?,
)

/**
 * 兼容层别名。
 *
 * 主帧交换缓冲的真实实现已经迁到 `:pixel-core`，
 * 当前先保留旧包名入口。
 */
typealias FrameSwapBuffer = com.purride.pixelcore.FrameSwapBuffer

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
