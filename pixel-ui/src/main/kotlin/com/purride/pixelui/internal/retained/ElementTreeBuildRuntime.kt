package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * retained element tree 的构建运行时协议。
 */
internal interface ElementTreeBuildRuntime {
    /**
     * 解析显式的 retained element tree 构建请求。
     */
    fun resolveElementTree(request: ElementTreeBuildRequest): Element?

    /**
     * 解析一棵 Widget 根树。
     */
    fun resolveElementTree(root: Widget): Element? {
        return resolveElementTree(
            request = ElementTreeBuildRequest(root = root),
        )
    }

    /**
     * 释放构建运行时持有的 retained element tree 资源。
     */
    fun dispose()
}
