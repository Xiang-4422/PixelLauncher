package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelTextFieldNode

/**
 * 负责 legacy 文本输入框的视觉绘制。
 */
internal class PixelTextFieldVisualSupport(
    private val textFieldLayoutSupport: PixelTextFieldLayoutSupport,
) {
    /**
     * 绘制文本输入框的背景、边框、文本和光标。
     */
    fun render(
        node: PixelTextFieldNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
    ) {
        val borderTone = textFieldLayoutSupport.resolveBorderTone(node)
        buffer.fillRect(
            left = bounds.left,
            top = bounds.top,
            rectWidth = bounds.width,
            rectHeight = bounds.height,
            value = node.style.fillTone.value,
        )
        borderTone?.let { tone ->
            buffer.drawRect(
                left = bounds.left,
                top = bounds.top,
                rectWidth = bounds.width,
                rectHeight = bounds.height,
                value = tone.value,
            )
        }

        val displayState = textFieldLayoutSupport.resolveDisplayState(node, bounds)
        if (displayState.text.isNotEmpty()) {
            displayState.rasterizer.drawText(
                buffer = buffer,
                text = displayState.text,
                x = displayState.textX,
                y = displayState.textY,
                value = displayState.style.tone.value,
            )
        }

        if (node.enabled && !node.readOnly && node.state.isFocused) {
            val cursorX = textFieldLayoutSupport.resolveCursorX(node, displayState)
            val cursorTop = bounds.top + node.style.padding
            val cursorHeight = (bounds.height - (node.style.padding * 2)).coerceAtLeast(1)
            buffer.fillRect(
                left = cursorX.coerceAtMost(bounds.right - 1),
                top = cursorTop,
                rectWidth = 1,
                rectHeight = cursorHeight,
                value = node.style.cursorTone.value,
            )
        }
    }
}
