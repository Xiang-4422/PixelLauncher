package com.purride.pixelui.internal

import com.purride.pixelui.Widget

internal class RetainedBuildRuntime(
    private val onVisualUpdate: () -> Unit,
    private val elementChildUpdater: ElementChildUpdater,
) : ElementTreeBuildRuntime {
    private val buildOwner = BuildOwner(
        onVisualUpdate = onVisualUpdate,
        elementChildUpdater = elementChildUpdater,
    )

    override fun resolveElementTree(root: Widget): Element? {
        buildOwner.updateRootWidget(root)
        buildOwner.buildScope()
        return buildOwner.rootElement
    }

    override fun dispose() {
        buildOwner.dispose()
    }
}
