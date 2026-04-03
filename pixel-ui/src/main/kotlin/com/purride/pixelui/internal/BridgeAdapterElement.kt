package com.purride.pixelui.internal

/**
 * retained build tree 到 bridge 渲染树的桥接 element。
 *
 * 它不是 retained 语义本身的一部分，所以单独放在 bridge 文件里，避免继续把
 * retained element 层和 legacy 节点适配揉在一起。
 */
internal class BridgeAdapterElement(
    widget: BridgeWidget,
) : Element(widget), BridgeResolvableElement {
    private val childSlot = MultiChildElementSlot()

    override fun performRebuild() {
        childSlot.update(
            owner = owner,
            parent = this,
            newWidgets = (widget as BridgeWidget).childWidgets,
        )
    }

    override fun resolveBridgeNode(childNodes: BridgeNodeChildren): BridgeRenderNode {
        owner.clearListenableDependencies(this)
        return (widget as BridgeWidget).createBridgeNode(
            context = this,
            childNodes = childNodes,
        )
    }

    override fun visitChildren(visitor: (Element) -> Unit) {
        childSlot.visit(visitor)
    }
}
