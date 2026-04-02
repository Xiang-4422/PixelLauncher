package com.purride.pixelui.internal

import com.purride.pixelui.Widget

internal class RetainedBuildRuntime(
    private val onVisualUpdate: () -> Unit,
    private val fallbackInflater: (Widget) -> Element?,
) {
    private val buildOwner = BuildOwner(
        onVisualUpdate = onVisualUpdate,
        fallbackInflater = fallbackInflater,
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
