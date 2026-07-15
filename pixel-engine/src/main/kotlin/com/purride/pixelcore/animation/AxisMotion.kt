package com.purride.pixelcore

import kotlin.math.abs

/**
 * 一维位移运行时状态。
 *
 * 这个状态只描述拖动与吸附过程本身，不知道“页数”“当前页”“目标页”。
 * 分页语义由 pixel-engine UI layer 在更上一层负责。
 */
public data class AxisMotionState(
    val isDragging: Boolean = false,
    val dragOffsetPx: Float = 0f,
    val isSettling: Boolean = false,
    val settleStartOffsetPx: Float = 0f,
    val settleEndOffsetPx: Float = 0f,
    val settleProgress: Float = 1f,
)

/** 一维拖动和吸附动画控制器，供 pager/list 等上层语义复用。 */
public class AxisMotionController(
    private val settleDurationMs: Long = 240L,
) {
    /** 创建初始静止状态。 */
    public fun create(): AxisMotionState = AxisMotionState()

    /** 进入拖动状态，并取消正在进行的吸附动画。 */
    public fun startDrag(state: AxisMotionState): AxisMotionState {
        return state.copy(
            isDragging = true,
            isSettling = false,
            settleStartOffsetPx = 0f,
            settleEndOffsetPx = 0f,
            settleProgress = 1f,
        )
    }

    /** 按像素增量更新拖动偏移，并夹紧到给定范围。 */
    public fun dragBy(
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

    /** 结束拖动并吸附到目标偏移；距离足够小时立即完成。 */
    public fun settleTo(
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

    /** 回到初始静止状态。 */
    public fun reset(): AxisMotionState = create()

    /** 推进吸附动画，未在 settling 时保持原状态。 */
    public fun step(state: AxisMotionState, deltaMs: Long): AxisMotionState {
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

    /** 返回当前应该绘制的视觉偏移，settling 时包含缓动。 */
    public fun visualOffsetPx(state: AxisMotionState): Float {
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

    /** 当前是否处于拖动或吸附动画中。 */
    public fun isActive(state: AxisMotionState): Boolean = state.isDragging || state.isSettling

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + ((end - start) * progress.coerceIn(0f, 1f))
    }

    private fun easeOutCubic(progress: Float): Float {
        val safeProgress = progress.coerceIn(0f, 1f)
        val oneMinusT = 1f - safeProgress
        return 1f - (oneMinusT * oneMinusT * oneMinusT)
    }

    /** 集中提供 `AxisMotion` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        private const val SETTLE_EPSILON_PX = 0.25f
    }
}
