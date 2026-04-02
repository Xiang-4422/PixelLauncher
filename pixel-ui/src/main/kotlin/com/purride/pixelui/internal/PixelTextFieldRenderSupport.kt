package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.internal.legacy.PixelTextFieldNode

internal class PixelTextFieldRenderSupport(
    private val defaultTextRasterizer: PixelTextRasterizer,
    private val textRenderSupport: PixelTextRenderSupport,
) {
    fun measure(node: PixelTextFieldNode): PixelSize {
        val textRasterizer = textRenderSupport.resolveTextFieldRasterizer(node)
        val displayText = node.state.text.ifEmpty { node.placeholder.ifEmpty { " " } }
        val textWidth = textRasterizer.measureText(displayText)
        val textHeight = textRasterizer.measureHeight(displayText)
        return PixelSize(
            width = textWidth + (node.style.padding * 2) + if (node.state.isFocused) 2 else 0,
            height = textHeight + (node.style.padding * 2),
        )
    }

    fun render(
        node: PixelTextFieldNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val borderTone = if (!node.enabled) {
            node.style.disabledBorderTone ?: node.style.borderTone
        } else if (node.readOnly) {
            node.style.readOnlyBorderTone ?: node.style.borderTone
        } else if (node.state.isFocused) {
            node.style.focusedBorderTone ?: node.style.borderTone
        } else {
            node.style.borderTone
        }
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

        val displayText = node.state.text.ifEmpty { node.placeholder }
        val displayStyle = if (!node.enabled) {
            if (node.state.text.isEmpty()) {
                node.style.disabledPlaceholderStyle
            } else {
                node.style.disabledTextStyle
            }
        } else if (node.state.text.isEmpty()) {
            node.style.placeholderStyle
        } else {
            node.style.textStyle
        }
        val displayRasterizer = displayStyle.textRasterizer ?: defaultTextRasterizer
        val contentMaxWidth = (bounds.width - (node.style.padding * 2)).coerceAtLeast(0)
        val visibleDisplayText = textRenderSupport.clipTextToWidth(
            text = displayText,
            maxWidth = contentMaxWidth,
            rasterizer = displayRasterizer,
        )
        val textHeight = displayRasterizer.measureHeight(visibleDisplayText.ifEmpty { " " })
        val textY = bounds.top + ((bounds.height - textHeight).coerceAtLeast(0) / 2)
        val textX = bounds.left + node.style.padding
        if (visibleDisplayText.isNotEmpty()) {
            displayRasterizer.drawText(
                buffer = buffer,
                text = visibleDisplayText,
                x = textX,
                y = textY,
                value = displayStyle.tone.value,
            )
        }

        if (node.enabled && !node.readOnly && node.state.isFocused) {
            val visibleText = node.state.text.take(node.state.selectionStart.coerceAtMost(node.state.text.length))
            val clippedVisibleText = textRenderSupport.clipTextToWidth(
                text = visibleText,
                maxWidth = contentMaxWidth,
                rasterizer = node.style.textStyle.textRasterizer ?: defaultTextRasterizer,
            )
            val cursorX = textX + (node.style.textStyle.textRasterizer ?: defaultTextRasterizer).measureText(clippedVisibleText)
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
