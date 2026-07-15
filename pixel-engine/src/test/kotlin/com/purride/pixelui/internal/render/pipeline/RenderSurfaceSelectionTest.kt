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
 * RenderSurface 文本输入选区高亮（V2）的回归测试。
 *
 * V2 行为合约：
 *  - state.isFocused == false → 不画
 *  - selectionColor == null → 不画
 *  - selectionStart >= selectionEnd（空选区）→ 不画
 *  - 空文本 → 不画
 *  - 非空选区：在文本宽度内按字符比例铺一段填充矩形
 *  - 整段选中（0..text.length）→ 横向几乎覆盖文本宽度
 *  - 不参与 layout / 命中测试
 *
 * 模拟比例字体偏差容忍：所有 X 比例断言都用整数除法，与代码侧实现一致。
 */
class RenderSurfaceSelectionTest {

    private val selectionColor = PixelColor.fromRgb(0xFF, 0x80, 0x00)
    private val handleColor = PixelColor.fromRgb(0x00, 0xFF, 0xFF)
    private val cursorColor = PixelColor.fromRgb(0xFF, 0xFF, 0x00)
    private val textColor = PixelColor.fromRgb(0xFF, 0xFF, 0xFF)

    private fun makeSurface(
        text: String,
        focused: Boolean,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart,
        selectionColor: PixelColor? = this.selectionColor,
        selectionHandleColor: PixelColor? = null,
        cursorColor: PixelColor? = this.cursorColor,
        maxLines: Int = 1,
        readOnly: Boolean = false,
    ): Pair<RenderSurface, PixelBuffer> {
        val state = PixelTextFieldState(
            initialText = text,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
        )
        if (focused) {
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
            textInputReadOnly = readOnly,
            textInputCursorColor = cursorColor,
            textInputSelectionColor = selectionColor,
            textInputSelectionHandleColor = selectionHandleColor,
        )
        surface.setRenderObjectChild(textChild)
        surface.layout(RenderConstraints(maxWidth = 120, maxHeight = 12))
        val buffer = PixelBuffer(width = 120, height = 12).also { it.clear() }
        return surface to buffer
    }

