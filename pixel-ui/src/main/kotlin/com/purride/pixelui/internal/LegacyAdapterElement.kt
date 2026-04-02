package com.purride.pixelui.internal

/**
 * retained build tree 到 legacy 渲染树的桥接 element。
 *
 * 它不是 retained 语义本身的一部分，所以单独放在 bridge 文件里，避免继续把
 * retained element 层和 legacy 节点适配揉在一起。
 */
internal class LegacyAdapterElement(
    widget: LegacyNodeWidget,
) : Element(widget) {
    private var children = emptyList<Element>()

    override fun performRebuild() {
        val childWidgets = (widget as LegacyNodeWidget).childWidgets
        val nextChildren = ArrayList<Element>(childWidgets.size)
        val maxCount = maxOf(children.size, childWidgets.size)
        for (index in 0 until maxCount) {
            val current = children.getOrNull(index)
            val nextWidget = childWidgets.getOrNull(index)
            owner.updateChild(
                parent = this,
                current = current,
                newWidget = nextWidget,
            )?.let(nextChildren::add)
        }
        children = nextChildren
    }

    override fun createLegacyTree(): LegacyRenderNode {
        owner.clearListenableDependencies(this)
        val childNodes = children.mapNotNull { child -> child.createLegacyTree() }
        return (widget as LegacyNodeWidget).createLegacyNode(
            context = this,
            childNodes = childNodes,
        )
    }

    override fun visitChildren(visitor: (Element) -> Unit) {
        children.forEach(visitor)
    }
}
