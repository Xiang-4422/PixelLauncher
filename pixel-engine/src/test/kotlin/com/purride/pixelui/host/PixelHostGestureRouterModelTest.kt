package com.purride.pixelui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelHostGestureRouterModelTest {
    @Test
    fun ownerIsNotChosenBeforeTouchSlop() {
        val model = PixelHostGestureRouterModel(touchSlop = 4f)
        model.down(hasSlider = false, hasPager = true, hasList = true, hasTextInput = false)

        model.move(rawDeltaX = 1f, rawDeltaY = 1f, pagerWantsDrag = false, listCanConsumeDrag = true, allowListToPagerHandoff = false)

        assertEquals(PixelHostGestureRouterModel.Owner.NONE, model.owner)
        assertFalse(model.touchMoved)
    }

    @Test
    fun listConsumesVerticalDragWhenScrollable() {
        val model = PixelHostGestureRouterModel(touchSlop = 4f)
        model.down(hasSlider = false, hasPager = true, hasList = true, hasTextInput = false)

        model.move(rawDeltaX = 1f, rawDeltaY = 8f, pagerWantsDrag = false, listCanConsumeDrag = true, allowListToPagerHandoff = false)

        assertEquals(PixelHostGestureRouterModel.Owner.LIST, model.owner)
    }

    @Test
    fun listEdgeHandsOffToPager() {
        val model = PixelHostGestureRouterModel(touchSlop = 4f)
        model.down(hasSlider = false, hasPager = true, hasList = true, hasTextInput = false)
        model.move(rawDeltaX = 0f, rawDeltaY = 8f, pagerWantsDrag = false, listCanConsumeDrag = true, allowListToPagerHandoff = false)

        model.move(rawDeltaX = 0f, rawDeltaY = 12f, pagerWantsDrag = false, listCanConsumeDrag = false, allowListToPagerHandoff = true)

        assertEquals(PixelHostGestureRouterModel.Owner.PAGER, model.owner)
        assertTrue(model.edgeHandoff)
    }

    @Test
    fun textFieldTapFocusesButDragDoesNot() {
        val model = PixelHostGestureRouterModel(touchSlop = 4f)
        model.down(hasSlider = false, hasPager = false, hasList = false, hasTextInput = true)
        assertEquals(PixelHostGestureRouterModel.Owner.TEXT_INPUT, model.up())

        model.down(hasSlider = false, hasPager = false, hasList = false, hasTextInput = true)
        model.move(rawDeltaX = 8f, rawDeltaY = 0f, pagerWantsDrag = false, listCanConsumeDrag = false, allowListToPagerHandoff = false)
        assertEquals(PixelHostGestureRouterModel.Owner.NONE, model.up())
    }

    @Test
    fun cancelClearsOwnerCandidatesAndMovement() {
        val model = PixelHostGestureRouterModel(touchSlop = 4f)
        model.down(hasSlider = false, hasPager = true, hasList = true, hasTextInput = false)
        model.move(rawDeltaX = 8f, rawDeltaY = 0f, pagerWantsDrag = true, listCanConsumeDrag = false, allowListToPagerHandoff = false)

        model.cancel()

        assertEquals(PixelHostGestureRouterModel.Owner.NONE, model.owner)
        assertFalse(model.candidatePager)
        assertFalse(model.candidateList)
        assertFalse(model.touchMoved)
    }

    @Test
    fun sliderOwnsGestureAndBlocksPagerListCandidates() {
        val model = PixelHostGestureRouterModel(touchSlop = 4f)
        model.down(hasSlider = true, hasPager = true, hasList = true, hasTextInput = true)

        assertEquals(PixelHostGestureRouterModel.Owner.SLIDER, model.owner)
        assertFalse(model.candidatePager)
        assertFalse(model.candidateList)
        assertFalse(model.candidateTextInput)
    }
}
