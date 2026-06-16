package com.purride.pixelui.testing

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.PixelFocusManager
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelKeyEvent
import com.purride.pixelui.PixelTextEditAction
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.internal.PixelListTarget
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelRect
import com.purride.pixelui.internal.PixelRefreshTarget
import com.purride.pixelui.internal.PixelRenderResult
import com.purride.pixelui.internal.PixelScrollbarTarget
import com.purride.pixelui.internal.PixelSliderTarget
import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.PixelUiRuntime
import kotlin.math.abs
import java.util.IdentityHashMap

public class PixelTester {
    public val scheduler: ManualFrameScheduler = ManualFrameScheduler()
    public val vsync: PixelTickerProvider = PixelTickerProvider(scheduler)

    private val runtime = PixelUiRuntime(onVisualUpdate = { needsRender = true })
    private var root: Widget? = null
    private var logicalWidth: Int = 0
    private var logicalHeight: Int = 0
    private var needsRender: Boolean = false
    private var focusedTextInputTarget: PixelTextInputTarget? = null
    public var clipboardText: String? = null
        private set
    private var currentNanos: Long = 0L

    internal var renderResult: PixelRenderResult? = null
        private set

    public fun pumpWidget(widget: Widget, logicalWidth: Int, logicalHeight: Int) {
        root = widget
        this.logicalWidth = logicalWidth
        this.logicalHeight = logicalHeight
        needsRender = true
        render()
    }

    public fun tap(finder: PixelFinder) {
        val point = resolvePoint(finder, TargetKind.ANY)
        dispatchTapAt(point.x, point.y)
        render()
    }

    public fun doubleTap(finder: PixelFinder) {
        val point = resolvePoint(finder, TargetKind.ANY)
        val target = renderResult?.textInputTargets?.lastOrNull { it.bounds.contains(point.x, point.y) }
            ?: fail("No text input target at (${point.x},${point.y})", finder)
        if (target.readOnly) return
        focusTextInput(target)
        target.controller.selectWordAt(target.state, resolveTextInputSelection(target, point.x, point.y))
        render()
    }

    public fun longPress(finder: PixelFinder) {
        val point = resolvePoint(finder, TargetKind.ANY)
        val target = renderResult?.textInputTargets?.lastOrNull { it.bounds.contains(point.x, point.y) }
            ?: fail("No text input target at (${point.x},${point.y})", finder)
        if (target.readOnly) return
        focusTextInput(target)
        target.controller.selectWordAt(target.state, resolveTextInputSelection(target, point.x, point.y))
        render()
    }

    public fun drag(finder: PixelFinder, dx: Int, dy: Int) {
        val point = resolvePoint(finder, TargetKind.DRAG)
        dispatchDrag(point.x, point.y, dx, dy)
        render()
    }

    public fun fling(finder: PixelFinder, dx: Int, dy: Int, velocityPxPerSecond: Float) {
        val point = resolvePoint(finder, TargetKind.DRAG)
        dispatchFling(point.x, point.y, dx, dy, velocityPxPerSecond)
        render()
    }

    public fun cancelDrag(finder: PixelFinder, dx: Int = 0, dy: Int = 0) {
        val point = resolvePoint(finder, TargetKind.DRAG)
        dispatchDragCancel(point.x, point.y, dx, dy)
        render()
    }

    public fun dragSelectionStartHandle(finder: PixelFinder, dx: Int, dy: Int) {
        dragSelectionHandle(finder, TextSelectionHandle.START, dx, dy)
    }

    public fun dragSelectionEndHandle(finder: PixelFinder, dx: Int, dy: Int) {
        dragSelectionHandle(finder, TextSelectionHandle.END, dx, dy)
    }

    public fun enterText(finder: PixelFinder, text: String) {
        val target = resolveTextInputTarget(finder)
        ensureTextInputEditable(target)
        focusTextInput(target)
        target.controller.updateText(target.state, text)
        target.onChanged?.invoke(target.state.text)
        render()
    }

    public fun submitTextInput() {
        val target = focusedTextInputTarget ?: fail("No focused text input target")
        target.onSubmitted?.invoke(target.state.text)
        if (target.action == com.purride.pixelui.PixelTextInputAction.NEXT) {
            PixelFocusManager.dispatchKeyEvent(PixelKeyEvent(PixelKey.TAB))
            needsRender = true
            render()
        }
    }

