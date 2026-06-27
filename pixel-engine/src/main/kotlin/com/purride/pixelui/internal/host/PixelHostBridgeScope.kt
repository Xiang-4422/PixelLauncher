package com.purride.pixelui.internal.host

import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.PixelHostBridge
import com.purride.pixelui.Widget

internal class PixelHostBridgeScope(
    val bridge: PixelHostBridge?,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return bridge !== (oldWidget as? PixelHostBridgeScope)?.bridge
    }
}
