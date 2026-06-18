package com.purride.pixelui

import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

public enum class PixelKey {
    TAB,
    SHIFT_TAB,
    ARROW_UP,
    ARROW_DOWN,
    ARROW_LEFT,
    ARROW_RIGHT,
    ENTER,
    BACK,
    ESCAPE,
    CHARACTER,
    UNKNOWN,
}

public data class PixelKeyEvent(
    val key: PixelKey,
    val character: Char? = null,
)

public enum class PixelFocusDirection {
    NEXT,
    PREVIOUS,
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

/**
 * 焦点获得时把指定滚动 item 保持在可见区域内。
 *
 * 普通 `ListView`、`GridView`、`SingleChildScrollView` 传 [itemIndex]；
 * `CustomScrollView` 的 lazy sliver 传 [sliverIndex] 和 [itemIndex]。
 */
public data class PixelFocusScrollTarget(
    val state: PixelListState,
    val controller: PixelListController,
    val itemIndex: Int,
    val sliverIndex: Int? = null,
) {
    init {
        require(itemIndex >= 0) { "itemIndex must be >= 0" }
        require(sliverIndex == null || sliverIndex >= 0) { "sliverIndex must be >= 0" }
    }
}

public fun interface FocusTraversalPolicy {
    public fun next(
        nodes: List<FocusNode>,
        current: FocusNode?,
        direction: PixelFocusDirection,
    ): FocusNode?
}

public object ReadingOrderFocusTraversalPolicy : FocusTraversalPolicy {
    override fun next(
        nodes: List<FocusNode>,
        current: FocusNode?,
        direction: PixelFocusDirection,
    ): FocusNode? {
        val focusable = nodes.filter { it.canRequestFocus }
        if (focusable.isEmpty()) return null
        val currentIndex = current?.let { focusable.indexOf(it) }.orZero()
        return when (direction) {
            PixelFocusDirection.PREVIOUS,
            PixelFocusDirection.LEFT,
            PixelFocusDirection.UP,
            -> focusable[(currentIndex - 1).floorMod(focusable.size)]
            PixelFocusDirection.NEXT,
            PixelFocusDirection.RIGHT,
            PixelFocusDirection.DOWN,
            -> focusable[(currentIndex + 1).floorMod(focusable.size)]
        }
    }
}

public class GridFocusTraversalPolicy(
    columns: Int,
) : FocusTraversalPolicy {
    public val columns: Int = columns.coerceAtLeast(1)

    override fun next(
        nodes: List<FocusNode>,
        current: FocusNode?,
        direction: PixelFocusDirection,
    ): FocusNode? {
        val focusable = nodes.filter { it.canRequestFocus }
        if (focusable.isEmpty()) return null
        val currentIndex = current?.let { focusable.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        val targetIndex = when (direction) {
            PixelFocusDirection.NEXT -> (currentIndex + 1).floorMod(focusable.size)
            PixelFocusDirection.PREVIOUS -> (currentIndex - 1).floorMod(focusable.size)
            PixelFocusDirection.LEFT -> {
                if (currentIndex % columns == 0) return null
                currentIndex - 1
            }
            PixelFocusDirection.RIGHT -> {
                if (currentIndex % columns == columns - 1 || currentIndex + 1 >= focusable.size) return null
                currentIndex + 1
            }
            PixelFocusDirection.UP -> {
                val next = currentIndex - columns
                if (next < 0) return null
                next
            }
            PixelFocusDirection.DOWN -> {
                val next = currentIndex + columns
                if (next >= focusable.size) return null
                next
            }
        }
        return focusable.getOrNull(targetIndex)
    }
}

public class FocusNode(
    public val debugLabel: String? = null,
    public var canRequestFocus: Boolean = true,
    public var onKeyEvent: ((PixelKeyEvent) -> Boolean)? = null,
) : ChangeNotifier() {
    public var isFocused: Boolean = false
        private set

    internal var scope: FocusScopeNode? = null

    public fun requestFocus(): Boolean {
        return scope?.requestFocus(this) ?: PixelFocusManager.rootScope.requestFocus(this)
    }

    public fun unfocus() {
        scope?.clearFocus(this)
        if (PixelFocusManager.primaryFocus === this) {
            PixelFocusManager.clearFocus()
        }
    }

    internal fun setFocused(focused: Boolean) {
        if (isFocused == focused) return
        isFocused = focused
        notifyListeners()
    }
}

public class FocusScopeNode(
    public var traversalPolicy: FocusTraversalPolicy = ReadingOrderFocusTraversalPolicy,
) : ChangeNotifier() {
    private val nodes = mutableListOf<FocusNode>()

    public val focusedChild: FocusNode?
        get() = nodes.lastOrNull { it.isFocused }

    public fun attach(node: FocusNode) {
        if (node in nodes) return
        node.scope = this
        nodes += node
        notifyListeners()
    }

    public fun detach(node: FocusNode) {
        if (nodes.remove(node)) {
            if (node.isFocused) {
                node.setFocused(false)
                PixelFocusManager.clearFocus(node)
            }
            node.scope = null
            notifyListeners()
        }
    }

    public fun requestFocus(node: FocusNode): Boolean {
        if (!node.canRequestFocus) return false
        if (node !in nodes) attach(node)
        nodes.filter { it !== node && it.isFocused }.forEach { it.setFocused(false) }
        node.setFocused(true)
        PixelFocusManager.setPrimaryFocus(node)
        notifyListeners()
        return true
    }

    public fun clearFocus(node: FocusNode? = null) {
        val targets = if (node == null) nodes.toList() else listOf(node)
        targets.filter { it.isFocused }.forEach { it.setFocused(false) }
        if (node == null || PixelFocusManager.primaryFocus === node) {
            PixelFocusManager.clearFocus(node)
        }
        notifyListeners()
    }

    public fun focusInDirection(direction: PixelFocusDirection): Boolean {
        val next = traversalPolicy.next(nodes = nodes.toList(), current = focusedChild, direction = direction)
            ?: return false
        return requestFocus(next)
    }
}

public object PixelFocusManager {
    public val rootScope: FocusScopeNode = FocusScopeNode()

    public var primaryFocus: FocusNode? = null
        private set

    public fun setPrimaryFocus(node: FocusNode) {
        primaryFocus?.takeUnless { it === node }?.setFocused(false)
        primaryFocus = node
    }

    public fun clearFocus(node: FocusNode? = null) {
        if (node == null || primaryFocus === node) {
            primaryFocus?.setFocused(false)
            primaryFocus = null
        }
    }

    public fun dispatchKeyEvent(event: PixelKeyEvent): Boolean {
        val focused = primaryFocus
        if (focused?.onKeyEvent?.invoke(event) == true) {
            return true
        }
        val scope = focused?.scope ?: rootScope
        return when (event.key) {
            PixelKey.TAB -> scope.focusInDirection(PixelFocusDirection.NEXT)
            PixelKey.SHIFT_TAB -> scope.focusInDirection(PixelFocusDirection.PREVIOUS)
            PixelKey.ARROW_UP -> scope.focusInDirection(PixelFocusDirection.UP)
            PixelKey.ARROW_DOWN -> scope.focusInDirection(PixelFocusDirection.DOWN)
            PixelKey.ARROW_LEFT -> scope.focusInDirection(PixelFocusDirection.LEFT)
            PixelKey.ARROW_RIGHT -> scope.focusInDirection(PixelFocusDirection.RIGHT)
            else -> false
        }
    }
}

public fun FocusScope(
    child: Widget,
    node: FocusScopeNode = PixelFocusManager.rootScope,
    traversalPolicy: FocusTraversalPolicy = ReadingOrderFocusTraversalPolicy,
    key: Any? = null,
): Widget {
    node.traversalPolicy = traversalPolicy
    return FocusScopeWidget(node = node, child = child, key = key)
}

public fun Focus(
    child: Widget,
    node: FocusNode = FocusNode(),
    autofocus: Boolean = false,
    canRequestFocus: Boolean = true,
    onKeyEvent: ((PixelKeyEvent) -> Boolean)? = null,
    scrollTarget: PixelFocusScrollTarget? = null,
    key: Any? = null,
): Widget {
    node.canRequestFocus = canRequestFocus
    node.onKeyEvent = onKeyEvent
    return FocusWidget(node = node, autofocus = autofocus, scrollTarget = scrollTarget, child = child, key = key)
}

private class FocusScopeWidget(
    val node: FocusScopeNode,
    override val child: Widget,
    override val key: Any?,
) : InheritedNotifier<FocusScopeNode>(notifier = node, child = child, key = key)

private class FocusWidget(
    val node: FocusNode,
    val autofocus: Boolean,
    val scrollTarget: PixelFocusScrollTarget?,
    val child: Widget,
    override val key: Any?,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FocusWidgetState()
}

private class FocusWidgetState : State<FocusWidget>() {
    private var attachedScope: FocusScopeNode? = null
    private var ensuredScrollTarget: PixelFocusScrollTarget? = null

    override fun initState() {
        attachToScope()
        if (widget.autofocus) {
            widget.node.requestFocus()
        }
    }

    override fun didChangeDependencies() {
        attachToScope()
        if (widget.autofocus && PixelFocusManager.primaryFocus == null) {
            widget.node.requestFocus()
        }
    }

    override fun didUpdateWidget(oldWidget: FocusWidget) {
        if (oldWidget.node !== widget.node) {
            attachedScope?.detach(oldWidget.node)
            attachedScope = null
            ensuredScrollTarget = null
        }
        if (oldWidget.scrollTarget != widget.scrollTarget) {
            ensuredScrollTarget = null
        }
        attachToScope()
        if (widget.autofocus && PixelFocusManager.primaryFocus == null) {
            widget.node.requestFocus()
        }
    }

    override fun dispose() {
        attachedScope?.detach(widget.node)
    }

    override fun build(context: BuildContext): Widget {
        context.watch(widget.node)
        ensureFocusedItemVisible()
        return FocusNodeScope(
            node = widget.node,
            child = widget.child,
            key = widget.key?.let { "$it-node-scope" },
        )
    }

    private fun attachToScope() {
        val scope = context.getInheritedWidgetOfExactType<FocusScopeWidget>()?.node
            ?: PixelFocusManager.rootScope
        if (attachedScope === scope) return
        attachedScope?.detach(widget.node)
        attachedScope = scope
        scope.attach(widget.node)
    }

    private fun ensureFocusedItemVisible() {
        val target = widget.scrollTarget
        if (!widget.node.isFocused) {
            ensuredScrollTarget = null
            return
        }
        if (target == null || ensuredScrollTarget == target) return
        if (ensureVisibleIfReady(target)) {
            ensuredScrollTarget = target
        }
    }

    private fun ensureVisibleIfReady(target: PixelFocusScrollTarget): Boolean {
        if (target.state.viewportHeightPx <= 0) return false
        val sliverIndex = target.sliverIndex
        if (sliverIndex != null) {
            if (target.state.sliverListGeometries[sliverIndex]?.itemHeightPx(target.itemIndex) == null) return false
            target.controller.scrollSliverItemIntoView(
                state = target.state,
                sliverIndex = sliverIndex,
                itemIndex = target.itemIndex,
            )
            return true
        }
        if (
            target.itemIndex !in target.state.itemTopOffsetsPx.indices ||
            target.itemIndex !in target.state.itemHeightsPx.indices
        ) {
            return false
        }
        target.controller.scrollItemIntoView(state = target.state, itemIndex = target.itemIndex)
        return true
    }
}

internal class FocusNodeScope(
    val node: FocusNode,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return (oldWidget as? FocusNodeScope)?.node !== node
    }
}

private fun Int?.orZero(): Int = this ?: 0

private fun Int.floorMod(divisor: Int): Int {
    val remainder = this % divisor
    return if (remainder < 0) remainder + divisor else remainder
}
