package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * direct pipeline 嵌套滚动手势策略的回归测试。
 */
class NestedScrollGesturePolicyTest {
    /**
     * 垂直分页内的垂直列表还能消费拖动时，外层分页应该让给内层列表。
     */
    @Test
    fun verticalPagerDefersToConsumableListDrag() {
        assertTrue(
            NestedScrollGesturePolicy.shouldDeferPagerToList(
                pagerAxis = PixelAxis.VERTICAL,
                pagerWantsDrag = true,
                listWantsDrag = true,
                listCanConsumeDrag = true,
            ),
        )
        assertFalse(
            NestedScrollGesturePolicy.shouldDeferPagerToList(
                pagerAxis = PixelAxis.HORIZONTAL,
                pagerWantsDrag = true,
                listWantsDrag = true,
                listCanConsumeDrag = true,
            ),
        )
    }

    /**
     * 列表到达边界后，同一次纵向拖动可以接力给外层分页。
     */
    @Test
    fun listHandsOffToPagerAtBoundary() {
        assertTrue(
            NestedScrollGesturePolicy.shouldHandOffListToPager(
                pagerAxis = PixelAxis.VERTICAL,
                listCanConsumeDrag = false,
                deltaPx = 8f,
            ),
        )
        assertFalse(
            NestedScrollGesturePolicy.shouldHandOffListToPager(
                pagerAxis = PixelAxis.VERTICAL,
                listCanConsumeDrag = true,
                deltaPx = 8f,
            ),
        )
    }
}
