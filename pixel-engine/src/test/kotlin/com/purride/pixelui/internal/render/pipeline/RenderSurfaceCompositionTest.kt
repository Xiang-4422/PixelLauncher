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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RenderSurface IME composition 下划线（V2）的回归测试。
 *
 * V2 合约：
 *  - 仅在 state.isFocused == true、textInputCompositionColor 非空、composition
 *    范围非空（start < end）且都在 text 内时绘制
 *  - 下划线高度 = 1px，位于文本底部一行
 *  - X 范围按字符比例分摊，与 selection 同口径
 *  - 不参与 layout / 命中测试
 *
 * Controller 侧合约：
 *  - updateComposition(start, end): start < 0 或 end <= start 视作清空
 *  - 超界 clamp 到 [0, text.length]
 *  - clamp 后仍空就重置为 -1, -1
 */
class RenderSurfaceCompositionTest {

    private val compositionColor = PixelColor.fromRgb(0xFF, 0x80, 0x00)
    private val textColor = PixelColor.fromRgb(0xFF, 0xFF, 0xFF)

    private fun makeSurface(
        text: String,
        focused: Boolean,
        compositionStart: Int = -1,
        compositionEnd: Int = -1,
        compositionColor: PixelColor? = this.compositionColor,
    ): Pair<RenderSurface, PixelBuffer> {
        val state = PixelTextFieldState(initialText = text)
        val controller = PixelTextFieldController()
        if (focused) controller.focus(state)
        if (compositionStart >= 0 && compositionEnd > compositionStart) {
            controller.updateComposition(state, compositionStart, compositionEnd)
        }
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
            textInputCompositionColor = compositionColor,
        )
        surface.setRenderObjectChild(textChild)
        surface.layout(RenderConstraints(maxWidth = 120, maxHeight = 12))
        val buffer = PixelBuffer(width = 120, height = 12).also { it.clear() }
        return surface to buffer
    }

    @Test
    fun unfocusedFieldDoesNotPaintComposition() {
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = false,
            compositionStart = 0,
            compositionEnd = 3,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val pixels = buffer.pixels.count { it == compositionColor.argb }
        assertEquals("unfocused field must not paint composition underline", 0, pixels)
    }

    @Test
    fun nullCompositionColorPaintsNothing() {
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = true,
            compositionStart = 0,
            compositionEnd = 3,
            compositionColor = null,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val pixels = buffer.pixels.count { it == compositionColor.argb }
        assertEquals("null compositionColor must not paint underline", 0, pixels)
    }

    @Test
    fun missingCompositionDoesNotPaint() {
        // 没调 updateComposition；state.compositionStart 默认是 -1。
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = true,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val pixels = buffer.pixels.count { it == compositionColor.argb }
        assertEquals("no composition range -> no underline", 0, pixels)
    }

    @Test
    fun nonEmptyCompositionPaintsUnderline() {
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = true,
            compositionStart = 1,
            compositionEnd = 4,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val pixels = buffer.pixels.count { it == compositionColor.argb }
        assertNotEquals("non-empty composition should paint at least one underline pixel", 0, pixels)
    }

    @Test
    fun underlineIsExactlyOneRowTall() {
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = true,
            compositionStart = 0,
            compositionEnd = 5,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val width = 120
        val rows = mutableSetOf<Int>()
        for (i in buffer.pixels.indices) {
            if (buffer.pixels[i] == compositionColor.argb) rows += (i / width)
        }
        assertEquals("composition underline must span exactly one row, got $rows", 1, rows.size)
    }

    @Test
    fun controllerClampsCompositionWithinTextRange() {
        val state = PixelTextFieldState(initialText = "HI")
        val controller = PixelTextFieldController()
        controller.updateComposition(state, compositionStart = -5, compositionEnd = 2)
        assertEquals("negative start treated as 'no composition'", -1, state.compositionStart)
        assertEquals(-1, state.compositionEnd)

        controller.updateComposition(state, compositionStart = 0, compositionEnd = 99)
        assertEquals(0, state.compositionStart)
        assertEquals(2, state.compositionEnd)

        controller.updateComposition(state, compositionStart = 3, compositionEnd = 3)
        assertEquals("start == end -> cleared", -1, state.compositionStart)
        assertEquals(-1, state.compositionEnd)
    }

    @Test
    fun updateTextClearsCompositionWhenNotProvided() {
        val state = PixelTextFieldState(initialText = "ABCDEF")
        val controller = PixelTextFieldController()
        controller.updateComposition(state, 1, 4)
        assertEquals(1, state.compositionStart)

        // 不传 composition 参数（默认 -1）→ 应当清空旧值。
        controller.updateText(state, text = "ABCDEF")
        assertEquals(-1, state.compositionStart)
        assertEquals(-1, state.compositionEnd)
    }

    @Test
    fun updateTextCarriesNewCompositionRange() {
        val state = PixelTextFieldState(initialText = "")
        val controller = PixelTextFieldController()
        controller.updateText(
            state = state,
            text = "你好",
            selectionStart = 2,
            selectionEnd = 2,
            compositionStart = 0,
            compositionEnd = 2,
        )
        assertEquals("你好", state.text)
        assertEquals(0, state.compositionStart)
        assertEquals(2, state.compositionEnd)
    }

    @Test
    fun underlineSitsBelowSelectionWhenBothPresent() {
        // 当 selection 和 composition 都画时，selection 是块状（高 = textHeight），
        // composition 是底边 1 行。所以 composition 像素 Y 应当大于等于 selection 像素的最大 Y。
        val selectionColor = PixelColor.fromRgb(0x00, 0xFF, 0xFF)
        val state = PixelTextFieldState(initialText = "ABCDE", selectionStart = 0, selectionEnd = 5)
        val controller = PixelTextFieldController()
        controller.focus(state)
        controller.updateComposition(state, 0, 5)
        val textChild = RenderText(
            text = "ABCDE",
            style = PixelTextStyle(color = textColor),
            textAlign = PixelTextAlign.START,
            textDirection = TextDirection.LTR,
            softWrap = false,
            overflow = TextOverflow.CLIP,
            maxLines = 1,
            defaultTextRasterizer = PixelBitmapFont.Default,
        )
        val surface = RenderSurface(
            textInputState = state,
            textInputController = controller,
            textInputSelectionColor = selectionColor,
            textInputCompositionColor = compositionColor,
        )
        surface.setRenderObjectChild(textChild)
        surface.layout(RenderConstraints(maxWidth = 120, maxHeight = 12))
        val buffer = PixelBuffer(width = 120, height = 12).also { it.clear() }
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val width = 120
        val compositionMaxY = (0 until buffer.pixels.size)
            .filter { buffer.pixels[it] == compositionColor.argb }
            .maxOfOrNull { it / width } ?: -1
        val selectionMaxY = (0 until buffer.pixels.size)
            .filter { buffer.pixels[it] == selectionColor.argb }
            .maxOfOrNull { it / width } ?: -1
        assertNotEquals(-1, compositionMaxY)
        assertNotEquals(-1, selectionMaxY)
        assertTrue(
            "composition underline should sit at or below selection bottom (composition=$compositionMaxY selection=$selectionMaxY)",
            compositionMaxY >= selectionMaxY,
        )
    }
}
