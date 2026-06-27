package com.purride.pixelui

import com.purride.pixelui.state.PixelTextFieldController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTextFieldControllerTest {

    private val controller = PixelTextFieldController()

    @Test
    fun updateTextAlsoUpdatesSelection() {
        val state = controller.create(initialText = "ABC")

        controller.updateText(
            state = state,
            text = "HELLO",
            selectionStart = 2,
            selectionEnd = 4,
        )

        assertEquals("HELLO", state.text)
        assertEquals(2, state.selectionStart)
        assertEquals(4, state.selectionEnd)
    }

    @Test
    fun focusAndBlurToggleFocusedFlag() {
        val state = controller.create()

        controller.focus(state)
        assertTrue(state.isFocused)

        controller.blur(state)
        assertFalse(state.isFocused)
    }

    @Test
    fun requestBlurCancelsFocusThatHasNotBeenApplied() {
        val state = controller.create()

        controller.requestFocus(state)
        assertTrue(state.focusRequested)
        assertFalse(state.blurRequested)

        controller.requestBlur(state)
        assertFalse(state.blurRequested)
        assertFalse(state.focusRequested)
    }

    @Test
    fun repeatedFocusAndBlurRequestsAreIdempotent() {
        val state = controller.create()
        var notifications = 0
        controller.addListener { notifications += 1 }

        controller.requestFocus(state)
        controller.requestFocus(state)
        assertEquals(1, notifications)

        controller.focus(state)
        val focusedNotifications = notifications
        controller.requestFocus(state)
        assertEquals(focusedNotifications, notifications)

        controller.requestBlur(state)
        controller.requestBlur(state)
        assertEquals(focusedNotifications + 1, notifications)
    }

    @Test
    fun clearResetsTextAndMovesSelectionToStart() {
        val state = controller.create(initialText = "HELLO")

        controller.clear(state)

        assertEquals("", state.text)
        assertEquals(0, state.selectionStart)
        assertEquals(0, state.selectionEnd)
    }

    @Test
    fun selectAllMarksWholeTextRange() {
        val state = controller.create(initialText = "PIXEL")

        controller.selectAll(state)

        assertEquals(0, state.selectionStart)
        assertEquals(5, state.selectionEnd)
    }

    @Test
    fun copyCutAndPasteOperateOnCurrentSelection() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "hello world")
        controller.setSelection(state, 0, 5)

        assertEquals("hello", controller.selectedText(state))
        assertEquals("hello", controller.cutSelection(state))
        assertEquals(" world", state.text)
        assertEquals(0, state.selectionStart)

        controller.paste(state, "pixel")
        assertEquals("pixel world", state.text)
        assertEquals(5, state.selectionStart)
        assertEquals(5, state.selectionEnd)
    }

    @Test
    fun selectWordAtSelectsAsciiWordRange() {
        val state = controller.create(initialText = "HELLO pixel_42!")

        controller.selectWordAt(state, index = 8)

        assertEquals(6, state.selectionStart)
        assertEquals(14, state.selectionEnd)
        assertEquals("pixel_42", state.text.substring(state.selectionStart, state.selectionEnd))
    }

    @Test
    fun selectWordAtSelectsSingleCjkCharacter() {
        val state = controller.create(initialText = "你好吗")

        controller.selectWordAt(state, index = 1)

        assertEquals(1, state.selectionStart)
        assertEquals(2, state.selectionEnd)
    }

    @Test
    fun selectWordAtWhitespaceCollapsesToCaret() {
        val state = controller.create(initialText = "A B")

        controller.selectWordAt(state, index = 1)

        assertEquals(1, state.selectionStart)
        assertEquals(1, state.selectionEnd)
    }

    @Test
    fun selectWordAtPunctuationSelectsOnlyPunctuationCharacter() {
        val state = controller.create(initialText = "A,B")

        controller.selectWordAt(state, index = 1)

        assertEquals(1, state.selectionStart)
        assertEquals(2, state.selectionEnd)
        assertEquals(",", state.text.substring(state.selectionStart, state.selectionEnd))
    }

    @Test
    fun selectWordAtEndIndexSelectsPreviousWord() {
        val state = controller.create(initialText = "A BETA")

        controller.selectWordAt(state, index = state.text.length)

        assertEquals(2, state.selectionStart)
        assertEquals(6, state.selectionEnd)
        assertEquals("BETA", state.text.substring(state.selectionStart, state.selectionEnd))
    }

    @Test
    fun selectWordAtOutOfRangeNegativeIndexClampsToStart() {
        val state = controller.create(initialText = "ALPHA B")

        controller.selectWordAt(state, index = -20)

        assertEquals(0, state.selectionStart)
        assertEquals(5, state.selectionEnd)
        assertEquals("ALPHA", state.text.substring(state.selectionStart, state.selectionEnd))
    }

    @Test
    fun setSelectionClampsIntoCurrentTextRange() {
        val state = controller.create(initialText = "ABCD")

        controller.setSelection(
            state = state,
            selectionStart = -2,
            selectionEnd = 99,
        )

        assertEquals(0, state.selectionStart)
        assertEquals(4, state.selectionEnd)
    }

    @Test
    fun createClampsInitialSelectionIntoTextRange() {
        val state = controller.create(
            initialText = "ABC",
            selectionStart = -10,
            selectionEnd = 99,
        )

        assertEquals(0, state.selectionStart)
        assertEquals(3, state.selectionEnd)
    }

    @Test
    fun updateTextClampsSelectionIntoNewTextRange() {
        val state = controller.create(initialText = "LONG")

        controller.updateText(
            state = state,
            text = "AB",
            selectionStart = 8,
            selectionEnd = 12,
        )

        assertEquals(2, state.selectionStart)
        assertEquals(2, state.selectionEnd)
    }

    @Test
    fun saveAndRestoreStateKeepsTextSelectionAndClearsComposition() {
        val source = controller.create(initialText = "PIXEL", selectionStart = 1, selectionEnd = 4)
        controller.updateComposition(source, compositionStart = 1, compositionEnd = 3)
        val savedState = controller.saveState(source)
        val restored = controller.create(initialText = "OLD")

        controller.restoreState(restored, savedState)

        assertEquals("PIXEL", restored.text)
        assertEquals(1, restored.selectionStart)
        assertEquals(4, restored.selectionEnd)
        assertEquals(-1, restored.compositionStart)
        assertEquals(-1, restored.compositionEnd)
    }

    // ── 多行 selection clamp（§14 V2）──────────────────────────────────────

    /**
     * 多行文本里 selection 用扁平 index（含 \n）；clamp 必须按 text.length
     * 整体处理，不按行号。验证选区可以横跨换行符。
     */
    @Test
    fun multilineSelectionAcrossNewlineIsPreserved() {
        val state = controller.create(initialText = "AB\nCD\nEF")

        controller.setSelection(state = state, selectionStart = 1, selectionEnd = 7)
        assertEquals(1, state.selectionStart)
        assertEquals(7, state.selectionEnd)
        // 横跨两个 \n
        assertEquals(
            "B\nCD\nE",
            state.text.substring(state.selectionStart, state.selectionEnd),
        )
    }

    /**
     * caret 落在 \n 索引上：与落在普通字符索引上等价，不应被特殊裁掉。
     */
    @Test
    fun caretOnNewlineIndexIsValid() {
        val state = controller.create(initialText = "AB\nCD")
        // text indices: A=0 B=1 \n=2 C=3 D=4 ; length = 5
        controller.setSelection(state = state, selectionStart = 2, selectionEnd = 2)
        assertEquals(2, state.selectionStart)
        assertEquals(2, state.selectionEnd)
    }

    /**
     * 全是换行符的文本：text.length == 行数；clamp 仍按扁平 length 处理。
     */
    @Test
    fun newlineOnlyTextAcceptsClampedSelection() {
        val state = controller.create(initialText = "\n\n\n")
        controller.setSelection(state = state, selectionStart = 99, selectionEnd = 99)
        assertEquals(3, state.selectionStart)
        assertEquals(3, state.selectionEnd)
    }

    /**
     * 倒序 selection（end < start）：当前合约是把 end 抬到 start 处，
     * 等价于退化成 caret，不抛异常。这条单测把这个合约钉死避免回归。
     */
    @Test
    fun invertedSelectionCollapsesToStart() {
        val state = controller.create(initialText = "ABCDEF")
        controller.setSelection(state = state, selectionStart = 4, selectionEnd = 1)
        assertEquals(4, state.selectionStart)
        assertEquals(
            "inverted selection collapses to caret at start",
            4,
            state.selectionEnd,
        )
    }

    /**
     * 多行 text 缩短后，原本横跨换行的 selection 落到新 text 末尾，不能越界。
     */
    @Test
    fun multilineShrinkClampsSelectionToNewEnd() {
        val state = controller.create(initialText = "LINE1\nLINE2\nLINE3")
        controller.setSelection(state = state, selectionStart = 6, selectionEnd = 11)

        controller.updateText(state = state, text = "LINE1", selectionStart = 6, selectionEnd = 11)
        assertEquals("LINE1", state.text)
        // 新长度 5；旧 selection 全部夹到末尾
        assertEquals(5, state.selectionStart)
        assertEquals(5, state.selectionEnd)
    }
}
