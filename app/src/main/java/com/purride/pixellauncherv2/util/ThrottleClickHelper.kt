package com.purride.pixellauncherv2.util

import android.os.SystemClock

class ThrottleClickHelper(
    private val intervalMs: Long = 500L,
) {

    private var lastClickTimestampMs: Long = 0L

    fun canClick(nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        if (nowMs - lastClickTimestampMs < intervalMs) {
            return false
        }

        lastClickTimestampMs = nowMs
        return true
    }
}
