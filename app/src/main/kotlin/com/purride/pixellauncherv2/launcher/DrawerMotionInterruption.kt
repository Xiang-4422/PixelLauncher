package com.purride.pixellauncherv2.launcher

data class DrawerMotionRuntimeState(
    val isDragging: Boolean,
    val isAnimating: Boolean,
    val residualOffsetPx: Float,
    val velocityPxPerSecond: Float,
)

object DrawerMotionInterruption {

    fun settleBeforeExplicitAction(state: DrawerMotionRuntimeState): DrawerMotionRuntimeState {
        return state.copy(
            isDragging = false,
            isAnimating = false,
            residualOffsetPx = 0f,
            velocityPxPerSecond = 0f,
        )
    }
}
