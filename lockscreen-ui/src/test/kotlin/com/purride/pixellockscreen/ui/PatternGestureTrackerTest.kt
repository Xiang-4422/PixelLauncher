package com.purride.pixellockscreen.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证图案二维拖动、跨格补齐、去重和清零边界。 */
class PatternGestureTrackerTest {
    /** 水平跨两格必须自动补齐中点，并按顺序逐格通知。 */
    @Test
    fun horizontalGapAddsMiddleCellInOrder() {
        /** 记录当前手势事件的测试装配。 */
        val fixture = TrackerFixture()
        /** 纵屏图案布局。 */
        val layout = fixture.layout

        fixture.tracker.start(layout.centerX(0), layout.centerY(0))
        fixture.tracker.update(layout.centerX(2), layout.centerY(2))

        assertEquals(listOf(0, 1, 2), fixture.cells)
        assertEquals(1, fixture.startedCount)
        assertEquals(3, fixture.tracker.size)
    }

    /** 对角跨两格必须自动补齐中央格。 */
    @Test
    fun diagonalGapAddsCenterCell() {
        /** 记录当前手势事件的测试装配。 */
        val fixture = TrackerFixture()
        /** 纵屏图案布局。 */
        val layout = fixture.layout

        fixture.tracker.start(layout.centerX(0), layout.centerY(0))
        fixture.tracker.update(layout.centerX(8), layout.centerY(8))

        assertEquals(listOf(0, 4, 8), fixture.cells)
    }

    /** 快速线段必须逐逻辑像素补采，重复经过的格子不得再次追加。 */
    @Test
    fun fastMoveSamplesCrossedCellsAndIgnoresDuplicates() {
        /** 记录当前手势事件的测试装配。 */
        val fixture = TrackerFixture()
        /** 纵屏图案布局。 */
        val layout = fixture.layout

        fixture.tracker.start(layout.centerX(6), layout.centerY(6))
        fixture.tracker.update(layout.centerX(8), layout.centerY(8))
        fixture.tracker.update(layout.centerX(6), layout.centerY(6))

        assertEquals(listOf(6, 7, 8), fixture.cells)
    }

    /** 完成回调只暴露长度，并在回调后覆写内部路径。 */
    @Test
    fun completionReportsLengthAndZeroizesPath() {
        /** 记录当前手势事件的测试装配。 */
        val fixture = TrackerFixture()
        /** 纵屏图案布局。 */
        val layout = fixture.layout
        fixture.tracker.start(layout.centerX(0), layout.centerY(0))
        fixture.tracker.update(layout.centerX(4), layout.centerY(4))

        fixture.tracker.end()

        assertEquals(listOf(2), fixture.completions)
        assertEquals(0, fixture.tracker.size)
        assertTrue(internalCellsOf(fixture.tracker).all { cell -> cell == -1 })
    }

    /** 取消已经开始的路径必须通知一次并立即清零。 */
    @Test
    fun cancellationNotifiesAndZeroizesPath() {
        /** 记录当前手势事件的测试装配。 */
        val fixture = TrackerFixture()
        /** 纵屏图案布局。 */
        val layout = fixture.layout
        fixture.tracker.start(layout.centerX(3), layout.centerY(3))

        fixture.tracker.cancel()
        fixture.tracker.cancel()

        assertEquals(1, fixture.cancelledCount)
        assertEquals(0, fixture.tracker.size)
        assertTrue(internalCellsOf(fixture.tracker).all { cell -> cell == -1 })
    }

    /** 方向布局必须保持图案画布完全位于逻辑场景内。 */
    @Test
    fun portraitAndLandscapeLayoutsStayInsideViewport() {
        listOf(false, true).forEach { isLandscape ->
            /** 当前方向布局。 */
            val layout = patternCredentialLayout(isLandscape)
            assertTrue(layout.patternLeft >= 0)
            assertTrue(layout.patternTop >= 0)
            assertTrue(
                layout.patternLeft + PatternCredentialLayout.PATTERN_SIZE <= layout.logicalWidth,
            )
            assertTrue(
                layout.patternTop + PatternCredentialLayout.PATTERN_SIZE <= layout.logicalHeight,
            )
            assertFalse(layout.promptWidth <= 0 || layout.feedbackWidth <= 0)
        }
    }

    /** 通过反射读取固定路径数组，避免生产类型暴露调试数据接口。 */
    private fun internalCellsOf(tracker: PatternGestureTracker): IntArray {
        /** 跟踪器内部固定路径字段。 */
        val field = PatternGestureTracker::class.java.getDeclaredField("cells")
        field.isAccessible = true
        return field.get(tracker) as IntArray
    }

    /** 为纯手势测试记录所有非敏感控制事件。 */
    private class TrackerFixture {
        /** 纵屏测试布局。 */
        val layout: PatternCredentialLayout = patternCredentialLayout(isLandscape = false)

        /** 按顺序记录的测试格子。 */
        val cells: MutableList<Int> = mutableListOf()

        /** 完成时记录的路径长度。 */
        val completions: MutableList<Int> = mutableListOf()

        /** 开始通知次数。 */
        var startedCount: Int = 0

        /** 取消通知次数。 */
        var cancelledCount: Int = 0

        /** 待测试的二维跟踪器。 */
        val tracker: PatternGestureTracker = PatternGestureTracker(
            layout = layout,
            onStarted = { startedCount += 1 },
            onCellAdded = cells::add,
            onCompleted = completions::add,
            onCancelled = { cancelledCount += 1 },
            onVisualChanged = {},
        )
    }
}
