package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.PixelMotionRole
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.Switch
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.PixelColorTween
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** Switch thumb、颜色、快速反向和 reduce-motion 的确定性契约。 */
class PixelSwitchMotionTest {
    /** checked 立即更新逻辑树，视觉在 0/25/50/75/100 帧同步插值位置与颜色。 */
    @Test
    fun selectionFramesAndRapidRetargetRemainContinuous() {
        // 受控值模拟业务状态的 true/false/true 快速切换。
        val checked = ValueNotifier(false)
        val tester = PixelTester()
        tester.pumpWidget(
            motionRoot(tester, PixelMotionSettings.Default) {
                ValueListenableBuilder(checked) { _, selected ->
                    Switch(checked = selected, onChanged = { checked.value = it }, key = "switch")
                }
            },
            logicalWidth = 16,
            logicalHeight = 9,
        )
        assertSwitchFrame(tester, 0f)

        checked.value = true
        beginMotion(tester)
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            tester.pumpFrame(250)
            assertSwitchFrame(tester, progress)
        }
        val switchNode = tester.semanticsNodesByLabel("Switch").single()
        assertEquals(true, switchNode.checked)

        checked.value = false
        beginMotion(tester)
        tester.pumpFrame(500)
        assertSwitchFrame(tester, 0.5f)
        val beforeRetarget = tester.renderResult!!.buffer.pixels.copyOf()
        checked.value = true
        tester.pumpFrame(0)
        assertTrue(beforeRetarget.contentEquals(tester.renderResult!!.buffer.pixels))
        tester.pumpFrame(0)
        tester.pumpFrame(500)
        assertSwitchFrame(tester, 0.75f)
        tester.pumpFrame(500)
        assertSwitchFrame(tester, 1f)
        assertEquals(0, tester.vsync.activeTickerCount)

        tester.dispose()
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** 无 MotionScope 与 reduce motion 都同步显示 checked 终态且没有时钟资源。 */
    @Test
    fun immediateModesDoNotCreateTicker() {
        // 无 scope 是公开降级契约，受控更新不应崩溃或创建私有 ticker。
        val outsideChecked = ValueNotifier(false)
        val outsideTester = PixelTester()
        outsideTester.pumpWidget(
            ValueListenableBuilder(outsideChecked) { _, selected ->
                Switch(checked = selected, onChanged = { outsideChecked.value = it })
            },
            logicalWidth = 16,
            logicalHeight = 9,
        )
        outsideChecked.value = true
        outsideTester.pumpFrame(0)
        assertSwitchFrame(outsideTester, 1f)
        assertEquals(0, outsideTester.vsync.liveTickerCount)
        outsideTester.dispose()

        // Selection role 在 reduce motion 下也必须同步终态。
        val reducedChecked = ValueNotifier(false)
        val reducedTester = PixelTester()
        reducedTester.pumpWidget(
            motionRoot(reducedTester, PixelMotionSettings(reduceMotion = true)) {
                ValueListenableBuilder(reducedChecked) { _, selected ->
                    Switch(checked = selected, onChanged = { reducedChecked.value = it })
                }
            },
            logicalWidth = 16,
            logicalHeight = 9,
        )
        reducedChecked.value = true
        reducedTester.pumpFrame(0)
        assertSwitchFrame(reducedTester, 1f)
        assertEquals(0, reducedTester.scheduler.pendingCount)
        assertEquals(0, reducedTester.vsync.liveTickerCount)
        reducedTester.dispose()
    }

