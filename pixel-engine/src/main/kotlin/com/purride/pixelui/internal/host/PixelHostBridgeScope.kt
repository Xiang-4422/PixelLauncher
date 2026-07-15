package com.purride.pixelui.internal.host

import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.PixelHostBridge
import com.purride.pixelui.Widget

/** 定义 `PixelHostBridgeScope` 在 `PixelHostBridgeScope` 中承担的数据或执行职责，并保持公开不变量稳定。 */
public class PixelHostBridgeScope(
    /** 提供 `PixelHostBridgeScope` 用于识别或兼容校验的 `bridge` 值。 */
    public val bridge: PixelHostBridge?,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return bridge !== (oldWidget as? PixelHostBridgeScope)?.bridge
    }
}