    public fun performTextEditAction(
        finder: PixelFinder,
        action: PixelTextEditAction,
        pasteText: String? = null,
    ): Boolean {
        val target = resolveTextInputTarget(finder)
        focusTextInput(target)
        val changed = when (action) {
            PixelTextEditAction.COPY -> {
                val selected = target.controller.selectedText(target.state)
                if (selected.isEmpty()) return false
                clipboardText = selected
                false
            }
            PixelTextEditAction.CUT -> {
                ensureTextInputEditable(target)
                val selected = target.controller.cutSelection(target.state) ?: return false
                clipboardText = selected
                true
            }
            PixelTextEditAction.PASTE -> {
                ensureTextInputEditable(target)
                val text = pasteText ?: clipboardText.orEmpty()
                if (text.isEmpty()) return false
                target.controller.paste(target.state, text)
                true
            }
            PixelTextEditAction.SELECT_ALL -> {
                if (target.state.text.isEmpty()) return false
                target.controller.selectAll(target.state)
                false
            }
        }
        if (changed) target.onChanged?.invoke(target.state.text)
        render()
        return true
    }

    public fun pressKey(key: PixelKey, character: Char? = null): Boolean {
        val handled = PixelFocusManager.dispatchKeyEvent(PixelKeyEvent(key = key, character = character))
        if (handled) {
            needsRender = true
            render()
        }
        return handled
    }

    public fun composeText(finder: PixelFinder, text: String) {
        val target = resolveTextInputTarget(finder)
        ensureTextInputEditable(target)
        focusTextInput(target)
        val start = target.state.selectionStart
        val end = target.state.selectionEnd
        val before = target.state.text.substring(0, start)
        val after = target.state.text.substring(end)
        val nextText = before + text + after
        target.controller.updateText(
            state = target.state,
            text = nextText,
            selectionStart = start + text.length,
            selectionEnd = start + text.length,
            compositionStart = start,
            compositionEnd = start + text.length,
        )
        target.onChanged?.invoke(target.state.text)
        render()
    }

    public fun updateComposition(finder: PixelFinder, start: Int, end: Int) {
        val target = resolveTextInputTarget(finder)
        ensureTextInputEditable(target)
        focusTextInput(target)
        target.controller.updateComposition(target.state, start, end)
        render()
    }

    public fun pumpFrame(deltaMs: Long) {
        val nextNanos = currentNanos + deltaMs * 1_000_000L
        currentNanos = nextNanos
        stepActiveScrollTargets(deltaMs)
        scheduler.advanceFrame(nextNanos)
        render()
    }

    public fun pumpAndSettle(maxFrames: Int = 60) {
        repeat(maxFrames) {
            pumpFrame(16)
            if (!hasPendingActivity()) {
                return
            }
        }
        fail("pumpAndSettle did not settle after $maxFrames frames")
    }

    public fun dumpElementTree(): String = runtime.dumpElementTree()

    public fun dumpRenderTree(): String = runtime.dumpRenderTree()

    public fun dumpSemanticsTree(): String {
        val nodes = renderResult?.semanticsNodes.orEmpty()
        if (nodes.isEmpty()) return "<empty semantics>"
        return nodes.joinToString(separator = "\n") { node ->
            "${node.role} label=\"${node.label}\" enabled=${node.enabled} focused=${node.focused} bounds=${node.left},${node.top},${node.width},${node.height}"
        }
    }

    public fun exists(finder: PixelFinder): Boolean {
        return finder.resolve(runtime.collectWidgets()) != null || finder.resolve(root) != null
    }

    public fun dispose() {
        runtime.dispose()
        scheduler.clear()
        PixelFocusManager.clearFocus()
    }

    private fun render() {
        val widget = root ?: return
        var pass = 0
        do {
            renderResult = runtime.render(widget, logicalWidth, logicalHeight)
            val requestedTarget = renderResult
                ?.textInputTargets
                ?.lastOrNull { it.state.focusRequested }
            if (requestedTarget != null) {
                requestedTarget.state.focusRequested = false
                focusTextInput(requestedTarget)
            }
            pass += 1
        } while (requestedTarget != null && pass < 2)
        focusedTextInputTarget = renderResult
            ?.textInputTargets
            ?.lastOrNull { it.state.isFocused }
            ?: focusedTextInputTarget?.let { previous ->
                renderResult?.textInputTargets?.lastOrNull { it.state === previous.state }
            }
        needsRender = false
    }

