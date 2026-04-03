package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.internal.legacy.PixelTextFieldNode

/**
 * 负责 legacy 文本输入框的显示样式和光标布局计算。
 */
internal class PixelTextFieldLayoutSupport(
    private val defaultTextRasterizer: PixelTextRasterizer,
    private val textRenderSupport: PixelTextRenderSupport,
) {
    /**
     * 计算输入框的测量尺寸。
     */
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

    /**
     * 解析当前输入框应该使用的边框色。
     */
    fun resolveBorderTone(node: PixelTextFieldNode) = if (!node.enabled) {
        node.style.disabledBorderTone ?: node.style.borderTone
    } else if (node.readOnly) {
        node.style.readOnlyBorderTone ?: node.style.borderTone
    } else if (node.state.isFocused) {
        node.style.focusedBorderTone ?: node.style.borderTone
    } else {
        node.style.borderTone
    }

    /**
     * 计算当前应显示的文本内容和样式。
     */
    fun resolveDisplayState(
        node: PixelTextFieldNode,
        bounds: PixelRect,
    ): TextFieldDisplayState {
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
        return TextFieldDisplayState(
            text = visibleDisplayText,
            rasterizer = displayRasterizer,
            contentMaxWidth = contentMaxWidth,
            textX = bounds.left + node.style.padding,
            textY = bounds.top + ((bounds.height - textHeight).coerceAtLeast(0) / 2),
            style = displayStyle,
        )
    }

    /**
     * 计算当前光标的 x 坐标。
     */
    fun resolveCursorX(
        node: PixelTextFieldNode,
        displayState: TextFieldDisplayState,
    ): Int {
        val cursorRasterizer = node.style.textStyle.textRasterizer ?: defaultTextRasterizer
        val visibleText = node.state.text.take(node.state.selectionStart.coerceAtMost(node.state.text.length))
        val clippedVisibleText = textRenderSupport.clipTextToWidth(
            text = visibleText,
            maxWidth = displayState.contentMaxWidth,
            rasterizer = cursorRasterizer,
        )
        return displayState.textX + cursorRasterizer.measureText(clippedVisibleText)
    }
}

/**
 * 文本输入框当前帧展示所需的派生布局信息。
 */
internal data class TextFieldDisplayState(
    val text: String,
    val rasterizer: PixelTextRasterizer,
    val contentMaxWidth: Int,
    val textX: Int,
    val textY: Int,
    val style: com.purride.pixelui.PixelTextStyle,
)
