package com.purride.pixelui.testing

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelKeyEvent
import com.purride.pixelui.PixelTextInputEvent
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelTextEditAction
import com.purride.pixelui.TextInputSelectionHandle
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
import com.purride.pixelui.internal.PixelTextInputSelectionGesture
import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.PixelUiRuntime
import com.purride.pixelui.internal.TeardownFailureCollector
import kotlin.math.abs
import java.util.IdentityHashMap

/**
 * 离屏驱动 pixel widget 树的 SDK 测试工具。
 *
 * 它不依赖 Android View，可以在普通单元测试里 pump widget、发送输入、
 * 推进帧并读取像素/semantics 结果。
 */
public class PixelTester {
    /**
     * 控制测试帧时间的手动调度器。
     */
    public val scheduler: ManualFrameScheduler = ManualFrameScheduler()

    /**
     * 给动画控制器使用的测试 ticker provider。
     */
    public val vsync: PixelTickerProvider = PixelTickerProvider(scheduler)

    private val runtime = PixelUiRuntime(onVisualUpdate = { needsRender = true })
    private var root: Widget? = null
    private var logicalWidth: Int = 0
    private var logicalHeight: Int = 0
    private var needsRender: Boolean = false
    private var focusedTextInputTarget: PixelTextInputTarget? = null
    /**
     * 最近一次复制或剪切得到的文本。
     */
    public var clipboardText: String? = null
        private set
    private var currentNanos: Long = 0L
    private val activeGestures = mutableMapOf<Int, ActiveTestGesture>()
    private var primaryPointerId: Int? = null
    /** Click target currently receiving virtual mouse/stylus hover feedback. */
    private var hoveredClickTarget: PixelClickTarget? = null
    /** Slider target currently receiving virtual mouse/stylus hover feedback. */
    private var hoveredSliderTarget: PixelSliderTarget? = null
    /** Scrollbar target currently receiving virtual mouse/stylus hover feedback. */
    private var hoveredScrollbarTarget: PixelScrollbarTarget? = null
    /** Refresh target currently receiving virtual mouse/stylus hover feedback. */
    private var hoveredRefreshTarget: PixelRefreshTarget? = null
    /** Horizontal logical coordinate of the current virtual hover pointer. */
    private var hoveredLogicalX: Int? = null
    /** Vertical logical coordinate of the current virtual hover pointer. */
    private var hoveredLogicalY: Int? = null

    internal var renderResult: PixelRenderResult? = null
        private set

    /**
     * 设置根 widget 并立即渲染一帧。
     */
    public fun pumpWidget(widget: Widget, logicalWidth: Int, logicalHeight: Int) {
        root = widget
        this.logicalWidth = logicalWidth
        this.logicalHeight = logicalHeight
        needsRender = true
        render()
    }

    /**
     * 点击 [finder] 命中的 widget 中心点。
     */
    public fun tap(finder: PixelFinder) {
        val point = resolvePoint(finder, TargetKind.ANY)
        dispatchTapAt(point.x, point.y)
        render()
    }

    /**
     * 在文本输入上触发双击选词。
     */
    public fun doubleTap(finder: PixelFinder) {
        val point = resolvePoint(finder, TargetKind.ANY)
        renderResult?.textInputTargets?.lastOrNull { it.bounds.contains(point.x, point.y) }?.let { target ->
            if (target.readOnly) return
            focusTextInput(target)
            target.controller.selectWordAt(target.state, resolveTextInputSelection(target, point.x, point.y))
            render()
            return
        }
        val target = renderResult?.clickTargets?.lastOrNull {
            it.bounds.contains(point.x, point.y) && it.onDoubleTap != null
        } ?: fail("No double tap target at (${point.x},${point.y})", finder)
        target.onDoubleTap?.invoke()
        needsRender = true
        render()
    }

    /**
     * 长按命中的 widget 或文本输入。
     */
    public fun longPress(finder: PixelFinder) {
        val point = resolvePoint(finder, TargetKind.ANY)
        renderResult?.textInputTargets?.lastOrNull { it.bounds.contains(point.x, point.y) }?.let { target ->
            if (target.readOnly) return
            focusTextInput(target)
            target.controller.selectWordAt(target.state, resolveTextInputSelection(target, point.x, point.y))
            render()
            return
        }
        val target = renderResult?.clickTargets?.lastOrNull {
            it.bounds.contains(point.x, point.y) && it.onLongPress != null
        } ?: fail("No long press target at (${point.x},${point.y})", finder)
        target.onLongPress?.invoke()
        needsRender = true
        render()
    }

    /**
     * 从命中点拖动指定像素距离。
     */
    public fun drag(finder: PixelFinder, dx: Int, dy: Int) {
        val point = resolvePoint(finder, TargetKind.DRAG)
        dispatchDrag(point.x, point.y, dx, dy)
        render()
    }

    /**
     * 从命中点触发一次带速度的 fling。
     */
    public fun fling(finder: PixelFinder, dx: Int, dy: Int, velocityPxPerSecond: Float) {
        val point = resolvePoint(finder, TargetKind.DRAG)
        dispatchFling(point.x, point.y, dx, dy, velocityPxPerSecond)
        render()
    }

    /**
     * 启动拖动后立刻取消，用于验证取消分支。
     */
    public fun cancelDrag(finder: PixelFinder, dx: Int = 0, dy: Int = 0) {
        val point = resolvePoint(finder, TargetKind.DRAG)
        dispatchDragCancel(point.x, point.y, dx, dy)
        render()
    }

    /**
     * 启动一条可分多步推进的测试手势。
     */
    public fun startGesture(finder: PixelFinder, pointerId: Int = 0): PixelTestGesture {
        val point = resolvePoint(finder, TargetKind.ANY)
        beginGesture(pointerId, point.x, point.y)
        render()
        return DefaultPixelTestGesture(this, pointerId)
    }

    /**
 * 执行 `PixelTester` 的 `down` 公开行为；具体参数、返回和副作用见下文。
 *
     * Sends a virtual pointer down and returns the gesture that owns the matching up or cancel.
     *
     * This is an explicit interaction-state alias for [startGesture].
     */
    public fun down(finder: PixelFinder, pointerId: Int = 0): PixelTestGesture {
        return startGesture(finder, pointerId)
    }

    /** 执行 `PixelTester` 的 `up` 公开行为；具体参数、返回和副作用见下文。
 *
 * Sends a virtual pointer up for [pointerId].
 */
    public fun up(pointerId: Int = 0) {
        endGesture(pointerId)
    }

    /** 判断 `PixelTester` 是否满足 `cancel` 条件，不修改现有状态。
 *
 * Sends a virtual pointer cancel for [pointerId].
 */
    public fun cancel(pointerId: Int = 0) {
        cancelGesture(pointerId)
    }

    /** 执行 `PixelTester` 的 `hover` 公开行为；具体参数、返回和副作用见下文。
 *
 * Moves a virtual mouse/stylus hover pointer to the center of [finder].
 */
    public fun hover(finder: PixelFinder) {
        val point = resolvePoint(finder, TargetKind.ANY)
        updateHover(point.x, point.y)
        render()
    }

    /** 执行 `PixelTester` 的 `exitHover` 公开行为；具体参数、返回和副作用见下文。
 *
 * Removes the current virtual mouse/stylus hover pointer.
 */
    public fun exitHover() {
        clearHover()
        render()
    }

    internal fun moveGestureBy(pointerId: Int, dx: Int, dy: Int, deltaMs: Long = 16L) {
        val gesture = activeGestures[pointerId] ?: fail("No active gesture for pointer $pointerId")
        gesture.moveBy(dx, dy, deltaMs.coerceAtLeast(1L))
        render()
    }

    internal fun endGesture(pointerId: Int) {
        val gesture = activeGestures.remove(pointerId) ?: fail("No active gesture for pointer $pointerId")
        gesture.up()
        if (primaryPointerId == pointerId) promotePrimaryPointer()
        render()
    }

    internal fun cancelGesture(pointerId: Int) {
        val gesture = activeGestures.remove(pointerId) ?: fail("No active gesture for pointer $pointerId")
        gesture.cancel()
        if (primaryPointerId == pointerId) promotePrimaryPointer()
        render()
    }

