package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget

/**
 * 可被 bridge element 解析成 bridge render node 的 widget 协议。
 */
internal interface BridgeWidget : Widget {
    /**
     * 当前 bridge widget 暴露给 retained build tree 的子 widget 列表。
     */
    val childWidgets: List<Widget>

    /**
     * 用当前上下文和已解析好的子节点构建 bridge render node。
     */
    fun createBridgeNode(
        context: BuildContext,
        childNodes: BridgeNodeChildren,
    ): BridgeRenderNode
}
