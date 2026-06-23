package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedScrollSessionTest {
    @Test
    fun ownerStaysNoneBeforeActiveTargetIsChosen() {
        val session = NestedScrollSession()

        session.candidatePagerTarget = pagerTarget()
        session.candidateListTarget = listTarget()

        assertEquals(NestedScrollSession.Owner.NONE, session.owner)
    }

    @Test
    fun activeListAndPagerAreMutuallyExclusiveOwners() {
        val session = NestedScrollSession()
        val pager = pagerTarget()
        val list = listTarget()

        session.activeListTarget = list
        assertSame(list, session.activeListTarget)
        assertNull(session.activePagerTarget)
        assertEquals(NestedScrollSession.Owner.LIST, session.owner)

        session.activePagerTarget = pager
        assertSame(pager, session.activePagerTarget)
        assertNull(session.activeListTarget)
        assertEquals(NestedScrollSession.Owner.PAGER, session.owner)
    }

    @Test
    fun edgeHandoffAndDeltasClearOnReset() {
        val session = NestedScrollSession()
        session.activeListTarget = listTarget()
        session.consumedDeltaPx = 4f
        session.remainingDeltaPx = 2f
        session.edgeHandoff = true

        session.resetGesture()

        assertEquals(NestedScrollSession.Owner.NONE, session.owner)
        assertNull(session.activeListTarget)
        assertEquals(0f, session.consumedDeltaPx, 0f)
        assertEquals(0f, session.remainingDeltaPx, 0f)
        assertFalse(session.edgeHandoff)
    }

    @Test
    fun textInputOwnerSurvivesGestureResetUntilExplicitlyCleared() {
        val session = NestedScrollSession()
        val textInput = textInputTarget()

        session.markTextInputOwner(textInput)
        session.edgeHandoff = true
        session.resetGesture()

        assertSame(textInput, session.focusedTextInputTarget)
        assertEquals(NestedScrollSession.Owner.TEXT_INPUT, session.owner)
        assertFalse(session.edgeHandoff)

        session.clearTextInputOwner()
        assertNull(session.focusedTextInputTarget)
        assertEquals(NestedScrollSession.Owner.NONE, session.owner)
    }

    @Test
    fun clearCandidatesKeepsActiveOwnerAndClearsRemainingDelta() {
        val session = NestedScrollSession()
        val list = listTarget()
        session.activeListTarget = list
        session.candidatePagerTarget = pagerTarget()
        session.candidateListTarget = listTarget()
        session.remainingDeltaPx = 3f

        session.clearCandidates()

        assertSame(list, session.activeListTarget)
        assertEquals(NestedScrollSession.Owner.LIST, session.owner)
        assertNull(session.candidatePagerTarget)
        assertNull(session.candidateListTarget)
        assertEquals(0f, session.remainingDeltaPx, 0f)
        assertTrue(session.activeListTarget != null)
    }

    private fun pagerTarget(): PixelPagerTarget {
        return PixelPagerTarget(
            bounds = PixelRect(0, 0, 16, 16),
            axis = PixelAxis.HORIZONTAL,
            state = PixelPagerState(pageCount = 3),
            controller = PixelPagerController(),
            onPageChanged = null,
            onPageDragStart = null,
        )
    }

    private fun listTarget(): PixelListTarget {
        return PixelListTarget(
            bounds = PixelRect(0, 0, 16, 16),
            viewportHeightPx = 16,
            contentHeightPx = 48,
            state = PixelListState(),
            controller = PixelListController(),
        )
    }

    private fun textInputTarget(): PixelTextInputTarget {
        return PixelTextInputTarget(
            bounds = PixelRect(0, 0, 16, 8),
            state = PixelTextFieldState(initialText = "A"),
            controller = PixelTextFieldController(),
            readOnly = false,
            autofocus = false,
            minLines = 1,
            maxLines = 1,
            inputType = PixelInputType.TEXT,
            action = PixelTextInputAction.DONE,
            onChanged = null,
            onSubmitted = null,
        )
    }
}
