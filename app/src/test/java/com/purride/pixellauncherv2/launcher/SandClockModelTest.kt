package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTextRasterizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SandClockModelTest {

    @Test
    fun phaseMachineFollowsDisplayCollapseReformCycle() {
        // DISPLAY 只被分钟变化打断
        assertEquals(
            SandClockModel.Phase.DISPLAY,
            SandClockModel.nextPhase(SandClockModel.Phase.DISPLAY, 99_999L, minuteChanged = false),
        )
        assertEquals(
            SandClockModel.Phase.COLLAPSE,
            SandClockModel.nextPhase(SandClockModel.Phase.DISPLAY, 0L, minuteChanged = true),
        )
        // COLLAPSE 与 REFORM 按时长推进，不响应分钟变化（动画不被打断）
        assertEquals(
            SandClockModel.Phase.COLLAPSE,
            SandClockModel.nextPhase(SandClockModel.Phase.COLLAPSE, 100L, minuteChanged = true),
        )
        assertEquals(
            SandClockModel.Phase.REFORM,
            SandClockModel.nextPhase(
                SandClockModel.Phase.COLLAPSE,
                SandClockModel.COLLAPSE_DURATION_MS,
                minuteChanged = false,
            ),
        )
        assertEquals(
            SandClockModel.Phase.DISPLAY,
            SandClockModel.nextPhase(
                SandClockModel.Phase.REFORM,
                SandClockModel.REFORM_DURATION_MS,
                minuteChanged = false,
            ),
        )
    }

    @Test
    fun reformProgressClampsToUnitRange() {
        assertEquals(0f, SandClockModel.reformProgress(0L))
        assertEquals(1f, SandClockModel.reformProgress(SandClockModel.REFORM_DURATION_MS))
        assertEquals(1f, SandClockModel.reformProgress(SandClockModel.REFORM_DURATION_MS * 3))
    }

    @Test
    fun clockTextFallsBackToPlaceholder() {
        assertEquals("18:42", SandClockModel.clockText("18:42"))
        assertEquals("--:--", SandClockModel.clockText(""))
        assertEquals("--:--", SandClockModel.clockText("   "))
    }

    // ── Seed renderer ─────────────────────────────────────────────────────────

    @Test
    fun upscaleExpandsEachInkPixelToSquareBlock() {
        val source = PixelBuffer(width = 2, height = 1)
        source.setPixel(0, 0, PixelColor.White)

        val scaled = SandClockSeedRenderer.upscale(source, factor = 3)

        assertEquals(6, scaled.width)
        assertEquals(3, scaled.height)
        // 左半 3×3 全亮、右半全暗
        for (y in 0 until 3) {
            for (x in 0 until 3) {
                assertEquals(PixelColor.White.argb, scaled.pixels[y * 6 + x])
                assertEquals(0, scaled.pixels[y * 6 + 3 + x])
            }
        }
    }

    @Test
    fun scaleForPicksTheTighterBudgetAndAllowsOneAsFloor() {
        // 宽预算 180*0.8/30=4，高预算 390*0.3/14=8 → 取 4
        assertEquals(4, SandClockSeedRenderer.scaleFor(30, 14, fieldWidth = 180, fieldHeight = 390))
        // 预算不足 1x 但场地放得下 → 兜底 1
        assertEquals(1, SandClockSeedRenderer.scaleFor(30, 14, fieldWidth = 36, fieldHeight = 40))
        // 连 1x 都放不下 → 0
        assertEquals(0, SandClockSeedRenderer.scaleFor(30, 14, fieldWidth = 20, fieldHeight = 10))
    }

    @Test
    fun renderTimeSeedCentersInkInUpperThirdOfField() {
        val seed = SandClockSeedRenderer.renderTimeSeed(
            text = "8",
            rasterizer = FakeGlyphRasterizer(),
            fieldWidth = 40,
            fieldHeight = 100,
            color = PixelColor.White,
        )

        assertNotNull(seed)
        val ink = buildList {
            for (y in 0 until seed!!.height) {
                for (x in 0 until seed.width) {
                    if (seed.pixels[y * seed.width + x] != 0) add(x to y)
                }
            }
        }
        // FakeGlyphRasterizer 画 2×2 墨迹；宽预算 40*0.8/2=16、高预算 100*0.3/2=15 → scale 15
        assertEquals(30 * 30, ink.size)
        // 水平居中：墨迹左边缘 = (40-30)/2 = 5
        assertEquals(5, ink.minOf { (x, _) -> x })
        // 垂直锚定上三分之一：墨迹中心 y ≈ 100*0.32
        val centerY = (ink.minOf { (_, y) -> y } + ink.maxOf { (_, y) -> y }) / 2
        assertEquals(31, centerY)
    }

    @Test
    fun renderTimeSeedRejectsBlankTextAndImpossibleField() {
        val rasterizer = FakeGlyphRasterizer()
        assertNull(SandClockSeedRenderer.renderTimeSeed(" ", rasterizer, 40, 100, PixelColor.White))
        assertNull(SandClockSeedRenderer.renderTimeSeed("8", rasterizer, 1, 1, PixelColor.White))
    }

    /** 画一个 2×2 实心块的最小栅格器。 */
    private class FakeGlyphRasterizer : PixelTextRasterizer {
        override fun measureText(text: String): Int = 2
        override fun measureHeight(text: String): Int = 2
        override fun drawText(buffer: PixelBuffer, text: String, x: Int, y: Int, color: PixelColor) {
            for (dy in 0 until 2) {
                for (dx in 0 until 2) {
                    buffer.setPixel(x + dx, y + dy, color)
                }
            }
        }
    }
}
