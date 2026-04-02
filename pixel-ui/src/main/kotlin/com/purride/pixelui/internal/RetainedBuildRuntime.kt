package com.purride.pixelui.internal

import com.purride.pixelui.Widget

internal class RetainedBuildRuntime(
    private val onVisualUpdate: () -> Unit,
) {
    private val buildOwner = BuildOwner(onVisualUpdate)

    fun resolveLegacyTree(root: Widget): LegacyRenderNode? {
        buildOwner.updateRootWidget(root)
        buildOwner.buildScope()
        return LegacyTreeResolver.resolve(buildOwner.rootElement)
    }

    fun dispose() {
        buildOwner.dispose()
    }
}
