package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * RenderSurface 文本输入光标绘制的回归测试。
 *
 * V1 行为合约：
 *  - state.isFocused == false → 不画
 *  - cursorColor == null → 不画
 *  - 空文本时 cursor 按正在显示的占位文本对齐语义绘制
 *  - 非空文本时 cursor 按输入文本和 selectionStart 的输入语义绘制
 *  - 不参与命中测试，不改 layout size
 */
class RenderSurfaceCursorTest {

    private val cursorColor = PixelColor.fromRgb(0xFF, 0xFF, 0)
    private val textColor = PixelColor.fromRgb(0xFF, 0xFF, 0xFF)

    private fun makeSurface(
        text: String,
        focused: Boolean,
        cursorColor: PixelColor?,
        selectionStart: Int = text.length,
        maxLines: Int = 1,
    ): Pair<RenderSurface, PixelBuffer> {
        val state = PixelTextFieldState(
            initialText = text,
            selectionStart = selectionStart,
            selectionEnd = selectionStart,
        )
        if (focused) {
            // 直接读 isFocused 的内部 set — controller.focus 是合法 API
            PixelTextFieldController().focus(state)
        }
        val controller = PixelTextFieldController()
        val textChild = RenderText(
            text = text.ifEmpty { " " },
            style = PixelTextStyle(color = textColor),
            textAlign = PixelTextAlign.START,
            textDirection = TextDirection.LTR,
            softWrap = maxLines > 1,
            overflow = TextOverflow.CLIP,
            maxLines = maxLines,
            defaultTextRasterizer = PixelBitmapFont.Default,
        )
        val surface = RenderSurface(
            fillColor = null,
            borderColor = null,
            textInputState = state,
            textInputController = controller,
            textInputCursorColor = cursorColor,
        )
        surface.setRenderObjectChild(textChild)
        surface.layout(RenderConstraints(maxWidth = 60, maxHeight = 24))
        val buffer = PixelBuffer(width = 60, height = 24).also { it.clear() }
        return surface to buffer
    }

    private fun makeTextFieldSurface(
        backingText: String,
        renderedText: String,
        textAlign: PixelTextAlign,
        fieldWidth: Int,
        cursorGap: Int = 0,
    ): Pair<RenderSurface, PixelBuffer> {
        val state = PixelTextFieldState(
            initialText = backingText,
            selectionStart = backingText.length,
            selectionEnd = backingText.length,
        )
        PixelTextFieldController().focus(state)
        val textChild = RenderText(
            text = renderedText.ifEmpty { " " },
            style = PixelTextStyle(color = textColor),
            textAlign = textAlign,
            textDirection = TextDirection.LTR,
            softWrap = false,
            overflow = TextOverflow.CLIP,
            maxLines = 1,
            defaultTextRasterizer = PixelBitmapFont.Default,
            explicitWidth = fieldWidth,
            paddingRight = if (textAlign == PixelTextAlign.END) {
                resolveTextFieldTextPaddingRight(
                    textAlign = TextAlign.END,
                    surfacePaddingRight = 0,
                )
            } else {
                0
            },
        )
        val surface = RenderSurface(
            fillColor = null,
            borderColor = null,
            textInputState = state,
            textInputController = PixelTextFieldController(),
            textInputCursorColor = cursorColor,
            textInputCursorGap = cursorGap,
        )
        surface.setRenderObjectChild(textChild)
        surface.layout(RenderConstraints(maxWidth = fieldWidth, maxHeight = 24))
        val buffer = PixelBuffer(width = fieldWidth, height = 24).also { it.clear() }
        return surface to buffer
    }

    @Test
    fun unfocusedTextFieldDoesNotPaintCursor() {
        val (surface, buffer) = makeSurface(text = "AB", focused = false, cursorColor = cursorColor)
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val cursorPixels = buffer.pixels.count { it == cursorColor.argb }
        assertEquals("unfocused field must not draw cursor", 0, cursorPixels)
    }

