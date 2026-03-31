package com.purride.pixelcore

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class HorizontalPageState(
    val pageCount: Int,
    val currentIndex: Int,
    val isDragging: Boolean = false,
    val dragOffsetPx: Float = 0f,
    val isSettling: Boolean = false,
    val settleTargetIndex: Int = currentIndex,
    val settleStartOffsetPx: Float = 0f,
    val settleEndOffsetPx: Float = 0f,
    val settleProgress: Float = 1f,
)

data class HorizontalPageSnapshot(
    val anchorPageIndex: Int,
    val dragOffsetPx: Float,
    val pageCount: Int,
)

class HorizontalPageController(
    private val distanceThresholdFraction: Float = 0.4f,
    private val velocityThresholdPagesPerSecond: Float = 0.35f,
    private val settleDurationMs: Long = 240L,
) {
    fun create(pageCount: Int, currentIndex: Int): HorizontalPageState {
        val safePageCount = pageCount.coerceAtLeast(1)
        val safeIndex = currentIndex.coerceIn(0, safePageCount - 1)
        return HorizontalPageState(
            pageCount = safePageCount,
            currentIndex = safeIndex,
            settleTargetIndex = safeIndex,
        )
    }

    fun syncToIndex(state: HorizontalPageState, targetIndex: Int): HorizontalPageState {
        val safeIndex = targetIndex.coerceIn(0, state.pageCount - 1)
        return if (isActive(state)) {
            state.copy(
                currentIndex = safeIndex,
                settleTargetIndex = safeIndex,
            )
        } else {
            state.copy(
                currentIndex = safeIndex,
                settleTargetIndex = safeIndex,
                dragOffsetPx = 0f,
                settleStartOffsetPx = 0f,
                settleEndOffsetPx = 0f,
                settleProgress = 1f,
            )
        }
    }

    fun startDrag(state: HorizontalPageState): HorizontalPageState {
        return state.copy(
            isDragging = true,
            dragOffsetPx = 0f,
            isSettling = false,
            settleTargetIndex = state.currentIndex,
            settleStartOffsetPx = 0f,
            settleEndOffsetPx = 0f,
            settleProgress = 1f,
        )
    }

    fun dragBy(state: HorizontalPageState, deltaPx: Float, pageWidth: Int): HorizontalPageState {
        if (!state.isDragging) {
            return state
        }
        val safeWidth = pageWidth.coerceAtLeast(1).toFloat()
        val minOffset = if (state.currentIndex < state.pageCount - 1) -safeWidth else 0f
        val maxOffset = if (state.currentIndex > 0) safeWidth else 0f
        val nextOffset = (state.dragOffsetPx + deltaPx).coerceIn(minOffset, maxOffset)
        return state.copy(dragOffsetPx = nextOffset)
    }

    fun endDrag(
        state: HorizontalPageState,
        pageWidth: Int,
        velocityPxPerSecond: Float,
    ): HorizontalPageState {
        if (!state.isDragging) {
            return state
        }

        val safeWidth = pageWidth.coerceAtLeast(1).toFloat()
        val currentIndex = state.currentIndex
        val nextIndex = (currentIndex + 1).coerceAtMost(state.pageCount - 1)
        val previousIndex = (currentIndex - 1).coerceAtLeast(0)
        val distanceThreshold = safeWidth * distanceThresholdFraction
        val velocityThreshold = safeWidth * velocityThresholdPagesPerSecond

        val direction = resolveDirection(
            offsetPx = state.dragOffsetPx,
            distanceThreshold = distanceThreshold,
            velocityPxPerSecond = velocityPxPerSecond,
            velocityThreshold = velocityThreshold,
        )
        val targetIndex = when {
            direction > 0 -> previousIndex
            direction < 0 -> nextIndex
            else -> currentIndex
        }
        val endOffset = when {
            targetIndex > currentIndex -> -safeWidth
            targetIndex < currentIndex -> safeWidth
            else -> 0f
        }

        val shouldSettle = abs(state.dragOffsetPx - endOffset) > settleEpsilonPx
        return if (shouldSettle) {
            state.copy(
                isDragging = false,
                isSettling = true,
                settleTargetIndex = targetIndex,
                settleStartOffsetPx = state.dragOffsetPx,
                settleEndOffsetPx = endOffset,
                settleProgress = 0f,
            )
        } else {
            state.copy(
                isDragging = false,
                dragOffsetPx = 0f,
                isSettling = false,
                currentIndex = targetIndex,
                settleTargetIndex = targetIndex,
                settleStartOffsetPx = 0f,
                settleEndOffsetPx = 0f,
                settleProgress = 1f,
            )
        }
    }

    fun step(state: HorizontalPageState, deltaMs: Long): HorizontalPageState {
        if (!state.isSettling) {
            return state
        }
        val progressIncrement = deltaMs.toFloat() / settleDurationMs.coerceAtLeast(1).toFloat()
        val nextProgress = (state.settleProgress + progressIncrement).coerceIn(0f, 1f)
        return if (nextProgress >= 1f) {
            val settledIndex = state.settleTargetIndex.coerceIn(0, state.pageCount - 1)
            state.copy(
                currentIndex = settledIndex,
                isSettling = false,
                isDragging = false,
                dragOffsetPx = 0f,
                settleTargetIndex = settledIndex,
                settleStartOffsetPx = 0f,
                settleEndOffsetPx = 0f,
                settleProgress = 1f,
            )
        } else {
            state.copy(settleProgress = nextProgress)
        }
    }

    fun visualOffsetPx(state: HorizontalPageState): Float {
        return when {
            state.isDragging -> state.dragOffsetPx
            state.isSettling -> lerp(
                start = state.settleStartOffsetPx,
                end = state.settleEndOffsetPx,
                progress = easeOutCubic(state.settleProgress),
            )

            else -> 0f
        }
    }

    fun snapshot(state: HorizontalPageState): HorizontalPageSnapshot {
        return HorizontalPageSnapshot(
            anchorPageIndex = state.currentIndex,
            dragOffsetPx = visualOffsetPx(state),
            pageCount = state.pageCount,
        )
    }

    fun isActive(state: HorizontalPageState): Boolean = state.isDragging || state.isSettling

    private fun resolveDirection(
        offsetPx: Float,
        distanceThreshold: Float,
        velocityPxPerSecond: Float,
        velocityThreshold: Float,
    ): Int {
        return when {
            abs(offsetPx) >= distanceThreshold -> offsetPx.sign()
            abs(velocityPxPerSecond) >= velocityThreshold -> velocityPxPerSecond.sign()
            else -> 0
        }
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + ((end - start) * progress.coerceIn(0f, 1f))
    }

    private fun easeOutCubic(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        val oneMinusT = 1f - t
        return 1f - (oneMinusT * oneMinusT * oneMinusT)
    }

    private fun Float.sign(): Int = when {
        this > 0f -> 1
        this < 0f -> -1
        else -> 0
    }

    companion object {
        private const val settleEpsilonPx: Float = 0.25f
    }
}

