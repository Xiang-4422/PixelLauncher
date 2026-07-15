package com.purride.pixelui

import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldSavedState
import com.purride.pixelui.state.PixelTextFieldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** Blur ends the transient IME composition without rewriting text or moving selection. */
    @Test
    fun blurClearsCompositionBeforeTheNextFocusSession(): Unit {
        /** Decomposed source whose composition is observable independently from selection. */
        val state = controller.create(initialText = "e\u0301", selectionStart = 2)
        controller.focus(state)
        controller.updateComposition(state, compositionStart = 0, compositionEnd = 2)

        controller.blur(state)

        assertFalse(state.isFocused)
        assertEquals("e\u0301", state.text)
        assertEquals(2, state.selectionStart)
        assertEquals(2, state.selectionEnd)
        assertEquals(-1, state.compositionStart)
        assertEquals(-1, state.compositionEnd)
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

    /** 构造器对折叠、非空和倒序 selection 使用同一字素规范化合约。 */
    @Test
    fun constructorNormalizesGraphemeOffsetsAndKeepsLegacyInvertedCollapse() {
        /** UTF-16 长度为 2 的 supplementary 字素，内部 offset 2 距两端等距。 */
        val emoji = "\uD83D\uDE00"
        /** 等距折叠点应按 downstream affinity 吸附到 emoji 之后。 */
        val collapsed = PixelTextFieldState(
            initialText = "A${emoji}B",
            selectionStart = 2,
            selectionEnd = 2,
        )
        assertEquals(3, collapsed.selectionStart)
        assertEquals(3, collapsed.selectionEnd)

        /** 命中 emoji 内部的非空范围必须向外扩展到完整 surrogate pair。 */
        val expanded = PixelTextFieldState(
            initialText = "A${emoji}B",
            selectionStart = 2,
            selectionEnd = 3,
        )
        assertEquals(1, expanded.selectionStart)
        assertEquals(3, expanded.selectionEnd)

        /** 倒序范围保留历史语义：在 start 的最近边界折叠，而不是反转范围。 */
        val inverted = PixelTextFieldState(
            initialText = "A${emoji}B",
            selectionStart = 2,
            selectionEnd = 1,
        )
        assertEquals(3, inverted.selectionStart)
        assertEquals(3, inverted.selectionEnd)
    }

    /** updateText 对 1.0 要求的代表性 Unicode 序列都只产生完整字素 selection。 */
    @Test
    fun updateTextExpandsRepresentativeUnicodeClustersWithoutChangingText() {
        /** 接收每轮 Unicode 样本的可复用状态。 */
        val state = controller.create()
        for (cluster in editingClusters()) {
            /** 在样本两侧放置 ASCII 边界，便于验证扩展范围没有吞掉邻居。 */
            val source = "A${cluster}B"
            /** 单 code-unit 孤立 surrogate 没有内部 offset，其余样本从内部开始命中。 */
            val requestedStart = if (cluster.length == 1) 1 else 2
            /** 终点保持非空，并最多落到样本末端。 */
            val requestedEnd = (requestedStart + 1).coerceAtMost(1 + cluster.length)

            controller.updateText(
                state = state,
                text = source,
                selectionStart = requestedStart,
                selectionEnd = requestedEnd,
            )

            assertEquals("text must not be normalized for ${cluster.toCodeUnitDebug()}", source, state.text)
            assertEquals("selection start for ${cluster.toCodeUnitDebug()}", 1, state.selectionStart)
            assertEquals(
                "selection end for ${cluster.toCodeUnitDebug()}",
                1 + cluster.length,
                state.selectionEnd,
            )
        }
    }

    /** setSelection 同时验证 clamp、最近边界和非空范围向外扩展。 */
    @Test
    fun setSelectionClampsAndSnapsToGraphemeBoundaries() {
        /** keycap 是 ASCII、VS16 与 combining enclosing keycap 组成的单个字素。 */
        val keycap = "1\uFE0F\u20E3"
        /** 待规范化 selection 的状态。 */
        val state = controller.create(initialText = "A${keycap}B")

        controller.setSelection(state, selectionStart = 2, selectionEnd = 3)
        assertEquals(1, state.selectionStart)
        assertEquals(4, state.selectionEnd)

        controller.setSelection(state, selectionStart = -100, selectionEnd = -100)
        assertEquals(0, state.selectionStart)
        assertEquals(0, state.selectionEnd)

        controller.setSelection(state, selectionStart = 100, selectionEnd = 100)
        assertEquals(state.text.length, state.selectionStart)
        assertEquals(state.text.length, state.selectionEnd)
    }

    /** composition 非空时向外扩展，无效或空范围清除，且不改写原文。 */
    @Test
    fun compositionExpandsWholeClusterAndInvalidRangesClearIt() {
        /** 多 code-point emoji family，用于验证 composition 不停在 ZWJ 序列内部。 */
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"
        /** family 两侧带邻居的原始文本。 */
        val source = "A${family}B"
        /** 接收 composition 的状态。 */
        val state = controller.create(initialText = source)

        controller.updateText(
            state = state,
            text = source,
            selectionStart = 2,
            selectionEnd = 2,
            compositionStart = 2,
            compositionEnd = 1 + family.length - 1,
        )
        assertEquals(1, state.selectionStart)
        assertEquals(1, state.selectionEnd)
        assertEquals(1, state.compositionStart)
        assertEquals(1 + family.length, state.compositionEnd)
        assertEquals(source, state.text)

        controller.updateComposition(state, compositionStart = 2, compositionEnd = 2)
        assertEquals(-1, state.compositionStart)
        assertEquals(-1, state.compositionEnd)

        controller.updateComposition(state, compositionStart = -1, compositionEnd = source.length)
        assertEquals(-1, state.compositionStart)
        assertEquals(-1, state.compositionEnd)
    }

    /** save/restore 对旧式内部 offset 重新规范化，并继续丢弃瞬态 composition。 */
    @Test
    fun savedStateRestoreNormalizesOffsetsAndClearsComposition() {
        /** 旧快照可能把 caret 留在 supplementary surrogate pair 内部。 */
        val saved = PixelTextFieldSavedState(
            text = "A\uD83D\uDE00B",
            selectionStart = 2,
            selectionEnd = 2,
        )
        /** 预先带 composition 的目标状态，验证 restore 明确清除它。 */
        val restored = controller.create(initialText = "OLD")
        controller.updateComposition(restored, compositionStart = 0, compositionEnd = 2)

        controller.restoreState(restored, saved)

        assertEquals(saved.text, restored.text)
        assertEquals(3, restored.selectionStart)
        assertEquals(3, restored.selectionEnd)
        assertEquals(-1, restored.compositionStart)
        assertEquals(-1, restored.compositionEnd)

        /** 再保存的快照必须只包含稳定端点。 */
        val roundTrip = controller.saveState(restored)
        assertEquals(3, roundTrip.selectionStart)
        assertEquals(3, roundTrip.selectionEnd)
    }

    /** replace/paste 在新文本上重建边界图，并按原样保留 decomposed 内容。 */
    @Test
    fun replaceAndPasteRebuildBoundaryMapWithoutNfcNormalization() {
        /** 两个 emoji 中间插入 ZWJ 后会跨拼接点合成一个 family 字素。 */
        val joined = controller.create(
            initialText = "\uD83D\uDC69\uD83D\uDC67",
            selectionStart = 2,
        )
        assertTrue(controller.replaceSelection(joined, "\u200D"))
        assertEquals("\uD83D\uDC69\u200D\uD83D\uDC67", joined.text)
        assertEquals(joined.text.length, joined.selectionStart)
        assertEquals(joined.text.length, joined.selectionEnd)

        /** combining acute 应继续以 decomposed 两个 code unit 保存。 */
        val decomposed = controller.create(initialText = "e")
        controller.paste(decomposed, "\u0301")
        assertEquals("e\u0301", decomposed.text)
        assertEquals(2, decomposed.text.length)
        assertEquals(2, decomposed.selectionStart)
        assertFalse(controller.replaceSelection(decomposed, ""))
    }

    /** copy/cut 会把内部命中向外扩展，因此 family 只会整体复制和删除。 */
    @Test
    fun copyAndCutOperateOnWholeGraphemeSelection() {
        /** 作为单个扩展字素的 emoji family。 */
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        /** family 两侧保留字符用于观察剪切边界。 */
        val state = controller.create(initialText = "A${family}B")
        controller.setSelection(state, selectionStart = 2, selectionEnd = family.length)

        assertEquals(family, controller.selectedText(state))
        assertEquals(family, controller.cutSelection(state))
        assertEquals("AB", state.text)
        assertEquals(1, state.selectionStart)
        assertEquals(1, state.selectionEnd)
        assertNull(controller.cutSelection(state))
    }

    /** selectWordAt 将非 ASCII 内容视为一个完整字素，并使 CRLF 的空白 caret 保持稳定。 */
    @Test
    fun selectWordUsesWholeGraphemeForEmojiAndCrlf() {
        /** 带 skin-tone modifier 的 emoji 单字素。 */
        val toned = "\uD83D\uDC4B\uD83C\uDFFD"
        /** emoji 两侧的输入文本。 */
        val emojiState = controller.create(initialText = "A${toned}B")
        controller.selectWordAt(emojiState, index = 3)
        assertEquals(1, emojiState.selectionStart)
        assertEquals(1 + toned.length, emojiState.selectionEnd)
        assertEquals(toned, controller.selectedText(emojiState))

        /** CRLF 按 UAX #29 是单字素，内部等距命中按 downstream affinity 折叠到终点。 */
        val crlfState = controller.create(initialText = "A\r\nB")
        controller.selectWordAt(crlfState, index = 2)
        assertEquals(3, crlfState.selectionStart)
        assertEquals(3, crlfState.selectionEnd)
    }

    /** A decomposed ASCII base remains part of its surrounding ASCII word. */
    @Test
    fun selectWordTreatsAsciiBaseWithCombiningMarksAsWordCluster() {
        /** The acute accent shares one EGC with `e` but must not break the ASCII word scan. */
        val word = "cafe\u0301_test"
        /** Punctuation terminates the word after the decomposed cluster sequence. */
        val state = controller.create(initialText = "$word!")

        controller.selectWordAt(state, index = 4)

        assertEquals(0, state.selectionStart)
        assertEquals(word.length, state.selectionEnd)
        assertEquals(word, controller.selectedText(state))
    }

    /** caret move 一次跨越一个字素，且 Boolean 返回值只报告实际 selection 变化。 */
    @Test
    fun caretMovementTraversesWholeClustersAndReportsChanges() {
        /** 由多个 code point 组成但逻辑上只有一个字素的 family。 */
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        /** 从 family 前方开始移动的状态。 */
        val state = controller.create(initialText = "A${family}B", selectionStart = 1)

        assertTrue(controller.moveCaretForward(state))
        assertEquals(1 + family.length, state.selectionStart)
        assertTrue(controller.moveCaretBackward(state))
        assertEquals(1, state.selectionStart)

        assertTrue(controller.moveCaretForward(state, extendSelection = true))
        assertEquals(1, state.selectionStart)
        assertEquals(1 + family.length, state.selectionEnd)
        assertTrue(controller.moveCaretBackward(state))
        assertEquals(1, state.selectionStart)
        assertEquals(1, state.selectionEnd)

        controller.setSelection(state, 0)
        assertFalse(controller.moveCaretBackward(state))
        controller.setSelection(state, state.text.length)
        assertFalse(controller.moveCaretForward(state))
    }

    /** backward/forward delete 对全部代表性序列都只删除一个完整字素。 */
    @Test
    fun deleteBackwardAndForwardNeverSplitSupportedClusters() {
        for (cluster in editingClusters()) {
            /** 光标置于样本之后，用于验证 backward delete。 */
            val backward = controller.create(
                initialText = "A${cluster}B",
                selectionStart = 1 + cluster.length,
            )
            assertTrue("backward delete for ${cluster.toCodeUnitDebug()}", controller.deleteBackward(backward))
            assertEquals("backward text for ${cluster.toCodeUnitDebug()}", "AB", backward.text)
            assertEquals(1, backward.selectionStart)
            assertEquals(1, backward.selectionEnd)

            /** 光标置于样本之前，用于验证 forward delete。 */
            val forward = controller.create(
                initialText = "A${cluster}B",
                selectionStart = 1,
            )
            assertTrue("forward delete for ${cluster.toCodeUnitDebug()}", controller.deleteForward(forward))
            assertEquals("forward text for ${cluster.toCodeUnitDebug()}", "AB", forward.text)
            assertEquals(1, forward.selectionStart)
            assertEquals(1, forward.selectionEnd)
        }
    }

    /** 删除 selection 优先于邻接字素，并清除成功编辑前的 composition。 */
    @Test
    fun deleteSelectionExpandsOutwardAndClearsComposition() {
        /** 用 RI pair 验证国旗字素不会只删除其中一个 regional indicator。 */
        val flag = "\uD83C\uDDE8\uD83C\uDDF3"
        /** selection 和 composition 都命中旗帜内部的状态。 */
        val state = controller.create(initialText = "A${flag}B")
        controller.setSelection(state, selectionStart = 2, selectionEnd = 3)
        controller.updateComposition(state, compositionStart = 2, compositionEnd = 3)

        assertTrue(controller.deleteForward(state))
        assertEquals("AB", state.text)
        assertEquals(1, state.selectionStart)
        assertEquals(1, state.selectionEnd)
        assertEquals(-1, state.compositionStart)
        assertEquals(-1, state.compositionEnd)

        controller.setSelection(state, 0)
        assertFalse(controller.deleteBackward(state))
        controller.setSelection(state, state.text.length)
        assertFalse(controller.deleteForward(state))
    }

    /**
     * 返回 C2 编辑验收覆盖的 Unicode 样本；最后一个故意是孤立 high surrogate，用于证明
     * 非标量旧文本仍以单 UTF-16 code unit 的原子簇确定处理。
     */
    private fun editingClusters(): List<String> {
        return listOf(
            "e\u0301",
            "\uD83D\uDE00",
            "\uD83D\uDC4B\uD83C\uDFFD",
            "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66",
            "\u2708\uFE0F",
            "1\uFE0F\u20E3",
            "\uD83C\uDDE8\uD83C\uDDF3",
            "\r\n",
            "\uD83D",
        )
    }

    /** 把 UTF-16 code unit 转成稳定十六进制文本，便于失败信息显示孤立 surrogate。 */
    private fun String.toCodeUnitDebug(): String {
        return map { codeUnit -> "U+%04X".format(codeUnit.code) }.joinToString(separator = " ")
    }
}
