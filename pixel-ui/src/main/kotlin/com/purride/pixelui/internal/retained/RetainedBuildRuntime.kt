package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * retained build runtime 的默认实现。
 *
 * 这层负责驱动 `BuildOwner` 更新根 Widget、执行 dirty build，并返回最新的
 * retained element tree 根节点。
 */
internal class RetainedBuildRuntime(
    private val onVisualUpdate: () -> Unit,
    private val elementChildUpdater: ElementChildUpdater,
) : ElementTreeBuildRuntime {
    private val buildOwner = BuildOwner(
        onVisualUpdate = onVisualUpdate,
        elementChildUpdater = elementChildUpdater,
    )

    /**
     * 执行一次 retained element tree 构建请求。
     */
    override fun resolveElementTree(request: ElementTreeBuildRequest): Element? {
        buildOwner.updateRootWidget(request.root)
        buildOwner.buildScope()
        return buildOwner.rootElement
    }

    /**
     * 释放内部 `BuildOwner` 以及整棵 retained element tree。
     */
    override fun dispose() {
        buildOwner.dispose()
    }
}
