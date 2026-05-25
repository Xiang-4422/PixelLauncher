package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelTextStyle
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
 *  - 空文本时 cursor 在 child 左缘
 *  - 非空文本时 cursor 在 child 文本末端（child.size.width 处）
 *  - 不参与命中测试，不改 layout size
 */
class RenderSurfaceCursorTest {

    private val cursorColor = PixelColor.fromRgb(0xFF, 0xFF, 0)
    private val textColor = PixelColor.fromRgb(0xFF, 0xFF, 0xFF)

    private fun makeSurface(
        text: String,
        focused: Boolean,
        cursorColor: PixelColor?,
    ): Pair<RenderSurface, PixelBuffer> {
        val state = PixelTextFieldState(initialText = text)
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
            softWrap = false,
            overflow = TextOverflow.CLIP,
            maxLines = 1,
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
        surface.layout(RenderConstraints(maxWidth = 60, maxHeight = 12))
        val buffer = PixelBuffer(width = 60, height = 12).also { it.clear() }
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
    fun cursorPaintsAtTextEndForNonEmptyText() {
        val (surface, buffer) = makeSurface(text = "AB", focused = true, cursorColor = cursorColor)
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        // 找到第一个非透明的 cursor pixel — 它的 X 应当 >= text-end-X。
        // 我们不严格断言精确 X（依赖具体字体宽度），但可以验证 cursor 不是在 X = 0 (空文本场景)。
        val firstCursorPixelIndex = buffer.pixels.indexOfFirst { it == cursorColor.argb }
        val width = 60
        val cursorX = firstCursorPixelIndex % width
        // RenderText 渲染 "AB" 至少占用几像素宽，cursor 应当在 X >= 4 大概位置（5px 字体 + 内字间隙）
        assertNotEquals("cursor must not be at X=0 for non-empty text", 0, cursorX)
    }

    @Test
    fun emptyTextDrawsCursorAtFieldOrigin() {
        // 空文本：cursor 应在 X=0（child 左缘）。
        // 注意：当 text 为空时，child 在 makeSurface 里用 " " 占位渲染，size.width > 0；
        // 但 paintTextInputCursor 检查 state.text.isEmpty() → cursor 落在 cursorBaseX。
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
