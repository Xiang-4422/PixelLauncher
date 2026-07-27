package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelMotionRole
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.Slider
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** Slider 程序值、直接拖动、反向与同步降级的像素契约。 */
class PixelSliderMotionTest {
    /** 程序值按 selection token 经过 0/25/50/75/100 帧并支持当前视觉值反向。 */
    @Test
    fun programValueFramesAndRapidReverseRemainContinuous() {
        // ValueNotifier 模拟非手势触发的受控程序更新。
        val value = ValueNotifier(0f)
        val tester = PixelTester()
        tester.pumpWidget(
            motionRoot(tester, PixelMotionSettings.Default) {
                ValueListenableBuilder(value) { _, controlled ->
                    Slider(value = controlled, key = "slider")
                }
            },
            logicalWidth = SliderWidth,
            logicalHeight = 7,
        )
        assertSliderFrame(tester, 0f)

        value.value = 1f
        beginMotion(tester)
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            tester.pumpFrame(250)
            assertSliderFrame(tester, progress)
        }

        value.value = 0f
        beginMotion(tester)
        tester.pumpFrame(500)
        assertSliderFrame(tester, 0.5f)
        val beforeRetarget = tester.renderResult!!.buffer.pixels.copyOf()
        value.value = 1f
        tester.pumpFrame(0)
        assertTrue(beforeRetarget.contentEquals(tester.renderResult!!.buffer.pixels))
        tester.pumpFrame(0)
        tester.pumpFrame(500)
        assertSliderFrame(tester, 0.75f)
        tester.pumpFrame(500)
        assertSliderFrame(tester, 1f)
        assertEquals(0, tester.vsync.activeTickerCount)