    /**
     * 拖动文本选区起始手柄。
     */
    public fun dragSelectionStartHandle(finder: PixelFinder, dx: Int, dy: Int) {
        dragSelectionHandle(finder, TextInputSelectionHandle.START, dx, dy)
    }

    /**
     * 拖动文本选区结束手柄。
     */
    public fun dragSelectionEndHandle(finder: PixelFinder, dx: Int, dy: Int) {
        dragSelectionHandle(finder, TextInputSelectionHandle.END, dx, dy)
    }

    /**
     * 聚焦文本输入并替换全部文本。
     */
    public fun enterText(finder: PixelFinder, text: String) {
        val target = resolveTextInputTarget(finder)
        ensureTextInputEditable(target)
        focusTextInput(target)
        target.controller.updateText(target.state, text)
        target.onChanged?.invoke(target.state.text)
        render()
    }

    /**
     * 提交当前聚焦文本输入。
     */
    public fun submitTextInput() {
        val target = focusedTextInputTarget ?: fail("No focused text input target")
        target.onSubmitted?.invoke(target.state.text)
        if (target.action == com.purride.pixelui.PixelTextInputAction.NEXT) {
            runtime.focusOwner.dispatchKeyEvent(PixelKeyEvent(PixelKey.TAB))
            needsRender = true
            render()
        }
    }

    /**
     * 对文本输入执行复制、剪切、粘贴或全选。
     */
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

    /**
     * 向当前 focus tree 发送一个非文本按键（导航、激活或取消）。
     *
     * 可打印文本请改用 [pressText]，两者语义不重叠。
     */
    public fun pressKey(key: PixelKey): Boolean {
        /** 本 tester 的聚焦节点链是否消费了这个归一化非文本按键事件。 */
        val handled = runtime.focusOwner.dispatchKeyEvent(PixelKeyEvent(key = key))
        if (handled) {
            needsRender = true
            render()
        }
        return handled
    }

    /**
 * 执行 `PixelTester` 的 `pressText` 公开行为；具体参数、返回和副作用见下文。
 *
     * Sends one exact text payload to this tester's runtime-local focused node chain.
     *
     * Supplementary-plane、组合簇和多 code point 文本都保持为一次事件，不会拆分，也不会退化
     * 到 [pressKey]。
     *
     * @param text Exact UTF-16 text supplied by the simulated input source.
     * @return `true` when a focused text handler consumed the complete payload.
     */
    public fun pressText(text: String): Boolean {
        /** Exact platform-independent event retained as one unit throughout focus dispatch. */
        val event = PixelTextInputEvent(text)
        /** Whether this tester's focused node chain consumed the event or its eligible fallback. */
        val handled = runtime.focusOwner.dispatchTextInputEvent(event)
        if (handled) {
            needsRender = true
            render()
        }
        return handled
    }

    /**
     * 在文本输入当前位置写入一段输入法 composition 文本。
     */
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

    /**
     * 更新文本输入 composition 范围。
     */
    public fun updateComposition(finder: PixelFinder, start: Int, end: Int) {
        val target = resolveTextInputTarget(finder)
        ensureTextInputEditable(target)
        focusTextInput(target)
        target.controller.updateComposition(target.state, start, end)
        render()
    }

    /**
     * 推进一帧并渲染最新 UI。
     */
    public fun pumpFrame(deltaMs: Long) {
        val nextNanos = currentNanos + deltaMs * 1_000_000L
        currentNanos = nextNanos
        stepActiveScrollTargets(deltaMs)
        scheduler.advanceFrame(nextNanos)
        render()
    }

    /**
     * 持续推进帧，直到动画、ticker 和滚动活动都停止。
     */
    public fun pumpAndSettle(maxFrames: Int = 60) {
        repeat(maxFrames) {
            pumpFrame(16)
            if (!hasPendingActivity()) {
                return
            }
        }
        fail("pumpAndSettle did not settle after $maxFrames frames")
    }

    /**
     * 返回当前 element tree 调试文本。
     */
    public fun dumpElementTree(): String = runtime.dumpElementTree()

    /**
     * 返回当前 render tree 调试文本。
     */
    public fun dumpRenderTree(): String = runtime.dumpRenderTree()

    /**
     * 返回当前 semantics tree 调试文本。
     */
    public fun dumpSemanticsTree(): String {
        val nodes = renderResult?.semanticsNodes.orEmpty()
        if (nodes.isEmpty()) return "<empty semantics>"
        val nodesById = nodes.associateBy(PixelSemanticsNode::id)
        return nodes.joinToString(separator = "\n") { node ->
            val depth = semanticDepth(node = node, nodesById = nodesById)
            buildString {
                repeat(depth) { append("  ") }
                append(node.role)
                append(" label=\"").append(node.label).append('"')
                append(" enabled=").append(node.enabled)
                append(" focused=").append(node.focused)
                append(" id=").append(node.id)
                append(" parent=").append(node.parentId ?: "HOST")
                node.value?.let { value -> append(" value=\"").append(value).append('"') }
                append(" selected=").append(node.selected)
                node.checked?.let { checked -> append(" checked=").append(checked) }
                node.expanded?.let { expanded -> append(" expanded=").append(expanded) }
                append(" actions=").append(node.actions)
                append(" bounds=")
                    .append(node.left).append(',')
                    .append(node.top).append(',')
                    .append(node.width).append(',')
                    .append(node.height)
            }
        }
    }

    /** 执行 `PixelTester` 的 `semanticsNodes` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the immutable semantic nodes from the most recently rendered frame.
 */
    public fun semanticsNodes(): List<PixelSemanticsNode> = renderResult?.semanticsNodes.orEmpty()

    /** 执行 `PixelTester` 的 `semanticsNode` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the node with [id], or `null` when that logical node is not in the current tree.
 */
    public fun semanticsNode(id: Long): PixelSemanticsNode? {
        return renderResult?.semanticsNodes?.firstOrNull { node -> node.id == id }
    }

    /** 执行 `PixelTester` 的 `semanticsNodesByLabel` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns all current nodes whose spoken label exactly equals [label], preserving tree order.
 */
    public fun semanticsNodesByLabel(label: String): List<PixelSemanticsNode> {
        return renderResult?.semanticsNodes.orEmpty().filter { node -> node.label == label }
    }

    /**
 * 执行 `PixelTester` 的 `performSemanticsAction` 公开行为；具体参数、返回和副作用见下文。
 *
     * Invokes one typed semantic action by stable node id and renders any resulting state change.
     *
     * This executes the callback owned by the semantic target; it never re-hit-tests a coordinate.
     */
    public fun performSemanticsAction(
        id: Long,
        action: PixelSemanticsAction,
        arguments: PixelSemanticsActionArguments = PixelSemanticsActionArguments(),
    ): Boolean {
        val target = renderResult?.semanticsTargets?.firstOrNull { candidate -> candidate.node.id == id }
            ?: return false
        if (!target.node.enabled) return false
        val handled = when (action) {
            PixelSemanticsAction.CLICK -> target.actions.onClick?.invoke()
            PixelSemanticsAction.LONG_CLICK -> target.actions.onLongClick?.invoke()
            PixelSemanticsAction.SCROLL_FORWARD -> target.actions.onScrollForward?.invoke()
            PixelSemanticsAction.SCROLL_BACKWARD -> target.actions.onScrollBackward?.invoke()
            PixelSemanticsAction.SET_TEXT -> arguments.text?.let { text -> target.actions.onSetText?.invoke(text) }
            PixelSemanticsAction.SET_SELECTION -> {
                val start = arguments.selectionStart
                val end = arguments.selectionEnd
                if (start == null || end == null) false else target.actions.onSetSelection?.invoke(start, end)
            }
            PixelSemanticsAction.SET_PROGRESS -> {
                arguments.progress?.takeIf(Float::isFinite)?.let { progress ->
                    target.actions.onSetProgress?.invoke(progress)
                }
            }
            PixelSemanticsAction.DISMISS -> target.actions.onDismiss?.invoke()
            PixelSemanticsAction.EXPAND -> target.actions.onExpand?.invoke()
            PixelSemanticsAction.COLLAPSE -> target.actions.onCollapse?.invoke()
            PixelSemanticsAction.CUSTOM -> arguments.customActionId?.let { customActionId ->
                target.actions.customActions
                    .firstOrNull { customAction -> customAction.id == customActionId }
                    ?.onInvoke
                    ?.invoke()
            }
        } ?: false
        if (handled) render()
        return handled
    }

