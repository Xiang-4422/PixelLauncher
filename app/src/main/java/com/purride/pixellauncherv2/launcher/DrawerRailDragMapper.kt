package com.purride.pixellauncherv2.launcher

object DrawerRailDragMapper {

    fun consumeDrag(
        accumulatedPx: Float,
        deltaPx: Float,
        pixelsPerApp: Float,
    ): DragConsumeResult {
        val threshold = pixelsPerApp.coerceAtLeast(1f)
        var accumulator = accumulatedPx + deltaPx
        var steps = 0

        while (accumulator >= threshold) {
            steps += 1
            accumulator -= threshold
        }
        while (accumulator <= -threshold) {
            steps -= 1
            accumulator += threshold
        }
        return DragConsumeResult(
            accumulatedPx = accumulator,
            stepDelta = steps,
        )
    }
}

data class DragConsumeResult(
    val accumulatedPx: Float,
    val stepDelta: Int,
)