    private fun stepActiveScrollTargets(deltaMs: Long) {
        val result = renderResult ?: return
        result.pagerTargets.forEach { target ->
            val wasActive = target.controller.isActive(target.state)
            target.controller.step(target.state, deltaMs)
            if (target.state.currentPage != target.state.lastDispatchedPage) {
                target.state.lastDispatchedPage = target.state.currentPage
                target.onPageChanged?.invoke(target.state.currentPage)
            }
            if (wasActive || target.controller.isActive(target.state)) needsRender = true
        }
        result.listTargets.forEach { target ->
            val wasActive = target.controller.isActive(target.state)
            target.controller.step(target.state, deltaMs, target.viewportHeightPx, target.contentHeightPx)
            if (wasActive || target.controller.isActive(target.state)) needsRender = true
        }
    }

    private fun hasPendingActivity(): Boolean {
        val result = renderResult
        val hasScrollActivity = result?.pagerTargets?.any { it.controller.isActive(it.state) } == true ||
            result?.listTargets?.any { it.controller.isActive(it.state) } == true
        return scheduler.pendingCount > 0 ||
            vsync.activeTickerCount > 0 ||
            needsRender ||
            runtime.hasPendingBuild() ||
            hasScrollActivity
    }

    private fun dispatchTapAt(x: Int, y: Int) {
        renderResult?.textInputTargets?.lastOrNull { it.bounds.contains(x, y) }?.let { target ->
            focusTextInput(target)
            return
        }
        val clickTarget = renderResult?.clickTargets?.lastOrNull { it.bounds.contains(x, y) }
            ?: fail("No click target at ($x,$y)")
        clickTarget.onClick.invoke()
        needsRender = true
    }

    private fun dispatchDrag(startX: Int, startY: Int, dx: Int, dy: Int) {
        renderResult?.scrollbarTargets?.lastOrNull { it.bounds.contains(startX, startY) }?.let { target ->
            dispatchScrollbarDrag(target, startY, startY + dy)
            return
        }
        renderResult?.refreshTargets?.lastOrNull { it.bounds.contains(startX, startY) }?.let { target ->
            if (dy > 0 && abs(dy) >= abs(dx) && target.canStartPull(dy.toFloat())) {
                dispatchRefreshDrag(target, dy)
                return
            }
        }
        renderResult?.sliderTargets?.lastOrNull { it.bounds.contains(startX, startY) }?.let { target ->
            dispatchSliderDrag(target, startX + dx)
            return
        }
        val listTarget = renderResult?.listTargets?.lastOrNull { it.bounds.contains(startX, startY) }
        val pagerTarget = renderResult?.pagerTargets?.lastOrNull { it.bounds.contains(startX, startY) }
        if (listTarget != null && shouldStartListDrag(dx, dy)) {
            val deltaY = dy.toFloat()
            if (listTarget.controller.canConsumeDrag(
                    listTarget.state,
                    deltaY,
                    listTarget.viewportHeightPx,
                    listTarget.contentHeightPx,
                )
            ) {
                dispatchListDrag(listTarget, deltaY)
            } else if (pagerTarget != null) {
                dispatchPagerDrag(pagerTarget, dx.toFloat(), deltaY)
            } else {
                dispatchListDrag(listTarget, deltaY)
            }
            return
        }
        if (pagerTarget != null) {
            dispatchPagerDrag(pagerTarget, dx.toFloat(), dy.toFloat())
            return
        }
        renderResult?.textInputTargets?.lastOrNull { it.bounds.contains(startX, startY) }?.let { target ->
            focusTextInput(target)
            if (target.state.selectionStart != target.state.selectionEnd) {
                val handle = nearestSelectionHandle(target, startX, startY)
                updateSelectionHandle(target, handle, startX + dx, startY + dy)
            } else {
                target.controller.setSelection(target.state, resolveTextInputSelection(target, startX + dx, startY + dy))
            }
            needsRender = true
            return
        }
        fail("No draggable target at ($startX,$startY)")
    }

