package com.purride.pixelui.internal

import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.Axis
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.TextDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证 [PixelLayoutMappings.kt] 中 RTL 文本方向下的映射镜像逻辑。
 *
 * 参考：
 * - [AlignmentDirectional.toPixelAlignment] —— START/END 在 RTL 下对称翻转
 * - [MainAxisAlignment.toPixelMainAxisAlignment] —— 水平主轴在 RTL 下 START/END 互换
 * - [CrossAxisAlignment.toPixelCrossAxisAlignment] —— 垂直轴在 RTL 下 START/END 互换
 */
class PixelLayoutMappingsRTLTest {

    // ── AlignmentDirectional ──────────────────────────────────────────────

    @Test
    fun `TOP_START mirrors to TOP_END in RTL`() {
        val result = AlignmentDirectional.TOP_START.toPixelAlignment(TextDirection.RTL)
        assertEquals(PixelAlignment.TOP_END, result)
    }

    @Test
    fun `TOP_END mirrors to TOP_START in RTL`() {
        val result = AlignmentDirectional.TOP_END.toPixelAlignment(TextDirection.RTL)
        assertEquals(PixelAlignment.TOP_START, result)
    }

    @Test
    fun `CENTER_START mirrors to CENTER_END in RTL`() {
        val result = AlignmentDirectional.CENTER_START.toPixelAlignment(TextDirection.RTL)
        assertEquals(PixelAlignment.CENTER_END, result)
    }

    @Test
    fun `CENTER alignment is unchanged across directions`() {
        val ltr = AlignmentDirectional.CENTER.toPixelAlignment(TextDirection.LTR)
        val rtl = AlignmentDirectional.CENTER.toPixelAlignment(TextDirection.RTL)
        assertEquals(PixelAlignment.CENTER, ltr)
        assertEquals(PixelAlignment.CENTER, rtl)
    }

    @Test
    fun `directional alignment maps correctly in LTR`() {
        // LTR：START → START，END → END，不做镜像
        assertEquals(
            PixelAlignment.TOP_START,
            AlignmentDirectional.TOP_START.toPixelAlignment(TextDirection.LTR),
        )
        assertEquals(
            PixelAlignment.TOP_END,
            AlignmentDirectional.TOP_END.toPixelAlignment(TextDirection.LTR),
        )
    }

    // ── MainAxisAlignment（水平轴 + RTL）──────────────────────────────────

    @Test
    fun `mainAxisAlignment START maps to END in horizontal RTL`() {
        val result = MainAxisAlignment.START.toPixelMainAxisAlignment(
            axis = Axis.HORIZONTAL,
            direction = TextDirection.RTL,
        )
        assertEquals(PixelMainAxisAlignment.END, result)
    }

    @Test
    fun `mainAxisAlignment END maps to START in horizontal RTL`() {
        val result = MainAxisAlignment.END.toPixelMainAxisAlignment(
            axis = Axis.HORIZONTAL,
            direction = TextDirection.RTL,
        )
        assertEquals(PixelMainAxisAlignment.START, result)
    }

    @Test
    fun `mainAxisAlignment CENTER unchanged in horizontal RTL`() {
        val result = MainAxisAlignment.CENTER.toPixelMainAxisAlignment(
            axis = Axis.HORIZONTAL,
            direction = TextDirection.RTL,
        )
        assertEquals(PixelMainAxisAlignment.CENTER, result)
    }

    @Test
    fun `mainAxisAlignment START unchanged in vertical RTL`() {
        // 垂直轴不受文本方向影响
        val result = MainAxisAlignment.START.toPixelMainAxisAlignment(
            axis = Axis.VERTICAL,
            direction = TextDirection.RTL,
        )
        assertEquals(PixelMainAxisAlignment.START, result)
    }

    // ── CrossAxisAlignment（垂直轴 + RTL）────────────────────────────────

    @Test
    fun `crossAxisAlignment START maps to END in vertical RTL`() {
        val result = CrossAxisAlignment.START.toPixelCrossAxisAlignment(
            axis = Axis.VERTICAL,
            direction = TextDirection.RTL,
        )
        assertEquals(PixelCrossAxisAlignment.END, result)
    }

    @Test
    fun `crossAxisAlignment END maps to START in vertical RTL`() {
        val result = CrossAxisAlignment.END.toPixelCrossAxisAlignment(
            axis = Axis.VERTICAL,
            direction = TextDirection.RTL,
        )
        assertEquals(PixelCrossAxisAlignment.START, result)
    }

    @Test
    fun `crossAxisAlignment CENTER unchanged in vertical RTL`() {
        val result = CrossAxisAlignment.CENTER.toPixelCrossAxisAlignment(
            axis = Axis.VERTICAL,
            direction = TextDirection.RTL,
        )
        assertEquals(PixelCrossAxisAlignment.CENTER, result)
    }

    @Test
    fun `crossAxisAlignment START unchanged in horizontal RTL`() {
        // 水平轴不受 RTL 影响（只有垂直轴受影响）
        val result = CrossAxisAlignment.START.toPixelCrossAxisAlignment(
            axis = Axis.HORIZONTAL,
            direction = TextDirection.RTL,
        )
        assertEquals(PixelCrossAxisAlignment.START, result)
    }
}
