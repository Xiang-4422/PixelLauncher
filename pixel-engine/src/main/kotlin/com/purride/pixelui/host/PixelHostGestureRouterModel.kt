package com.purride.pixelui

/**
 * Pure Kotlin gesture arbitration core used by host-router tests.
 *
 * Android-specific MotionEvent, coordinate mapping and VelocityTracker remain in
 * PixelHostView/PixelHostGestureRouter; this model captures the owner-selection
 * contract without depending on Android classes.
 */
internal class PixelHostGestureRouterModel(
    private val touchSlop: Float,
) {
    var owner: Owner = Owner.NONE
        private set
    var touchMoved: Boolean = false
        private set
    var candidatePager: Boolean = false
        private set
    var candidateList: Boolean = false
        private set
    var candidateTextInput: Boolean = false
        private set
    var edgeHandoff: Boolean = false
        private set

    fun down(
        hasSlider: Boolean,
        hasPager: Boolean,
        hasList: Boolean,
        hasTextInput: Boolean,
    ) {
        owner = if (hasSlider) Owner.SLIDER else Owner.NONE
        candidatePager = hasPager && !hasSlider
        candidateList = hasList && !hasSlider
        candidateTextInput = hasTextInput && !hasSlider
        touchMoved = false
        edgeHandoff = false
    }

    fun move(
        rawDeltaX: Float,
        rawDeltaY: Float,
        pagerWantsDrag: Boolean,
        listCanConsumeDrag: Boolean,
        allowListToPagerHandoff: Boolean,
    ) {
        if (kotlin.math.abs(rawDeltaX) > touchSlop || kotlin.math.abs(rawDeltaY) > touchSlop) {
            touchMoved = true
        }
        if (owner == Owner.SLIDER || owner == Owner.PAGER) return
        if (owner == Owner.LIST) {
            if (!listCanConsumeDrag && candidatePager && allowListToPagerHandoff) {
                owner = Owner.PAGER
                candidatePager = false
                candidateList = false
                edgeHandoff = true
            }
            return
        }
        val listWantsDrag = candidateList && kotlin.math.abs(rawDeltaY) > touchSlop &&
            kotlin.math.abs(rawDeltaY) >= kotlin.math.abs(rawDeltaX)
        if (listWantsDrag && listCanConsumeDrag) {
            owner = Owner.LIST
            candidateList = false
            return
        }
        if (candidatePager && pagerWantsDrag) {
            owner = Owner.PAGER
            candidatePager = false
            candidateList = false
        }
    }

    fun up(): Owner {
        val result = if (!touchMoved && candidateTextInput) Owner.TEXT_INPUT else owner
        resetGesture()
        return result
    }

    fun cancel() {
        resetGesture()
    }

    private fun resetGesture() {
        owner = Owner.NONE
        candidatePager = false
        candidateList = false
        candidateTextInput = false
        touchMoved = false
        edgeHandoff = false
    }

    enum class Owner {
        NONE,
        PAGER,
        LIST,
        TEXT_INPUT,
        SLIDER,
    }
}