    private fun dispatchFling(startX: Int, startY: Int, dx: Int, dy: Int, velocityPxPerSecond: Float) {
        val listTarget = renderResult?.listTargets?.lastOrNull { it.bounds.contains(startX, startY) }
        val pagerTarget = renderResult?.pagerTargets?.lastOrNull { it.bounds.contains(startX, startY) }
        if (listTarget != null && shouldStartListDrag(dx, dy)) {
            listTarget.controller.startDrag(listTarget.state)
            listTarget.controller.dragBy(listTarget.state, dy.toFloat(), listTarget.viewportHeightPx, listTarget.contentHeightPx)
            listTarget.controller.endDrag(
                listTarget.state,
                velocityPxPerSecond,
                listTarget.viewportHeightPx,
                listTarget.contentHeightPx,
            )
            needsRender = true
            return
        }
        if (pagerTarget != null) {
            val delta = when (pagerTarget.axis) {
                PixelAxis.HORIZONTAL -> dx.toFloat()
                PixelAxis.VERTICAL -> dy.toFloat()
            }
            val viewport = when (pagerTarget.axis) {
                PixelAxis.HORIZONTAL -> pagerTarget.bounds.width
                PixelAxis.VERTICAL -> pagerTarget.bounds.height
            }.coerceAtLeast(1)
            pagerTarget.controller.startDrag(pagerTarget.state)
            pagerTarget.controller.dragBy(pagerTarget.state, delta, viewport)
            pagerTarget.controller.endDrag(pagerTarget.state, viewport, velocityPxPerSecond)
            pagerTarget.onPageChanged?.invoke(pagerTarget.state.currentPage)
            needsRender = true
            return
        }
        fail("No fling target at ($startX,$startY)")
    }

    private fun dispatchDragCancel(startX: Int, startY: Int, dx: Int, dy: Int) {
        val listTarget = renderResult?.listTargets?.lastOrNull { it.bounds.contains(startX, startY) }
        val pagerTarget = renderResult?.pagerTargets?.lastOrNull { it.bounds.contains(startX, startY) }
        if (listTarget != null && shouldStartListDrag(dx, dy)) {
            listTarget.controller.startDrag(listTarget.state)
            if (dy != 0) {
                listTarget.controller.dragBy(listTarget.state, dy.toFloat(), listTarget.viewportHeightPx, listTarget.contentHeightPx)
            }
            listTarget.controller.endDrag(listTarget.state, 0f, listTarget.viewportHeightPx, listTarget.contentHeightPx)
            needsRender = true
            return
        }
        if (pagerTarget != null) {
            val delta = when (pagerTarget.axis) {
                PixelAxis.HORIZONTAL -> dx.toFloat()
                PixelAxis.VERTICAL -> dy.toFloat()
            }
            val viewport = when (pagerTarget.axis) {
                PixelAxis.HORIZONTAL -> pagerTarget.bounds.width
                PixelAxis.VERTICAL -> pagerTarget.bounds.height
            }.coerceAtLeast(1)
            pagerTarget.controller.startDrag(pagerTarget.state)
            if (delta != 0f) {
                pagerTarget.controller.dragBy(pagerTarget.state, delta, viewport)
            }
            pagerTarget.controller.cancelDrag(pagerTarget.state)
            needsRender = true
            return
        }
        fail("No cancellable drag target at ($startX,$startY)")
    }

    private fun dragSelectionHandle(finder: PixelFinder, handle: TextSelectionHandle, dx: Int, dy: Int) {
        val target = resolveTextInputTarget(finder)
        ensureTextInputEditable(target)
        if (target.state.selectionStart == target.state.selectionEnd) {
            fail("Text input target has no non-empty selection")
        }
        focusTextInput(target)
        val anchor = handlePoint(target, handle)
        updateSelectionHandle(target, handle, anchor.x + dx, anchor.y + dy)
        render()
    }

    private fun updateSelectionHandle(target: PixelTextInputTarget, handle: TextSelectionHandle, logicalX: Int, logicalY: Int) {
        val selection = resolveTextInputSelection(target, logicalX, logicalY)
        when (handle) {
            TextSelectionHandle.START -> target.controller.setSelection(
                target.state,
                selection.coerceAtMost(target.state.selectionEnd),
                target.state.selectionEnd,
            )
            TextSelectionHandle.END -> target.controller.setSelection(
                target.state,
                target.state.selectionStart,
                selection.coerceAtLeast(target.state.selectionStart),
            )
        }
        needsRender = true
    }

