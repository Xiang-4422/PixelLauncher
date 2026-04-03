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

        if (node.enabled) {
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
}
