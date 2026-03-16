package com.purride.pixellauncherv2.launcher

object DrawerDirectionalSettlePolicy {

    fun directionForDeltaY(deltaY: Float): Int {
        return when {
            deltaY < 0f -> 1
            deltaY > 0f -> -1
            else -> 0
        }
    }

    fun hasAdvanced(anchorIndex: Int, currentIndex: Int, direction: Int): Boolean {
        return when {
            direction > 0 -> currentIndex > anchorIndex
            direction < 0 -> currentIndex < anchorIndex
            else -> false
        }
    }

    fun canAdvance(currentIndex: Int, lastIndex: Int, direction: Int): Boolean {
        if (lastIndex < 0) {
            return false
        }
        return when {
            direction > 0 -> currentIndex < lastIndex
            direction < 0 -> currentIndex > 0
            else -> false
        }
    }

    fun shouldForceAdvance(
        anchorIndex: Int,
        currentIndex: Int,
        direction: Int,
        mustAdvance: Boolean,
        lastIndex: Int,
    ): Boolean {
        if (!mustAdvance || direction == 0) {
            return false
        }
        if (hasAdvanced(anchorIndex = anchorIndex, currentIndex = currentIndex, direction = direction)) {
            return false
        }
        return canAdvance(
            currentIndex = currentIndex,
            lastIndex = lastIndex,
            direction = direction,
        )
    }
}
