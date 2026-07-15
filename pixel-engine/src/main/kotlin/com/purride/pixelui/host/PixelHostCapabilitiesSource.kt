package com.purride.pixelui.host

import com.purride.pixelui.HostCapabilitiesData

/**
 * Internal lifecycle boundary for observing one Android Host environment snapshot.
 *
 * Implementations own locale, layout direction, text scale, contrast, density and refresh-rate
 * observation. Motion and logical display-feature projection remain Host-owned inputs merged into
 * the final atomic [HostCapabilitiesData]. Every lifecycle method is main-thread confined and must
 * be idempotent.
 */
internal interface PixelHostCapabilitiesSource {
    /** Latest immutable platform snapshot available without registering listeners. */
    val currentCapabilities: HostCapabilitiesData

    /** Registers [onChanged] and immediately publishes the newest complete snapshot. */
    fun attach(onChanged: (HostCapabilitiesData) -> Unit, displayId: Int?)

    /** Updates the physical display whose refresh rate belongs to this Host. */
    fun updateDisplay(displayId: Int?)

    /** Re-reads all platform values after a Host-owned geometry or cutout change. */
    fun refresh()

    /** Temporarily unregisters platform listeners while retaining the latest snapshot. */
    fun detach()

    /** Permanently unregisters listeners and rejects future attachment. */
    fun destroy()
}