        tester.dispose()
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** 拖动直接跟手；release 后才允许向受控程序值 settle。 */
    @Test
    fun dragIsImmediateAndReleaseSettlesToControlledValue() {
        // onDrag 故意不回写，验证组件仍能本地逐像素跟手。
        val tester = PixelTester()
        tester.pumpWidget(
            motionRoot(tester, PixelMotionSettings.Default) {
                Slider(value = 0f, onDrag = {}, onRelease = {}, key = "slider")
            },
            logicalWidth = SliderWidth,
            logicalHeight = 7,
        )

        val gesture = tester.startGesture(find.byKey("slider"))
        gesture.moveBy(dx = 6, dy = 0)
        // 从轨道中心向右 6px 到达最右端，直接帧不需要 selection ticker。
        assertSliderFrame(tester, 1f, allowFeedbackColor = true)
        gesture.up()
        beginMotion(tester)
        tester.pumpFrame(500)
        assertSliderFrame(tester, 0.5f)
        tester.pumpFrame(500)
        assertSliderFrame(tester, 0f)
        assertEquals(0, tester.vsync.activeTickerCount)
        tester.dispose()
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** reduce motion 的程序值同步落终态，不创建 ticker 或待处理帧。 */
    @Test
    fun reduceMotionProgramUpdateIsImmediate() {
        // Selection role 在 reduce motion 下解析为同步终态。
        val value = ValueNotifier(0f)
        val tester = PixelTester()
        tester.pumpWidget(
            motionRoot(tester, PixelMotionSettings(reduceMotion = true)) {
                ValueListenableBuilder(value) { _, controlled -> Slider(value = controlled) }
            },
            logicalWidth = SliderWidth,
            logicalHeight = 7,
        )
        value.value = 1f
        tester.pumpFrame(0)
        assertSliderFrame(tester, 1f)
        assertEquals(0, tester.scheduler.pendingCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Programmatic selection obeys theme delay and treats `None` as a synchronous terminal state. */
    @Test
    fun selectionDelayAndNonePresetAreAppliedExactly() {
        val delayedValue = ValueNotifier(0f)
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
                ValueListenableBuilder(delayedValue) { _, controlled -> Slider(value = controlled) }
            },
            logicalWidth = SliderWidth,
            logicalHeight = 7,
        )
        delayedValue.value = 1f
        beginMotion(delayedTester)
        delayedTester.pumpFrame(100)
        assertSliderFrame(delayedTester, 0f)
        delayedTester.pumpFrame(50)
        assertSliderFrame(delayedTester, 0.5f)
        delayedTester.dispose()

        val immediateValue = ValueNotifier(0f)
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
                ValueListenableBuilder(immediateValue) { _, controlled -> Slider(value = controlled) }
            },
            logicalWidth = SliderWidth,
            logicalHeight = 7,
        )
        immediateValue.value = 1f
        immediateTester.pumpFrame(0)
        assertSliderFrame(immediateTester, 1f)
        assertEquals(0, immediateTester.scheduler.pendingCount)
        assertEquals(0, immediateTester.vsync.liveTickerCount)
        immediateTester.dispose()
    }

    /** 包装线性 selection/feedback token 和统一测试时钟。 */
    private fun motionRoot(
        tester: PixelTester,
        settings: PixelMotionSettings,
        selection: PixelMotionSpec = selectionSpec(),
        child: () -> Widget,
    ): Widget {
        val feedback = PixelMotionSpec(
            duration = 1_000.milliseconds,
            curve = Curves.Linear,
            role = PixelMotionRole.Feedback,
        )
        return PixelMotionScope(
            vsync = tester.vsync,
            settings = settings,
            child = PixelMotionTheme(
                data = PixelMotionThemeData.Default.copy(selection = selection, feedback = feedback),
                child = child(),
            ),
        )
    }

    /** Creates the default linear programmatic-selection token for Slider tests. */
    private fun selectionSpec(): PixelMotionSpec = PixelMotionSpec(
        duration = 1_000.milliseconds,
        curve = Curves.Linear,
        role = PixelMotionRole.Selection,
    )

    /** 锚定新 selection ticker 的首帧。 */
    private fun beginMotion(tester: PixelTester) {
        tester.pumpFrame(0)
        tester.pumpFrame(0)
    }

    /**
     * 验证 token 解析出的 active 区段范围、thumb 覆盖列和其后的轨道像素。
     *
     * 默认 Slider token 没有 padding 与边框，因此 active 区段宽度等于整条轨道宽度；
     * 非端点进度时最后一列会被 thumb 覆盖，所以可见 active 像素数为 `fillWidth - 1`。
     */
    private fun assertSliderFrame(
        tester: PixelTester,
        progress: Float,
        allowFeedbackColor: Boolean = false,
    ) {
        /** Token 解析后的可绘制区段宽度，等于轨道整宽。 */
        val rangeWidth = SliderWidth
        /** 当前进度覆盖的整数像素宽度。 */
        val fillWidth = (rangeWidth * progress).roundToInt().coerceIn(0, rangeWidth)
        /** thumb 覆盖后仍然可见的 active 像素数量。 */
        val visibleActive = when {
            fillWidth == 0 -> 0
            fillWidth == rangeWidth -> rangeWidth
            else -> fillWidth - 1
        }
        if (visibleActive == 0) {
            assertEquals(TrackColor, tester.pixelAt(0, 2))
            return
        }
        /** 反馈态允许 active 颜色变化，但区段范围必须精确。 */
        val expectedActive = if (allowFeedbackColor) tester.pixelAt(0, 2) else ActiveColor
        assertTrue(expectedActive != TrackColor)
        assertEquals(expectedActive, tester.pixelAt(visibleActive - 1, 2))
        if (visibleActive < rangeWidth) {
            // thumb 覆盖 active 区段的最后一列，其后必须是轨道色。
            assertEquals(TrackColor, tester.pixelAt(visibleActive, 2))
        }
    }

    private companion object {
        /** 测试轨道宽度；默认 Slider token 无 padding，可绘制宽度等于该值。 */
        const val SliderWidth: Int = 12

        /** 默认深色主题解析出的 Slider active 前景（primary 角色）。 */
        val ActiveColor: PixelColor = PixelColorScheme.Dark.primary

        /** 默认深色主题解析出的 Slider 轨道填充（track 角色）。 */
        val TrackColor: PixelColor = PixelColorScheme.Dark.track
    }
}
