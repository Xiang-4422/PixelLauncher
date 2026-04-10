package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.internal.legacy.PixelTextFieldNode
import com.purride.pixelui.internal.legacy.PixelTextNode

/**
 * 负责为不同文本节点选择实际要使用的栅格器。
 */
internal class PixelTextRasterizerResolver(
    private val defaultTextRasterizer: PixelTextRasterizer,
) {
    /**
     * 解析普通文本节点要使用的栅格器。
     */
    fun resolveText(node: PixelTextNode): PixelTextRasterizer {
        return node.style.textRasterizer ?: defaultTextRasterizer
    }

    /**
     * 解析文本输入节点要使用的栅格器。
     */
    fun resolveTextField(node: PixelTextFieldNode): PixelTextRasterizer {
        return node.style.textStyle.textRasterizer ?: defaultTextRasterizer
    }
}
