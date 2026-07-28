package com.purride.pixelui.internal

import com.purride.pixelui.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证右对齐 TextField 为文字、空隙和最右侧光标保留稳定几何。 */
class TextFieldEndCursorGapTest {

    /** 零表面 padding 时仍应保留 1px 空隙和 1px 光标列。 */
    @Test
    fun endAlignment_zeroSurfacePadding_reservesGapAndCursorColumns() {
        assertEquals(
            2,
            resolveTextFieldTextPaddingRight(
                textAlign = TextAlign.END,
                surfacePaddingRight = 0,
            ),
        )
    }

    /** 更大的显式表面 padding 不应被最小尾距规则缩小。 */
    @Test
    fun endAlignment_largerSurfacePadding_isPreserved() {
        assertEquals(
            4,
            resolveTextFieldTextPaddingRight(
                textAlign = TextAlign.END,
                surfacePaddingRight = 4,
            ),
        )
    }

    /** 左对齐和居中不需要末端光标专用尾距。 */
    @Test
    fun nonEndAlignment_hasNoTextTailPadding() {
        assertEquals(0, resolveTextFieldTextPaddingRight(TextAlign.START, surfacePaddingRight = 4))
        assertEquals(0, resolveTextFieldTextPaddingRight(TextAlign.CENTER, surfacePaddingRight = 4))
    }
}
