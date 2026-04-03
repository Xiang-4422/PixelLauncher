package com.purride.pixelcore

import kotlin.math.abs

/**
 * 一维位移运行时状态。
 *
 * 这个状态只描述拖动与吸附过程本身，不知道“页数”“当前页”“目标页”。
 * 分页语义由 `:pixel-ui` 在更上一层负责。
 */
data class AxisMotionState(
    val isDragging: Boolean = false,
    val dragOffsetPx: Float = 0f,
    val isSettling: Boolean = false,
    val settleStartOffsetPx: Float = 0f,
    val settleEndOffsetPx: Float = 0f,
    val settleProgress: Float = 1f,
)

class AxisMotionController(
    private val settleDurationMs: Long = 240L,
) {
    fun create(): AxisMotionState = AxisMotionState()

    fun startDrag(state: AxisMotionState): AxisMotionState {
        return state.copy(
            isDragging = true,
            isSettling = false,
            settleStartOffsetPx = 0f,
            settleEndOffsetPx = 0f,
            settleProgress = 1f,
        )
    }

    fun dragBy(
        state: AxisMotionState,
        deltaPx: Float,
        minOffsetPx: Float,
        maxOffsetPx: Float,
    ): AxisMotionState {
        if (!state.isDragging) {
            return state
        }
        val nextOffset = (state.dragOffsetPx + deltaPx).coerceIn(minOffsetPx, maxOffsetPx)
        return state.copy(dragOffsetPx = nextOffset)
    }

    fun settleTo(
        state: AxisMotionState,
        targetOffsetPx: Float,
    ): AxisMotionState {
        return if (abs(state.dragOffsetPx - targetOffsetPx) <= SETTLE_EPSILON_PX) {
            state.copy(
                isDragging = false,
                isSettling = false,
                dragOffsetPx = targetOffsetPx,
                settleStartOffsetPx = targetOffsetPx,
                settleEndOffsetPx = targetOffsetPx,
                settleProgress = 1f,
            )
        } else {
            state.copy(
                isDragging = false,
                isSettling = true,
                settleStartOffsetPx = state.dragOffsetPx,
                settleEndOffsetPx = targetOffsetPx,
                settleProgress = 0f,
            )
        }
    }

    fun reset(): AxisMotionState = create()

    fun step(state: AxisMotionState, deltaMs: Long): AxisMotionState {
        if (!state.isSettling) {
            return state
        }
        val progressIncrement = deltaMs.toFloat() / settleDurationMs.coerceAtLeast(1).toFloat()
        val nextProgress = (state.settleProgress + progressIncrement).coerceIn(0f, 1f)
        return if (nextProgress >= 1f) {
            state.copy(
                isSettling = false,
                dragOffsetPx = state.settleEndOffsetPx,
                settleProgress = 1f,
            )
        } else {
            state.copy(settleProgress = nextProgress)
        }
    }

    fun visualOffsetPx(state: AxisMotionState): Float {
        return when {
            state.isDragging -> state.dragOffsetPx
            state.isSettling -> lerp(
                start = state.settleStartOffsetPx,
                end = state.settleEndOffsetPx,
                progress = easeOutCubic(state.settleProgress),
            )

            else -> state.dragOffsetPx
        }
    }

    fun isActive(state: AxisMotionState): Boolean = state.isDragging || state.isSettling

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + ((end - start) * progress.coerceIn(0f, 1f))
    }

    private fun easeOutCubic(progress: Float): Float {
        val safeProgress = progress.coerceIn(0f, 1f)
        val oneMinusT = 1f - safeProgress
        return 1f - (oneMinusT * oneMinusT * oneMinusT)
    }

    companion object {
        private const val SETTLE_EPSILON_PX = 0.25f
    }
}