    private fun dispatchSliderDrag(target: PixelSliderTarget, x: Int) {
        val ratio = ((x - target.bounds.left).toFloat() / target.bounds.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        target.onDrag(ratio)
        target.onRelease(ratio)
        needsRender = true
    }

    private fun dispatchScrollbarDrag(target: PixelScrollbarTarget, startY: Int, endY: Int) {
        val dragOffset = if (target.thumbBounds.contains(target.bounds.left, startY)) {
            startY - target.thumbBounds.top
        } else {
            target.thumbBounds.height / 2
        }.coerceIn(0, target.thumbBounds.height.coerceAtLeast(1))
        val thumbTravel = (target.bounds.height - target.thumbBounds.height).coerceAtLeast(0)
        val maxOffset = (target.contentHeightPx - target.viewportHeightPx).coerceAtLeast(0)
        if (thumbTravel > 0 && maxOffset > 0) {
            val thumbTop = (endY - target.bounds.top - dragOffset).coerceIn(0, thumbTravel)
            val targetOffset = (thumbTop.toFloat() / thumbTravel.toFloat()) * maxOffset.toFloat()
            target.controller.startDrag(target.state)
            target.controller.scrollTo(target.state, targetOffset, target.viewportHeightPx, target.contentHeightPx)
            target.controller.endDrag(target.state, 0f, target.viewportHeightPx, target.contentHeightPx)
        }
        needsRender = true
    }

    private fun dispatchRefreshDrag(target: PixelRefreshTarget, dy: Int) {
        target.controller.startPull(target.state)
        target.controller.updatePull(target.state, dy.toFloat().coerceAtLeast(0f), target.thresholdPx)
        if (target.controller.endPull(target.state, target.thresholdPx)) {
            target.onRefresh()
        }
        needsRender = true
    }

    private fun dispatchListDrag(target: PixelListTarget, dy: Float) {
        target.controller.startDrag(target.state)
        target.controller.dragBy(target.state, dy, target.viewportHeightPx, target.contentHeightPx)
        target.controller.endDrag(target.state, 0f, target.viewportHeightPx, target.contentHeightPx)
        needsRender = true
    }

    private fun dispatchPagerDrag(target: PixelPagerTarget, dx: Float, dy: Float) {
        val delta = when (target.axis) {
            PixelAxis.HORIZONTAL -> dx
            PixelAxis.VERTICAL -> dy
        }
        val viewport = when (target.axis) {
            PixelAxis.HORIZONTAL -> target.bounds.width
            PixelAxis.VERTICAL -> target.bounds.height
        }.coerceAtLeast(1)
        target.controller.startDrag(target.state)
        target.controller.dragBy(target.state, delta, viewport)
        target.controller.endDrag(target.state, viewport, 0f)
        target.onPageChanged?.invoke(target.state.currentPage)
        needsRender = true
    }

    private fun focusTextInput(target: PixelTextInputTarget) {
        if (target.readOnly) return
        focusedTextInputTarget?.takeUnless { it.state === target.state }?.let { previous ->
            previous.controller.blur(previous.state)
        }
        target.controller.focus(target.state)
        target.focusNode?.requestFocus()
        focusedTextInputTarget = target
        needsRender = true
    }

    private fun ensureTextInputEditable(target: PixelTextInputTarget) {
        if (target.readOnly) {
            fail("Text input target is readOnly at ${target.bounds}")
        }
    }

    private fun resolvePoint(finder: PixelFinder, kind: TargetKind): Point {
        val widget = resolveWidget(finder) ?: fail("No widget matched $finder", finder)
        resolveTextInputTargetOrNull(widget)?.let { return it.bounds.center }
        resolveClickTargetOrNull(widget)?.let { return it.bounds.center }
        if (kind == TargetKind.DRAG || kind == TargetKind.ANY) {
            resolveScrollbarTargetOrNull(widget)?.let { return it.thumbBounds.center }
            resolveSliderTargetOrNull(widget)?.let { return it.bounds.center }
            resolveRefreshTargetOrNull(widget)?.let { return it.bounds.center }
            resolveListTargetOrNull(widget)?.let { return it.bounds.center }
            resolvePagerTargetOrNull(widget)?.let { return it.bounds.center }
        }
        fail("Matched $finder but no render target was exported for it", finder)
    }

    private fun resolveTextInputTarget(finder: PixelFinder): PixelTextInputTarget {
        val widget = resolveWidget(finder) ?: fail("No widget matched $finder", finder)
        return resolveTextInputTargetOrNull(widget)
            ?: fail("Matched $finder but no text input target was exported for it", finder)
    }

    private fun resolveWidget(finder: PixelFinder): Any? {
        return finder.resolve(runtime.collectWidgets())
            ?: finder.resolve(root)
    }

    private fun resolveTextInputTargetOrNull(widget: Any): PixelTextInputTarget? {
        val controller = widget.readField("controller")
        val state = widget.readField("state")
        return renderResult?.textInputTargets?.lastOrNull {
            it.controller === controller && it.state === state
        }
    }

    private fun resolveClickTargetOrNull(widget: Any): PixelClickTarget? {
        val callback = widget.readField("onPressed") as? (() -> Unit)
            ?: widget.readField("onTap") as? (() -> Unit)
        return if (callback != null) {
            renderResult?.clickTargets?.lastOrNull { it.onClick === callback }
        } else {
            null
        }
    }

    private fun resolveSliderTargetOrNull(widget: Any): PixelSliderTarget? {
        val onDrag = widget.readField("onDrag") as? ((Float) -> Unit)
        return if (onDrag != null) renderResult?.sliderTargets?.lastOrNull { it.onDrag === onDrag } else null
    }

    private fun resolveListTargetOrNull(widget: Any): PixelListTarget? {
        val controller = widget.readField("controller")
        val state = widget.readField("state")
        return renderResult?.listTargets?.lastOrNull {
            it.controller === controller && it.state === state
        }
    }

    private fun resolveScrollbarTargetOrNull(widget: Any): PixelScrollbarTarget? {
        val state = widget.readField("state")
        return renderResult?.scrollbarTargets?.lastOrNull { it.state === state }
    }

    private fun resolveRefreshTargetOrNull(widget: Any): PixelRefreshTarget? {
        val state = widget.readField("state")
        val controller = widget.readField("controller")
        return renderResult?.refreshTargets?.lastOrNull {
            it.state === state && it.controller === controller
        }
    }

    private fun resolvePagerTargetOrNull(widget: Any): PixelPagerTarget? {
        val controller = widget.readField("controller")
        val state = widget.readField("state")
        return renderResult?.pagerTargets?.lastOrNull {
            it.controller === controller && it.state === state
        }
    }

    private fun shouldStartListDrag(dx: Int, dy: Int): Boolean {
        return abs(dy) >= abs(dx)
    }

    private fun nearestSelectionHandle(target: PixelTextInputTarget, logicalX: Int, logicalY: Int): TextSelectionHandle {
        val selection = resolveTextInputSelection(target, logicalX, logicalY)
        val startDistance = abs(selection - target.state.selectionStart)
        val endDistance = abs(selection - target.state.selectionEnd)
        return if (startDistance <= endDistance) TextSelectionHandle.START else TextSelectionHandle.END
    }

    private fun handlePoint(target: PixelTextInputTarget, handle: TextSelectionHandle): Point {
        val text = target.state.text
        if (text.isEmpty()) return target.bounds.center
        val index = when (handle) {
            TextSelectionHandle.START -> target.state.selectionStart
            TextSelectionHandle.END -> target.state.selectionEnd
        }.coerceIn(0, text.length)
        target.caretBoundsForIndex?.invoke(index)?.let { caret ->
            return Point(caret.left, caret.top + caret.height / 2)
        }
        val lines = text.split('\n')
        var lineStart = 0
        lines.forEachIndexed { lineIndex, line ->
            val lineEnd = lineStart + line.length
            if (index <= lineEnd || lineIndex == lines.lastIndex) {
                val x = target.bounds.left + if (line.isEmpty()) {
                    0
                } else {
                    ((index - lineStart).coerceIn(0, line.length).toLong() * target.bounds.width / line.length).toInt()
                }
                val lineHeight = (target.bounds.height / lines.size.coerceAtLeast(1)).coerceAtLeast(1)
                val y = target.bounds.top + lineIndex * lineHeight + lineHeight / 2
                return Point(x, y)
            }
            lineStart = lineEnd + 1
        }
        return target.bounds.center
    }

    private fun resolveTextInputSelection(target: PixelTextInputTarget, logicalX: Int, logicalY: Int): Int {
        target.textIndexAt?.let { mapper ->
            return mapper(logicalX, logicalY).coerceIn(0, target.state.text.length)
        }
        val text = target.state.text
        if (text.isEmpty()) return 0
        val lines = text.split('\n')
        val lineCount = lines.size.coerceAtLeast(1)
        val lineHeight = (target.bounds.height / lineCount).coerceAtLeast(1)
        val lineIndex = ((logicalY - target.bounds.top).coerceAtLeast(0) / lineHeight).coerceIn(0, lineCount - 1)
        val line = lines[lineIndex]
        val localX = (logicalX - target.bounds.left).coerceIn(0, target.bounds.width)
        val column = if (line.isEmpty()) 0 else (localX.toLong() * line.length / target.bounds.width.coerceAtLeast(1)).toInt()
        var index = 0
        repeat(lineIndex) { index += lines[it].length + 1 }
        return (index + column.coerceIn(0, line.length)).coerceIn(0, text.length)
    }

    private fun fail(message: String, finder: PixelFinder? = null): Nothing {
        error(
            buildString {
                append(message)
                if (finder != null) {
                    append("\n\nFinder diagnostics:\n")
                    append(finder.diagnostics(runtime.collectWidgets().ifEmpty { listOfNotNull(root) }))
                }
                append("\n\nElement tree:\n")
                append(runtime.dumpElementTree())
                append("\n\nRender tree:\n")
                append(runtime.dumpRenderTree())
                append("\n\nTargets:\n")
                append(renderResult.describeTargets())
            },
        )
    }

    private enum class TargetKind {
        ANY,
        DRAG,
    }

    private enum class TextSelectionHandle {
        START,
        END,
    }

    private data class Point(val x: Int, val y: Int)

    private val PixelRect.center: Point
        get() = Point(left + width / 2, top + height / 2)
}

public object find {
    public fun byText(text: String): PixelFinder = PixelFinder.ByText(text)
    public fun byType(type: kotlin.reflect.KClass<*>): PixelFinder = PixelFinder.ByType(type)
    public fun byKey(key: Any): PixelFinder = PixelFinder.ByKey(key)
}

public sealed class PixelFinder {
    internal abstract fun matches(widget: Any): Boolean

