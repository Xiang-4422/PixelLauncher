package com.purride.pixelui.internal

import com.purride.pixelui.internal.legacy.PixelTextFieldNode

/**
 * 负责 legacy 文本输入框导出输入目标。
 */
internal class PixelTextFieldTargetExport {
    /**
     * 按当前节点状态导出输入目标。
     */
    fun export(
        node: PixelTextFieldNode,
        bounds: PixelRect,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        if (!node.enabled) {
            return
        }

        textInputTargets += PixelTextInputTarget(
            bounds = bounds,
            state = node.state,
            controller = node.controller,
            readOnly = node.readOnly,
            autofocus = node.autofocus,
            action = node.textInputAction,
            onChanged = node.onChanged,
            onSubmitted = node.onSubmitted,
        )
    }
}