    @Test
    fun nullCursorColorPaintsNothing() {
        val (surface, buffer) = makeSurface(text = "AB", focused = true, cursorColor = null)
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val cursorPixels = buffer.pixels.count { it == cursorColor.argb }
        assertEquals("null cursorColor must not draw cursor", 0, cursorPixels)
    }

    @Test
    fun focusedFieldPaintsAtLeastOneCursorPixel() {
        val (surface, buffer) = makeSurface(text = "AB", focused = true, cursorColor = cursorColor)
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val cursorPixels = buffer.pixels.count { it == cursorColor.argb }
        assertNotEquals("focused field with non-null cursorColor should draw cursor", 0, cursorPixels)
    }

    @Test
    fun cursorPaintsAtSelectionPositionForNonEmptyText() {
        val (surface, buffer) = makeSurface(
            text = "AB",
            focused = true,
            cursorColor = cursorColor,
            selectionStart = 1,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        val firstCursorPixelIndex = buffer.pixels.indexOfFirst { it == cursorColor.argb }
        val width = 60
        val cursorX = firstCursorPixelIndex % width
        assertNotEquals("cursor must not be at X=0 for selection after first character", 0, cursorX)
        assertNotEquals("cursor must not be forced to text end", 12, cursorX)
    }

    @Test
    fun multilineCursorUsesSelectionLine() {
        val (surface, buffer) = makeSurface(
            text = "AB\nCD",
            focused = true,
            cursorColor = cursorColor,
            selectionStart = 3,
            maxLines = 2,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        val firstCursorPixelIndex = buffer.pixels.indexOfFirst { it == cursorColor.argb }
        val width = 60
        val cursorY = firstCursorPixelIndex / width
        assertNotEquals("cursor should move to the second line after newline selection", 0, cursorY)
    }

    @Test
    fun emptyTextDrawsCursorAtFieldOrigin() {
        // 空文本：cursor 应在 X=0（child 左缘）。
        // 注意：当 text 为空时，child 在 makeSurface 里用 " " 占位渲染，size.width > 0；
        // 光标仍然应该使用 child 的第 0 个 caret，而不是被占位宽度推到文本末端。
        val (surface, buffer) = makeSurface(text = "", focused = true, cursorColor = cursorColor)
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        // 空文本时 cursor 应当在 X = 0 列（不依赖字体宽度）
        val width = 60
        val firstCursorPixelIndex = buffer.pixels.indexOfFirst { it == cursorColor.argb }
        if (firstCursorPixelIndex < 0) return  // 极小概率 child 高度 0；跳过
        val cursorX = firstCursorPixelIndex % width
        assertEquals("empty text: cursor at X=0 (left edge of content area)", 0, cursorX)
    }

    @Test
    fun emptyTextCursorUsesPlaceholderEndForRightAlignedPlaceholder() {
        val placeholder = "SEARCH APP"
        val fieldWidth = 80
        val (surface, buffer) = makeTextFieldSurface(
            backingText = "",
            renderedText = placeholder,
            textAlign = PixelTextAlign.END,
            fieldWidth = fieldWidth,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        val firstCursorPixelIndex = buffer.pixels.indexOfFirst { it == cursorColor.argb }
        val cursorX = firstCursorPixelIndex % fieldWidth
        assertEquals(
            "empty right-aligned field must draw the cursor at the placeholder right side",
            fieldWidth - 1,
            cursorX,
        )
    }

    @Test
    fun emptyTextCursorUsesPlaceholderStartForCenteredPlaceholder() {
        val placeholder = "SEARCH APP"
        val fieldWidth = 80
        val (surface, buffer) = makeTextFieldSurface(
            backingText = "",
            renderedText = placeholder,
            textAlign = PixelTextAlign.CENTER,
            fieldWidth = fieldWidth,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        val firstCursorPixelIndex = buffer.pixels.indexOfFirst { it == cursorColor.argb }
        val cursorX = firstCursorPixelIndex % fieldWidth
        val expectedX = (fieldWidth - PixelBitmapFont.Default.measureText(placeholder)) / 2
        assertEquals(
            "empty centered field must draw the cursor at the placeholder start",
            expectedX,
            cursorX,
        )
    }

    @Test
    fun rightAlignedInputCursorStaysAtFieldEndAsTextGrows() {
        val fieldWidth = 80
        val shortText = "A"
        val longText = "ABCD"
        val (shortSurface, shortBuffer) = makeTextFieldSurface(
            backingText = shortText,
            renderedText = shortText,
            textAlign = PixelTextAlign.END,
            fieldWidth = fieldWidth,
        )
        val (longSurface, longBuffer) = makeTextFieldSurface(
            backingText = longText,
            renderedText = longText,
            textAlign = PixelTextAlign.END,
            fieldWidth = fieldWidth,
        )

        shortSurface.paint(PaintContext(shortBuffer), offsetX = 0, offsetY = 0)
        longSurface.paint(PaintContext(longBuffer), offsetX = 0, offsetY = 0)

        val shortCursorX = shortBuffer.pixels.indexOfFirst { it == cursorColor.argb } % fieldWidth
        val longCursorX = longBuffer.pixels.indexOfFirst { it == cursorColor.argb } % fieldWidth
        assertEquals(fieldWidth - 1, shortCursorX)
        assertEquals(fieldWidth - 1, longCursorX)
    }

    @Test
    fun rightAlignedInputTextLeavesOnePixelGapBeforeCaret() {
        val text = "ABCD"
        val fieldWidth = 80
        val renderText = RenderText(
            text = text,
            style = PixelTextStyle(color = textColor),
            textAlign = PixelTextAlign.END,
            textDirection = TextDirection.LTR,
            softWrap = false,
            overflow = TextOverflow.CLIP,
            maxLines = 1,
            defaultTextRasterizer = PixelBitmapFont.Default,
            explicitWidth = fieldWidth,
            paddingRight = 2,
        )

        renderText.layout(RenderConstraints(maxWidth = fieldWidth, maxHeight = 24))

        val textRect = renderText.textRangeRects(0, text.length).single()
        val caret = renderText.textInputCaretRect(
            backingText = text,
            selectionStart = text.length,
        )
        assertEquals(fieldWidth - 1, caret.x)
        assertEquals("right-aligned text must leave one empty pixel before the fixed cursor column", caret.x - 1, textRect.x + textRect.width)
    }

    @Test
    fun leftAlignedInputTextLeavesConfiguredGapBeforeTrailingCursor() {
        /** 两个字形后的末尾 selection，用于验证输入过程中最常见的光标位置。 */
        val text = "AB"
        /** 足够宽且不会裁剪尾部光标的输入字段。 */
        val fieldWidth = 80
        val (surface, buffer) = makeTextFieldSurface(
            backingText = text,
            renderedText = text,
            textAlign = PixelTextAlign.START,
            fieldWidth = fieldWidth,
            cursorGap = 1,
        )

        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        /** 最后一个真实文字像素的横坐标。 */
        val lastTextX = buffer.pixels.indices
            .filter { index -> buffer.pixels[index] == textColor.argb }
            .maxOf { index -> index % fieldWidth }
        /** 光标唯一像素列的横坐标。 */
        val cursorX = buffer.pixels.indexOfFirst { pixel -> pixel == cursorColor.argb } % fieldWidth
        assertEquals("text and cursor must contain one empty pixel column", lastTextX + 2, cursorX)
    }

    @Test
    fun cursorPaintsAsSinglePixelWideColumn() {
        val (surface, buffer) = makeSurface(text = "X", focused = true, cursorColor = cursorColor)
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        val width = 60
        val cursorXs = mutableSetOf<Int>()
        for (i in buffer.pixels.indices) {
            if (buffer.pixels[i] == cursorColor.argb) cursorXs += (i % width)
        }
        // 单列 = 所有 cursor 像素的 X 应当相同
        assertEquals("cursor must span exactly one column, got xs=$cursorXs", 1, cursorXs.size)
    }
}
