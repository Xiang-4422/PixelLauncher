package com.purride.pixelui.internal

import com.purride.pixelui.Listenable
import com.purride.pixelui.VoidCallback

internal class InheritedNotifierBinding(
    private val onNotifierChanged: () -> Unit,
) {
    private var currentNotifier: Listenable? = null
    private var callback: VoidCallback? = null

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

    fun clear() {
        detachCurrentNotifier()
        currentNotifier = null
        callback = null
    }

    private fun detachCurrentNotifier() {
        callback?.let { listener ->
            currentNotifier?.removeListener(listener)
        }
    }
}