object HorizontalPageRenderer {
    fun compose(
        currentPage: PixelBuffer,
        adjacentPage: PixelBuffer?,
        dragOffsetPx: Float,
        contentStartY: Int = 0,
    ): PixelBuffer {
        if (adjacentPage == null || abs(dragOffsetPx) < compositionEpsilonPx) {
            return currentPage
        }

        val out = PixelBuffer(width = currentPage.width, height = currentPage.height)
        out.clear()
        val lockedTopEndExclusive = contentStartY.coerceIn(0, currentPage.height)
        if (lockedTopEndExclusive > 0) {
            blitPage(
                out = out,
                page = currentPage,
                shiftX = 0,
                yStart = 0,
                yEndExclusive = lockedTopEndExclusive,
            )
        }

        val currentShiftX = dragOffsetPx.roundToInt().coerceIn(-currentPage.width, currentPage.width)
        blitPage(
            out = out,
            page = currentPage,
            shiftX = currentShiftX,
            yStart = lockedTopEndExclusive,
            yEndExclusive = currentPage.height,
        )

        val adjacentShiftX = if (dragOffsetPx > 0f) {
            currentShiftX - currentPage.width
        } else {
            currentShiftX + currentPage.width
        }
        blitPage(
            out = out,
            page = adjacentPage,
            shiftX = adjacentShiftX,
            yStart = lockedTopEndExclusive,
            yEndExclusive = currentPage.height,
        )
        return out
    }

    private fun blitPage(
        out: PixelBuffer,
        page: PixelBuffer,
        shiftX: Int,
        yStart: Int = 0,
        yEndExclusive: Int = page.height,
    ) {
        val destinationStartX = max(0, shiftX)
        val sourceStartX = max(0, -shiftX)
        val copyWidth = min(
            page.width - sourceStartX,
            out.width - destinationStartX,
        )
        val startY = yStart.coerceIn(0, out.height)
        val endY = yEndExclusive.coerceIn(startY, out.height)
        if (copyWidth <= 0) {
            return
        }
        if (endY <= startY) {
            return
        }

        for (y in startY until min(endY, page.height)) {
            for (x in 0 until copyWidth) {
                val value = page.getPixel(sourceStartX + x, y)
                out.setPixel(destinationStartX + x, y, value)
            }
        }
    }

    private const val compositionEpsilonPx: Float = 0.5f
}
