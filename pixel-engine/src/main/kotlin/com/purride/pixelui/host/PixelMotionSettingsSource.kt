package com.purride.pixelui.host

import com.purride.pixelui.PixelMotionSettings

/**
 * Internal lifecycle boundary for observing platform motion preferences.
 *
 * Implementations must make [attach], [detach], and [destroy] idempotent so a View can move
 * between windows without leaking a platform observer.
 */
internal interface PixelMotionSettingsSource {
    /** Latest settings snapshot available without registering an observer. */
    val currentSettings: PixelMotionSettings

    /** Registers [onChanged] and begins delivering distinct settings snapshots. */
    fun attach(onChanged: (PixelMotionSettings) -> Unit)

    /** Temporarily unregisters platform listeners while retaining the latest snapshot. */
    fun detach()

    /** Permanently unregisters listeners and rejects future attachment. */
    fun destroy()
}
