package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelClusterTextRasterizer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelFontMetrics
import com.purride.pixelui.PixelGraphemeBoundaryMap
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PixelTextOverflow.ELLIPSIS_START] 保留末尾、省略号置于开头。
 *
 * 存在的理由：正在输入的电话号码、文件路径、长 ID 都是**末位才是核对依据**的文本，
 * 默认的尾部省略会把用户刚按下的字符藏起来。这里锁住"保留哪一端"这个语义，
 * 以及截断必须发生在完整 grapheme 边界上。
 */
class PixelParagraphEllipsisStartTest {

    /** 每个非空 cluster 恰好 1 像素宽，便于按字符数推算可用宽度。 */
    private val rasterizer = OnePixelPerClusterRasterizer()

    private val style = PixelTextStyle(color = PixelColor.White, textRasterizer = rasterizer)

    @Test
    fun ellipsisStartKeepsTheTailAndPutsDotsInFront() {
        // 宽度 6 = "..." 占 3 + 尾部 3 个字符
        val text = layoutText("13800138000", availableWidth = 6, overflow = PixelTextOverflow.ELLIPSIS_START)

        assertEquals("...000", text)
    }

    @Test
    fun defaultEllipsisStillKeepsTheHead() {
        val text = layoutText("13800138000", availableWidth = 6, overflow = PixelTextOverflow.ELLIPSIS)

        // 对照组：默认策略保留开头，末位被丢弃——这正是号码行不能用它的原因。
        assertEquals("138...", text)
    }

    @Test
    fun textThatFitsIsNotEllipsizedFromEitherEnd() {
        assertEquals(
            "10086",
            layoutText("10086", availableWidth = 5, overflow = PixelTextOverflow.ELLIPSIS_START),
        )
        assertEquals(
            "10086",
            layoutText("10086", availableWidth = 99, overflow = PixelTextOverflow.ELLIPSIS_START),
        )
    }

    /** 宽度连省略号都放不下时不得渲染半个省略号，也不得崩。 */
    @Test
    fun widthTooNarrowForEllipsisRendersNothing() {
        assertEquals(
            "",
            layoutText("13800138000", availableWidth = 2, overflow = PixelTextOverflow.ELLIPSIS_START),
        )
    }

    /** 截断只能发生在完整 grapheme 边界：组合字符不得被劈开。 */
    @Test
    fun ellipsisStartNeverSplitsACombiningCluster() {
        // a b c d + "e\u0301"（组合成一个 cluster）= 5 个 cluster，宽 5 > 4。
        // 宽度只够省略号（3）+ 1 个完整 cluster，保留的必须是整个组合簇而非半个。
        val text = layoutText(
            "abcde\u0301",
            availableWidth = 4,
            overflow = PixelTextOverflow.ELLIPSIS_START,
        )

        assertEquals("...e\u0301", text)
        assertTrue("组合符必须与基字符一起保留", text.endsWith("e\u0301"))
    }

    /** CLIP 不生成省略号，与新枚举项互不影响。 */
    @Test
    fun clipRemainsUnaffected() {
        val layout = layout("13800138000", availableWidth = 6, overflow = PixelTextOverflow.CLIP)

        assertTrue(
            "CLIP 不得产生合成省略号簇",
            layout.lines.single().visualClusters.none { cluster -> cluster.isSynthetic },
        )
    }

    private fun layoutText(
        text: String,
        availableWidth: Int,
        overflow: PixelTextOverflow,
    ): String = layout(text, availableWidth, overflow)
        .lines
        .single()
        .visualClusters
        .joinToString(separator = "") { cluster -> cluster.renderText }

    private fun layout(
        text: String,
        availableWidth: Int,
        overflow: PixelTextOverflow,
    ): PixelParagraphLayout = PixelParagraphLayouter.layout(
        input = PixelParagraphInput(
            spans = listOf(PixelTextSpan(text, style)),
            textAlign = PixelTextAlign.START,
            textDirection = TextDirection.LTR,
            // 号码行的真实配置：单行、不折行。
            softWrap = false,
            overflow = overflow,
            maxLines = 1,
            defaultTextRasterizer = rasterizer,
        ),
        availableWidth = availableWidth,
    )

    /** 每个完整 grapheme 恰好 1 逻辑像素宽、1 行高，便于按字符数推算可用宽度。 */
    private class OnePixelPerClusterRasterizer : PixelClusterTextRasterizer {
        override fun measureText(text: String): Int = PixelGraphemeBoundaryMap(text).graphemeCount

        override fun measureHeight(text: String): Int = 1

        override fun canRasterizeCluster(cluster: String): Boolean = cluster.isNotEmpty()

        override fun fontMetrics(text: String): PixelFontMetrics = PixelFontMetrics(1, 0, 0, 1, 0, 0)

        override fun drawText(
            buffer: PixelBuffer,
            text: String,
            x: Int,
            y: Int,
            color: PixelColor,
        ) {
            if (text.isNotEmpty()) buffer.setPixel(x, y, color)
        }
    }
}
