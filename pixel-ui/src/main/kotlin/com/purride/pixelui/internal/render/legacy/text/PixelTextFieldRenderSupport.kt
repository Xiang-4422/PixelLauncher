package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.internal.legacy.PixelTextFieldNode

internal class PixelTextFieldRenderSupport(
    private val defaultTextRasterizer: PixelTextRasterizer,
    private val textRenderSupport: PixelTextRenderSupport,
) {
    private val textFieldLayoutSupport = PixelTextFieldLayoutSupport(
        defaultTextRasterizer = defaultTextRasterizer,
        textRenderSupport = textRenderSupport,
    )
    private val textFieldVisualSupport = PixelTextFieldVisualSupport(
        textFieldLayoutSupport = textFieldLayoutSupport,
    )
    private val textFieldTargetExport = PixelTextFieldTargetExport()

    /**
     * 测量文本输入框尺寸。
     */
    fun measure(node: PixelTextFieldNode): PixelSize = textFieldLayoutSupport.measure(node)

    /**
     * 渲染文本输入框内容、光标和输入目标。
     */
    fun render(
        node: PixelTextFieldNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        textFieldVisualSupport.render(
            node = node,
            bounds = bounds,
            buffer = buffer,
        )
        textFieldTargetExport.export(
            node = node,
            bounds = bounds,
            textInputTargets = textInputTargets,
        )
    }
}
