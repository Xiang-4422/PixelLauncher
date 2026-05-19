package com.purride.pixelui.host

import android.os.SystemClock

/**
 * PixelHostView 内部帧节拍状态。
 *
 * 只负责把 Android uptime 转成当前帧 delta，避免 View 主类继续持有细节状态。
 */
internal class PixelHostFrameLoop {
    private var lastFrameUptimeMs: Long = 0L

    fun consumeFrameDeltaMs(): Long {
        val now = SystemClock.uptimeMillis()
        val deltaMs = if (lastFrameUptimeMs == 0L) {
            16L
        } else {
            (now - lastFrameUptimeMs).coerceAtLeast(1L)
        }
        lastFrameUptimeMs = now
        return deltaMs
    }
}
