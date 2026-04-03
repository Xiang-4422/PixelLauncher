package com.purride.pixelui.internal

import com.purride.pixelui.Widget

internal class RetainedBuildRuntime(
    private val onVisualUpdate: () -> Unit,
    private val widgetAdapter: WidgetAdapter,
) : ElementTreeBuildRuntime {
    private val buildOwner = BuildOwner(
        onVisualUpdate = onVisualUpdate,
        widgetAdapter = widgetAdapter,
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
