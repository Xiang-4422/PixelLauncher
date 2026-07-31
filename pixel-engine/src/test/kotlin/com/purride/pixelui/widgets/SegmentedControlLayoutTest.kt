package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.PixelMotionRole
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SegmentedControlStyle
import com.purride.pixelui.SegmentedControlWidthPolicy
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** 分段选择器宽度策略、滑动指示块和减弱动态效果的回归契约。 */
class SegmentedControlLayoutTest {
    /** 内容宽、最长项等宽和固定等宽分别导出预期的分段语义宽度。 */
    @Test
    fun widthPoliciesResolveContentEqualAndFixedSegments() {
        /** 内容宽策略下两个差异明显的标签宽度。 */
        val contentWidths = segmentWidths(SegmentedControlWidthPolicy.Content)
        assertTrue(contentWidths[0] < contentWidths[1])

        /** 最长项等宽策略下两个标签宽度。 */
        val equalWidths = segmentWidths(SegmentedControlWidthPolicy.EqualToWidest)
        assertEquals(equalWidths[0], equalWidths[1])
        assertEquals(contentWidths[1], equalWidths[0])

        /** 调用方固定等宽策略下两个标签宽度。 */
        val fixedWidths = segmentWidths(SegmentedControlWidthPolicy.Fixed(width = FIXED_WIDTH))
        assertEquals(listOf(FIXED_WIDTH, FIXED_WIDTH), fixedWidths)
    }

    /** 内容宽模式切换时，选中块同时平滑改变水平位置和宽度。 */
    @Test
    fun contentWidthIndicatorInterpolatesPositionAndWidth() {
        /** 调用方持有的受控选择下标。 */
        val selected = ValueNotifier(0)
        /** 为动画帧提供确定性虚拟时钟的测试宿主。 */
        val tester = PixelTester()
        tester.pumpWidget(
            motionSelector(
                tester = tester,
                selected = selected,
                settings = PixelMotionSettings.Default,
            ),
            logicalWidth = 80,
            logicalHeight = 16,
        )

        /** 起点帧中选中填充色的水平像素集合。 */
        val initialRun = selectedPixelRun(tester)
        selected.value = 1
        beginMotion(tester)
        tester.pumpFrame(500)
        /** 动画中点的选中填充色水平像素集合。 */
        val midpointRun = selectedPixelRun(tester)
        tester.pumpFrame(500)
        /** 动画终点的选中填充色水平像素集合。 */
        val terminalRun = selectedPixelRun(tester)

        assertTrue(midpointRun.first() > initialRun.first())
        assertTrue(midpointRun.first() < terminalRun.first())
        assertTrue(midpointRun.size > initialRun.size)
        assertTrue(midpointRun.size < terminalRun.size)
        assertEquals(0, tester.vsync.activeTickerCount)

        tester.dispose()
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** reduce-motion 下受控选中块同步移动到终点且不创建 ticker。 */
    @Test
    fun reduceMotionMovesIndicatorImmediately() {
        /** 调用方持有的受控选择下标。 */
        val selected = ValueNotifier(0)
        /** 使用系统减弱动态效果偏好的测试宿主。 */
        val tester = PixelTester()
        tester.pumpWidget(
            motionSelector(
                tester = tester,
                selected = selected,
                settings = PixelMotionSettings(reduceMotion = true),
            ),
            logicalWidth = 80,
            logicalHeight = 16,
        )
        /** 切换前选中填充的最左像素。 */
        val initialLeft = selectedPixelRun(tester).first()

        selected.value = 1
        tester.pumpFrame(0)
        /** 切换后同步落到右项的最左像素。 */
        val terminalLeft = selectedPixelRun(tester).first()

        assertTrue(terminalLeft > initialLeft)
        assertEquals(0, tester.scheduler.pendingCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** 渲染指定宽度策略并读取两个 Tab 语义节点的实际宽度。 */
    private fun segmentWidths(widthPolicy: SegmentedControlWidthPolicy): List<Int> {
        /** 每个策略使用隔离渲染树，避免 retained 状态相互影响。 */
        val tester = PixelTester()
        return try {
            tester.pumpWidget(
                SegmentedControl(
                    labels = LABELS,
                    selectedIndex = 0,
                    onSelected = {},
                    widthPolicy = widthPolicy,
                    style = TEST_STYLE,
                ),
                logicalWidth = 80,
                logicalHeight = 16,
            )
            LABELS.map { label -> tester.semanticsNodesByLabel(label).single().width }
        } finally {
            tester.dispose()
        }
    }

    /** 构造带线性 selection motion 的可变内容宽选择器。 */
    private fun motionSelector(
        tester: PixelTester,
        selected: ValueNotifier<Int>,
        settings: PixelMotionSettings,
    ): Widget {
        /** 一秒线性动画让中点位置和宽度可以确定性断言。 */
        val selectionMotion = PixelMotionSpec(
            duration = 1_000.milliseconds,
            curve = Curves.Linear,
            role = PixelMotionRole.Selection,
        )
        return PixelMotionScope(
            vsync = tester.vsync,
            settings = settings,
            child = PixelMotionTheme(
                data = PixelMotionThemeData.Default.copy(selection = selectionMotion),
                child = ValueListenableBuilder(selected) { _, selectedIndex ->
                    SegmentedControl(
                        labels = LABELS,
                        selectedIndex = selectedIndex,
                        onSelected = { selected.value = it },
                        widthPolicy = SegmentedControlWidthPolicy.Content,
                        style = TEST_STYLE,
                        key = "animated-segmented-control",
                    )
                },
            ),
        )
    }

    /** 读取避开文字和外边框的扫描行中全部选中填充色水平坐标。 */
    private fun selectedPixelRun(tester: PixelTester): List<Int> {
        /** 当前逻辑画布宽度内属于选中填充色的水平坐标。 */
        return (0 until 80).filter { x -> tester.pixelAt(x, INDICATOR_SCAN_Y) == SELECTED_COLOR }
    }

    /** 锚定 AnimatedPositioned ticker 的零进度首帧。 */
    private fun beginMotion(tester: PixelTester) {
        tester.pumpFrame(0)
        tester.pumpFrame(0)
    }

    /** 测试数据和像素样式的稳定常量。 */
    private companion object {
        /** 短标签和长标签用于制造明确的内容宽差异。 */
        val LABELS: List<String> = listOf("I", "WIDE")

        /** 固定宽策略的每项目标宽度。 */
        const val FIXED_WIDTH: Int = 24

        /** 指示块使用的唯一填充色。 */
        val SELECTED_COLOR: PixelColor = PixelColor.fromRgb(220, 40, 40)

        /** 扫描行位于上边框下方、标签字形上方。 */
        const val INDICATOR_SCAN_Y: Int = 1

        /** 隐藏文字并保留清晰背景、边框和选中块的测试样式。 */
        val TEST_STYLE: SegmentedControlStyle = SegmentedControlStyle(
            containerColor = PixelColor.Black,
            borderColor = PixelColor.White,
            selectedFillColor = SELECTED_COLOR,
            selectedContentColor = PixelColor.Transparent,
            unselectedContentColor = PixelColor.Transparent,
            disabledContentColor = PixelColor.Transparent,
            padding = EdgeInsets.symmetric(horizontal = 2, vertical = 2),
        )
    }
}