    /** Computes debug indentation while defending against malformed parent cycles. */
    private fun semanticDepth(
        node: PixelSemanticsNode,
        nodesById: Map<Long, PixelSemanticsNode>,
    ): Int {
        var parentId = node.parentId
        var depth = 0
        val visited = mutableSetOf<Long>()
        while (parentId != null && visited.add(parentId)) {
            val parent = nodesById[parentId] ?: break
            depth += 1
            parentId = parent.parentId
        }
        return depth
    }

    /**
     * 读取最后一次渲染结果中的单个像素。
     */
    public fun pixelAt(x: Int, y: Int): PixelColor {
        return renderedBuffer().getPixel(x, y)
    }

    /**
     * 判断最后一次渲染结果是否包含指定颜色。
     */
    public fun hasPixel(color: PixelColor): Boolean {
        return renderedBuffer().pixels.any { it == color.argb }
    }

    /**
     * 把最后一次渲染结果转成稳定的 ASCII 像素快照，供 golden 文本比对。
     */
    public fun dumpPixelsAsAscii(): String {
        val buffer = renderedBuffer()
        return buildString {
            append("size=").append(buffer.width).append('x').append(buffer.height).append('\n')
            for (y in 0 until buffer.height) {
                for (x in 0 until buffer.width) {
                    append(pixelChar(buffer.getPixel(x, y)))
                }
                append('\n')
            }
        }
    }

    /**
     * 判断 finder 是否能命中当前 widget tree。
     */
    public fun exists(finder: PixelFinder): Boolean {
        return finder.resolve(runtime.collectWidgets()) != null || finder.resolve(root) != null
    }

    /**
     * 释放 tester 持有的运行时和测试调度状态。
     */
    public fun dispose() {
        /** Terminal collector keeps test scheduler cleanup deterministic after user dispose errors. */
        val failures = TeardownFailureCollector()
        activeGestures.values.toList().forEach { gesture ->
            failures.capture { gesture.cancel() }
        }
        failures.capture { clearHover() }
        failures.capture { runtime.dispose() }
        failures.capture { scheduler.clear() }
        activeGestures.clear()
        primaryPointerId = null
        failures.throwIfAny()
    }

    private fun renderedBuffer() = renderResult?.buffer ?: fail("No rendered buffer; call pumpWidget first")