    public fun nth(index: Int): PixelFinder {
        require(index >= 0) { "index must be >= 0" }
        return Nth(this, index)
    }

    internal open fun resolve(root: Any?): Any? = resolveAll(root).firstOrNull()

    internal open fun resolveAll(root: Any?): List<Any> {
        if (root == null) return emptyList()
        val results = mutableListOf<Any>()
        val seen = IdentityHashMap<Any, Boolean>()
        walk(root, seen, results)
        return results
    }

    internal fun diagnostics(root: Any?): String {
        if (root == null) return "<no root widget>"
        val nodes = mutableListOf<FinderDiagnosticsNode>()
        val seen = IdentityHashMap<Any, Boolean>()
        walkDiagnostics(
            value = root,
            path = "\$",
            depth = 0,
            seen = seen,
            nodes = nodes,
        )
        val matches = nodes.filter { it.matches }
        return buildString {
            appendLine("matches=${matches.size}")
            if (matches.isEmpty()) {
                appendLine("matchedCandidates=<none>")
            } else {
                appendLine("matchedCandidates:")
                matches.forEachIndexed { index, node ->
                    appendLine("  [$index] ${node.path}: ${node.summary}")
                }
            }
            appendLine("Widget tree:")
            nodes.take(MAX_DIAGNOSTIC_NODES).forEach { node ->
                repeat(node.depth) { append("  ") }
                append(node.path)
                append(": ")
                append(node.summary)
                if (node.matches) append("  <match>")
                appendLine()
            }
            if (nodes.size > MAX_DIAGNOSTIC_NODES) {
                append("... truncated ")
                append(nodes.size - MAX_DIAGNOSTIC_NODES)
                append(" nodes")
            }
        }.trimEnd()
    }

