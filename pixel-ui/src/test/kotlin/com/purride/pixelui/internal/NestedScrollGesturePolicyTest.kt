package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedScrollGesturePolicyTest {

    @Test
    fun verticalPagerDefersToScrollableListWhenBothWantTheGesture() {
        assertTrue(
            NestedScrollGesturePolicy.shouldDeferPagerToList(
                pagerAxis = PixelAxis.VERTICAL,
                pagerWantsDrag = true,
                listWantsDrag = true,
                listCanConsumeDrag = true,
            ),
        )
    }

    @Test
    fun verticalPagerKeepsGestureWhenListHasReachedBoundary() {
        assertFalse(
            NestedScrollGesturePolicy.shouldDeferPagerToList(
                pagerAxis = PixelAxis.VERTICAL,
                pagerWantsDrag = true,
                listWantsDrag = true,
                listCanConsumeDrag = false,
            ),
        )
    }

    @Test
    fun horizontalPagerDoesNotUseVerticalListDeferralRule() {
        assertFalse(
            NestedScrollGesturePolicy.shouldDeferPagerToList(
                pagerAxis = PixelAxis.HORIZONTAL,
                pagerWantsDrag = true,
                listWantsDrag = true,
                listCanConsumeDrag = true,
            ),
        )
    }
}
