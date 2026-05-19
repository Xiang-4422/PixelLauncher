package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.PageController
import com.purride.pixelui.ScrollController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NestedScrollSessionTest {

    @Test
    fun activeOwnerTracksPagerAndListMutuallyExclusively() {
        val session = NestedScrollSession()
        val pagerTarget = PixelPagerTarget(
            bounds = PixelRect(left = 0, top = 0, width = 10, height = 10),
            axis = PixelAxis.VERTICAL,
            state = PageController().create(pageCount = 2),
            controller = PageController(),
            onPageChanged = null,
        )
        val listTarget = PixelListTarget(
            bounds = PixelRect(left = 0, top = 0, width = 10, height = 10),
            viewportHeightPx = 10,
            contentHeightPx = 20,
            state = ScrollController().create(),
            controller = ScrollController(),
        )

        session.activePagerTarget = pagerTarget
        assertEquals(NestedScrollSession.Owner.PAGER, session.owner)
        assertEquals(pagerTarget, session.activePagerTarget)

        session.activeListTarget = listTarget
        assertEquals(NestedScrollSession.Owner.LIST, session.owner)
        assertNull(session.activePagerTarget)
        assertEquals(listTarget, session.activeListTarget)

        session.resetGesture()
        assertEquals(NestedScrollSession.Owner.NONE, session.owner)
        assertNull(session.activeListTarget)
    }
}
