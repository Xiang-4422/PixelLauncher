package com.purride.pixelui.internal

import com.purride.pixelui.Listenable
import com.purride.pixelui.VoidCallback

/** Maintains one replaceable inherited notifier callback for a retained Element. */
internal class InheritedNotifierBinding(
    private val onNotifierChanged: () -> Unit,
) {
    private var currentNotifier: Listenable? = null
    /** Exact callback currently registered with [currentNotifier]. */
    private var callback: VoidCallback? = null

    /** Replaces the current notifier binding while preserving identity when unchanged. */
    fun bind(notifier: Listenable?) {
        if (currentNotifier === notifier) {
            return
        }
        detachCurrentNotifier()
        currentNotifier = notifier
        callback = notifier?.let { listenable ->
            VoidCallback(onNotifierChanged).also { listener ->
                listenable.addListener(listener)
            }
        }
    }

    /** Commits an empty binding before invoking consumer-provided listener removal code. */
    fun clear() {
        detachCurrentNotifier()
    }

    /** Detaches the exact notifier/callback pair and clears retained references first. */
    private fun detachCurrentNotifier() {
        /** Notifier captured before terminal references are cleared. */
        val notifier = currentNotifier
        /** Listener captured before terminal references are cleared. */
        val listener = callback
        currentNotifier = null
        callback = null
        if (notifier != null && listener != null) notifier.removeListener(listener)
    }
}
