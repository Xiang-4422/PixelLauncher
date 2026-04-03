package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * retained element tree 的构建运行时协议。
 */
internal interface ElementTreeBuildRuntime {
    fun resolveElementTree(root: Widget): Element?

    fun dispose()
}
