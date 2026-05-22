package com.purride.pixellauncherv2.render

/**
 * Lightweight animation state for the launcher's charge-animation ticker.
 *
 * The launch-shutter, boot-sequence, and drawer-reveal animations previously
 * tracked here have been removed; rendering is now handled entirely by
 * pixel-engine widgets in LauncherRootHost.
 */
data class LauncherAnimationState(
    val headerChargeTick: Int = 0,
) {
    fun nextFrame(): LauncherAnimationState = copy(headerChargeTick = headerChargeTick + 1)

    companion object {
        /** Target frame period for the decoration ticker (charge animation). */
        const val frameDelayMs: Long = 60L

        /**
         * How long to wait before actually launching an app (gives the shutter animation
         * time to play on the launcher side before the launched app appears).
         */
        const val launchShutterDurationMs: Long = frameDelayMs * 4   // 4 frames ≈ 240 ms
    }
}