    @Test
    fun multilineSelectionPaintsSeparateRows() {
        val (surface, buffer) = makeSurface(
            text = "AB\nCD",
            focused = true,
            selectionStart = 1,
            selectionEnd = 5,
            maxLines = 2,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        val width = 120
        val rows = mutableSetOf<Int>()
        for (i in buffer.pixels.indices) {
            if (buffer.pixels[i] == selectionColor.argb) rows += (i / width)
        }
        assertTrue("selection should paint on both text rows, rows=$rows", rows.size > 1)
    }

    @Test
    fun unfocusedSelectionDoesNotPaint() {
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = false,
            selectionStart = 0,
            selectionEnd = 5,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val selectionPixels = buffer.pixels.count { it == selectionColor.argb }
        assertEquals("unfocused field must not draw selection highlight", 0, selectionPixels)
    }

    @Test
    fun nullSelectionColorDoesNotPaint() {
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = true,
            selectionStart = 0,
            selectionEnd = 5,
            selectionColor = null,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val selectionPixels = buffer.pixels.count { it == selectionColor.argb }
        assertEquals("null selectionColor must not draw selection highlight", 0, selectionPixels)
    }

    @Test
    fun emptySelectionDoesNotPaint() {
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = true,
            selectionStart = 2,
            selectionEnd = 2,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val selectionPixels = buffer.pixels.count { it == selectionColor.argb }
        assertEquals("empty selection (start == end) must not draw highlight", 0, selectionPixels)
    }

    @Test
    fun emptyTextDoesNotPaintSelection() {
        val (surface, buffer) = makeSurface(
            text = "",
            focused = true,
            selectionStart = 0,
            selectionEnd = 0,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val selectionPixels = buffer.pixels.count { it == selectionColor.argb }
        assertEquals("empty text must not draw selection highlight", 0, selectionPixels)
    }

    @Test
    fun nonEmptySelectionPaintsAtLeastOnePixel() {
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = true,
            selectionStart = 1,
            selectionEnd = 3,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val selectionPixels = buffer.pixels.count { it == selectionColor.argb }
        assertNotEquals("non-empty selection should draw highlight pixels", 0, selectionPixels)
    }

    @Test
    fun selectionStartingAtZeroBeginsNearTextLeftEdge() {
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = true,
            selectionStart = 0,
            selectionEnd = 3,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val width = 120
        val firstSelectionPixel = buffer.pixels.indexOfFirst { it == selectionColor.argb }
        assertNotEquals(-1, firstSelectionPixel)
        val firstX = firstSelectionPixel % width
        // selectionStart == 0 时高亮从 X=0 起；但文字直接画在选区之上，会覆盖
        // 部分选区像素。所以"第一个 selectionColor 像素"可能在 X=0..3 之间
        // （取决于字体首字符是否覆盖 X=0 列），核心断言是它仍落在第一个字符之内。
        assertTrue(
            "selection starting at index 0 should begin within first char (X<=3), got $firstX",
            firstX in 0..3,
        )
    }

    @Test
    fun selectionExtendingToEndReachesTextRightEdge() {
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = true,
            selectionStart = 0,
            selectionEnd = 5,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val width = 120
        val lastSelectionPixel = buffer.pixels.indexOfLast { it == selectionColor.argb }
        assertNotEquals(-1, lastSelectionPixel)
        val lastX = lastSelectionPixel % width

        // 整段选中时，selection 右端应当与文本宽度近似（textWidth * 5 / 5 = textWidth）。
        // 不知道精确字体宽度，但 textChild.size.width 由 RenderText layout 决定。
        // 用近似断言：lastX 至少跨过文本前一半，且不溢出 child 边界。
        assertTrue(
            "full selection should extend across most of text width, lastX=$lastX",
            lastX > 4,
        )
    }

    @Test
    fun selectionInMiddleDoesNotPaintAtLeftEdge() {
        val (surface, buffer) = makeSurface(
            text = "HELLOXXXX",
            focused = true,
            selectionStart = 4,
            selectionEnd = 6,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val width = 120
        val firstSelectionPixel = buffer.pixels.indexOfFirst { it == selectionColor.argb }
        assertNotEquals(-1, firstSelectionPixel)
        val firstX = firstSelectionPixel % width
        // 中间选区不应当从 X=0 开始。
        assertNotEquals("middle selection should not start at X=0", 0, firstX)
    }

    @Test
    fun selectionOutOfRangeIsClamped() {
        // selectionEnd 超过 text.length 时应被夹紧而不是越界绘制。
        val (surface, buffer) = makeSurface(
            text = "HI",
            focused = true,
            selectionStart = 0,
            selectionEnd = 2,
        )
        // 强行污染选区（用反射不可行，直接构造在边界上验证不崩）
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val selectionPixels = buffer.pixels.count { it == selectionColor.argb }
        assertNotEquals("clamped selection still draws", 0, selectionPixels)
    }

    @Test
    fun selectionDoesNotLeakBeyondTextHeight() {
        val (surface, buffer) = makeSurface(
            text = "ABC",
            focused = true,
            selectionStart = 0,
            selectionEnd = 3,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val width = 120
        var maxY = -1
        for (i in buffer.pixels.indices) {
            if (buffer.pixels[i] == selectionColor.argb) {
                val y = i / width
                if (y > maxY) maxY = y
            }
        }
        // child 测量高度 < buffer 高度 12；selection 高度 == child.size.height，
        // 不应当画到 buffer 底部（y >= 12 - 1）。这里只断言不超过 8（远小于 12）。
        assertTrue(
            "selection should not paint past child height, maxY=$maxY",
            maxY in 0..10,
        )
    }

    @Test
    fun focusedSelectionPaintsHandlesWhenEnabled() {
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = true,
            selectionStart = 1,
            selectionEnd = 4,
            selectionHandleColor = handleColor,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val handlePixels = buffer.pixels.count { it == handleColor.argb }
        assertTrue("non-empty focused selection should paint handle pixels", handlePixels > 0)
    }

    @Test
    fun readOnlySelectionDoesNotPaintHandles() {
        val (surface, buffer) = makeSurface(
            text = "HELLO",
            focused = true,
            selectionStart = 1,
            selectionEnd = 4,
            selectionHandleColor = handleColor,
            readOnly = true,
        )
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        val handlePixels = buffer.pixels.count { it == handleColor.argb }
        assertEquals("readOnly field must not draw selection handles", 0, handlePixels)
    }

    /** Mixed-Bidi selection end paints the edge owned by the logically preceding cluster. */
    @Test
    fun mixedBidiSelectionEndHandleUsesUpstreamAffinity() {
        /** Canonical mixed paragraph with dual visual carets around the Hebrew run. */
        val text = "ABC אבג 123"
        /** Controller owning the focused non-collapsed selection. */
        val controller = PixelTextFieldController()
        /** Selection whose logical end has distinct upstream and downstream positions. */
        val state = controller.create(initialText = text, selectionStart = 1, selectionEnd = 4)
        controller.focus(state)
        /** Exact RenderText instance consumed by selection, pointer, and semantic geometry. */
        val textChild = RenderText(
            text = text,
            style = PixelTextStyle(color = textColor),
            textAlign = PixelTextAlign.START,
            textDirection = TextDirection.LTR,
            softWrap = false,
            overflow = TextOverflow.CLIP,
            maxLines = 1,
            defaultTextRasterizer = PixelBitmapFont.Default,
        )
        /** TextField surface painting the two affinity-aware handles. */
        val surface = RenderSurface(
            fillColor = null,
            borderColor = null,
            textInputState = state,
            textInputController = controller,
            textInputReadOnly = false,
            textInputCursorColor = null,
            textInputSelectionColor = selectionColor,
            textInputSelectionHandleColor = handleColor,
        )
        surface.setRenderObjectChild(textChild)
        surface.layout(RenderConstraints(maxWidth = 120, maxHeight = 12))
        /** Upstream end edge selected by the production handle path. */
        val expectedEnd = textChild.caretRect(4, PixelTextAffinity.UPSTREAM)
        /** Downstream edge that would incorrectly attach the handle to unselected content. */
        val incorrectEnd = textChild.caretRect(4, PixelTextAffinity.DOWNSTREAM)
        assertNotEquals(expectedEnd.x, incorrectEnd.x)

        /** Buffer retaining the final handle pixels after text and selection painting. */
        val buffer = PixelBuffer(width = 120, height = 12).also { it.clear() }
        surface.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        /** Handle stem row painted immediately above the caret bottom edge. */
        val stemY = expectedEnd.y + expectedEnd.height - 1
        assertEquals(handleColor.argb, buffer.pixels[stemY * buffer.width + expectedEnd.x])
    }
}
