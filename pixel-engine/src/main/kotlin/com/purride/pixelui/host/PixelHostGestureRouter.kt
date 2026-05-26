package com.purride.pixelui

import android.view.MotionEvent
import android.view.VelocityTracker
import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.internal.PixelTextInputTarget
import kotlin.math.abs

/**
 * Routes Android touch events to pixel-engine gesture owners.
 *
 * PixelHostView keeps platform entry points and coordinate mapping; this router
 * owns the pager/list/text field/slider arbitration state machine.
 */
internal class PixelHostGestureRouter(
    private val host: PixelHostView,
) {
    fun onTouchEvent(event: MotionEvent): Boolean? {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_UP -> onUp(event)
            MotionEvent.ACTION_CANCEL -> onCancel()
            else -> null
        }
    }

    private fun onDown(event: MotionEvent): Boolean {
        host.velocityTracker?.recycle()
        host.velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
        host.touchDownX = event.x
        host.touchDownY = event.y
        host.touchMoved = false
        val logicalPoint = host.mapTouchToLogical(event.x, event.y) ?: return true
        host.touchDownLogicalX = logicalPoint.first
        host.touchDownLogicalY = logicalPoint.second
        host.lastPagerLogicalX = logicalPoint.first
        host.lastPagerLogicalY = logicalPoint.second
        host.lastListLogicalY = logicalPoint.second
        host.activeSliderTarget = host.lastRenderResult
            ?.sliderTargets
            ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
        host.candidatePagerTarget = if (host.activeSliderTarget == null) {
            host.lastRenderResult
                ?.pagerTargets
                ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
        } else null
        host.candidateListTarget = if (host.activeSliderTarget == null) {
            host.lastRenderResult
                ?.listTargets
                ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
        } else null
        val textInputTarget = host.lastRenderResult
            ?.textInputTargets
            ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
        host.candidateTextInputTarget = if (host.activeSliderTarget == null) textInputTarget else null
        host.activeTextInputSelectionTarget = null
        host.activeTextInputSelectionHandle = null
        if (host.activeSliderTarget == null && textInputTarget != null && !textInputTarget.readOnly) {
            resolveNearestSelectionHandle(textInputTarget, logicalPoint.first, logicalPoint.second)?.let { handle ->
                host.activeTextInputSelectionTarget = textInputTarget
                host.activeTextInputSelectionHandle = handle
            }
        }
        if (textInputTarget == null && host.focusedTextInputTarget != null) {
            host.clearFocusedTextInput()
        }
        host.activePagerTarget = null
        host.activeListTarget = null
        host.nestedScrollSession.consumedDeltaPx = 0f
        host.nestedScrollSession.remainingDeltaPx = 0f
        host.nestedScrollSession.edgeHandoff = false
        return true
    }

    private fun onMove(event: MotionEvent): Boolean {
        host.velocityTracker?.addMovement(event)
        val logicalPoint = host.mapTouchToLogical(event.x, event.y) ?: return true
        val rawDeltaX = event.x - host.touchDownX
        val rawDeltaY = event.y - host.touchDownY
        if (abs(rawDeltaX) > host.touchSlop || abs(rawDeltaY) > host.touchSlop) host.touchMoved = true

        host.activeSliderTarget?.let { target ->
            val localX = logicalPoint.first - target.bounds.left
            val ratio = (localX.toFloat() / target.bounds.width).coerceIn(0f, 1f)
            target.onDrag(ratio)
            host.invalidate()
            return true
        }

        host.activeTextInputSelectionTarget?.let { target ->
            if (host.touchMoved && host.activePagerTarget == null && host.activeListTarget == null) {
                updateSelectionHandle(target, logicalPoint.first, logicalPoint.second)
                host.invalidate()
                return true
            }
        }

        host.candidateTextInputTarget?.let { target ->
            if (host.touchMoved && host.activePagerTarget == null && host.activeListTarget == null) {
                host.focusTextInput(target)
                val selection = resolveTextInputSelection(target, logicalPoint.first, logicalPoint.second)
                target.controller.setSelection(target.state, selection)
                host.invalidate()
                return true
            }
        }

        host.activePagerTarget?.let { target ->
            val deltaPx = when (target.axis) {
                PixelAxis.HORIZONTAL -> logicalPoint.first - host.lastPagerLogicalX
                PixelAxis.VERTICAL -> logicalPoint.second - host.lastPagerLogicalY
            }.toFloat()
            host.nestedScrollSession.consumedDeltaPx = deltaPx
            host.nestedScrollSession.remainingDeltaPx = 0f
            target.controller.dragBy(target.state, deltaPx, host.pagerViewportSize(target))
            host.lastPagerLogicalX = logicalPoint.first
            host.lastPagerLogicalY = logicalPoint.second
            host.invalidate()
            return true
        }

        host.activeListTarget?.let { target ->
            val deltaPx = (logicalPoint.second - host.lastListLogicalY).toFloat()
            val listCanConsumeDrag = target.controller.canConsumeDrag(
                target.state, deltaPx, target.viewportHeightPx, target.contentHeightPx,
            )
            host.nestedScrollSession.remainingDeltaPx = if (listCanConsumeDrag) 0f else deltaPx
            if (listCanConsumeDrag) {
                host.nestedScrollSession.consumedDeltaPx = deltaPx
                target.controller.dragBy(target.state, deltaPx, target.viewportHeightPx, target.contentHeightPx)
                host.lastListLogicalY = logicalPoint.second
                host.invalidate()
                return true
            }
            val pagerTarget = host.candidatePagerTarget
            if (pagerTarget != null &&
                host.nestedScrollPolicy.shouldHandOffListToPager(pagerTarget.axis, listCanConsumeDrag, deltaPx)
            ) {
                host.nestedScrollSession.edgeHandoff = true
                host.activeListTarget = null
                host.activePagerTarget = pagerTarget
                host.candidatePagerTarget = null
                host.candidateListTarget = null
                pagerTarget.controller.startDrag(pagerTarget.state)
                host.lastPagerLogicalX = logicalPoint.first
                host.lastPagerLogicalY = host.lastListLogicalY
                pagerTarget.controller.dragBy(pagerTarget.state, deltaPx, host.pagerViewportSize(pagerTarget))
                host.nestedScrollSession.consumedDeltaPx = deltaPx
                host.nestedScrollSession.remainingDeltaPx = 0f
                host.lastPagerLogicalX = logicalPoint.first
                host.lastPagerLogicalY = logicalPoint.second
                host.invalidate()
                return true
            }
            host.lastListLogicalY = logicalPoint.second
            return true
        }

        host.candidatePagerTarget?.let { target ->
            val pagerWantsDrag = host.pagerGesturePolicy.shouldStartDrag(target.axis, rawDeltaX, rawDeltaY, host.touchSlop)
            val listWantsDrag = host.candidateListTarget?.let { host.shouldStartListDrag(rawDeltaX, rawDeltaY) } ?: false
            val listCanConsumeDrag = host.candidateListTarget?.let { listTarget ->
                listTarget.controller.canConsumeDrag(listTarget.state, rawDeltaY, listTarget.viewportHeightPx, listTarget.contentHeightPx)
            } ?: false
            val shouldDeferToList = host.nestedScrollPolicy.shouldDeferPagerToList(
                target.axis, pagerWantsDrag, listWantsDrag, listCanConsumeDrag,
            )
            if (pagerWantsDrag && !shouldDeferToList) {
                host.activePagerTarget = target
                host.candidatePagerTarget = null
                target.controller.startDrag(target.state)
                val initialDeltaPx = when (target.axis) {
                    PixelAxis.HORIZONTAL -> logicalPoint.first - host.touchDownLogicalX
                    PixelAxis.VERTICAL -> logicalPoint.second - host.touchDownLogicalY
                }.toFloat()
                if (initialDeltaPx != 0f) {
                    target.controller.dragBy(target.state, initialDeltaPx, host.pagerViewportSize(target))
                }
                host.nestedScrollSession.consumedDeltaPx = initialDeltaPx
                host.nestedScrollSession.remainingDeltaPx = 0f
                host.lastPagerLogicalX = logicalPoint.first
                host.lastPagerLogicalY = logicalPoint.second
                host.candidateListTarget = null
                host.invalidate()
            }
        }

        host.candidateListTarget?.let { target ->
            if (host.shouldStartListDrag(rawDeltaX, rawDeltaY)) {
                host.activeListTarget = target
                host.candidateListTarget = null
                target.controller.startDrag(target.state)
                val initialDeltaPx = (logicalPoint.second - host.touchDownLogicalY).toFloat()
                if (initialDeltaPx != 0f) {
                    target.controller.dragBy(target.state, initialDeltaPx, target.viewportHeightPx, target.contentHeightPx)
                }
                host.nestedScrollSession.consumedDeltaPx = initialDeltaPx
                host.nestedScrollSession.remainingDeltaPx = 0f
                host.lastListLogicalY = logicalPoint.second
                host.invalidate()
            }
        }
        return true
    }

    private fun onUp(event: MotionEvent): Boolean {
        host.velocityTracker?.addMovement(event)
        host.velocityTracker?.computeCurrentVelocity(1000)
        val logicalPoint = host.mapTouchToLogical(event.x, event.y)

        host.activeSliderTarget?.let { target ->
            val localX = if (logicalPoint != null) {
                logicalPoint.first - target.bounds.left
            } else {
                target.bounds.width / 2
            }
            val ratio = (localX.toFloat() / target.bounds.width).coerceIn(0f, 1f)
            target.onRelease(ratio)
            host.activeSliderTarget = null
            host.invalidate()
            return true
        }

        host.activePagerTarget?.let { target ->
            val velocityPxPerSecond = host.rawVelocityToLogical(host.velocityTracker, target.axis)
            target.controller.endDrag(target.state, host.pagerViewportSize(target), velocityPxPerSecond)
            host.activePagerTarget = null
            host.candidatePagerTarget = null
            host.candidateListTarget = null
            host.candidateTextInputTarget = null
            clearActiveSelectionHandle()
            recycleVelocityTracker()
            host.invalidate()
            return true
        }
        host.activeListTarget?.let { target ->
            val velocityPxPerSecond = host.rawVelocityToLogical(host.velocityTracker, PixelAxis.VERTICAL)
            target.controller.endDrag(target.state, velocityPxPerSecond, target.viewportHeightPx, target.contentHeightPx)
            host.activeListTarget = null
            host.candidateListTarget = null
            host.candidatePagerTarget = null
            host.candidateTextInputTarget = null
            clearActiveSelectionHandle()
            recycleVelocityTracker()
            host.invalidate()
            return true
        }

        host.candidatePagerTarget = null
        host.candidateListTarget = null
        if (!host.touchMoved && logicalPoint != null) {
            (host.candidateTextInputTarget ?: host.resolveTextInputTarget(logicalPoint.first, logicalPoint.second))?.let { target ->
                host.focusTextInput(target)
                val selection = resolveTextInputSelection(target, logicalPoint.first, logicalPoint.second)
                if (!target.readOnly &&
                    host.lastTextInputTapState === target.state &&
                    event.eventTime - host.lastTextInputTapTimeMs in 0L..DOUBLE_TAP_TIMEOUT_MS
                ) {
                    target.controller.selectWordAt(target.state, selection)
                } else if (!target.readOnly) {
                    target.controller.setSelection(target.state, selection)
                }
                host.lastTextInputTapState = target.state
                host.lastTextInputTapTimeMs = event.eventTime
                host.invalidate()
                host.candidateTextInputTarget = null
                recycleVelocityTracker()
                return true
            }
            host.resolveClickTarget(logicalPoint.first, logicalPoint.second)?.onClick?.invoke()
            host.invalidate()
        }
        host.candidateTextInputTarget = null
        clearActiveSelectionHandle()
        recycleVelocityTracker()
        return true
    }

    private fun onCancel(): Boolean {
        host.activeSliderTarget = null
        clearActiveSelectionHandle()
        host.activePagerTarget?.let { target ->
            target.controller.cancelDrag(target.state)
            host.invalidate()
        }
        host.activeListTarget?.let { target ->
            target.controller.endDrag(target.state, 0f, target.viewportHeightPx, target.contentHeightPx)
        }
        host.nestedScrollSession.resetGesture()
        recycleVelocityTracker()
        return true
    }

    private fun updateSelectionHandle(target: PixelTextInputTarget, logicalX: Int, logicalY: Int) {
        if (target.readOnly) return
        host.focusTextInput(target)
        val selection = resolveTextInputSelection(target, logicalX, logicalY)
        when (host.activeTextInputSelectionHandle) {
            TextInputSelectionHandle.START -> target.controller.setSelection(
                target.state,
                selection.coerceAtMost(target.state.selectionEnd),
                target.state.selectionEnd,
            )
            TextInputSelectionHandle.END -> target.controller.setSelection(
                target.state,
                target.state.selectionStart,
                selection.coerceAtLeast(target.state.selectionStart),
            )
            null -> Unit
        }
    }

    private fun resolveNearestSelectionHandle(target: PixelTextInputTarget, logicalX: Int, logicalY: Int): TextInputSelectionHandle? {
        val state = target.state
        if (state.selectionStart == state.selectionEnd) return null
        val selection = resolveTextInputSelection(target, logicalX, logicalY)
        val startDistance = abs(selection - state.selectionStart)
        val endDistance = abs(selection - state.selectionEnd)
        return if (startDistance <= endDistance) TextInputSelectionHandle.START else TextInputSelectionHandle.END
    }

    private fun clearActiveSelectionHandle() {
        host.activeTextInputSelectionTarget = null
        host.activeTextInputSelectionHandle = null
    }

    private fun resolveTextInputSelection(target: PixelTextInputTarget, logicalX: Int, logicalY: Int): Int {
        val text = target.state.text
        if (text.isEmpty()) return 0
        val lines = text.split('\n')
        val lineCount = lines.size.coerceAtLeast(1)
        val lineHeight = (target.bounds.height / lineCount).coerceAtLeast(1)
        val localY = (logicalY - target.bounds.top).coerceAtLeast(0)
        val lineIndex = (localY / lineHeight).coerceIn(0, lineCount - 1)
        val line = lines[lineIndex]
        val localX = (logicalX - target.bounds.left).coerceIn(0, target.bounds.width)
        val column = if (line.isEmpty()) 0 else (localX.toLong() * line.length / target.bounds.width.coerceAtLeast(1)).toInt()
        var index = 0
        repeat(lineIndex) { lineOffset ->
            index += lines[lineOffset].length + 1
        }
        return (index + column.coerceIn(0, line.length)).coerceIn(0, text.length)
    }

    private fun recycleVelocityTracker() {
        host.velocityTracker?.recycle()
        host.velocityTracker = null
    }

    private companion object {
        const val DOUBLE_TAP_TIMEOUT_MS = 300L
    }
}

internal enum class TextInputSelectionHandle {
    START,
    END,
}
