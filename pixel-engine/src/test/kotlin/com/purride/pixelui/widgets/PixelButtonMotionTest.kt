package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.PixelMotionRole
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.PixelColorTween
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Button 微交互在统一虚拟时钟上的像素与资源生命周期契约。 */
class PixelButtonMotionTest {
    /** pressed、pause/resume 与 cancel 反向都从当前容器颜色连续推进。 */
    @Test
    fun pressedFramesPauseAndCancelRetargetWithoutLayoutChange() {
        // Tester 的 provider 等价于 Host 注入的统一 Motion 时钟。
        val tester = PixelTester()
        tester.pumpWidget(
            motionRoot(
                tester = tester,
                settings = PixelMotionSettings.Default,
                child = OutlinedButton(text = "GO", onPressed = {}, key = "button"),
            ),
            logicalWidth = 24,
            logicalHeight = 12,
        )
        // 初始默认容器透明，组件自然布局不会因 feedback 改变。
        val initialPixels = tester.renderResult!!.buffer.pixels.size
        assertEquals(PixelColor.Transparent, tester.pixelAt(1, 1))

        val gesture = tester.startGesture(find.byKey("button"))
        beginMotion(tester)
        tester.pumpFrame(250)
        assertEquals(pressedFeedbackColor(0.25f), tester.pixelAt(1, 1))

        tester.vsync.pause()
        tester.pumpFrame(60_000)
        assertEquals(pressedFeedbackColor(0.25f), tester.pixelAt(1, 1))
        assertEquals(0, tester.scheduler.pendingCount)
        tester.vsync.resume()
        tester.pumpFrame(0)
        tester.pumpFrame(250)
        assertEquals(pressedFeedbackColor(0.5f), tester.pixelAt(1, 1))

        gesture.cancel()
        val cancellationFrame = tester.pixelAt(1, 1)
        beginMotion(tester)
        assertEquals(cancellationFrame, tester.pixelAt(1, 1))
        tester.pumpFrame(500)
        assertEquals(pressedFeedbackColor(0.25f), tester.pixelAt(1, 1))
        tester.pumpFrame(500)
        assertEquals(PixelColor.Transparent, tester.pixelAt(1, 1))
        assertEquals(initialPixels, tester.renderResult!!.buffer.pixels.size)
        assertEquals(0, tester.vsync.activeTickerCount)

        tester.dispose()
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** reduce motion 下 pressed/release 同步落终态且不创建 ticker。 */
    @Test
    fun reduceMotionUsesImmediateFeedbackWithoutTicker() {
        // reduceMotion 对 Feedback role 的解析结果必须是同步终态。
        val tester = PixelTester()
        tester.pumpWidget(
            motionRoot(
                tester = tester,
                settings = PixelMotionSettings(reduceMotion = true),
                child = OutlinedButton(text = "GO", onPressed = {}, key = "button"),
            ),
            logicalWidth = 24,
            logicalHeight = 12,
        )

        val gesture = tester.startGesture(find.byKey("button"))
        assertEquals(pressedFeedbackColor(1f), tester.pixelAt(1, 1))
        assertEquals(0, tester.scheduler.pendingCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        gesture.up()
        assertEquals(PixelColor.Transparent, tester.pixelAt(1, 1))
        assertEquals(0, tester.scheduler.pendingCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** hover 使用 feedback token，而 focus 作为独立像素层不替换容器状态。 */
    @Test
    fun hoverAndFocusUseFeedbackTokenWithoutChangingBounds() {
        // 显式 FocusNode 让键盘焦点路径无需 Android View 即可确定性验证。
        val focusNode = FocusNode()
        val tester = PixelTester()
        tester.pumpWidget(
            motionRoot(
                tester = tester,
                settings = PixelMotionSettings.Default,
                child = Focus(
                    node = focusNode,
                    child = OutlinedButton(text = "GO", onPressed = {}, key = "button"),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 12,
        )

        tester.hover(find.byKey("button"))
        beginMotion(tester)
        tester.pumpFrame(1_000)
        assertEquals(HoveredColor, tester.pixelAt(1, 1))
        tester.exitHover()
        beginMotion(tester)
        tester.pumpFrame(1_000)
        assertEquals(PixelColor.Transparent, tester.pixelAt(1, 1))

        focusNode.requestFocus()
        tester.pumpFrame(0)
        assertEquals(FocusColor, tester.pixelAt(0, 0))
        assertEquals(PixelColor.Transparent, tester.pixelAt(1, 1))
        assertTrue(tester.dumpSemanticsTree().contains("focused=true"))
        tester.dispose()
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** feedback delay holds the initial frame, while `None` bypasses both delay and ticker. */
    @Test
    fun feedbackDelayAndNonePresetAreAppliedExactly() {
        val delayedTester = PixelTester()
        val delayedSpec = feedbackSpec().copy(
            duration = 100.milliseconds,
            delay = 100.milliseconds,
        )
        delayedTester.pumpWidget(
            motionRoot(
                tester = delayedTester,
                settings = PixelMotionSettings.Default,
                feedback = delayedSpec,
                child = OutlinedButton(text = "GO", onPressed = {}, key = "button"),
            ),
            logicalWidth = 24,
            logicalHeight = 12,
        )
        val delayedGesture = delayedTester.startGesture(find.byKey("button"))
        beginMotion(delayedTester)
        delayedTester.pumpFrame(100)
        assertEquals(PixelColor.Transparent, delayedTester.pixelAt(1, 1))
        delayedTester.pumpFrame(50)
        assertEquals(pressedFeedbackColor(0.5f), delayedTester.pixelAt(1, 1))
        delayedGesture.cancel()
        delayedTester.dispose()

        val immediateTester = PixelTester()
        val noneSpec = feedbackSpec().copy(
            delay = 500.milliseconds,
            transition = PixelMotionTransitionPreset.None,
        )
        immediateTester.pumpWidget(
            motionRoot(
                tester = immediateTester,
                settings = PixelMotionSettings.Default,
                feedback = noneSpec,
                child = OutlinedButton(text = "GO", onPressed = {}, key = "button"),
            ),
            logicalWidth = 24,
            logicalHeight = 12,
        )
        immediateTester.startGesture(find.byKey("button"))
        assertEquals(pressedFeedbackColor(1f), immediateTester.pixelAt(1, 1))
        assertEquals(0, immediateTester.scheduler.pendingCount)
        assertEquals(0, immediateTester.vsync.liveTickerCount)
        immediateTester.dispose()
    }

    /** 构造只覆盖 feedback token 的确定性 Motion 测试树。 */
    private fun motionRoot(
        tester: PixelTester,
        settings: PixelMotionSettings,
        feedback: PixelMotionSpec = feedbackSpec(),
        child: com.purride.pixelui.Widget,
    ): com.purride.pixelui.Widget {
        return PixelMotionScope(
            vsync = tester.vsync,
            settings = settings,
            child = PixelMotionTheme(
                data = PixelMotionThemeData.Default.copy(feedback = feedback),
                child = child,
            ),
        )
    }

    /** Creates the linear feedback token shared by default button motion cases. */
    private fun feedbackSpec(): PixelMotionSpec = PixelMotionSpec(
        duration = 1_000.milliseconds,
        curve = Curves.Linear,
        role = PixelMotionRole.Feedback,
    )

    /** 锚定新 ticker 的首帧，使随后毫秒数直接等于片段进度。 */
    private fun beginMotion(tester: PixelTester) {
        tester.pumpFrame(0)
        tester.pumpFrame(0)
    }

    /** 计算透明 Normal 容器朝 pressed Primary 色移动后的真实 ARGB。 */
    private fun pressedFeedbackColor(progress: Float): PixelColor {
        return PixelColorTween(PixelColor.Transparent, PressedColor).lerp(progress)
    }

    private companion object {
        /** 默认 PixelTheme 的 focus 颜色。 */
        val FocusColor: PixelColor = PixelColor.fromRgb(255, 200, 0)

        /** 默认 Button pressed 容器使用的 Primary 色。 */
        val PressedColor: PixelColor = PixelColor.fromRgb(80, 180, 110)

        /** 默认 Button hover 容器使用的 SurfaceVariant 色。 */
        val HoveredColor: PixelColor = PixelColor.fromRgb(60, 60, 60)
    }
}
