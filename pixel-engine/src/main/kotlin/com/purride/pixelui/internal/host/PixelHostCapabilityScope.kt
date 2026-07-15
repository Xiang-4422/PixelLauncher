package com.purride.pixelui.internal.host

import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.PixelHostCapabilitySet
import com.purride.pixelui.Widget

/** 在 widget 子树中发布聚焦 Host capability 集合。 */
public class PixelHostCapabilityScope(
    /** 当前 Host 的不可变 capability 快照。 */
    public val capabilities: PixelHostCapabilitySet,
    /** 接收 capability 的 widget 子树。 */
    override val child: Widget,
    /** retained tree 使用的可选稳定键。 */
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    /** capability 集合变化时通知依赖节点重建。 */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return capabilities != (oldWidget as? PixelHostCapabilityScope)?.capabilities
    }
}