    /** pressed、hover 和 focus 反馈按优先级叠加在当前 selection 颜色之上。 */
    @Test
    fun interactionFeedbackRetargetsAcrossHoverPressAndFocus() {
        // FocusNode 让最后一段 focus feedback 可在 JVM 虚拟树中确定性触发。
        val focusNode = FocusNode()
        val tester = PixelTester()
        tester.pumpWidget(
            motionRoot(tester, PixelMotionSettings.Default) {
                Focus(
                    node = focusNode,
                    child = Switch(checked = false, onChanged = {}, key = "switch"),
                )
            },
            logicalWidth = 16,
            logicalHeight = 9,
        )

        tester.hover(find.byKey("switch"))
        beginMotion(tester)
        tester.pumpFrame(1_000)
        assertEquals(feedbackColor(0.5f), tester.pixelAt(0, 0))

        val gesture = tester.startGesture(find.byKey("switch"))
        beginMotion(tester)
        tester.pumpFrame(1_000)
        assertEquals(feedbackColor(1f), tester.pixelAt(0, 0))
        gesture.cancel()
        beginMotion(tester)
        tester.pumpFrame(1_000)
        assertEquals(feedbackColor(0.5f), tester.pixelAt(0, 0))

        tester.exitHover()
        beginMotion(tester)
        tester.pumpFrame(1_000)
        assertEquals(InactiveColor, tester.pixelAt(0, 0))
        focusNode.requestFocus()
        beginMotion(tester)
        tester.pumpFrame(1_000)
        assertEquals(feedbackColor(1f), tester.pixelAt(0, 0))
        assertTrue(tester.dumpSemanticsTree().contains("focused=true"))

        tester.dispose()
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** Selection delay preserves the outgoing frame and `None` applies checked state synchronously. */
    @Test
    fun selectionDelayAndNonePresetAreAppliedExactly() {
        val delayedChecked = ValueNotifier(false)
        val delayedTester = PixelTester()
        val delayedSelection = selectionSpec().copy(
            duration = 100.milliseconds,
            delay = 100.milliseconds,
        )
        delayedTester.pumpWidget(
            motionRoot(
                tester = delayedTester,
                settings = PixelMotionSettings.Default,
                selection = delayedSelection,
            ) {
                ValueListenableBuilder(delayedChecked) { _, selected ->
                    Switch(checked = selected, onChanged = {}, key = "switch")
                }
            },
            logicalWidth = 16,
            logicalHeight = 9,
        )
        delayedChecked.value = true
        beginMotion(delayedTester)
        delayedTester.pumpFrame(100)
        assertSwitchFrame(delayedTester, 0f)
        delayedTester.pumpFrame(50)
        assertSwitchFrame(delayedTester, 0.5f)
        delayedTester.dispose()

        val immediateChecked = ValueNotifier(false)
        val immediateTester = PixelTester()
        val noneSelection = selectionSpec().copy(
            delay = 500.milliseconds,
            transition = PixelMotionTransitionPreset.None,
        )
        immediateTester.pumpWidget(
            motionRoot(
                tester = immediateTester,
                settings = PixelMotionSettings.Default,
                selection = noneSelection,
            ) {
                ValueListenableBuilder(immediateChecked) { _, selected ->
                    Switch(checked = selected, onChanged = {}, key = "switch")
                }
            },
            logicalWidth = 16,
            logicalHeight = 9,
        )
        immediateChecked.value = true
        immediateTester.pumpFrame(0)
        assertSwitchFrame(immediateTester, 1f)
        assertEquals(0, immediateTester.scheduler.pendingCount)
        assertEquals(0, immediateTester.vsync.liveTickerCount)
        immediateTester.dispose()
    }

    /** 包装线性 selection token 和指定系统 motion 设置。 */
    private fun motionRoot(
        tester: PixelTester,
        settings: PixelMotionSettings,
        selection: PixelMotionSpec = selectionSpec(),
        child: () -> Widget,
    ): Widget {
        return PixelMotionScope(
            vsync = tester.vsync,
            settings = settings,
            child = PixelMotionTheme(
                data = PixelMotionThemeData.Default.copy(selection = selection),
                child = child(),
            ),
        )
    }

    /** Creates the default linear selection token for Switch tests. */
    private fun selectionSpec(): PixelMotionSpec = PixelMotionSpec(
        duration = 1_000.milliseconds,
        curve = Curves.Linear,
        role = PixelMotionRole.Selection,
    )

    /** 锚定 selection ticker 的首帧。 */
    private fun beginMotion(tester: PixelTester) {
        tester.pumpFrame(0)
        tester.pumpFrame(0)
    }

    /** 同时验证 track ARGB、thumb 像素位置与旧位置已清除。 */
    private fun assertSwitchFrame(tester: PixelTester, progress: Float) {
        val expectedColor = PixelColorTween(InactiveColor, ActiveColor).lerp(progress)
        val expectedLeft = (1f + 7f * progress).roundToInt()
        assertEquals(expectedColor, tester.pixelAt(0, 0))
        assertEquals(expectedColor, tester.pixelAt(expectedLeft, 1))
        if (expectedLeft != 1) {
            assertFalse(tester.pixelAt(1, 1) == expectedColor)
        }
    }

    /** 计算未选中颜色朝默认 focus 色移动后的反馈颜色。 */
    private fun feedbackColor(progress: Float): PixelColor {
        return PixelColorTween(InactiveColor, FocusColor).lerp(progress)
    }

    private companion object {
        /** Switch 默认未选中颜色。 */
        val InactiveColor: PixelColor = PixelColor.fromRgb(120, 120, 120)

        /** Switch 默认选中颜色。 */
        val ActiveColor: PixelColor = PixelColor.fromRgb(80, 180, 110)

        /** 默认 PixelTheme 的 focus 颜色。 */
        val FocusColor: PixelColor = PixelColor.fromRgb(255, 200, 0)
    }
}
