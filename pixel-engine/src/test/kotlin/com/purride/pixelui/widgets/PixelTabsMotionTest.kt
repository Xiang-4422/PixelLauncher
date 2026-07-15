package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelMotionRole
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.Tabs
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.PixelColorTween
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Tabs selection 交叉变化、稳定 identity 与快速切换资源契约。 */
class PixelTabsMotionTest {
    /** 0→2 的旧/新边框在 0/25/50/75/100 帧交叉变化。 */
    @Test
    fun selectionCrossFadesAcrossPercentFrames() {
        // selectedIndex 由调用方立即拥有，三个 retained 子 key 在整个测试中保持稳定。
        val selected = ValueNotifier(0)
        val tester = PixelTester()
        tester.pumpWidget(
            motionTabs(tester, selected, PixelMotionSettings.Default),
            logicalWidth = 60,
            logicalHeight = 12,
        )
        assertTrue(tester.hasPixel(tabColor(1f)))
        assertTrue(tester.hasPixel(tabColor(0f)))

        selected.value = 2
        beginMotion(tester)
        listOf(0.25f, 0.5f, 0.75f).forEach { incomingProgress ->
            tester.pumpFrame(250)
            assertTrue(tester.hasPixel(tabColor(incomingProgress)))
            assertTrue(tester.hasPixel(tabColor(1f - incomingProgress)))
        }
        tester.pumpFrame(250)
        assertTrue(tester.hasPixel(tabColor(1f)))
        assertTrue(tester.hasPixel(tabColor(0f)))
        assertEquals(0, tester.vsync.activeTickerCount)

        tester.dispose()
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** 快速 0→2→1 从当前每项边框强度继续，最终没有旧 ticker 或旧选中色。 */
    @Test
    fun rapidSelectionRetargetPreservesFrameAndDisposesOnUnmount() {
        // 每一项独立 retarget，保证 outgoing 与 incoming 都不会跳回端点。
        val selected = ValueNotifier(0)
        val tester = PixelTester()
        tester.pumpWidget(
            motionTabs(tester, selected, PixelMotionSettings.Default),
            logicalWidth = 60,
            logicalHeight = 12,
        )
        selected.value = 2
        beginMotion(tester)
        tester.pumpFrame(500)
        val beforeRetarget = tester.renderResult!!.buffer.pixels.copyOf()

        selected.value = 1
        tester.pumpFrame(0)
        assertTrue(beforeRetarget.contentEquals(tester.renderResult!!.buffer.pixels))
        tester.pumpFrame(0)
        tester.pumpFrame(500)
        assertTrue(tester.hasPixel(tabColor(0.25f)))
        assertTrue(tester.hasPixel(tabColor(0.5f)))
        tester.pumpFrame(500)
        assertTrue(tester.hasPixel(tabColor(1f)))
        assertTrue(tester.hasPixel(tabColor(0f)))
        assertEquals(0, tester.vsync.activeTickerCount)

        tester.dispose()
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** reduce motion 下 selectedIndex 同步呈现且不保留任何 selection ticker。 */
    @Test
    fun reduceMotionSelectionIsImmediate() {
        // Selection role 在 reduce motion 下应同步完成。
        val selected = ValueNotifier(0)
        val tester = PixelTester()
        tester.pumpWidget(
            motionTabs(tester, selected, PixelMotionSettings(reduceMotion = true)),
            logicalWidth = 60,
            logicalHeight = 12,
        )
        selected.value = 2
        tester.pumpFrame(0)
        assertTrue(tester.hasPixel(tabColor(1f)))
        assertTrue(tester.hasPixel(tabColor(0f)))
        assertEquals(0, tester.scheduler.pendingCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Selection delay holds both tab colors and `None` skips delay without creating tickers. */
    @Test
    fun selectionDelayAndNonePresetAreAppliedExactly() {
        val delayedSelected = ValueNotifier(0)
        val delayedTester = PixelTester()
        val delayedSelection = selectionSpec().copy(
            duration = 100.milliseconds,
            delay = 100.milliseconds,
        )
        delayedTester.pumpWidget(
            motionTabs(
                tester = delayedTester,
                selected = delayedSelected,
                settings = PixelMotionSettings.Default,
                selection = delayedSelection,
            ),
            logicalWidth = 60,
            logicalHeight = 12,
        )
        delayedSelected.value = 2
        beginMotion(delayedTester)
        delayedTester.pumpFrame(100)
        assertTrue(delayedTester.hasPixel(tabColor(1f)))
        assertTrue(delayedTester.hasPixel(tabColor(0f)))
        delayedTester.pumpFrame(50)
        assertTrue(delayedTester.hasPixel(tabColor(0.5f)))
        delayedTester.dispose()

        val immediateSelected = ValueNotifier(0)
        val immediateTester = PixelTester()
        val noneSelection = selectionSpec().copy(
            delay = 500.milliseconds,
            transition = PixelMotionTransitionPreset.None,
        )
        immediateTester.pumpWidget(
            motionTabs(
                tester = immediateTester,
                selected = immediateSelected,
                settings = PixelMotionSettings.Default,
                selection = noneSelection,
            ),
            logicalWidth = 60,
            logicalHeight = 12,
        )
        immediateSelected.value = 2
        immediateTester.pumpFrame(0)
        assertTrue(immediateTester.hasPixel(tabColor(1f)))
        assertTrue(immediateTester.hasPixel(tabColor(0f)))
        assertEquals(0, immediateTester.scheduler.pendingCount)
        assertEquals(0, immediateTester.vsync.liveTickerCount)
        immediateTester.dispose()
    }

    /** 构造使用线性 selection token 的三项 Tabs 树。 */
    private fun motionTabs(
        tester: PixelTester,
        selected: ValueNotifier<Int>,
        settings: PixelMotionSettings,
        selection: PixelMotionSpec = selectionSpec(),
    ): Widget {
        return PixelMotionScope(
            vsync = tester.vsync,
            settings = settings,
            child = PixelMotionTheme(
                data = PixelMotionThemeData.Default.copy(selection = selection),
                child = ValueListenableBuilder(selected) { _, index ->
                    Tabs(
                        labels = listOf("A", "B", "C"),
                        selectedIndex = index,
                        onSelected = { selected.value = it },
                        key = "tabs",
                    )
                },
            ),
        )
    }

    /** Creates the default linear selection token shared by Tabs cases. */
    private fun selectionSpec(): PixelMotionSpec = PixelMotionSpec(
        duration = 1_000.milliseconds,
        curve = Curves.Linear,
        role = PixelMotionRole.Selection,
    )

    /** 锚定每个发生变化的 Tab selection ticker 首帧。 */
    private fun beginMotion(tester: PixelTester) {
        tester.pumpFrame(0)
        tester.pumpFrame(0)
    }

    /** 计算白色未选中边框到绿色选中边框的真实 ARGB。 */
    private fun tabColor(progress: Float): PixelColor {
        return PixelColorTween(PixelColor.White, SelectedColor).lerp(progress)
    }

    private companion object {
        /** Tabs 保持历史视觉基线的绿色选中颜色。 */
        val SelectedColor: PixelColor = PixelColor.fromRgb(80, 180, 110)
    }
}
