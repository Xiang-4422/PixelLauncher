package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * direct pipeline 分页手势策略的回归测试。
 */
class PagerGesturePolicyTest {
    /**
     * 水平分页只在水平位移超过触摸阈值并压过垂直位移时启动。
     */
    @Test
    fun horizontalPagerStartsOnlyWhenHorizontalDragWins() {
        assertTrue(
            PagerGesturePolicy.shouldStartDrag(
                axis = PixelAxis.HORIZONTAL,
                deltaX = 12f,
                deltaY = 2f,
                touchSlopPx = 4f,
            ),
        )
        assertFalse(
            PagerGesturePolicy.shouldStartDrag(
                axis = PixelAxis.HORIZONTAL,
                deltaX = 5f,
                deltaY = 8f,
                touchSlopPx = 4f,
            ),
        )
    }

    /**
     * 垂直分页只在垂直位移超过触摸阈值并压过水平位移时启动。
     */
    @Test
    fun verticalPagerStartsOnlyWhenVerticalDragWins() {
        assertTrue(
            PagerGesturePolicy.shouldStartDrag(
                axis = PixelAxis.VERTICAL,
                deltaX = 2f,
                deltaY = 12f,
                touchSlopPx = 4f,
            ),
        )
        assertFalse(
            PagerGesturePolicy.shouldStartDrag(
                axis = PixelAxis.VERTICAL,
                deltaX = 8f,
                deltaY = 5f,
                touchSlopPx = 4f,
            ),
        )
    }
}
