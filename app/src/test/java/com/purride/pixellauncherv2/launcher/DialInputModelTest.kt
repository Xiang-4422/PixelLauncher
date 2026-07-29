package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialInputModelTest {

    @Test
    fun appendAcceptsDialableCharsOnly() {
        assertEquals("1", DialInputModel.append("", '1'))
        assertEquals("1*", DialInputModel.append("1", '*'))
        assertEquals("1*#", DialInputModel.append("1*", '#'))
        assertEquals("1,", DialInputModel.append("1", ','))
        // 字母等非可拨字符原样返回。
        assertEquals("1", DialInputModel.append("1", 'A'))
        assertEquals("1", DialInputModel.append("1", ' '))
    }

    @Test
    fun appendKeepsPlusPrefixOnlyAtStart() {
        assertEquals("+", DialInputModel.append("", '+'))
        assertEquals("+86", DialInputModel.append("+8", '6'))
        // 中间的 + 无意义，忽略。
        assertEquals("86", DialInputModel.append("86", '+'))
    }

    @Test
    fun appendStopsAtMaxLength() {
        val full = "1".repeat(DialInputModel.MAX_LENGTH)
        assertEquals(full, DialInputModel.append(full, '2'))
        val nearlyFull = "1".repeat(DialInputModel.MAX_LENGTH - 1)
        assertEquals(nearlyFull + "2", DialInputModel.append(nearlyFull, '2'))
    }

    @Test
    fun backspaceRemovesLastCharAndToleratesEmpty() {
        assertEquals("13", DialInputModel.backspace("138"))
        assertEquals("", DialInputModel.backspace("1"))
        assertEquals("", DialInputModel.backspace(""))
    }

    @Test
    fun isCallableRequiresAtLeastOneDialableChar() {
        assertTrue(DialInputModel.isCallable("10086"))
        assertTrue(DialInputModel.isCallable("*#06#"))
        assertFalse(DialInputModel.isCallable(""))
    }

    @Test
    fun displayTextFallsBackToPlaceholder() {
        assertEquals("10086", DialInputModel.displayText("10086"))
        assertEquals("ENTER NUMBER", DialInputModel.displayText(""))
        assertEquals("DIAL", DialInputModel.displayText("", placeholder = "DIAL"))
    }

    @Test
    fun displayTextGroupsMainlandMobileOnly() {
        assertEquals("138 0013 8000", DialInputModel.displayText("13800138000"))
        // 位数不足、非 1 开头、含符号的一律原样显示
        assertEquals("1380013800", DialInputModel.displayText("1380013800"))
        assertEquals("02112345678", DialInputModel.displayText("02112345678"))
        assertEquals("+8613800138", DialInputModel.displayText("+8613800138"))
    }

    @Test
    fun truncateKeepingTailHidesHeadNotTail() {
        // 尾号是核对依据，必须留在可见范围内
        assertEquals("..8000", DialInputModel.truncateKeepingTail("13800138000", maxChars = 6))
        assertEquals("13800138000", DialInputModel.truncateKeepingTail("13800138000", maxChars = 11))
        assertEquals("13800138000", DialInputModel.truncateKeepingTail("13800138000", maxChars = 99))
        // 放不下省略号时给尾号本身
        assertEquals("0", DialInputModel.truncateKeepingTail("13800138000", maxChars = 1))
        assertEquals("13800138000", DialInputModel.truncateKeepingTail("13800138000", maxChars = 0))
    }

    /**
     * 匹配槽文本永不为空——空串在渲染层退化成 0 高度，键盘会随每次按键上下弹跳。
     * 这是 dialMatchSlot 注释里承诺的不变量，此前只写在注释里没有实现。
     */
    @Test
    fun matchSlotTextNeverCollapsesToEmpty() {
        assertTrue(DialInputModel.matchSlotText(null, null, 0).isNotEmpty())
        assertTrue(DialInputModel.matchSlotText("", "13800138000", 0).isNotEmpty())
        assertTrue(DialInputModel.matchSlotText("   ", null, 3).isNotEmpty())
    }

    @Test
    fun matchSlotTextShowsNameWithNumberOrExtraCount() {
        assertEquals("ALICE  13800138000", DialInputModel.matchSlotText("Alice", "13800138000", 0))
        // 多命中时让位给条数：号码此时没有区分度
        assertEquals("ALICE  +2", DialInputModel.matchSlotText("Alice", "13800138000", 2))
        // 没有号码也要显示姓名，不能回落到占位
        assertEquals("ALICE", DialInputModel.matchSlotText("Alice", null, 0))
        assertEquals("ALICE", DialInputModel.matchSlotText("Alice", "  ", 0))
    }

    @Test
    fun digitForKeyCodeMapsNumberRowNumpadAndSymbols() {
        assertEquals('0', DialInputModel.digitForKeyCode(7))
        assertEquals('9', DialInputModel.digitForKeyCode(16))
        assertEquals('*', DialInputModel.digitForKeyCode(17))
        assertEquals('#', DialInputModel.digitForKeyCode(18))
        assertEquals('+', DialInputModel.digitForKeyCode(81))
        assertEquals('0', DialInputModel.digitForKeyCode(144))
        assertEquals('9', DialInputModel.digitForKeyCode(153))
        // 非拨号键（此处为 KEYCODE_A）不产出字符。
        assertNull(DialInputModel.digitForKeyCode(29))
    }

    @Test
    fun keypadRowsCoverTwelveKeysInDialerOrder() {
        val keys = DialInputModel.keypadRows.flatten()
        assertEquals(12, keys.size)
        assertEquals(listOf('1', '2', '3'), DialInputModel.keypadRows.first())
        assertEquals(listOf('*', '0', '#'), DialInputModel.keypadRows.last())
        assertTrue(DialInputModel.keypadRows.all { row -> row.size == 3 })
    }

    @Test
    fun coerceCallPageKeepsIndexInRange() {
        assertEquals(CallPageIndex.RECENT, CallPageIndex.coerce(-1))
        assertEquals(CallPageIndex.DIAL, CallPageIndex.coerce(9))
        assertEquals(CallPageIndex.DIAL, CallPageIndex.coerce(CallPageIndex.DIAL))
    }
}
