package com.purride.pixelui.internal

import com.purride.pixelui.Widget

internal class RetainedBuildRuntime(
    private val onVisualUpdate: () -> Unit,
    private val widgetAdapter: WidgetAdapter,
) {
    private val buildOwner = BuildOwner(
        onVisualUpdate = onVisualUpdate,
        widgetAdapter = widgetAdapter,
    )

    fun resolveElementTree(root: Widget): Element? {
        buildOwner.updateRootWidget(root)
        buildOwner.buildScope()
        return buildOwner.rootElement
    }

    fun dispose() {
        buildOwner.dispose()
    }
}
