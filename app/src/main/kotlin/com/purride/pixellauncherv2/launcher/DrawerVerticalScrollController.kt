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

data class DrawerSettleTarget(
    val direction: Int,
    val targetResidualPx: Float,
    val completionStepDelta: Int,
)

data class DrawerVerticalScrollAnimationStep(
    val stepDelta: Int,
    val residualOffsetPx: Float,
    val nextVelocityPxPerSecond: Float,
    val settleTarget: DrawerSettleTarget?,
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
        thresholds: DrawerVerticalScrollThresholds,
    ): DrawerVerticalScrollAnimationStep {
        val settleTarget = resolveSettleTarget(
            residualOffsetPx = residualOffsetPx,
            velocityPxPerSecond = velocityPxPerSecond,
            thresholds = thresholds,
        )
        if (abs(velocityPxPerSecond) >= minFlingVelocityPxPerSecond) {
            return DrawerVerticalScrollAnimationStep(
                stepDelta = 0,
                residualOffsetPx = residualOffsetPx,
                nextVelocityPxPerSecond = velocityPxPerSecond,
                settleTarget = settleTarget,
                isAnimating = true,
            )
        }
        if (settleTarget != null) {
            return DrawerVerticalScrollAnimationStep(
                stepDelta = 0,
                residualOffsetPx = residualOffsetPx,
                nextVelocityPxPerSecond = 0f,
                settleTarget = settleTarget,
                isAnimating = true,
            )
        }
        return DrawerVerticalScrollAnimationStep(
            stepDelta = 0,
            residualOffsetPx = 0f,
            nextVelocityPxPerSecond = 0f,
            settleTarget = null,
            isAnimating = false,
        )
    }

    fun stepAnimation(
        residualOffsetPx: Float,
        velocityPxPerSecond: Float,
        thresholds: DrawerVerticalScrollThresholds,
        deltaMs: Long,
        settleTarget: DrawerSettleTarget? = null,
    ): DrawerVerticalScrollAnimationStep {
        val deltaSeconds = (deltaMs.coerceAtLeast(0L)).toFloat() / 1000f
        var residual = residualOffsetPx
        var velocity = velocityPxPerSecond
        var stepDelta = 0
        var nextSettleTarget = settleTarget

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
            if (nextSettleTarget != null) {
                stepDelta += nextSettleTarget.completionStepDelta
                residual -= nextSettleTarget.targetResidualPx
                nextSettleTarget = null
            }
            residual = stepTowardsZero(
                residualOffsetPx = residual,
                deltaSeconds = deltaSeconds,
            )
            if (abs(residual) <= snapEpsilonPx) {
                residual = 0f
            }
        }

        return DrawerVerticalScrollAnimationStep(
            stepDelta = stepDelta,
            residualOffsetPx = residual,
            nextVelocityPxPerSecond = velocity,
            settleTarget = nextSettleTarget,
            isAnimating = abs(velocity) >= minFlingVelocityPxPerSecond ||
                nextSettleTarget != null ||
                abs(residual) > snapEpsilonPx,
        )
    }

    private fun resolveSettleTarget(
        residualOffsetPx: Float,
        velocityPxPerSecond: Float,
        thresholds: DrawerVerticalScrollThresholds,
    ): DrawerSettleTarget? {
        val direction = when {
            velocityPxPerSecond <= -minFlingVelocityPxPerSecond -> 1
            velocityPxPerSecond >= minFlingVelocityPxPerSecond -> -1
            residualOffsetPx <= -snapEpsilonPx -> 1
            residualOffsetPx >= snapEpsilonPx -> -1
            else -> 0
        }
        return when (direction) {
            1 -> DrawerSettleTarget(
                direction = 1,
                targetResidualPx = -thresholds.upwardStepPx.coerceAtLeast(1f),
                completionStepDelta = 1,
            )
            -1 -> DrawerSettleTarget(
                direction = -1,
                targetResidualPx = thresholds.downwardStepPx.coerceAtLeast(1f),
                completionStepDelta = -1,
            )
            else -> null
        }
    }

    private fun stepTowardsZero(
        residualOffsetPx: Float,
        deltaSeconds: Float,
    ): Float {
        if (abs(residualOffsetPx) <= snapEpsilonPx) {
            return 0f
        }
        if (deltaSeconds <= 0f) {
            return residualOffsetPx
        }
        val settleProgress = 1f - exp((-settleApproachPerSecond * deltaSeconds).toDouble()).toFloat()
        return residualOffsetPx * (1f - settleProgress)
    }

    private const val minFlingVelocityPxPerSecond = 30f
    private const val flingDecayPerSecond = 7.5f
    private const val settleApproachPerSecond = 14f
    private const val snapEpsilonPx = 0.25f
}
