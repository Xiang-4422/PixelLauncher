package com.purride.pixelui

import com.purride.pixelui.state.PixelListController
import org.junit.Assert.assertEquals
import org.junit.Test

class PixelListControllerTest {

    private val controller = PixelListController()

    @Test
    fun dragByClampsScrollOffsetWithinContentRange() {
        val state = controller.create()

        controller.dragBy(
            state = state,
            deltaPx = -18f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertEquals(18f, state.scrollOffsetPx, 0.001f)

        controller.dragBy(
            state = state,
            deltaPx = -40f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertEquals(30f, state.scrollOffsetPx, 0.001f)

        controller.dragBy(
            state = state,
            deltaPx = 80f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertEquals(0f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun syncClampsInitialOffsetToViewportUpperBound() {
        val state = controller.create(initialScrollOffsetPx = 80f)

        controller.sync(
            state = state,
            viewportHeightPx = 24,
            contentHeightPx = 40,
        )

        assertEquals(16f, state.scrollOffsetPx, 0.001f)
    }
}