    private fun walk(value: Any?, seen: IdentityHashMap<Any, Boolean>, results: MutableList<Any>) {
        if (value == null) return
        if (value is String || value is Number || value is Boolean || value is Enum<*>) return
        if (seen.put(value, true) != null) return
        if (matches(value)) results += value
        if (value is Iterable<*>) {
            for (child in value) walk(child, seen, results)
            return
        }
        val pkg = value::class.java.name
        if (!pkg.startsWith("com.purride.")) return
        for (field in value::class.java.declaredFields) {
            field.isAccessible = true
            walk(field.get(value), seen, results)
        }
    }

    private fun walkDiagnostics(
        value: Any?,
        path: String,
        depth: Int,
        seen: IdentityHashMap<Any, Boolean>,
        nodes: MutableList<FinderDiagnosticsNode>,
    ) {
        if (value == null) return
        if (value is String || value is Number || value is Boolean || value is Enum<*>) return
        if (seen.put(value, true) != null) return
        if (nodes.size >= MAX_DIAGNOSTIC_NODES * 2) return
        if (value is Iterable<*>) {
            value.forEachIndexed { index, child ->
                walkDiagnostics(child, "$path[$index]", depth, seen, nodes)
            }
            return
        }
        val className = value::class.java.name
        if (!className.startsWith("com.purride.")) return
        nodes += FinderDiagnosticsNode(
            path = path,
            summary = value.summaryForDiagnostics(),
            depth = depth,
            matches = matches(value),
        )
        for (field in value::class.java.declaredFields) {
            field.isAccessible = true
            walkDiagnostics(
                value = field.get(value),
                path = "$path.${field.name}",
                depth = depth + 1,
                seen = seen,
                nodes = nodes,
            )
        }
    }