    private fun render() {
        val widget = root ?: return
        var pass = 0
        do {
            renderResult = runtime.render(widget, logicalWidth, logicalHeight)
            renderResult?.let(::reconcileInteractionTargets)
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

    /** Rebinds every virtual interaction owner to the newly published render snapshot. */
    private fun reconcileInteractionTargets(result: PixelRenderResult) {
        activeGestures.values.toList().forEach { gesture -> gesture.reconcileTargets(result) }
        reconcileHoverTargets(result)
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

    private fun beginGesture(pointerId: Int, x: Int, y: Int) {
        if (activeGestures.containsKey(pointerId)) {
            fail("Pointer $pointerId already has an active gesture")
        }
        val target = resolveGestureTarget(x, y) ?: fail("No gesture target at ($x,$y)")
        val pressedClickTarget = if (target is TestGestureTarget.Slider || target is TestGestureTarget.Scrollbar) {
            null
        } else {
            renderResult?.clickTargets?.lastOrNull { clickTarget ->
                clickTarget.bounds.contains(x, y) && clickTarget.onPressedChanged != null
            }
        }
        if (primaryPointerId == null) primaryPointerId = pointerId
        val gesture = ActiveTestGesture(
            pointerId = pointerId,
            startX = x,
            startY = y,
            currentX = x,
            currentY = y,
            target = target,
            pressedClickTarget = pressedClickTarget,
        )
        activeGestures[pointerId] = gesture
        gesture.down()
    }

    private fun promotePrimaryPointer() {
        primaryPointerId = activeGestures.keys.minOrNull()
    }

    private fun isPrimaryPointer(pointerId: Int): Boolean {
        return primaryPointerId == null || primaryPointerId == pointerId
    }

    private fun resolveGestureTarget(x: Int, y: Int): TestGestureTarget? {
        return renderResult?.scrollbarTargets?.lastOrNull { it.bounds.contains(x, y) }?.let(TestGestureTarget::Scrollbar)
            ?: renderResult?.refreshTargets?.lastOrNull { it.bounds.contains(x, y) }?.let(TestGestureTarget::Refresh)
            ?: renderResult?.sliderTargets?.lastOrNull { it.bounds.contains(x, y) }?.let(TestGestureTarget::Slider)
            ?: renderResult?.textInputTargets?.lastOrNull { it.bounds.contains(x, y) }?.let(TestGestureTarget::TextInput)
            ?: renderResult?.listTargets?.lastOrNull { it.bounds.contains(x, y) }?.let(TestGestureTarget::List)
            ?: renderResult?.pagerTargets?.lastOrNull { it.bounds.contains(x, y) }?.let(TestGestureTarget::Pager)
            ?: renderResult?.clickTargets?.lastOrNull { it.bounds.contains(x, y) }?.let(TestGestureTarget::Click)
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
                PixelTextInputSelectionGesture.setCollapsedSelection(target, startX + dx, startY + dy)
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
            pagerTarget.controller.startDrag(pagerTarget.state, viewport)
            pagerTarget.onPageDragStart?.invoke()
            pagerTarget.controller.dragBy(pagerTarget.state, delta, viewport)
            pagerTarget.controller.endDrag(pagerTarget.state, viewport, velocityPxPerSecond)
            pagerTarget.onPageChanged?.invoke(pagerTarget.state.currentPage)
            needsRender = true
            return
        }
        fail("No fling target at ($startX,$startY)")
    }

    private fun dispatchDragCancel(startX: Int, startY: Int, dx: Int, dy: Int) {
        renderResult?.scrollbarTargets?.lastOrNull { it.bounds.contains(startX, startY) }?.let { target ->
            target.onPressedChanged?.invoke(true)
            dispatchScrollbarDragUpdate(target, startY, startY + dy)
            target.controller.endDrag(target.state, 0f, target.viewportHeightPx, target.contentHeightPx)
            target.onPressedChanged?.invoke(false)
            needsRender = true
            return
        }
        renderResult?.refreshTargets?.lastOrNull { it.bounds.contains(startX, startY) }?.let { target ->
            if (dy > 0 && abs(dy) >= abs(dx) && target.canStartPull(dy.toFloat())) {
                target.controller.startPull(target.state)
                target.onPressedChanged?.invoke(true)
                target.controller.updatePull(target.state, dy.toFloat(), target.thresholdPx)
                cancelRefreshPull(target)
                target.onPressedChanged?.invoke(false)
                needsRender = true
                return
            }
        }
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
            pagerTarget.controller.startDrag(pagerTarget.state, viewport)
            pagerTarget.onPageDragStart?.invoke()
            if (delta != 0f) {
                pagerTarget.controller.dragBy(pagerTarget.state, delta, viewport)
            }
            pagerTarget.controller.cancelDrag(pagerTarget.state)
            needsRender = true
            return
        }
        fail("No cancellable drag target at ($startX,$startY)")
    }

    private fun dragSelectionHandle(finder: PixelFinder, handle: TextInputSelectionHandle, dx: Int, dy: Int) {
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

    private fun updateSelectionHandle(
        target: PixelTextInputTarget,
        handle: TextInputSelectionHandle,
        logicalX: Int,
        logicalY: Int,
    ) {
        if (PixelTextInputSelectionGesture.dragHandle(target, handle, logicalX, logicalY)) needsRender = true
    }

    private fun dispatchSliderDrag(target: PixelSliderTarget, x: Int) {
        val ratio = ((x - target.bounds.left).toFloat() / target.bounds.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        target.onDrag(ratio)
        target.onRelease(ratio)
        needsRender = true
    }

    /** Updates an active slider without incorrectly synthesizing release on every move. */
    private fun dispatchSliderDragUpdate(target: PixelSliderTarget, x: Int) {
        val ratio = ((x - target.bounds.left).toFloat() / target.bounds.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        target.onDrag(ratio)
        needsRender = true
    }

    /** Completes an active slider at its latest virtual pointer position. */
    private fun dispatchSliderRelease(target: PixelSliderTarget, x: Int) {
        val ratio = ((x - target.bounds.left).toFloat() / target.bounds.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        target.onRelease(ratio)
        needsRender = true
    }

    /** Transfers virtual hover ownership with Host slider/scrollbar/click/refresh precedence. */
    private fun updateHover(x: Int, y: Int) {
        /** Highest-priority compact value-control target under the virtual pointer. */
        val sliderTarget = renderResult?.sliderTargets?.lastOrNull { target ->
            target.bounds.contains(x, y) && target.onHoveredChanged != null
        }
        /** Overlay scrollbar considered only when no slider owns the point. */
        val scrollbarTarget = if (sliderTarget == null) {
            renderResult?.scrollbarTargets?.lastOrNull { target ->
                target.bounds.contains(x, y) && target.onHoveredChanged != null
            }
        } else {
            null
        }
        /** Nested click content takes precedence over its enclosing refresh boundary. */
        val clickTarget = if (sliderTarget == null && scrollbarTarget == null) {
            renderResult?.clickTargets?.lastOrNull { target ->
                target.bounds.contains(x, y) && target.onHoveredChanged != null
            }
        } else {
            null
        }
        /** Lowest-priority refresh boundary under otherwise passive content. */
        val refreshTarget = if (sliderTarget == null && scrollbarTarget == null && clickTarget == null) {
            renderResult?.refreshTargets?.lastOrNull { target ->
                target.bounds.contains(x, y) && target.onHoveredChanged != null
            }
        } else {
            null
        }
        if (!sameSliderTarget(hoveredSliderTarget, sliderTarget)) {
            hoveredSliderTarget?.onHoveredChanged?.invoke(false)
            hoveredSliderTarget = sliderTarget
            sliderTarget?.onHoveredChanged?.invoke(true)
            needsRender = true
        } else if (sliderTarget != null) {
            hoveredSliderTarget = sliderTarget
        }
        if (!sameScrollbarTarget(hoveredScrollbarTarget, scrollbarTarget)) {
            hoveredScrollbarTarget?.onHoveredChanged?.invoke(false)
            hoveredScrollbarTarget = scrollbarTarget
            scrollbarTarget?.onHoveredChanged?.invoke(true)
            needsRender = true
        } else if (scrollbarTarget != null) {
            hoveredScrollbarTarget = scrollbarTarget
        }
        if (!sameClickTarget(hoveredClickTarget, clickTarget)) {
            hoveredClickTarget?.onHoveredChanged?.invoke(false)
            hoveredClickTarget = clickTarget
            clickTarget?.onHoveredChanged?.invoke(true)
            needsRender = true
        } else if (clickTarget != null) {
            hoveredClickTarget = clickTarget
        }
        if (!sameRefreshTarget(hoveredRefreshTarget, refreshTarget)) {
            hoveredRefreshTarget?.onHoveredChanged?.invoke(false)
            hoveredRefreshTarget = refreshTarget
            refreshTarget?.onHoveredChanged?.invoke(true)
            needsRender = true
        } else if (refreshTarget != null) {
            hoveredRefreshTarget = refreshTarget
        }
        /** Whether any retained component owns this virtual hover point. */
        val ownsHover = sliderTarget != null || scrollbarTarget != null ||
            clickTarget != null || refreshTarget != null
        hoveredLogicalX = if (ownsHover) x else null
        hoveredLogicalY = if (ownsHover) y else null
    }

    /** Keeps hover only when the same retained source is still interactive under the pointer. */
    private fun reconcileHoverTargets(result: PixelRenderResult) {
        val logicalX = hoveredLogicalX
        val logicalY = hoveredLogicalY
        hoveredClickTarget?.let { previous ->
            val replacement = result.clickTargets
                .lastOrNull { candidate -> sameClickTarget(previous, candidate) }
                ?.takeIf { target ->
                    target.onHoveredChanged != null &&
                        logicalX != null &&
                        logicalY != null &&
                        target.bounds.contains(logicalX, logicalY)
                }
            if (replacement != null) {
                hoveredClickTarget = replacement
            } else {
                hoveredClickTarget = null
                previous.onHoveredChanged?.invoke(false)
                needsRender = true
            }
        }
        hoveredSliderTarget?.let { previous ->
            val replacement = result.sliderTargets
                .lastOrNull { candidate -> sameSliderTarget(previous, candidate) }
                ?.takeIf { target ->
                    target.onHoveredChanged != null &&
                        logicalX != null &&
                        logicalY != null &&
                        target.bounds.contains(logicalX, logicalY)
                }
            if (replacement != null) {
                hoveredSliderTarget = replacement
            } else {
                hoveredSliderTarget = null
                previous.onHoveredChanged?.invoke(false)
                needsRender = true
            }
        }
        hoveredScrollbarTarget?.let { previous ->
            /** Matching enabled scrollbar still under the virtual pointer. */
            val replacement = result.scrollbarTargets
                .lastOrNull { candidate -> sameScrollbarTarget(previous, candidate) }
                ?.takeIf { target ->
                    target.onHoveredChanged != null &&
                        logicalX != null &&
                        logicalY != null &&
                        target.bounds.contains(logicalX, logicalY)
                }
            if (replacement != null) {
                hoveredScrollbarTarget = replacement
            } else {
                hoveredScrollbarTarget = null
                previous.onHoveredChanged?.invoke(false)
                needsRender = true
            }
        }
        hoveredRefreshTarget?.let { previous ->
            /** Matching enabled refresh boundary still under the virtual pointer. */
            val replacement = result.refreshTargets
                .lastOrNull { candidate -> sameRefreshTarget(previous, candidate) }
                ?.takeIf { target ->
                    target.onHoveredChanged != null &&
                        logicalX != null &&
                        logicalY != null &&
                        target.bounds.contains(logicalX, logicalY)
                }
            if (replacement != null) {
                hoveredRefreshTarget = replacement
            } else {
                hoveredRefreshTarget = null
                previous.onHoveredChanged?.invoke(false)
                needsRender = true
            }
        }
        if (
            hoveredClickTarget == null &&
            hoveredSliderTarget == null &&
            hoveredScrollbarTarget == null &&
            hoveredRefreshTarget == null
        ) {
            hoveredLogicalX = null
            hoveredLogicalY = null
        }
    }

    /** Clears virtual hover callbacks exactly once. */
    private fun clearHover() {
        val sliderTarget = hoveredSliderTarget
        val scrollbarTarget = hoveredScrollbarTarget
        val clickTarget = hoveredClickTarget
        val refreshTarget = hoveredRefreshTarget
        hoveredSliderTarget = null
        hoveredScrollbarTarget = null
        hoveredClickTarget = null
        hoveredRefreshTarget = null
        hoveredLogicalX = null
        hoveredLogicalY = null
        sliderTarget?.onHoveredChanged?.invoke(false)
        scrollbarTarget?.onHoveredChanged?.invoke(false)
        clickTarget?.onHoveredChanged?.invoke(false)
        refreshTarget?.onHoveredChanged?.invoke(false)
        if (sliderTarget != null || scrollbarTarget != null || clickTarget != null || refreshTarget != null) {
            needsRender = true
        }
    }

    /** Compares retained click identity across an interaction-triggered rebuild. */
    private fun sameClickTarget(first: PixelClickTarget?, second: PixelClickTarget?): Boolean {
        if (first == null || second == null) return first == null && second == null
        return if (first.source != null && second.source != null) first.source === second.source else first === second
    }

    /** Compares retained slider identity across an interaction-triggered rebuild. */
    private fun sameSliderTarget(first: PixelSliderTarget?, second: PixelSliderTarget?): Boolean {
        if (first == null || second == null) return first == null && second == null
        return if (first.source != null && second.source != null) first.source === second.source else first === second
    }

    /** Compares retained scrollbar identity across an interaction-triggered rebuild. */
    private fun sameScrollbarTarget(first: PixelScrollbarTarget?, second: PixelScrollbarTarget?): Boolean {
        if (first == null || second == null) return first == null && second == null
        return if (first.source != null && second.source != null) {
            first.source === second.source
        } else {
            first.state === second.state
        }
    }

    /** Compares retained refresh identity across an interaction-triggered rebuild. */
    private fun sameRefreshTarget(first: PixelRefreshTarget?, second: PixelRefreshTarget?): Boolean {
        if (first == null || second == null) return first == null && second == null
        return if (first.source != null && second.source != null) {
            first.source === second.source
        } else {
            first.state === second.state
        }
    }

    /** Resolves the matching target snapshot for one active virtual gesture. */
    private fun reconcileGestureTarget(
        previous: TestGestureTarget,
        result: PixelRenderResult,
    ): TestGestureTarget? {
        return when (previous) {
            is TestGestureTarget.Click -> result.clickTargets
                .lastOrNull { candidate -> sameClickTarget(previous.target, candidate) }
                ?.let(TestGestureTarget::Click)
            is TestGestureTarget.Slider -> result.sliderTargets
                .lastOrNull { candidate -> sameSliderTarget(previous.target, candidate) }
                ?.let(TestGestureTarget::Slider)
            is TestGestureTarget.TextInput -> result.textInputTargets
                .lastOrNull { candidate -> sameRetainedSource(previous.target.source, candidate.source, previous.target === candidate) }
                ?.let(TestGestureTarget::TextInput)
            is TestGestureTarget.List -> result.listTargets
                .lastOrNull { candidate -> sameRetainedSource(previous.target.source, candidate.source, previous.target.state === candidate.state) }
                ?.let(TestGestureTarget::List)
            is TestGestureTarget.Pager -> result.pagerTargets
                .lastOrNull { candidate -> sameRetainedSource(previous.target.source, candidate.source, previous.target.state === candidate.state) }
                ?.let(TestGestureTarget::Pager)
            is TestGestureTarget.Scrollbar -> result.scrollbarTargets
                .lastOrNull { candidate -> sameRetainedSource(previous.target.source, candidate.source, previous.target.state === candidate.state) }
                ?.let(TestGestureTarget::Scrollbar)
            is TestGestureTarget.Refresh -> result.refreshTargets
                .lastOrNull { candidate -> sameRetainedSource(previous.target.source, candidate.source, previous.target.state === candidate.state) }
                ?.let(TestGestureTarget::Refresh)
        }
    }

    /** Compares optional RenderObject sources and uses [fallback] only when either source is absent. */
    private fun sameRetainedSource(first: Any?, second: Any?, fallback: Boolean): Boolean {
        return if (first != null && second != null) first === second else fallback
    }

    private fun dispatchScrollbarDrag(target: PixelScrollbarTarget, startY: Int, endY: Int) {
        target.onPressedChanged?.invoke(true)
        dispatchScrollbarDragUpdate(target, startY, endY)
        target.controller.endDrag(target.state, 0f, target.viewportHeightPx, target.contentHeightPx)
        target.onPressedChanged?.invoke(false)
        needsRender = true
    }

    /** Updates one active scrollbar drag without synthesizing release on every move. */
    private fun dispatchScrollbarDragUpdate(target: PixelScrollbarTarget, startY: Int, endY: Int) {
        /** Pointer-to-thumb offset preserving the original grab point. */
        val dragOffset = if (target.thumbBounds.contains(target.bounds.left, startY)) {
            startY - target.thumbBounds.top
        } else {
            target.thumbBounds.height / 2
        }.coerceIn(0, target.thumbBounds.height.coerceAtLeast(1))
        /** Available thumb travel within the track. */
        val thumbTravel = (target.bounds.height - target.thumbBounds.height).coerceAtLeast(0)
        /** Maximum controlled list offset. */
        val maxOffset = (target.contentHeightPx - target.viewportHeightPx).coerceAtLeast(0)
        if (thumbTravel > 0 && maxOffset > 0) {
            /** Requested thumb top clamped to the track. */
            val thumbTop = (endY - target.bounds.top - dragOffset).coerceIn(0, thumbTravel)
            /** Absolute list offset mapped from the thumb position. */
            val targetOffset = (thumbTop.toFloat() / thumbTravel.toFloat()) * maxOffset.toFloat()
            target.controller.startDrag(target.state)
            target.controller.scrollTo(target.state, targetOffset, target.viewportHeightPx, target.contentHeightPx)
        }
        needsRender = true
    }

    private fun dispatchRefreshDrag(target: PixelRefreshTarget, dy: Int) {
        target.controller.startPull(target.state)
        target.onPressedChanged?.invoke(true)
        target.controller.updatePull(target.state, dy.toFloat().coerceAtLeast(0f), target.thresholdPx)
        target.onPressedChanged?.invoke(false)
        if (target.controller.endPull(target.state, target.thresholdPx)) {
            target.onRefresh()
        }
        needsRender = true
    }

    /** Cancels a non-refreshing pull below threshold so removal never invokes business logic. */
    private fun cancelRefreshPull(target: PixelRefreshTarget) {
        if (target.state.isRefreshing) return
        target.controller.updatePull(target.state, 0f, target.thresholdPx)
        target.controller.endPull(target.state, target.thresholdPx)
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
        target.controller.startDrag(target.state, viewport)
        target.onPageDragStart?.invoke()
        target.controller.dragBy(target.state, delta, viewport)
        target.controller.endDrag(target.state, viewport, 0f)
        target.onPageChanged?.invoke(target.state.currentPage)
        needsRender = true
    }

    private fun startPagerDrag(target: PixelPagerTarget, dx: Float, dy: Float) {
        val delta = when (target.axis) {
            PixelAxis.HORIZONTAL -> dx
            PixelAxis.VERTICAL -> dy
        }
        val viewport = pagerViewport(target)
        target.controller.startDrag(target.state, viewport)
        target.onPageDragStart?.invoke()
        target.controller.dragBy(target.state, delta, viewport)
        needsRender = true
    }

    private fun endPagerDrag(target: PixelPagerTarget, velocityPxPerSecond: Float = 0f) {
        val viewport = pagerViewport(target)
        target.controller.endDrag(target.state, viewport, velocityPxPerSecond)
        target.onPageChanged?.invoke(target.state.currentPage)
        needsRender = true
    }

    private fun cancelPagerDrag(target: PixelPagerTarget) {
        target.controller.cancelDrag(target.state)
        needsRender = true
    }

    private fun pagerViewport(target: PixelPagerTarget): Int {
        return when (target.axis) {
            PixelAxis.HORIZONTAL -> target.bounds.width
            PixelAxis.VERTICAL -> target.bounds.height
        }.coerceAtLeast(1)
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
        val targets = renderResult?.clickTargets.orEmpty()
        val callback = widget.clickCallbackOrNull()
        if (callback != null) {
            targets.lastOrNull {
                it.onClick === callback || it.onLongPress === callback || it.onDoubleTap === callback
            }?.let { return it }
        }

        // Stateful controls retain the public configuration widget and build an internal
        // InteractionDetector whose forwarding onTap owns the render target. Match that retained
        // descendant by the same key so find.byKey resolves the real render target.
        // 即按同一 key 匹配该 retained 后代，使 find.byKey 命中真实渲染目标。
        val widgetKey = widget.readField("key")
        if (widgetKey != null) {
            runtime.collectWidgets()
                .asSequence()
                .filter { candidate -> candidate !== widget && candidate.readField("key") == widgetKey }
                .mapNotNull(Any::clickCallbackOrNull)
                .forEach { forwardedCallback ->
                    targets.lastOrNull { target ->
                        target.onClick === forwardedCallback ||
                            target.onLongPress === forwardedCallback ||
                            target.onDoubleTap === forwardedCallback
                    }?.let { return it }
                }
        }
        /** Visible text exposed by leaf Text widgets after stateful control indirection. */
        val visibleText = (widget.readField("data") ?: widget.readField("text")) as? String
        if (visibleText != null) {
            /** Unique actionable semantic node whose public label matches the located text. */
            val semanticNode = renderResult?.semanticsNodes.orEmpty().singleOrNull { node ->
                node.label == visibleText &&
                    node.enabled &&
                    PixelSemanticsAction.CLICK in node.actions
            }
            if (semanticNode != null) {
                /** Logical center guaranteed to belong to the semantic node's pointer surface. */
                val centerX = semanticNode.left + semanticNode.width / 2
                /** Logical vertical center paired with [centerX]. */
                val centerY = semanticNode.top + semanticNode.height / 2
                targets.lastOrNull { target -> target.bounds.contains(centerX, centerY) }
                    ?.let { return it }
            }
        }
        return null
    }

    /** Correlates a public or retained Slider widget with its current render target snapshot. */
    @Suppress("UNCHECKED_CAST")
    private fun resolveSliderTargetOrNull(widget: Any): PixelSliderTarget? {
        val onDrag = widget.readField("onDrag") as? ((Float) -> Unit)
        val targets = renderResult?.sliderTargets.orEmpty()
        if (onDrag != null) {
            targets.lastOrNull { it.onDrag === onDrag }?.let { return it }
        }

        // Stateful Slider keeps the public callback on its owner while RenderSlider exports the
        // State's forwarding callback. Correlate the retained inner render widget by the same key.
        val widgetKey = widget.readField("key")
        if (widgetKey != null) {
            runtime.collectWidgets()
                .asSequence()
                .filter { candidate -> candidate !== widget && candidate.readField("key") == widgetKey }
                .mapNotNull { candidate -> candidate.readField("onDrag") as? ((Float) -> Unit) }
                .forEach { forwardedDrag ->
                    targets.lastOrNull { target -> target.onDrag === forwardedDrag }?.let { return it }
                }
        }
        return if (targets.size == 1 && widget::class.java.simpleName.contains("Slider")) {
            targets.single()
        } else {
            null
        }
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

    private fun nearestSelectionHandle(
        target: PixelTextInputTarget,
        logicalX: Int,
        logicalY: Int,
    ): TextInputSelectionHandle {
        return PixelTextInputSelectionGesture.nearestHandle(target, logicalX, logicalY)
            ?: TextInputSelectionHandle.START
    }

    private fun handlePoint(target: PixelTextInputTarget, handle: TextInputSelectionHandle): Point {
        val text = target.state.text
        if (text.isEmpty()) return target.bounds.center
        val index = when (handle) {
            TextInputSelectionHandle.START -> target.state.selectionStart
            TextInputSelectionHandle.END -> target.state.selectionEnd
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
        return PixelTextInputSelectionGesture.resolveSelection(target, logicalX, logicalY)
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

    private sealed class TestGestureTarget {
        data class Click(val target: PixelClickTarget) : TestGestureTarget()
        data class TextInput(val target: PixelTextInputTarget) : TestGestureTarget()
        data class List(val target: PixelListTarget) : TestGestureTarget()
        data class Pager(val target: PixelPagerTarget) : TestGestureTarget()
        data class Slider(val target: PixelSliderTarget) : TestGestureTarget()
        data class Scrollbar(val target: PixelScrollbarTarget) : TestGestureTarget()
        data class Refresh(val target: PixelRefreshTarget) : TestGestureTarget()
    }

    /** Identifies the exact callback channel currently owning virtual pressed feedback. */
    private enum class TestPressedFeedbackOwner {
        /** Simultaneous ordinary click target under a drag candidate. */
        Click,

        /** Slider target captured directly at pointer down. */
        Slider,

        /** Scrollbar target captured directly at pointer down. */
        Scrollbar,

        /** Refresh target promoted only after vertical pull arbitration. */
        Refresh,
    }

    private inner class ActiveTestGesture(
        val pointerId: Int,
        val startX: Int,
        val startY: Int,
        var currentX: Int,
        var currentY: Int,
        /** Current snapshot of the gesture owner, or null after that owner disappears. */
        private var target: TestGestureTarget?,
        /** Simultaneous clickable target retained while a list/pager remains a drag candidate. */
        private var pressedClickTarget: PixelClickTarget?,
    ) {
        private var moved = false
        private var dragging = false
        private var elapsedMs = 0L
        private val samples = mutableListOf(GestureSample(elapsedMs = 0L, x = startX, y = startY))
        /** Exact feedback channel that must receive one matching pressed=false. */
        private var pressedFeedbackOwner: TestPressedFeedbackOwner? = null

        /** Sends pressed=true to direct targets or the simultaneous click candidate at down. */
        fun down() {
            when (val activeTarget = target) {
                is TestGestureTarget.Slider -> activeTarget.target.onPressedChanged?.let { callback ->
                    callback(true)
                    pressedFeedbackOwner = TestPressedFeedbackOwner.Slider
                    needsRender = true
                }
                is TestGestureTarget.Scrollbar -> activeTarget.target.onPressedChanged?.let { callback ->
                    callback(true)
                    pressedFeedbackOwner = TestPressedFeedbackOwner.Scrollbar
                    needsRender = true
                }
                else -> (pressedClickTarget ?: (activeTarget as? TestGestureTarget.Click)?.target)
                    ?.onPressedChanged
                    ?.let { callback ->
                    callback(true)
                    pressedFeedbackOwner = TestPressedFeedbackOwner.Click
                    needsRender = true
                }
            }
        }

        /** Migrates this gesture to [result], cancelling stale pressed ownership exactly once. */
        fun reconcileTargets(result: PixelRenderResult) {
            val previousTarget = target
            val replacementTarget = previousTarget?.let { owner -> reconcileGestureTarget(owner, result) }
            val previousPressedTarget = pressedClickTarget
            val replacementPressedTarget = previousPressedTarget?.let { previous ->
                result.clickTargets
                    .lastOrNull { candidate -> sameClickTarget(previous, candidate) }
                    ?.takeIf { candidate -> candidate.onPressedChanged != null }
            }
            /** Whether the exact pressed callback owner disappeared or became inert. */
            val feedbackOwnerDisappeared = when (pressedFeedbackOwner) {
                TestPressedFeedbackOwner.Click -> replacementPressedTarget == null
                TestPressedFeedbackOwner.Slider -> replacementTarget !is TestGestureTarget.Slider ||
                    replacementTarget.target.onPressedChanged == null
                TestPressedFeedbackOwner.Scrollbar -> replacementTarget !is TestGestureTarget.Scrollbar ||
                    replacementTarget.target.onPressedChanged == null
                TestPressedFeedbackOwner.Refresh -> replacementTarget !is TestGestureTarget.Refresh ||
                    replacementTarget.target.onPressedChanged == null
                null -> false
            }
            if (feedbackOwnerDisappeared) {
                clearPressedFeedback()
            }
            if (previousTarget is TestGestureTarget.Scrollbar && replacementTarget !is TestGestureTarget.Scrollbar) {
                previousTarget.target.controller.endDrag(
                    previousTarget.target.state,
                    0f,
                    previousTarget.target.viewportHeightPx,
                    previousTarget.target.contentHeightPx,
                )
                needsRender = true
            }
            if (previousTarget is TestGestureTarget.Refresh && replacementTarget !is TestGestureTarget.Refresh && dragging) {
                cancelRefreshPull(previousTarget.target)
                dragging = false
                needsRender = true
            }
            target = replacementTarget
            pressedClickTarget = replacementPressedTarget
        }

        fun moveBy(dx: Int, dy: Int, deltaMs: Long) {
            currentX += dx
            currentY += dy
            elapsedMs += deltaMs
            samples += GestureSample(elapsedMs = elapsedMs, x = currentX, y = currentY)
            if (dx != 0 || dy != 0) moved = true
            val activeTarget = target
            if (
                moved &&
                activeTarget !is TestGestureTarget.Slider &&
                activeTarget !is TestGestureTarget.Scrollbar &&
                activeTarget !is TestGestureTarget.Refresh
            ) {
                clearPressedFeedback()
            }
            if (activeTarget == null) return
            if (!isPrimaryPointer(pointerId) && activeTarget.isPointerExclusive()) return
            when (activeTarget) {
                is TestGestureTarget.Click -> if (isHorizontalSwipe()) {
                    if (!dragging) {
                        activeTarget.target.onSwipeStart?.invoke()
                        dragging = true
                    }
                    activeTarget.target.onSwipeUpdate?.invoke(currentX - startX)
                    needsRender = true
                }
                is TestGestureTarget.TextInput -> moveTextInput(activeTarget.target)
                is TestGestureTarget.List -> moveList(activeTarget.target, dy)
                is TestGestureTarget.Pager -> movePager(activeTarget.target, dx, dy)
                is TestGestureTarget.Slider -> {
                    dispatchSliderDragUpdate(activeTarget.target, currentX)
                    dragging = true
                }
                is TestGestureTarget.Scrollbar -> {
                    dispatchScrollbarDragUpdate(activeTarget.target, startY, currentY)
                    dragging = true
                }
                is TestGestureTarget.Refresh -> {
                    /** Total pull vector used for the same directional arbitration as Host. */
                    val totalDx = currentX - startX
                    /** Positive total vertical pull distance from pointer down. */
                    val totalDy = currentY - startY
                    if (
                        !dragging &&
                        totalDy > 0 &&
                        abs(totalDy) >= abs(totalDx) &&
                        activeTarget.target.canStartPull(totalDy.toFloat())
                    ) {
                        clearPressedFeedback()
                        activeTarget.target.controller.startPull(activeTarget.target.state)
                        activeTarget.target.onPressedChanged?.invoke(true)
                        pressedFeedbackOwner = TestPressedFeedbackOwner.Refresh
                        dragging = true
                    }
                    if (dragging) {
                        activeTarget.target.controller.updatePull(
                            activeTarget.target.state,
                            totalDy.toFloat().coerceAtLeast(0f),
                            activeTarget.target.thresholdPx,
                        )
                        needsRender = true
                    }
                }
            }
        }

        fun up() {
            clearPressedFeedback()
            val activeTarget = target
            when (activeTarget) {
                is TestGestureTarget.Click -> {
                    if (!moved && activeTarget.target.bounds.contains(currentX, currentY)) {
                        activeTarget.target.onClick()
                        needsRender = true
                    } else if (isHorizontalSwipe()) {
                        activeTarget.target.onSwipeEnd?.invoke(currentX - startX)
                        val callback = if (currentX < startX) {
                            activeTarget.target.onSwipeLeft
                        } else {
                            activeTarget.target.onSwipeRight
                        }
                        callback?.invoke()
                        if (callback != null) {
                            needsRender = true
                        }
                    }
                }
                is TestGestureTarget.TextInput -> if (!moved) {
                    focusTextInput(activeTarget.target)
                }
                is TestGestureTarget.List -> if (dragging) {
                    activeTarget.target.controller.endDrag(
                        activeTarget.target.state,
                        velocityY(),
                        activeTarget.target.viewportHeightPx,
                        activeTarget.target.contentHeightPx,
                    )
                    needsRender = true
                }
                is TestGestureTarget.Pager -> if (dragging) endPagerDrag(activeTarget.target, velocityFor(activeTarget.target.axis))
                is TestGestureTarget.Slider -> dispatchSliderRelease(activeTarget.target, currentX)
                is TestGestureTarget.Scrollbar -> if (dragging) {
                    activeTarget.target.controller.endDrag(
                        activeTarget.target.state,
                        0f,
                        activeTarget.target.viewportHeightPx,
                        activeTarget.target.contentHeightPx,
                    )
                    needsRender = true
                }
                is TestGestureTarget.Refresh -> if (dragging) {
                    if (activeTarget.target.controller.endPull(
                            activeTarget.target.state,
                            activeTarget.target.thresholdPx,
                        )
                    ) {
                        activeTarget.target.onRefresh()
                    }
                    needsRender = true
                }
                null -> Unit
            }
            val currentPressedClickTarget = pressedClickTarget
            if (
                !moved &&
                activeTarget !is TestGestureTarget.Click &&
                activeTarget !is TestGestureTarget.TextInput &&
                activeTarget !is TestGestureTarget.Slider &&
                activeTarget !is TestGestureTarget.Scrollbar &&
                currentPressedClickTarget?.bounds?.contains(currentX, currentY) == true
            ) {
                currentPressedClickTarget.onClick.invoke()
                needsRender = true
            }
        }

        fun cancel() {
            clearPressedFeedback()
            when (val activeTarget = target) {
                is TestGestureTarget.Pager -> if (dragging) cancelPagerDrag(activeTarget.target)
                is TestGestureTarget.List -> if (dragging) {
                    activeTarget.target.controller.endDrag(
                        activeTarget.target.state,
                        0f,
                        activeTarget.target.viewportHeightPx,
                        activeTarget.target.contentHeightPx,
                    )
                    needsRender = true
                }
                is TestGestureTarget.Click -> if (dragging) {
                    activeTarget.target.onSwipeEnd?.invoke(0)
                    needsRender = true
                }
                is TestGestureTarget.Scrollbar -> if (dragging) {
                    activeTarget.target.controller.endDrag(
                        activeTarget.target.state,
                        0f,
                        activeTarget.target.viewportHeightPx,
                        activeTarget.target.contentHeightPx,
                    )
                    needsRender = true
                }
                is TestGestureTarget.Refresh -> if (dragging) {
                    cancelRefreshPull(activeTarget.target)
                    needsRender = true
                }
                is TestGestureTarget.TextInput,
                is TestGestureTarget.Slider,
                -> Unit
                null -> Unit
            }
        }

        /** Sends the matching pressed=false exactly once on release, cancel, or drag takeover. */
        private fun clearPressedFeedback() {
            val owner = pressedFeedbackOwner ?: return
            pressedFeedbackOwner = null
            when (owner) {
                TestPressedFeedbackOwner.Click -> {
                    val activeTarget = target
                    (pressedClickTarget ?: (activeTarget as? TestGestureTarget.Click)?.target)
                        ?.onPressedChanged
                        ?.invoke(false)
                }
                TestPressedFeedbackOwner.Slider -> {
                    (target as? TestGestureTarget.Slider)?.target?.onPressedChanged?.invoke(false)
                }
                TestPressedFeedbackOwner.Scrollbar -> {
                    (target as? TestGestureTarget.Scrollbar)?.target?.onPressedChanged?.invoke(false)
                }
                TestPressedFeedbackOwner.Refresh -> {
                    (target as? TestGestureTarget.Refresh)?.target?.onPressedChanged?.invoke(false)
                }
            }
            needsRender = true
        }

        private fun moveTextInput(target: PixelTextInputTarget) {
            focusTextInput(target)
            if (target.readOnly) return
            if (target.state.selectionStart != target.state.selectionEnd) {
                val handle = nearestSelectionHandle(target, startX, startY)
                updateSelectionHandle(target, handle, currentX, currentY)
            } else {
                PixelTextInputSelectionGesture.setCollapsedSelection(target, currentX, currentY)
            }
            dragging = true
            needsRender = true
        }

        private fun moveList(target: PixelListTarget, dy: Int) {
            if (!dragging) {
                target.controller.startDrag(target.state)
                dragging = true
            }
            target.controller.dragBy(target.state, dy.toFloat(), target.viewportHeightPx, target.contentHeightPx)
            needsRender = true
        }

        private fun movePager(target: PixelPagerTarget, dx: Int, dy: Int) {
            if (!dragging) {
                startPagerDrag(target, dx.toFloat(), dy.toFloat())
            } else {
                val delta = when (target.axis) {
                    PixelAxis.HORIZONTAL -> dx.toFloat()
                    PixelAxis.VERTICAL -> dy.toFloat()
                }
                target.controller.dragBy(target.state, delta, pagerViewport(target))
                needsRender = true
            }
            dragging = true
        }

        private fun velocityFor(axis: PixelAxis): Float {
            return when (axis) {
                PixelAxis.HORIZONTAL -> velocityX()
                PixelAxis.VERTICAL -> velocityY()
            }
        }

        private fun isHorizontalSwipe(): Boolean {
            val dx = currentX - startX
            val dy = currentY - startY
            val absX = abs(dx)
            val absY = abs(dy)
            return absX >= 4 && absX > absY * 1.2f
        }

        private fun velocityX(): Float = velocity { it.x }

        private fun velocityY(): Float = velocity { it.y }

        private fun velocity(selector: (GestureSample) -> Int): Float {
            if (samples.size < 2) return 0f
            val last = samples.last()
            val first = samples.asReversed().firstOrNull { last.elapsedMs - it.elapsedMs >= 16L }
                ?: samples.first()
            val dtMs = (last.elapsedMs - first.elapsedMs).coerceAtLeast(1L)
            return ((selector(last) - selector(first)).toFloat() * 1000f) / dtMs
        }
    }

    private data class GestureSample(val elapsedMs: Long, val x: Int, val y: Int)

    private fun TestGestureTarget.isPointerExclusive(): Boolean {
        return this is TestGestureTarget.List ||
            this is TestGestureTarget.Pager ||
            this is TestGestureTarget.Slider ||
            this is TestGestureTarget.Scrollbar ||
            this is TestGestureTarget.Refresh
    }

    private data class Point(val x: Int, val y: Int)

    private val PixelRect.center: Point
        get() = Point(left + width / 2, top + height / 2)
}

private fun pixelChar(color: PixelColor): Char {
    val argb = color.argb
    val alpha = (argb ushr 24) and 0xFF
    if (alpha == 0) return '.'
    val red = (argb ushr 16) and 0xFF
    val green = (argb ushr 8) and 0xFF
    val blue = argb and 0xFF
    val brightness = (red * 299 + green * 587 + blue * 114) / 1000
    return when {
        brightness >= 200 -> '#'
        brightness >= 50 -> '*'
        else -> '.'
    }
}

private class DefaultPixelTestGesture(
    private val tester: PixelTester,
    private val pointerId: Int,
) : PixelTestGesture {
    override fun moveBy(dx: Int, dy: Int): PixelTestGesture {
        tester.moveGestureBy(pointerId, dx, dy)
        return this
    }

    override fun moveBy(dx: Int, dy: Int, deltaMs: Long): PixelTestGesture {
        tester.moveGestureBy(pointerId, dx, dy, deltaMs)
        return this
    }

    override fun up() {
        tester.endGesture(pointerId)
    }

    override fun cancel() {
        tester.cancelGesture(pointerId)
    }
}

/**
 * 定义 `PixelSemanticsActionArguments` 在 `PixelTester` 中承担的数据与行为边界。
 *
 * Optional values consumed by parameterized [PixelTester.performSemanticsAction] requests.
 *
 * Only the field corresponding to the requested action is read.
 */
public data class PixelSemanticsActionArguments(
    /** 公开 `PixelTester` 的 `text` 配置或运行值。
 *
 * Replacement value for `SET_TEXT`.
 */
    public val text: String? = null,
    /** 公开 `PixelTester` 的 `selectionStart` 配置或运行值。
 *
 * Inclusive start for `SET_SELECTION`.
 */
    public val selectionStart: Int? = null,
    /** 公开 `PixelTester` 的 `selectionEnd` 配置或运行值。
 *
 * Exclusive end for `SET_SELECTION`.
 */
    public val selectionEnd: Int? = null,
    /** 公开 `PixelTester` 的 `progress` 配置或运行值。
 *
 * Requested numeric value for `SET_PROGRESS`.
 */
    public val progress: Float? = null,
    /** 公开 `PixelTester` 的 `customActionId` 配置或运行值。
 *
 * Stable custom action id for `CUSTOM`.
 */
    public val customActionId: String? = null,
)

/**
 * [PixelTester.startGesture] 返回的可推进测试手势。
 */
public interface PixelTestGesture {
    /**
     * 移动当前手势，默认按一帧时间推进。
     */
    public fun moveBy(dx: Int, dy: Int): PixelTestGesture

    /**
     * 移动当前手势，并指定本次移动经过的毫秒数。
     */
    public fun moveBy(dx: Int, dy: Int, deltaMs: Long): PixelTestGesture

    /**
     * 抬起当前手势。
     */
    public fun up()

    /**
     * 取消当前手势。
     */
    public fun cancel()
}

/**
 * 常用 finder 工厂。
 */
public object find {
    /**
     * 按文本内容查找 widget。
     */
    public fun byText(text: String): PixelFinder = PixelFinder.ByText(text)

    /**
     * 按 Kotlin 类型查找 widget。
     */
    public fun byType(type: kotlin.reflect.KClass<*>): PixelFinder = PixelFinder.ByType(type)

    /**
     * 按 widget key 查找 widget。
     */
    public fun byKey(key: Any): PixelFinder = PixelFinder.ByKey(key)
}

/**
 * 从当前 widget tree 中定位目标 widget 的查询对象。
 */
public sealed class PixelFinder {
    internal abstract fun matches(widget: Any): Boolean

    /**
     * 选择当前 finder 命中结果中的第 [index] 项。
     */
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
            nodes.take(maxDiagnosticNodes).forEach { node ->
                repeat(node.depth) { append("  ") }
                append(node.path)
                append(": ")
                append(node.summary)
                if (node.matches) append("  <match>")
                appendLine()
            }
            if (nodes.size > maxDiagnosticNodes) {
                append("... truncated ")
                append(nodes.size - maxDiagnosticNodes)
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
        if (nodes.size >= maxDiagnosticNodes * 2) return
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

    /**
     * 通过 text/data/placeholder/state.text 字段匹配文本。
     */
    public data class ByText(/** 保存 `PixelTester` 对外传递的 `text` 数据。 */ public val text: String) : PixelFinder() {
        override fun matches(widget: Any): Boolean {
            val state = widget.readField("state")
            return widget.readField("text") == text ||
                widget.readField("data") == text ||
                widget.readField("placeholder") == text ||
                state?.readField("text") == text
        }
    }

    /**
     * 通过运行时类型匹配 widget。
     */
    public data class ByType(/** 提供 `PixelTester` 用于识别或兼容校验的 `type` 值。 */ public val type: kotlin.reflect.KClass<*>) : PixelFinder() {
        override fun matches(widget: Any): Boolean = type.java.isInstance(widget)
    }

    /**
     * 通过 widget key 匹配 widget。
     */
    public data class ByKey(/** 提供 `PixelTester` 用于识别或兼容校验的 `key` 值。 */ public val key: Any) : PixelFinder() {
        override fun matches(widget: Any): Boolean = widget.readField("key") == key
    }

    /**
     * 包装另一个 finder，只返回指定序号的命中项。
     */
    public data class Nth(/** 记录 `PixelTester` 的 `finder` 配置或运行值，读取与更新均遵守所属类型约束。 */ public val finder: PixelFinder, /** 保存 `PixelTester` 的 `index` 计数或索引边界。 */ public val index: Int) : PixelFinder() {
        /** Delegates matching to the wrapped finder for diagnostics. */
        override fun matches(widget: Any): Boolean = finder.matches(widget)

        /**
         * Selects logical text controls without double-counting their decorative Text child.
         *
         * Stateful standard controls retain a configuration widget and render a nested TextWidget;
         * both expose the same string. When at least one non-text owner matches, prefer those owners
         * so `find.byText("OK").nth(1)` still means the second button rather than button one's label.
         */
        override fun resolve(root: Any?): Any? {
            val matches = finder.resolveAll(root)
            val logicalMatches = if (finder is ByText) {
                val nonTextMatches = matches.filterNot { candidate ->
                    candidate::class.java.simpleName == "TextWidget"
                }
                nonTextMatches.ifEmpty { matches }
            } else {
                matches
            }
            return logicalMatches.getOrNull(index)
        }

        /** Returns only the selected logical occurrence. */
        override fun resolveAll(root: Any?): List<Any> = listOfNotNull(resolve(root))
    }

    private data class FinderDiagnosticsNode(
        val path: String,
        val summary: String,
        val depth: Int,
        val matches: Boolean,
    )

    private companion object {
        val maxDiagnosticNodes = 160
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

/** Reads the first click-like callback carried by a public or internal interaction widget. */
@Suppress("UNCHECKED_CAST")
private fun Any.clickCallbackOrNull(): (() -> Unit)? {
    return readField("onPressed") as? (() -> Unit)
        ?: readField("onTap") as? (() -> Unit)
        ?: readField("onLongPress") as? (() -> Unit)
        ?: readField("onDoubleTap") as? (() -> Unit)
}
