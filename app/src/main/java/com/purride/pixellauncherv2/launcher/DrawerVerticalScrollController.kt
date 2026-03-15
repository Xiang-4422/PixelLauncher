package com.purride.pixellauncherv2.launcher

import kotlin.math.abs
import kotlin.math.exp

data class DrawerVerticalScrollThresholds(
    val upwardStepPx: Float,
    val downwardStepPx: Float,
)

data class DrawerVerticalScrollConsumeResult(
    val stepDelta: Int,
    val residualOffsetPx: Float,
)

data class DrawerVerticalScrollAnimationStep(
    val stepDelta: Int,
    val residualOffsetPx: Float,
    val nextVelocityPxPerSecond: Float,
    val isAnimating: Boolean,
)

object DrawerVerticalScrollController {

    fun consumeDrag(
        residualOffsetPx: Float,
        deltaPx: Float,
        thresholds: DrawerVerticalScrollThresholds,
    ): DrawerVerticalScrollConsumeResult {
        val upStep = thresholds.upwardStepPx.coerceAtLeast(1f)
        val downStep = thresholds.downwardStepPx.coerceAtLeast(1f)
        var residual = residualOffsetPx + deltaPx
        var steps = 0

        while (residual <= -upStep) {
            steps += 1
            residual += upStep
        }
        while (residual >= downStep) {
            steps -= 1
            residual -= downStep
        }

        return DrawerVerticalScrollConsumeResult(
            stepDelta = steps,
            residualOffsetPx = residual,
        )
    }

    fun release(
        residualOffsetPx: Float,
        velocityPxPerSecond: Float,
        @Suppress("UNUSED_PARAMETER") thresholds: DrawerVerticalScrollThresholds,
    ): DrawerVerticalScrollAnimationStep {
        if (abs(velocityPxPerSecond) >= minFlingVelocityPxPerSecond) {
            return DrawerVerticalScrollAnimationStep(
                stepDelta = 0,
                residualOffsetPx = residualOffsetPx,
                nextVelocityPxPerSecond = velocityPxPerSecond,
                isAnimating = true,
            )
        }
        return DrawerVerticalScrollAnimationStep(
            stepDelta = 0,
            residualOffsetPx = residualOffsetPx,
            nextVelocityPxPerSecond = 0f,
            isAnimating = abs(residualOffsetPx) > snapEpsilonPx,
        )
    }

    fun stepAnimation(
        residualOffsetPx: Float,
        velocityPxPerSecond: Float,
        thresholds: DrawerVerticalScrollThresholds,
        deltaMs: Long,
    ): DrawerVerticalScrollAnimationStep {
        val deltaSeconds = (deltaMs.coerceAtLeast(0L)).toFloat() / 1000f
        var residual = residualOffsetPx
        var velocity = velocityPxPerSecond
        var stepDelta = 0

        if (abs(velocity) >= minFlingVelocityPxPerSecond && deltaSeconds > 0f) {
            val flingDrag = consumeDrag(
                residualOffsetPx = residual,
                deltaPx = velocity * deltaSeconds,
                thresholds = thresholds,
            )
            residual = flingDrag.residualOffsetPx
            stepDelta += flingDrag.stepDelta

            val decayFactor = exp((-flingDecayPerSecond * deltaSeconds).toDouble()).toFloat()
            velocity *= decayFactor
            if (abs(velocity) < minFlingVelocityPxPerSecond) {
                velocity = 0f
            }
        } else {
            velocity = 0f
        }

        if (velocity == 0f) {
            val snapDistance = snapSpeedPxPerSecond * deltaSeconds
            residual = snapTowardsZero(
                residualOffsetPx = residual,
                maxStepPx = snapDistance,
            )
        }

        return DrawerVerticalScrollAnimationStep(
            stepDelta = stepDelta,
            residualOffsetPx = residual,
            nextVelocityPxPerSecond = velocity,
            isAnimating = abs(velocity) >= minFlingVelocityPxPerSecond || abs(residual) > snapEpsilonPx,
        )
    }

    private fun snapTowardsZero(residualOffsetPx: Float, maxStepPx: Float): Float {
        if (abs(residualOffsetPx) <= snapEpsilonPx) {
            return 0f
        }
        if (maxStepPx <= 0f) {
            return residualOffsetPx
        }
        return when {
            residualOffsetPx > 0f -> (residualOffsetPx - maxStepPx).coerceAtLeast(0f)
            residualOffsetPx < 0f -> (residualOffsetPx + maxStepPx).coerceAtMost(0f)
            else -> 0f
        }
    }

    private const val minFlingVelocityPxPerSecond = 30f
    private const val flingDecayPerSecond = 7.5f
    private const val snapSpeedPxPerSecond = 420f
    private const val snapEpsilonPx = 0.25f
}
