package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextListSupportTest {

    @Test
    fun hitTestResolvesPartialTopRowWithinViewport() {
        val viewport = TextListViewport(
            top = 20,
            bottomExclusive = 100,
            rowHeight = 10,
        )

        val hit = TextListSupport.hitTestRow(
            viewport = viewport,
            logicalY = 22,
            rowCount = 10,
            listStartIndex = 3,
            scrollOffsetPx = -4,
        )

        assertEquals(3, hit)
    }

    @Test
    fun hitTestReturnsNullOutsideViewport() {
        val viewport = TextListViewport(
            top = 20,
            bottomExclusive = 100,
            rowHeight = 10,
        )

        val hit = TextListSupport.hitTestRow(
            viewport = viewport,
            logicalY = 101,
            rowCount = 10,
            listStartIndex = 0,
            scrollOffsetPx = 0,
        )

        assertNull(hit)
    }

    @Test
    fun settleBeforeExplicitActionClearsMotionButKeepsIndices() {
        val settled = TextListSupport.settleBeforeExplicitAction(
            TextListRuntimeState(
                selectedIndex = 7,
                listStartIndex = 5,
                residualOffsetPx = -6f,
                velocityPxPerSecond = 180f,
                settleTarget = DrawerSettleTarget(
                    direction = 1,
                    targetResidualPx = -14f,
                    completionStepDelta = 1,
                ),
                isDragging = true,
                isAnimating = true,
            ),
        )

        assertEquals(7, settled.selectedIndex)
        assertEquals(5, settled.listStartIndex)
        assertEquals(0f, settled.residualOffsetPx)
        assertEquals(0f, settled.velocityPxPerSecond)
        assertEquals(null, settled.settleTarget)
        assertFalse(settled.isDragging)
        assertFalse(settled.isAnimating)
    }

    @Test
    fun scrollableContentRequiresMoreRowsThanViewport() {
        val viewport = TextListViewport(
            top = 10,
            bottomExclusive = 50,
            rowHeight = 10,
        )

        assertFalse(TextListSupport.hasScrollableContent(4, viewport))
        assertTrue(TextListSupport.hasScrollableContent(5, viewport))
    }
}
