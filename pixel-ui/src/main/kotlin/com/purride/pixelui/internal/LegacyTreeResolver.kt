package com.purride.pixelui.internal

/**
 * retained element tree 到 legacy 渲染树的桥接解析器。
 */
internal object LegacyTreeResolver {
    fun resolve(root: Element?): LegacyRenderNode? {
        return root?.createLegacyTree()
    }
}