    public data class ByText(public val text: String) : PixelFinder() {
        override fun matches(widget: Any): Boolean {
            val state = widget.readField("state")
            return widget.readField("text") == text ||
                widget.readField("data") == text ||
                widget.readField("placeholder") == text ||
                state?.readField("text") == text
        }
    }

    public data class ByType(public val type: kotlin.reflect.KClass<*>) : PixelFinder() {
        override fun matches(widget: Any): Boolean = type.java.isInstance(widget)
    }

    public data class ByKey(public val key: Any) : PixelFinder() {
        override fun matches(widget: Any): Boolean = widget.readField("key") == key
    }

    public data class Nth(public val finder: PixelFinder, public val index: Int) : PixelFinder() {
        override fun matches(widget: Any): Boolean = finder.matches(widget)

        override fun resolve(root: Any?): Any? = finder.resolveAll(root).getOrNull(index)

        override fun resolveAll(root: Any?): List<Any> = listOfNotNull(resolve(root))
    }

    private data class FinderDiagnosticsNode(
        val path: String,
        val summary: String,
        val depth: Int,
        val matches: Boolean,
    )

    private companion object {
        const val MAX_DIAGNOSTIC_NODES = 160
    }
}

private fun Any.summaryForDiagnostics(): String {
    return buildString {
        append(this@summaryForDiagnostics::class.java.simpleName)
        val details = buildList {
            readField("key")?.let { add("key=$it") }
            readField("text")?.let { add("text=$it") }
            readField("data")?.let { add("data=$it") }
            readField("placeholder")?.let { add("placeholder=$it") }
            readField("state")?.readField("text")?.let { add("state.text=$it") }
        }
        if (details.isNotEmpty()) {
            append("(")
            append(details.joinToString())
            append(")")
        }
    }
}

private fun PixelRenderResult?.describeTargets(): String {
    if (this == null) return "<no render result>"
    return buildString {
        appendLine("clickTargets=${clickTargets.map { it.bounds }}")
        appendLine("pagerTargets=${pagerTargets.map { it.bounds }}")
        appendLine("listTargets=${listTargets.map { it.bounds }}")
        appendLine("scrollbarTargets=${scrollbarTargets.map { it.bounds }}")
        appendLine("refreshTargets=${refreshTargets.map { it.bounds }}")
        appendLine("textInputTargets=${textInputTargets.map { it.bounds }}")
        appendLine("sliderTargets=${sliderTargets.map { it.bounds }}")
        appendLine("semanticsNodes=${semanticsNodes.map { "${it.role}:${it.label}" }}")
    }.trimEnd()
}

private fun Any.readField(name: String): Any? {
    return generateSequence(this::class.java as Class<*>?) { it.superclass }
        .flatMap { it.declaredFields.asSequence() }
        .firstOrNull { it.name == name }
        ?.let { field ->
            field.isAccessible = true
            field.get(this)
        }
}
