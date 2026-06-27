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
     * 尝试用最近的 PixelErrorBoundary 恢复一次 render 阶段异常。
     *
     * 返回恢复后的根 element；没有可用边界时返回 null，并由调用方继续抛出原异常。
     */
    fun recoverFromRenderError(error: Throwable): Element?

    /**
     * 返回 retained build runtime 的内部诊断快照。
     */
    fun collectDiagnostics(): BuildOwnerDiagnostics

    fun collectWidgets(): List<Widget>

    /**
     * 释放构建运行时持有的 retained element tree 资源。
     */
    fun dispose()
}
