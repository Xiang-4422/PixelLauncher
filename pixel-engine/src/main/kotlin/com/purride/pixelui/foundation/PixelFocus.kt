package com.purride.pixelui

import com.purride.pixelui.internal.PixelFocusDispatcher
import com.purride.pixelui.internal.PixelArtifactInternalApi
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

/**
 * 定义 `PixelKey` 在 `PixelFocus` 中承担的数据与行为边界。
 *
 * Platform-independent key understood by PixelUI focus and standard-control dispatch.
 *
 * 本枚举只表达导航、激活和取消语义。任何可打印文本（BMP、supplementary、组合簇、
 * 多 code point 的 IME 提交）都只通过 [PixelTextInputEvent] 投递，不会出现在这里。
 */
public enum class PixelKey {
    /** Moves focus to the next eligible control. */
    TAB,
    /** Moves focus to the previous eligible control. */
    SHIFT_TAB,
    /** Requests upward directional traversal. */
    ARROW_UP,
    /** Requests downward directional traversal. */
    ARROW_DOWN,
    /** Requests leftward directional traversal. */
    ARROW_LEFT,
    /** Requests rightward directional traversal. */
    ARROW_RIGHT,
    /** Primary confirmation key used to activate the focused control. */
    ENTER,
    /** Non-text activation key used by buttons and other standard controls. */
    SPACE,
    /** Platform back action, also used to dismiss the top modal presentation. */
    BACK,
    /** Escape action used to dismiss the top modal presentation. */
    ESCAPE,
    /** Key that could not be mapped to a supported PixelUI action. */
    UNKNOWN,
}

/**
 * 表示 `PixelFocus` 的 `PixelKeyEvent` 稳定结果或事件分支。
 *
 * Normalized navigation, activation, or dismissal event dispatched through a focus owner.
 *
 * 该事件不携带文本；可打印输入统一由 [PixelTextInputEvent] 表达。
 *
 * @param key Navigation, activation, or dismissal category of this event.
 */
public data class PixelKeyEvent(
    /** 本事件所属的导航、激活或取消类别。 */
    val key: PixelKey,
)

/**
 * 表示 `PixelFocus` 的 `PixelTextInputEvent` 稳定结果或事件分支。
 *
 * Platform-independent text payload dispatched without narrowing Unicode input to one UTF-16 unit.
 *
 * [text] is delivered exactly as supplied: PixelUI does not normalize, split, or otherwise rewrite
 * the payload. A single event may therefore contain one supplementary-plane code point, one
 * extended grapheme cluster, or a multi-code-point IME commit.
 *
 * @param text Exact UTF-16 text committed by the input source.
 */
public data class PixelTextInputEvent(
    /** 公开 `PixelFocus` 的 `text` 配置或运行值。
 *
 * Exact, potentially multi-code-point text committed by the input source.
 */
    public val text: String,
)

/** 定义 `PixelFocusDirection` 在 `PixelFocus` 中承担的数据与行为边界。
 *
 * Direction requested from a [FocusTraversalPolicy].
 */
public enum class PixelFocusDirection {
    /** Advances to the next item in logical reading order. */
    NEXT,
    /** Moves to the previous item in logical reading order. */
    PREVIOUS,
    /** Moves to an eligible item above the current item. */
    UP,
    /** Moves to an eligible item below the current item. */
    DOWN,
    /** Moves to an eligible item left of the current item. */
    LEFT,
    /** Moves to an eligible item right of the current item. */
    RIGHT,
}

/**
 * 焦点获得时把指定滚动 item 保持在可见区域内。
 *
 * 普通 `ListView`、`GridView`、`SingleChildScrollView` 传 [itemIndex]；
 * `CustomScrollView` 的 lazy sliver 传 [sliverIndex] 和 [itemIndex]。
 */
public data class PixelFocusScrollTarget(
    /** Layout state that provides the current viewport and item geometry. */
    val state: PixelListState,
    /** Controller used to perform the visibility adjustment. */
    val controller: PixelListController,
    /** Zero-based item index within the target list or lazy sliver. */
    val itemIndex: Int,
    /** Zero-based lazy-sliver index, or `null` for a regular list. */
    val sliverIndex: Int? = null,
) {
    /** Validates indices eagerly so a later focus transition cannot issue an invalid scroll. */
    init {
        require(itemIndex >= 0) { "itemIndex must be >= 0" }
        require(sliverIndex == null || sliverIndex >= 0) { "sliverIndex must be >= 0" }
    }
}

/** 定义 `FocusTraversalPolicy` 在 `PixelFocus` 中的可替换调用契约。
 *
 * Selects the next focus node for a requested traversal direction.
 */
public fun interface FocusTraversalPolicy {
    /**
 * 执行 `PixelFocus` 的 `next` 公开行为；具体参数、返回和副作用见下文。
 *
     * Resolves one traversal request against nodes in retained reading order.
     *
     * @param nodes Nodes owned by the scope, including nodes that may currently be disabled.
     * @param current Currently focused node, or `null` before initial focus is established.
     * @param direction Logical or directional movement requested by input dispatch.
     * @return The node to focus, or `null` when movement in [direction] is not available.
     */
    public fun next(
        nodes: List<FocusNode>,
        current: FocusNode?,
        direction: PixelFocusDirection,
    ): FocusNode?
}

/** 集中提供 `PixelFocus` 的 `ReadingOrderFocusTraversalPolicy` 共享入口。
 *
 * Cyclic one-dimensional traversal in the retained reading order of enabled nodes.
 */
public object ReadingOrderFocusTraversalPolicy : FocusTraversalPolicy {
    /** Selects the adjacent enabled node and wraps at either end of the scope. */
    override fun next(
        nodes: List<FocusNode>,
        current: FocusNode?,
        direction: PixelFocusDirection,
    ): FocusNode? {
        /** Enabled candidates eligible to receive the resulting focus request. */
        val focusable = nodes.filter { it.canRequestFocus }
        if (focusable.isEmpty()) return null
        /** Position of [current] among enabled candidates, or `null` for initial traversal. */
        val currentIndex = current?.let(focusable::indexOf)?.takeIf { it >= 0 }
        if (currentIndex == null) {
            return when (direction) {
                PixelFocusDirection.PREVIOUS,
                PixelFocusDirection.LEFT,
                PixelFocusDirection.UP,
                -> focusable.last()
                PixelFocusDirection.NEXT,
                PixelFocusDirection.RIGHT,
                PixelFocusDirection.DOWN,
                -> focusable.first()
            }
        }
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

/**
 * 定义 `GridFocusTraversalPolicy` 在 `PixelFocus` 中承担的数据与行为边界。
 *
 * Row-major focus traversal policy for grids with a fixed number of [columns].
 *
 * Logical next/previous traversal wraps through the complete list. Directional left/right stops
 * at row edges, while up/down stops when the requested row does not contain a candidate.
 *
 * @param columns Requested column count; values below one are normalized to one.
 */
public class GridFocusTraversalPolicy(
    columns: Int,
) : FocusTraversalPolicy {
    /** 公开 `PixelFocus` 的 `columns` 配置或运行值。
 *
 * Effective positive number of columns used by row-major index calculations.
 */
    public val columns: Int = columns.coerceAtLeast(1)

    /** Selects the next enabled grid node for [direction], if that cell exists. */
    override fun next(
        nodes: List<FocusNode>,
        current: FocusNode?,
        direction: PixelFocusDirection,
    ): FocusNode? {
        /** Enabled candidates kept in their row-major retained order. */
        val focusable = nodes.filter { it.canRequestFocus }
        if (focusable.isEmpty()) return null
        /** Row-major position of [current], or `null` before this scope has a valid focus. */
        val currentIndex = current?.let { focusable.indexOf(it) }?.takeIf { it >= 0 }
        if (currentIndex == null) {
            return when (direction) {
                PixelFocusDirection.PREVIOUS -> focusable.last()
                else -> focusable.first()
            }
        }
        /** Row-major candidate index produced by the requested logical or spatial movement. */
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
                /** Index one grid row above the current node. */
                val next = currentIndex - columns
                if (next < 0) return null
                next
            }
            PixelFocusDirection.DOWN -> {
                /** Index one grid row below the current node. */
                val next = currentIndex + columns
                if (next >= focusable.size) return null
                next
            }
        }
        return focusable.getOrNull(targetIndex)
    }
}

/**
 * 定义 `FocusNode` 在 `PixelFocus` 中承担的数据与行为边界。
 *
 * Mutable focus handle that can be retained by application code across widget rebuilds.
 *
 * App-authored [onKeyEvent] handlers run before standard-component fallback handlers. A mounted
 * node belongs to exactly one runtime owner, which prevents focus and key dispatch from leaking
 * between multiple Hosts or test runtimes.
 *
 * @param debugLabel Optional diagnostic name used in ownership error messages.
 * @param canRequestFocus Initial caller-controlled focusability before component enabled gates.
 * @param onKeyEvent Optional application shortcut handler; return `true` to consume the event.
 */
public class FocusNode(
    /** 公开 `PixelFocus` 的 `debugLabel` 配置或运行值。
 *
 * Optional diagnostic name used in ownership error messages.
 */
    public val debugLabel: String? = null,
    canRequestFocus: Boolean = true,
    /** 保存 `PixelFocus` 在 `onKeyEvent` 时调用的事件回调。
 *
 * Application shortcut handler evaluated before standard-component fallback behavior.
 */
    public var onKeyEvent: ((PixelKeyEvent) -> Boolean)? = null,
) : ChangeNotifier() {
    /**
 * 保存 `PixelFocus` 在 `onTextInput` 时调用的事件回调。
 *
     * Application text handler receiving the exact payload committed by the input source.
     *
     * 返回 `true` 表示整段文本被消费；返回 `false` 会继续冒泡到外层 Focus 节点。文本永远
     * 不会退化成 [onKeyEvent]：两条链路语义互不重叠。
     */
    public var onTextInput: ((PixelTextInputEvent) -> Boolean)? = null

    /** Caller-requested focusability before standard-component enabled gates are applied. */
    private var requestedCanRequestFocus: Boolean = canRequestFocus

    /** Standard components currently preventing this node from participating in traversal. */
    private val focusabilityBlockOwners: MutableSet<Any> = mutableSetOf()

    /** 表示 `PixelFocus` 当前是否满足 `canRequestFocus` 对应条件。
 *
 * Whether this node may become the primary focus of its owning runtime.
 */
    public var canRequestFocus: Boolean
        get() = requestedCanRequestFocus && focusabilityBlockOwners.isEmpty()
        set(value) {
            /** Focusability before applying the caller's new preference. */
            val wasFocusable = canRequestFocus
            if (requestedCanRequestFocus == value) return
            requestedCanRequestFocus = value
            if (wasFocusable && !canRequestFocus && isFocused) scope?.handleFocusedNodeDisabled(this)
            notifyListeners()
        }

    /** 表示 `PixelFocus` 当前是否满足 `isFocused` 对应条件。
 *
 * Whether this node currently owns primary focus in its runtime.
 */
    public var isFocused: Boolean = false
        private set

    /** Scope that currently retains this node. */
    internal var scope: FocusScopeNode? = null

    /** Nearest enclosing Focus node used to bubble application shortcuts before component actions. */
    internal var parentNode: FocusNode? = null

    /** Identity of the standard component that installed [defaultKeyHandler]. */
    private var defaultKeyHandlerOwner: Any? = null

    /** 在调用方 [onKeyEvent] 之后触发的标准组件兜底处理器。 */
    private var defaultKeyHandler: ((PixelKeyEvent) -> Boolean)? = null

    /** 由本焦点节点独占代表的自动标准控件（若存在）。 */
    private var automaticControlOwner: Any? = null

    /** 执行 `PixelFocus` 的 `requestFocus` 公开行为；具体参数、返回和副作用见下文。
 *
 * Requests primary focus from the runtime that owns this node.
 *
 * 未挂载到任何 scope 的节点没有 runtime 归属，直接返回 `false`，不会影响其它 runtime。
 */
    public fun requestFocus(): Boolean {
        /** 用于把请求路由到正确 runtime owner 的已挂载 scope。 */
        val attachedScope = scope ?: return false
        return attachedScope.requestFocus(this)
    }

    /** 执行 `PixelFocus` 的 `unfocus` 公开行为；具体参数、返回和副作用见下文。
 *
 * Releases focus without affecting another runtime or an unrelated node.
 *
 * 未挂载节点没有可释放的焦点状态，此调用是安全的空操作。
 */
    public fun unfocus() {
        scope?.clearFocus(this)
    }

    /** Updates the observable focus bit after an owner-level transition. */
    internal fun setFocused(focused: Boolean) {
        if (isFocused == focused) return
        isFocused = focused
        notifyListeners()
    }

    /** Installs one retained standard-component fallback without replacing app shortcuts. */
    /** 供标准组件层绑定默认按键处理器的 artifact 内部 SPI。 */
    @PixelArtifactInternalApi
    public fun bindDefaultKeyHandler(owner: Any, handler: (PixelKeyEvent) -> Boolean) {
        defaultKeyHandlerOwner = owner
        defaultKeyHandler = handler
    }

    /** Removes the fallback only when [owner] still owns the current binding. */
    /** 解除标准组件层此前绑定的默认按键处理器。 */
    @PixelArtifactInternalApi
    public fun unbindDefaultKeyHandler(owner: Any) {
        if (defaultKeyHandlerOwner !== owner) return
        defaultKeyHandlerOwner = null
        defaultKeyHandler = null
    }

    /** Returns whether [owner] may exclusively represent one automatic control with this node. */
    /** 判断一个组件 owner 是否可以接管自动焦点控制。 */
    @PixelArtifactInternalApi
    public fun canClaimAutomaticControl(owner: Any): Boolean {
        return automaticControlOwner == null || automaticControlOwner === owner
    }

    /** Claims this node for exactly one automatic control to prevent descendant action collisions. */
    /** 由标准组件层声明自动焦点控制所有权。 */
    @PixelArtifactInternalApi
    public fun claimAutomaticControl(owner: Any) {
        check(canClaimAutomaticControl(owner)) {
            "FocusNode ${debugLabel ?: "<unnamed>"} is already bound to another automatic control"
        }
        automaticControlOwner = owner
    }

    /** Releases the automatic-control claim only when [owner] still owns it. */
    /** 释放标准组件层的自动焦点控制所有权。 */
    @PixelArtifactInternalApi
    public fun releaseAutomaticControl(owner: Any) {
        if (automaticControlOwner === owner) automaticControlOwner = null
    }

    /** Applies one component enabled gate without overwriting the caller's focusability choice. */
    /** 绑定一个标准组件对节点可聚焦状态的门控。 */
    @PixelArtifactInternalApi
    public fun bindFocusability(owner: Any, enabled: Boolean) {
        /** Effective focusability before updating this component's gate. */
        val wasFocusable = canRequestFocus
        /** Whether the set of components blocking focusability actually changed. */
        val changed = if (enabled) focusabilityBlockOwners.remove(owner) else focusabilityBlockOwners.add(owner)
        if (!changed) return
        if (wasFocusable && !canRequestFocus && isFocused) scope?.handleFocusedNodeDisabled(this)
        notifyListeners()
    }

    /** Removes a component enabled gate when its retained wrapper leaves this node. */
    /** 移除一个标准组件此前注册的可聚焦状态门控。 */
    @PixelArtifactInternalApi
    public fun unbindFocusability(owner: Any) {
        if (!focusabilityBlockOwners.remove(owner)) return
        notifyListeners()
    }

    /** Dispatches only the application-authored shortcut phase for owner-level bubbling. */
    internal fun dispatchAppKeyEvent(event: PixelKeyEvent): Boolean = onKeyEvent?.invoke(event) == true

    /** Dispatches only the application-authored text phase for owner-level bubbling. */
    internal fun dispatchAppTextInput(event: PixelTextInputEvent): Boolean = onTextInput?.invoke(event) == true

    /** Dispatches only the standard component fallback after every ancestor shortcut declined. */
    internal fun dispatchDefaultKeyEvent(event: PixelKeyEvent): Boolean = defaultKeyHandler?.invoke(event) == true
}

/**
 * 定义 `FocusScopeNode` 在 `PixelFocus` 中承担的数据与行为边界。
 *
 * Retained traversal scope that owns an ordered set of directly mounted [FocusNode] instances.
 *
 * A scope binds to exactly one runtime owner while mounted. Nested scope ancestry is also used to
 * trap focus inside the top modal and to restore focus to an opener when that modal closes.
 *
 * @param traversalPolicy Policy used for directional traversal among directly attached nodes.
 */
public class FocusScopeNode(
    /** 公开 `PixelFocus` 的 `traversalPolicy` 配置或运行值。
 *
 * Policy used for directional traversal among directly attached nodes.
 */
    public var traversalPolicy: FocusTraversalPolicy = ReadingOrderFocusTraversalPolicy,
) : ChangeNotifier() {
    /** Nodes mounted directly below this scope in retained reading order. */
    private val nodes = mutableListOf<FocusNode>()

    /** Runtime owner responsible for the one primary focus shared by all nested scopes. */
    internal var owner: PixelFocusOwner? = null
        private set

    /** Parent scope used to prove modal ancestry and restore an opener fallback. */
    internal var parentScope: FocusScopeNode? = null
        private set

    /** Owner whose unbind is deferred until every descendant Focus node detaches. */
    private var pendingUnbindOwner: PixelFocusOwner? = null

    /** Whether a logically closed retained modal forbids focus throughout this scope subtree. */
    private var focusBlocked: Boolean = false

    /** Nearest hosted overlay presentation owned directly by this scope, when applicable. */
    private var overlayFocusOrder: PixelOverlayFocusOrder? = null

    /** 公开 `PixelFocus` 的 `focusedChild` 配置或运行值。
 *
 * Last directly focused child retained for scope-local restoration.
 */
    public val focusedChild: FocusNode?
        get() = nodes.lastOrNull { it.isFocused }

    /** 向 `PixelFocus` 注册 `attach` 内容并绑定对应生命周期。
 *
 * Attaches one node, safely removing a previous same-runtime scope registration.
 */
    public fun attach(node: FocusNode) {
        if (node in nodes) return
        /** Scope that retained [node] before this attachment, if any. */
        val previousScope = node.scope
        if (previousScope != null && previousScope !== this) {
            /** 持有上一次挂载关系的 runtime。 */
            val previousOwner = previousScope.owner
            /** 本次挂载完成后将持有 [node] 的 runtime。 */
            val nextOwner = owner
            check(previousOwner == null || nextOwner != null) {
                "A mounted FocusNode cannot be moved into a scope without a PixelUiRuntime owner"
            }
            check(previousOwner == null || nextOwner == null || previousOwner === nextOwner) {
                "FocusNode ${node.debugLabel ?: "<unnamed>"} cannot be mounted in two PixelUiRuntime owners"
            }
            previousScope.detach(node)
        }
        node.scope = this
        nodes += node
        owner?.handleNodeAttached(node, this)
        notifyListeners()
    }

    /** 从 `PixelFocus` 释放 `detach` 内容并收敛相关所有权。
 *
 * Detaches one node and clears only this scope owner's primary focus when needed.
 */
    public fun detach(node: FocusNode) {
        if (nodes.remove(node)) {
            if (node.isFocused) {
                node.setFocused(false)
                owner?.clearFocus(node)
            }
            // 清空 scope 后节点即为未挂载状态，requestFocus 会安全返回 false。
            if (node.scope === this) node.scope = null
            completePendingUnbindIfEmpty()
            notifyListeners()
        }
    }

    /** 执行 `PixelFocus` 的 `requestFocus` 公开行为；具体参数、返回和副作用见下文。
 *
 * Requests focus through the scope's runtime owner.
 *
 * 未绑定 runtime 的 scope 无法产生 primary focus，直接返回 `false`。
 */
    public fun requestFocus(node: FocusNode): Boolean {
        if (!node.canRequestFocus || hasBlockedAncestor()) return false
        /** 承载本 scope primary focus 所必需的 runtime owner。 */
        val focusOwner = owner ?: return false
        if (node !in nodes) attach(node)
        /** runtime owner 是否接受 [node] 成为新的 primary focus。 */
        val focused = focusOwner.requestFocus(node, this)
        if (focused) notifyListeners()
        return focused
    }

    /** 从 `PixelFocus` 释放 `clearFocus` 内容并收敛相关所有权。
 *
 * Clears either one node or every directly attached node.
 */
    public fun clearFocus(node: FocusNode? = null) {
        /** 需要清除可观察聚焦状态的直接子节点集合。 */
        val targets = if (node == null) nodes.toList() else listOf(node)
        targets.filter { it.isFocused }.forEach { it.setFocused(false) }
        /** 本 scope 若已绑定 runtime owner，则需要同步更新其 primary-focus 引用。 */
        val focusOwner = owner
        if (focusOwner != null) {
            if (node != null) {
                focusOwner.clearFocus(node)
            } else if (focusOwner.primaryFocus?.scope?.isDescendantOf(this) == true) {
                focusOwner.clearFocus()
            }
        }
        notifyListeners()
    }

    /** 执行 `PixelFocus` 的 `focusInDirection` 公开行为；具体参数、返回和副作用见下文。
 *
 * Moves focus according to the current traversal policy.
 */
    public fun focusInDirection(direction: PixelFocusDirection): Boolean {
        /** Enabled direct child selected by [traversalPolicy]. */
        val next = traversalPolicy.next(nodes = nodes.toList(), current = focusedChild, direction = direction)
            ?: return false
        return requestFocus(next)
    }

    /** Binds this scope to one runtime and records its scope-tree parent. */
    internal fun bindOwner(owner: PixelFocusOwner, parentScope: FocusScopeNode?) {
        /** Runtime currently retaining this scope before the requested binding. */
        val previousOwner = this.owner
        check(previousOwner == null || previousOwner === owner) {
            "FocusScopeNode cannot be mounted in two PixelUiRuntime owners"
        }
        this.owner = owner
        this.parentScope = parentScope?.takeUnless { it === this }
        pendingUnbindOwner = null
        owner.registerScope(this)
    }

    /** Releases runtime ownership after the retained scope leaves the tree. */
    internal fun unbindOwner(expectedOwner: PixelFocusOwner) {
        if (owner !== expectedOwner) return
        if (nodes.isNotEmpty()) {
            pendingUnbindOwner = expectedOwner
            return
        }
        expectedOwner.unregisterScope(this)
        owner = null
        parentScope = null
        pendingUnbindOwner = null
    }

    /** Returns whether this scope is [ancestor] or is nested below it. */
    internal fun isDescendantOf(ancestor: FocusScopeNode): Boolean {
        /** Scope currently being compared while walking toward the runtime root. */
        var candidate: FocusScopeNode? = this
        while (candidate != null) {
            if (candidate === ancestor) return true
            candidate = candidate.parentScope
        }
        return false
    }

    /** Returns the first enabled direct child for autofocus and restoration. */
    internal fun firstFocusableNode(): FocusNode? = nodes.firstOrNull(FocusNode::canRequestFocus)

    /** Returns the last enabled direct child for reverse initial traversal. */
    internal fun lastFocusableNode(): FocusNode? = nodes.lastOrNull(FocusNode::canRequestFocus)

    /** Returns directly attached nodes in retained traversal order. */
    internal fun focusableNodes(): List<FocusNode> = nodes.filter(FocusNode::canRequestFocus)

    /** Blocks or re-enables this retained scope at a logical modal lifecycle boundary. */
    /** 跨 artifact 切换 retained 子树的焦点隔离状态。 */
    @PixelArtifactInternalApi
    public fun setFocusBlocked(blocked: Boolean) {
        focusBlocked = blocked
        if (blocked && owner?.primaryFocus?.scope?.isDescendantOf(this) == true) owner?.clearFocus()
    }

    /** 返回当前 runtime 的 primary focus 是否位于本 scope 子树。 */
    @PixelArtifactInternalApi
    public fun containsPrimaryFocus(): Boolean {
        /** 当前 scope 所属 runtime 的 primary focus scope。 */
        val focusedScope = owner?.primaryFocus?.scope ?: return false
        return focusedScope.isDescendantOf(this)
    }

    /** Updates the canonical hosted-overlay order represented directly by this scope. */
    internal fun setOverlayFocusOrder(order: PixelOverlayFocusOrder?) {
        overlayFocusOrder = order
    }

    /** Returns the nearest hosted-overlay order inherited by this scope subtree. */
    internal fun inheritedOverlayFocusOrder(): PixelOverlayFocusOrder? {
        /** Scope inspected while walking from the nearest presentation toward the runtime root. */
        var candidate: FocusScopeNode? = this
        while (candidate != null) {
            candidate.overlayFocusOrder?.let { order -> return order }
            candidate = candidate.parentScope
        }
        return null
    }

    /** Returns true when this scope or any parent is a logically closed retained modal. */
    internal fun hasBlockedAncestor(): Boolean {
        /** Scope currently inspected for a logical modal block. */
        var candidate: FocusScopeNode? = this
        while (candidate != null) {
            if (candidate.focusBlocked) return true
            candidate = candidate.parentScope
        }
        return false
    }

    /** Moves away from a focused node that became disabled. */
    internal fun handleFocusedNodeDisabled(node: FocusNode) {
        if (node !in nodes) return
        /** Former retained position used to find the next enabled sibling cyclically. */
        val startIndex = nodes.indexOf(node)
        node.setFocused(false)
        owner?.clearFocus(node)
        /** First enabled sibling after [node], excluding the node that became disabled. */
        val replacement = (1 until nodes.size)
            .asSequence()
            .map { offset -> nodes[(startIndex + offset).floorMod(nodes.size)] }
            .firstOrNull(FocusNode::canRequestFocus)
        if (replacement != null) requestFocus(replacement)
    }

    /** Completes a deferred scope-owner release after its last node has detached. */
    private fun completePendingUnbindIfEmpty() {
        /** Runtime waiting for the final direct node to leave this scope. */
        val expectedOwner = pendingUnbindOwner ?: return
        if (nodes.isNotEmpty() || owner !== expectedOwner) return
        expectedOwner.unregisterScope(this)
        owner = null
        parentScope = null
        pendingUnbindOwner = null
    }
}

/**
 * Per-runtime focus state shared by one Host or one off-screen [com.purride.pixelui.testing.PixelTester].
 *
 * This owner is intentionally internal. Public callers keep using [FocusNode], [FocusScopeNode],
 * a concrete `PixelHostView`, or `PixelTester` instead of selecting a process-global active Host.
 */
internal class PixelFocusOwner(
    /** Whether a modal without explicit autofocus provisionally selects its first mounted child. */
    private val automaticallyFocusModalDescendants: Boolean = true,
) : PixelFocusDispatcher {
    /** Root traversal scope unique to this runtime. */
    val rootScope: FocusScopeNode = FocusScopeNode()

    /** Primary focus unique to this runtime. */
    var primaryFocus: FocusNode? = null
        private set

    /** Whether terminal runtime disposal has rejected future input. */
    private var disposed: Boolean = false

    /** Monotonic token source for modal activations in this runtime. */
    private var nextModalToken: Long = 1L

    /** Monotonic token source for hosted-overlay focus-layer registrations. */
    private var nextOverlayFocusToken: Long = 1L

    /** Monotonic token source for Host-level normalized dismiss-key handlers. */
    private var nextDismissKeyHandlerToken: Long = 1L

    /** Active modal scopes in logical presentation order. */
    private val modalStack: MutableList<PixelModalFocusEntry> = mutableListOf()

    /** Active hosted-overlay scopes used for order-aware eligibility and opener restoration. */
    private val overlayFocusLayers: MutableList<PixelOverlayFocusEntry> = mutableListOf()

    /** Mounted Host handlers receiving Escape/Back before background focus-node shortcuts. */
    private val dismissKeyHandlers: MutableList<PixelDismissKeyHandlerEntry> = mutableListOf()

    /** Mounted scopes in retained build order, including the runtime root. */
    private val mountedScopes: MutableList<FocusScopeNode> = mutableListOf()

    /** Binds the root scope immediately so every later attachment has an owner anchor. */
    init {
        rootScope.bindOwner(this, parentScope = null)
    }

    /** Requests focus while rejecting nodes owned by another runtime. */
    fun requestFocus(node: FocusNode, scope: FocusScopeNode = node.scope ?: rootScope): Boolean {
        if (disposed || !node.canRequestFocus || scope.hasBlockedAncestor()) return false
        check(scope.owner == null || scope.owner === this) {
            "FocusNode ${node.debugLabel ?: "<unnamed>"} belongs to another PixelUiRuntime"
        }
        if (scope.owner == null) scope.bindOwner(this, rootScope)
        /** Topmost modal entry that restricts background scopes but permits higher overlay routes. */
        val topModal = modalStack.lastOrNull()
        if (topModal != null && !scopeAllowedByModal(scope, topModal)) return false
        if (node.scope !== scope) scope.attach(node)
        primaryFocus?.takeUnless { it === node }?.setFocused(false)
        primaryFocus = node
        node.setFocused(true)
        markOverlayFocusOwned(scope)
        return true
    }

    /** Clears this runtime's primary focus without touching any sibling runtime. */
    fun clearFocus(node: FocusNode? = null) {
        if (node != null && primaryFocus !== node) return
        primaryFocus?.setFocused(false)
        primaryFocus = null
    }

    /** Dispatches one normalized key through app, component, then traversal priority. */
    override fun dispatchKeyEvent(event: PixelKeyEvent): Boolean {
        if (disposed) return false
        /** Topmost logical modal, which receives dismissal keys and traps traversal. */
        val topModal = modalStack.lastOrNull()
        if (topModal != null && (event.key == PixelKey.ESCAPE || event.key == PixelKey.BACK)) {
            /** Route callback, when present, owns the normalized dismissal request. */
            val dismissRequest = topModal.onDismissRequest
            if (dismissRequest != null) {
                dismissRequest.invoke()
                return true
            }
            /** 未提供 dismiss 回调的独立 modal 表面按声明继续拦截关闭键。 */
            if (topModal.consumeUnhandledDismissRequest) return true
        }
        if (event.key == PixelKey.ESCAPE || event.key == PixelKey.BACK) {
            /** Stable top-to-bottom snapshot tolerating route removal during a dismiss callback. */
            val dismissSnapshot = dismissKeyHandlers.toList().asReversed()
            for (entry in dismissSnapshot) {
                /** A callback removed by an earlier reentrant handler must not receive this key. */
                val remainsRegistered = dismissKeyHandlers.any { candidate ->
                    candidate.token == entry.token
                }
                if (remainsRegistered && entry.handler()) return true
            }
        }
        /** Node at which app and standard-control key bubbling begins. */
        val focused = primaryFocus
        /** Focused-to-root node chain used for shortcut-first event bubbling. */
        val focusChain = focusedNodeChain()
        if (focusChain.any { node -> node.dispatchAppKeyEvent(event) }) return true
        if (focusChain.any { node -> node.dispatchDefaultKeyEvent(event) }) return true
        /** [event] 对应的遍历方向；非导航输入时为 `null`。 */
        val direction = when (event.key) {
            PixelKey.TAB -> PixelFocusDirection.NEXT
            PixelKey.SHIFT_TAB -> PixelFocusDirection.PREVIOUS
            PixelKey.ARROW_UP -> PixelFocusDirection.UP
            PixelKey.ARROW_DOWN -> PixelFocusDirection.DOWN
            PixelKey.ARROW_LEFT -> PixelFocusDirection.LEFT
            PixelKey.ARROW_RIGHT -> PixelFocusDirection.RIGHT
            else -> null
        }
        if (direction == null) return false
        if (event.key == PixelKey.TAB || event.key == PixelKey.SHIFT_TAB) {
            return focusAcrossScopes(direction) || topModal != null
        }
        if (focused == null) return focusInitial(direction) || topModal != null
        /** 方向遍历是否选中并聚焦了一个兄弟节点。 */
        val moved = (focused.scope ?: rootScope).focusInDirection(direction)
        return moved || topModal != null
    }

    /**
     * Dispatches exact text through focused-to-root handlers in bubbling order.
     *
     * 文本永远保持一次事件：supplementary code point、组合簇和多 code point 的 IME 提交都不会被
     * 拆分，也不会退化到 [dispatchKeyEvent]。没有节点消费时返回 `false`。
     */
    override fun dispatchTextInputEvent(event: PixelTextInputEvent): Boolean {
        if (disposed) return false
        /** 按冒泡顺序接收精确 String 载荷的“聚焦节点到根”链。 */
        val focusChain = focusedNodeChain()
        return focusChain.any { node -> node.dispatchAppTextInput(event) }
    }

    /** Builds the focused-to-root node chain while defending against malformed ancestry cycles. */
    private fun focusedNodeChain(): List<FocusNode> {
        return buildList {
            /** Identity set preventing malformed explicit-node ancestry from creating a dispatch loop. */
            val visited = mutableSetOf<FocusNode>()
            /** Current focus ancestor appended while it remains non-null and previously unseen. */
            var candidate = primaryFocus
            while (candidate != null && visited.add(candidate)) {
                add(candidate)
                candidate = candidate.parentNode
            }
        }
    }

    /** Releases only this runtime's focus state at its terminal lifecycle boundary. */
    fun dispose() {
        if (disposed) return
        disposed = true
        modalStack.clear()
        overlayFocusLayers.clear()
        dismissKeyHandlers.clear()
        mountedScopes.clear()
        clearFocus()
    }

    /** Registers one Host-level normalized dismiss handler in retained mount order. */
    internal fun registerDismissKeyHandler(handler: () -> Boolean): PixelDismissKeyHandlerToken {
        check(!disposed) { "Cannot register a dismiss key handler on a disposed PixelUiRuntime" }
        /** Runtime-local token returned to the exact retained widget State for disposal. */
        val token = PixelDismissKeyHandlerToken(nextDismissKeyHandlerToken++)
        dismissKeyHandlers += PixelDismissKeyHandlerEntry(token = token, handler = handler)
        return token
    }

    /** Removes the exact Host-level normalized dismiss handler represented by [token]. */
    internal fun unregisterDismissKeyHandler(token: PixelDismissKeyHandlerToken) {
        dismissKeyHandlers.removeAll { entry -> entry.token == token }
    }

    /** Registers one mounted scope exactly once in retained build order. */
    internal fun registerScope(scope: FocusScopeNode) {
        if (scope !in mountedScopes) mountedScopes += scope
    }

    /** Removes one empty scope after its retained boundary unmounts. */
    internal fun unregisterScope(scope: FocusScopeNode) {
        if (scope !== rootScope) mountedScopes.remove(scope)
    }

    /** Registers one hosted overlay route without granting it modal-trap semantics. */
    internal fun activateOverlayFocusLayer(
        scope: FocusScopeNode,
        parentScope: FocusScopeNode,
        order: PixelOverlayFocusOrder,
    ): PixelOverlayFocusToken {
        check(!disposed) { "Cannot activate an overlay focus layer on a disposed PixelUiRuntime" }
        scope.bindOwner(this, parentScope)
        scope.setFocusBlocked(false)
        scope.setOverlayFocusOrder(order)
        /** Existing registration retained by the same keyed presentation across rebuilds. */
        val existingIndex = overlayFocusLayers.indexOfFirst { entry -> entry.scope === scope }
        if (existingIndex >= 0) {
            /** Existing layer whose opener and ownership history must remain stable. */
            val existing = overlayFocusLayers[existingIndex]
            overlayFocusLayers[existingIndex] = existing.copy(order = order)
            return existing.token
        }
        /** New layer capturing the exact focus visible before this route mounted. */
        val entry = PixelOverlayFocusEntry(
            token = PixelOverlayFocusToken(nextOverlayFocusToken++),
            scope = scope,
            order = order,
            opener = primaryFocus,
            openerScope = primaryFocus?.scope,
        )
        overlayFocusLayers += entry
        return entry.token
    }

    /** Refreshes a retained route's stable canonical layer/insertion tuple after a rebuild. */
    internal fun updateOverlayFocusLayer(
        token: PixelOverlayFocusToken,
        order: PixelOverlayFocusOrder,
    ) {
        /** Exact registered layer whose order follows the host's canonical presentation list. */
        val index = overlayFocusLayers.indexOfFirst { entry -> entry.token == token }
        if (index < 0) return
        /** Registered layer retaining opener and focus-ownership state across this update. */
        val existing = overlayFocusLayers[index]
        existing.scope.setOverlayFocusOrder(order)
        overlayFocusLayers[index] = existing.copy(order = order)
    }

    /** Removes one non-trapping overlay layer and restores focus only when it had owned primary. */
    internal fun deactivateOverlayFocusLayer(token: PixelOverlayFocusToken) {
        /** Exact layer registration being removed from this runtime. */
        val index = overlayFocusLayers.indexOfFirst { entry -> entry.token == token }
        if (index < 0) return
        /** Removed registration containing opener and focus-ownership evidence. */
        val entry = overlayFocusLayers.removeAt(index)
        /** Whether focus was still inside this route before its scope became logically unavailable. */
        val focusedInsideLayer = primaryFocus?.scope?.isDescendantOf(entry.scope) == true
        // Preserve restoration through a higher route whose opener depended on this removed route.
        for (higherIndex in overlayFocusLayers.indices) {
            /** Remaining route whose captured opener may point into the removed presentation. */
            val higher = overlayFocusLayers[higherIndex]
            if (higher.openerScope?.isDescendantOf(entry.scope) == true) {
                overlayFocusLayers[higherIndex] = higher.copy(
                    opener = entry.opener,
                    openerScope = entry.openerScope,
                )
            }
        }
        entry.scope.setOverlayFocusOrder(null)
        entry.scope.setFocusBlocked(true)
        if (!entry.ownsPrimary) return
        /** Focus outside the removed route proves another eligible owner already took control. */
        if (!focusedInsideLayer && primaryFocus != null) return
        if (primaryFocus != null) clearFocus()
        if (restoreOverlayOpener(entry)) return
        /** Active modal that remains after this higher route closed, when one exists. */
        val activeModal = modalStack.lastOrNull()
        activeModal?.scope?.let { modalScope ->
            firstFocusableDescendant(modalScope)?.let { node ->
                requestFocus(node, checkNotNull(node.scope))
            }
        }
    }

    /** Marks the nearest registered overlay route as having successfully owned primary focus. */
    private fun markOverlayFocusOwned(scope: FocusScopeNode) {
        /** Nearest presentation scope enclosing the focused node, favoring nested overlay hosts. */
        val index = overlayFocusLayers.indexOfLast { entry -> scope.isDescendantOf(entry.scope) }
        if (index < 0 || overlayFocusLayers[index].ownsPrimary) return
        overlayFocusLayers[index] = overlayFocusLayers[index].copy(ownsPrimary = true)
    }

    /** Claims the first explicit autofocus candidate within one registered overlay route. */
    private fun claimOverlayAutofocus(scope: FocusScopeNode): Boolean {
        /** Nearest presentation scope enclosing this autofocus candidate. */
        val index = overlayFocusLayers.indexOfLast { entry -> scope.isDescendantOf(entry.scope) }
        if (index < 0) return false
        /** Layer whose first explicit autofocus candidate wins across descendant rebuilds. */
        val entry = overlayFocusLayers[index]
        if (entry.autofocusChosen) return false
        overlayFocusLayers[index] = entry.copy(autofocusChosen = true)
        return true
    }

    /** Restores the exact overlay opener or the first valid descendant of its retained scope. */
    private fun restoreOverlayOpener(entry: PixelOverlayFocusEntry): Boolean {
        /** Exact node focused immediately before this presentation registered. */
        val opener = entry.opener
        /** Current mounted scope for [opener], which may have changed after a rebuild. */
        val openerScope = opener?.scope
        if (
            opener != null &&
            opener.canRequestFocus &&
            openerScope?.owner === this &&
            scopeAllowedByCurrentModal(openerScope)
        ) {
            return requestFocus(opener, openerScope)
        }
        /** Original opener scope retained for deterministic fallback selection. */
        val fallbackScope = entry.openerScope
        /** First enabled mounted descendant of the original opener scope. */
        val fallback = fallbackScope?.let(::firstFocusableDescendant)
        if (
            fallback != null &&
            fallbackScope.owner === this &&
            scopeAllowedByCurrentModal(fallbackScope)
        ) {
            return requestFocus(fallback, checkNotNull(fallback.scope))
        }
        return false
    }

    /** Reports whether one scope remains eligible under the runtime's current top modal. */
    private fun scopeAllowedByCurrentModal(scope: FocusScopeNode): Boolean {
        /** Current modal restriction, or no restriction when the stack is empty. */
        val topModal = modalStack.lastOrNull() ?: return true
        return scopeAllowedByModal(scope, topModal)
    }

    /** Allows modal descendants plus routes canonically above that modal in the same host. */
    private fun scopeAllowedByModal(
        scope: FocusScopeNode,
        modal: PixelModalFocusEntry,
    ): Boolean = scopeAllowedByModalScope(scope, modal.scope)

    /** Compares one candidate scope against a modal scope and their inherited route orders. */
    private fun scopeAllowedByModalScope(
        scope: FocusScopeNode,
        modalScope: FocusScopeNode,
    ): Boolean {
        if (scope.isDescendantOf(modalScope)) return true
        /** Hosted route containing the requested focus target, when one exists. */
        val sourceOrder = scope.inheritedOverlayFocusOrder() ?: return false
        /** Hosted route containing the modal owner; standalone modals intentionally have no bypass. */
        val modalOrder = modalScope.inheritedOverlayFocusOrder() ?: return false
        return sourceOrder.isAbove(modalOrder)
    }

    /**
     * Activates one modal scope, captures its opener, and prevents background focus requests.
     */
    internal fun activateModal(
        scope: FocusScopeNode,
        parentScope: FocusScopeNode,
        onDismissRequest: (() -> Unit)?,
        consumeUnhandledDismissRequest: Boolean,
    ): PixelModalFocusToken {
        check(!disposed) { "Cannot activate a modal on a disposed PixelUiRuntime" }
        scope.bindOwner(this, parentScope)
        scope.setFocusBlocked(false)
        /** Existing activation for the same retained modal scope, if already registered. */
        val existingIndex = modalStack.indexOfFirst { entry -> entry.scope === scope }
        if (existingIndex >= 0) {
            /** Existing entry whose identity and opener must remain stable across rebuilds. */
            val existing = modalStack[existingIndex]
            modalStack[existingIndex] = existing.copy(
                onDismissRequest = onDismissRequest,
                consumeUnhandledDismissRequest = consumeUnhandledDismissRequest,
            )
            return existing.token
        }
        /** New modal-stack entry capturing focus state immediately before activation. */
        val entry = PixelModalFocusEntry(
            token = PixelModalFocusToken(nextModalToken++),
            scope = scope,
            opener = primaryFocus,
            openerScope = primaryFocus?.scope,
            onDismissRequest = onDismissRequest,
            consumeUnhandledDismissRequest = consumeUnhandledDismissRequest,
        )
        modalStack += entry
        /** Scope containing the pre-modal primary focus, when one existed. */
        val focusedScope = primaryFocus?.scope
        /** Whether canonical overlay order permits the existing focus to remain above this modal. */
        val existingFocusAllowed = focusedScope?.let { candidate ->
            scopeAllowedByModalScope(candidate, scope)
        } == true
        if (!existingFocusAllowed) {
            if (focusedScope != null) clearFocus()
            scope.firstFocusableNode()?.let { requestFocus(it, scope) }
        }
        return entry.token
    }

    /** Updates the current dismiss callback without changing modal order or opener state. */
    internal fun updateModal(
        token: PixelModalFocusToken,
        onDismissRequest: (() -> Unit)?,
        consumeUnhandledDismissRequest: Boolean,
    ) {
        /** Position of the exact activation whose callback should be refreshed. */
        val index = modalStack.indexOfFirst { entry -> entry.token == token }
        if (index < 0) return
        modalStack[index] = modalStack[index].copy(
            onDismissRequest = onDismissRequest,
            consumeUnhandledDismissRequest = consumeUnhandledDismissRequest,
        )
    }

    /**
     * Deactivates one modal immediately and restores its opener only when it was topmost.
     */
    internal fun deactivateModal(token: PixelModalFocusToken) {
        /** Position of the exact activation being removed from the modal stack. */
        val index = modalStack.indexOfFirst { entry -> entry.token == token }
        if (index < 0) return
        /** Whether removal should immediately perform opener restoration. */
        val wasTop = index == modalStack.lastIndex
        /** Removed activation containing the opener and fallback restoration state. */
        val entry = modalStack.removeAt(index)
        entry.scope.setFocusBlocked(true)
        // Rewrite higher modal openers that pointed into the removed modal subtree.
        for (upperIndex in index..modalStack.lastIndex) {
            /** Higher modal entry whose opener may depend on the removed scope. */
            val upper = modalStack[upperIndex]
            if (upper.openerScope?.isDescendantOf(entry.scope) == true) {
                modalStack[upperIndex] = upper.copy(
                    opener = entry.opener,
                    openerScope = entry.openerScope,
                )
            }
        }
        if (!wasTop) return
        /** Scope containing primary focus after the modal-stack removal. */
        val focusedScope = primaryFocus?.scope
        /** Modal now responsible for focus trapping, if another presentation remains. */
        val nextTop = modalStack.lastOrNull()
        if (focusedScope != null) {
            if (focusedScope.isDescendantOf(entry.scope)) {
                clearFocus()
            } else if (nextTop == null || scopeAllowedByModal(focusedScope, nextTop)) {
                // A canonically higher route already owns valid focus and must not be overwritten.
                return
            } else {
                clearFocus()
            }
        }
        /** Exact node that held focus immediately before the removed modal activated. */
        val opener = entry.opener
        /** Current scope of [opener], which may differ after retained-tree changes. */
        val openerScope = opener?.scope
        if (
            opener != null &&
            opener.canRequestFocus &&
            openerScope?.owner === this &&
            (nextTop == null || scopeAllowedByModal(openerScope, nextTop))
        ) {
            requestFocus(opener, openerScope)
            return
        }
        /** Original opener scope used when the exact opener is no longer eligible. */
        val fallbackScope = entry.openerScope
        /** First enabled descendant of [fallbackScope], if one is still mounted. */
        val fallback = fallbackScope?.let(::firstFocusableDescendant)
        if (
            fallback != null &&
            fallbackScope.owner === this &&
            (nextTop == null || scopeAllowedByModal(checkNotNull(fallback.scope), nextTop))
        ) {
            requestFocus(fallback, checkNotNull(fallback.scope))
            return
        }
        nextTop?.scope?.let { scope ->
            firstFocusableDescendant(scope)?.let { node -> requestFocus(node, checkNotNull(node.scope)) }
        }
    }

    /** Gives the first enabled descendant initial focus while an active modal is empty. */
    internal fun handleNodeAttached(node: FocusNode, scope: FocusScopeNode) {
        if (
            !automaticallyFocusModalDescendants ||
            disposed ||
            primaryFocus != null ||
            !node.canRequestFocus
        ) {
            return
        }
        /** Active modal waiting for its first focusable descendant to mount. */
        val topModal = modalStack.lastOrNull() ?: return
        if (scope.isDescendantOf(topModal.scope)) requestFocus(node, scope)
    }

    /** Gives the first autofocus descendant priority over a provisional modal first child. */
    internal fun requestAutofocus(node: FocusNode, scope: FocusScopeNode): Boolean {
        /** Index of the active modal that may accept its first explicit autofocus node. */
        val topIndex = modalStack.lastIndex
        if (topIndex >= 0) {
            /** Active modal entry whose autofocus choice is retained across descendant rebuilds. */
            val top = modalStack[topIndex]
            /** Existing focus scope that may belong to a canonically higher non-modal route. */
            val focusedScope = primaryFocus?.scope
            /** Whether a higher presentation must retain focus while this modal mounts or rebuilds. */
            val higherRouteOwnsFocus = focusedScope != null &&
                !focusedScope.isDescendantOf(top.scope) &&
                scopeAllowedByModal(focusedScope, top)
            if (scope.isDescendantOf(top.scope) && !top.autofocusChosen && !higherRouteOwnsFocus) {
                modalStack[topIndex] = top.copy(autofocusChosen = true)
                return requestFocus(node, scope)
            }
            /** A higher hosted route may claim autofocus without becoming another modal trap. */
            if (
                !scope.isDescendantOf(top.scope) &&
                scopeAllowedByModal(scope, top) &&
                claimOverlayAutofocus(scope)
            ) {
                return requestFocus(node, scope)
            }
            return false
        }
        /** Explicit autofocus in a hosted non-modal route may take focus but does not trap traversal. */
        if (claimOverlayAutofocus(scope)) return requestFocus(node, scope)
        return primaryFocus == null && requestFocus(node, scope)
    }

    /** Selects the first or last enabled node when no primary focus exists yet. */
    private fun focusInitial(direction: PixelFocusDirection): Boolean {
        /** Mounted scopes allowed by modal trapping and logical-close blocks. */
        val eligibleScopes = eligibleScopesForTraversal()
        /** Whether initial focus should start from the end of retained order. */
        val reverse = direction == PixelFocusDirection.PREVIOUS ||
            direction == PixelFocusDirection.LEFT ||
            direction == PixelFocusDirection.UP
        /** Eligible scopes ordered in the direction of the initial request. */
        val orderedScopes = if (reverse) eligibleScopes.asReversed() else eligibleScopes
        for (scope in orderedScopes) {
            /** First enabled node in this scope for the requested direction. */
            val candidate = if (reverse) scope.lastFocusableNode() else scope.firstFocusableNode()
            if (candidate != null) return requestFocus(candidate, scope)
        }
        return false
    }

    /** Traverses every eligible scope for Tab/Shift+Tab while keeping modal focus trapped. */
    private fun focusAcrossScopes(direction: PixelFocusDirection): Boolean {
        /** Distinct enabled nodes flattened in retained scope and node order. */
        val nodes = eligibleScopesForTraversal()
            .flatMap(FocusScopeNode::focusableNodes)
            .distinct()
        if (nodes.isEmpty()) return false
        /** Current position in the flattened traversal list, or `null` for initial traversal. */
        val currentIndex = primaryFocus?.let(nodes::indexOf)?.takeIf { it >= 0 }
        /** Whether traversal proceeds toward lower retained indices. */
        val reverse = direction == PixelFocusDirection.PREVIOUS
        /** Cyclic destination index for this Tab or Shift+Tab request. */
        val targetIndex = when {
            currentIndex == null && reverse -> nodes.lastIndex
            currentIndex == null -> 0
            reverse -> (currentIndex - 1).floorMod(nodes.size)
            else -> (currentIndex + 1).floorMod(nodes.size)
        }
        /** Enabled node selected from the flattened traversal list. */
        val target = nodes[targetIndex]
        return requestFocus(target, target.scope ?: rootScope)
    }

    /** Returns modal descendants and canonically higher routes, or every unblocked non-modal scope. */
    private fun eligibleScopesForTraversal(): List<FocusScopeNode> {
        /** Modal that filters background traversal while allowing higher hosted presentations. */
        val modal = modalStack.lastOrNull()
        return mountedScopes.filter { scope ->
            !scope.hasBlockedAncestor() && (modal == null || scopeAllowedByModal(scope, modal))
        }
    }

    /** Finds the first enabled node mounted anywhere below [scope]. */
    private fun firstFocusableDescendant(scope: FocusScopeNode): FocusNode? {
        return mountedScopes
            .asSequence()
            .filter { candidate -> candidate.isDescendantOf(scope) && !candidate.hasBlockedAncestor() }
            .mapNotNull(FocusScopeNode::firstFocusableNode)
            .firstOrNull()
    }
}

/** 定义 `PixelOverlayFocusOrder` 在 `PixelFocus` 中承担的数据与行为边界。
 *
 * Canonical focus order for one route inside one exact [PixelOverlayHost] State.
 */
@PixelArtifactInternalApi
public data class PixelOverlayFocusOrder(
    /** 公开 `PixelFocus` 的 `hostIdentity` 配置或运行值。
 *
 * Permanent host identity preventing order comparisons across unrelated overlay stacks.
 */
    public val hostIdentity: Any,
    /** 公开 `PixelFocus` 的 `layerOrder` 配置或运行值。
 *
 * Explicit [PixelOverlayLayer] ordinal used as the canonical primary sort key.
 */
    public val layerOrder: Int,
    /** 公开 `PixelFocus` 的 `insertionOrder` 配置或运行值。
 *
 * Controller insertion order used as the canonical same-layer tie-breaker.
 */
    public val insertionOrder: Long,
) {
    /** 判断 `PixelFocus` 是否满足 `isAbove` 条件，不修改现有状态。
 *
 * Returns true only when this route is above [other] in the same hosted stack.
 */
    public fun isAbove(other: PixelOverlayFocusOrder): Boolean {
        if (hostIdentity !== other.hostIdentity) return false
        return layerOrder > other.layerOrder ||
            (layerOrder == other.layerOrder && insertionOrder > other.insertionOrder)
    }
}

/** Opaque runtime-local identity returned for one hosted-overlay focus-layer registration. */
internal data class PixelOverlayFocusToken(
    /** Monotonic identity unique within one [PixelFocusOwner]. */
    val id: Long,
)

/** Focus restoration record retained for one hosted overlay presentation. */
private data class PixelOverlayFocusEntry(
    /** Token used by the keyed presentation boundary to unregister this exact layer. */
    val token: PixelOverlayFocusToken,
    /** Route-owned scope supplying inherited presentation order to every descendant. */
    val scope: FocusScopeNode,
    /** Stable canonical host/layer/insertion tuple represented by this route. */
    val order: PixelOverlayFocusOrder,
    /** Node focused immediately before this route registered. */
    val opener: FocusNode?,
    /** Original opener scope used when the exact opener is later unavailable. */
    val openerScope: FocusScopeNode?,
    /** Whether a descendant of this route ever successfully owned runtime primary focus. */
    val ownsPrimary: Boolean = false,
    /** Whether this route has already accepted its first explicit autofocus descendant. */
    val autofocusChosen: Boolean = false,
)

/** Opaque runtime-local identity for one Host normalized dismiss-key registration. */
internal data class PixelDismissKeyHandlerToken(
    /** Monotonic identity unique within one [PixelFocusOwner]. */
    val id: Long,
)

/** Mounted callback receiving Escape/Back before background focus-node shortcuts. */
private data class PixelDismissKeyHandlerEntry(
    /** Exact registration identity used for reentrant snapshot validation and disposal. */
    val token: PixelDismissKeyHandlerToken,
    /** Callback applying the owning Host's canonical route dismiss policy. */
    val handler: () -> Boolean,
)

/** Opaque runtime-local identity returned for one active modal focus scope. */
internal data class PixelModalFocusToken(
    /** Monotonic identity unique within one [PixelFocusOwner]. */
    val id: Long,
)

/** Focus restoration record retained for one active modal presentation. */
private data class PixelModalFocusEntry(
    /** Token used by the retained boundary to deactivate the exact entry. */
    val token: PixelModalFocusToken,
    /** Scope whose descendants exclusively receive focus while this entry is topmost. */
    val scope: FocusScopeNode,
    /** Node focused immediately before activation. */
    val opener: FocusNode?,
    /** Opener scope used to select a deterministic fallback when the opener disappears. */
    val openerScope: FocusScopeNode?,
    /** Callback consumed by Escape or gamepad Back while this entry is topmost. */
    val onDismissRequest: (() -> Unit)?,
    /** 没有回调的 modal 是否仍在 focus owner 内部捕获 Escape/Back。 */
    val consumeUnhandledDismissRequest: Boolean,
    /** 该 modal 是否已经接受了它的第一个显式 autofocus 后代。 */
    val autofocusChosen: Boolean = false,
)

/**
 * 执行 `PixelFocus` 的 `FocusScope` 公开行为；具体参数、返回和副作用见下文。
 *
 * Creates a retained focus scope for [child].
 *
 * When [node] is `null`, widget State owns one stable scope across rebuilds. 显式传入的 scope 会绑定
 * 到最近的 runtime（Host 或 `PixelTester`），不同 runtime 之间的焦点状态互不可见。
 *
 * @param child Widget subtree whose directly mounted Focus widgets join the resolved scope.
 * @param node Optional caller-retained scope; `null` creates a State-owned scope.
 * @param traversalPolicy Policy applied to directional movement within the resolved scope.
 * @param key Optional retained identity for the scope boundary.
 */
public fun FocusScope(
    child: Widget,
    node: FocusScopeNode? = null,
    traversalPolicy: FocusTraversalPolicy = ReadingOrderFocusTraversalPolicy,
    key: Any? = null,
): Widget {
    return FocusScopeHostWidget(
        child = child,
        node = node,
        traversalPolicy = traversalPolicy,
        key = key,
    )
}

/**
 * 为子树提供独立的焦点遍历策略。
 *
 * 默认会在组件 state 内持有一个 [FocusScopeNode]；如果业务需要跨页面保存焦点状态，可显式传入 [node]。
 *
 * @param child Widget subtree grouped under the traversal policy.
 * @param traversalPolicy Policy used for directional movement among grouped nodes.
 * @param node Optional caller-retained scope; `null` creates a stable State-owned scope.
 * @param key Optional retained identity for the traversal group.
 */
public fun FocusTraversalGroup(
    child: Widget,
    traversalPolicy: FocusTraversalPolicy = ReadingOrderFocusTraversalPolicy,
    node: FocusScopeNode? = null,
    key: Any? = null,
): Widget {
    return FocusTraversalGroupWidget(
        child = child,
        traversalPolicy = traversalPolicy,
        node = node,
        key = key,
    )
}

/**
 * 执行 `PixelFocus` 的 `Focus` 公开行为；具体参数、返回和副作用见下文。
 *
 * Publishes one retained [FocusNode] to [child] and binds it to the nearest runtime focus scope.
 *
 * App shortcuts in [onKeyEvent] bubble from the focused node toward enclosing Focus widgets before
 * standard-control activation. If [node] is omitted, widget State owns a stable node rather than
 * allocating one on every rebuild.
 *
 * [onKeyEvent] 只处理导航、激活和取消等非文本快捷键；所有可打印文本（含 supplementary、
 * 组合簇和多 code point 的 IME 提交）都通过 [onTextInput] 以完整 String 投递。两条链路各自
 * 独立冒泡，不存在互相回落。
 *
 * @param child Widget subtree represented by the resolved focus node.
 * @param node Optional caller-retained node; `null` creates a stable State-owned node.
 * @param autofocus Requests initial focus when the owner is empty. Inside a hosted overlay route,
 * the first eligible candidate may supersede lower content according to canonical route order.
 * @param canRequestFocus Caller-controlled focusability combined with component enabled gates.
 * @param onKeyEvent Optional non-text shortcut handler; return `true` to consume the event.
 * @param onTextInput Optional exact-text handler; return `true` to consume the complete payload.
 * @param scrollTarget Optional list item kept visible whenever this node gains focus.
 * @param key Optional retained identity for the Focus widget and inherited node boundary.
 */
public fun Focus(
    child: Widget,
    node: FocusNode? = null,
    autofocus: Boolean = false,
    canRequestFocus: Boolean = true,
    onKeyEvent: ((PixelKeyEvent) -> Boolean)? = null,
    onTextInput: ((PixelTextInputEvent) -> Boolean)? = null,
    scrollTarget: PixelFocusScrollTarget? = null,
    key: Any? = null,
): Widget {
    return FocusWidget(
        node = node,
        autofocus = autofocus,
        canRequestFocus = canRequestFocus,
        onKeyEvent = onKeyEvent,
        onTextInput = onTextInput,
        scrollTarget = scrollTarget,
        child = child,
        key = key,
    )
}

/** Stateful public-scope configuration that retains a default node across rebuilds. */
private class FocusScopeHostWidget(
    /** Declarative child mounted below the resolved focus scope. */
    val child: Widget,
    /** Optional caller-owned scope; null creates one retained by State. */
    val node: FocusScopeNode?,
    /** Traversal policy updated on the resolved scope each build. */
    val traversalPolicy: FocusTraversalPolicy,
    /** Stable retained identity supplied by the caller. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the owner-binding state for this scope. */
    override fun createState(): State<out StatefulWidget> = FocusScopeHostState()
}

/** Binds one retained focus scope to the nearest runtime owner. */
private class FocusScopeHostState : State<FocusScopeHostWidget>() {
    /** Default scope retained when the caller omits an explicit node. */
    private val ownedNode: FocusScopeNode = FocusScopeNode()

    /** 当前由该 State 绑定的 scope。 */
    private var boundNode: FocusScopeNode? = null

    /** 当前由该 State 绑定的 runtime owner。 */
    private var boundOwner: PixelFocusOwner? = null

    /** 把调用方持有或 State 持有的 scope 绑定到外层 runtime，并向下发布。 */
    override fun build(context: BuildContext): Widget {
        /** 由外层 Host、PixelTester 或 retained build runtime 注入的 runtime owner。 */
        val owner = context.requirePixelFocusOwner()
        /** 本次 build 中将被绑定并向下发布的稳定 scope。 */
        val node = widget.node ?: ownedNode
        /** Nearest explicitly inherited parent scope, if one encloses this boundary. */
        val inheritedParent = context.getInheritedWidgetOfExactType<FocusScopeWidget>()?.node
        /** Parent used for modal ancestry without making the runtime root its own parent. */
        val parentScope = inheritedParent ?: owner.rootScope.takeUnless { it === node }
        if (boundNode !== node || boundOwner !== owner) {
            /** Previous runtime released before rebinding a changed scope or owner. */
            val previousOwner = boundOwner
            if (previousOwner != null) boundNode?.unbindOwner(previousOwner)
            node.bindOwner(owner, parentScope)
            boundNode = node
            boundOwner = owner
        } else {
            node.bindOwner(owner, parentScope)
        }
        node.traversalPolicy = widget.traversalPolicy
        return FocusScopeWidget(node = node, child = widget.child, key = widget.key)
    }

    /** Releases only an empty scope when this retained boundary leaves the runtime. */
    override fun dispose() {
        /** Runtime expected to own [boundNode] at this State's terminal boundary. */
        val owner = boundOwner
        if (owner != null) boundNode?.unbindOwner(owner)
        boundNode = null
        boundOwner = null
    }
}

/** Inherited notifier that exposes one bound scope to descendant Focus widgets. */
private class FocusScopeWidget(
    /** Bound scope inherited by descendant Focus and nested scope widgets. */
    val node: FocusScopeNode,
    /** Declarative subtree receiving [node] through this notifier. */
    override val child: Widget,
    /** Stable retained identity inherited from the public scope boundary. */
    override val key: Any?,
) : InheritedNotifier<FocusScopeNode>(notifier = node, child = child, key = key)

/** Stateful traversal-group configuration that retains a default scope across rebuilds. */
private class FocusTraversalGroupWidget(
    /** Declarative subtree grouped under [traversalPolicy]. */
    val child: Widget,
    /** Policy installed on the resolved group scope. */
    val traversalPolicy: FocusTraversalPolicy,
    /** Optional caller-owned scope; `null` selects the State-owned scope. */
    val node: FocusScopeNode?,
    /** Stable retained identity supplied by the public factory. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates State that owns the default traversal scope. */
    override fun createState(): State<out StatefulWidget> = FocusTraversalGroupState()
}

/** Retains the implicit scope used by one [FocusTraversalGroupWidget]. */
private class FocusTraversalGroupState : State<FocusTraversalGroupWidget>() {
    /** Stable fallback scope used whenever the caller does not supply one. */
    private val ownedNode = FocusScopeNode()

    /** Delegates owner binding and scope publication to [FocusScope]. */
    override fun build(context: BuildContext): Widget {
        return FocusScope(
            child = widget.child,
            node = widget.node ?: ownedNode,
            traversalPolicy = widget.traversalPolicy,
            key = widget.key,
        )
    }
}

/** Declarative configuration for one State-retained focus-node boundary. */
private class FocusWidget(
    /** Optional caller-owned node; null creates one retained by State. */
    val node: FocusNode?,
    /** Whether this node requests initial focus or explicit hosted-overlay autofocus. */
    val autofocus: Boolean,
    /** 遍历和直接请求是否可以聚焦该节点。 */
    val canRequestFocus: Boolean,
    /** 在标准组件默认行为之前求值的调用方快捷键处理器。 */
    val onKeyEvent: ((PixelKeyEvent) -> Boolean)?,
    /** 接收完整提交载荷的调用方精确文本处理器。 */
    val onTextInput: ((PixelTextInputEvent) -> Boolean)?,
    /** Optional list item kept visible after focus moves here. */
    val scrollTarget: PixelFocusScrollTarget?,
    /** Declarative child that observes this node through [FocusNodeScope]. */
    val child: Widget,
    /** Stable retained identity supplied by the caller. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the retained node binding state. */
    override fun createState(): State<out StatefulWidget> = FocusWidgetState()
}

/** Owns, configures, and mounts the active node for one [FocusWidget]. */
private class FocusWidgetState : State<FocusWidget>() {
    /** Default node retained when the caller omits an explicit node. */
    private val ownedNode: FocusNode = FocusNode()

    /** Scope that currently retains [activeNode]. */
    private var attachedScope: FocusScopeNode? = null

    /** Last scroll target successfully made visible for the current focus. */
    private var ensuredScrollTarget: PixelFocusScrollTarget? = null

    /** Resolves the current caller-owned or State-owned node. */
    private val activeNode: FocusNode
        get() = widget.node ?: ownedNode

    /** Initial attachment is completed from didChangeDependencies where inherited scopes are valid. */
    override fun initState() {
        configureNode()
    }

    /** Rebinds after the runtime owner or nearest scope changes. */
    override fun didChangeDependencies() {
        configureNode()
        configureParentNode()
        attachToScope()
        requestAutofocusIfAvailable()
    }

    /** Rebinds an explicitly replaced node without disturbing another runtime. */
    override fun didUpdateWidget(oldWidget: FocusWidget) {
        /** Node configured by the previous widget instance. */
        val oldNode = oldWidget.node ?: ownedNode
        /** Node selected by the current widget instance. */
        val nextNode = activeNode
        if (oldNode !== nextNode) {
            attachedScope?.detach(oldNode)
            oldNode.parentNode = null
            attachedScope = null
            ensuredScrollTarget = null
        }
        if (oldWidget.scrollTarget != widget.scrollTarget) {
            ensuredScrollTarget = null
        }
        configureNode()
        configureParentNode()
        attachToScope()
        requestAutofocusIfAvailable()
    }

    /** Detaches the exact node retained by this State. */
    override fun dispose() {
        attachedScope?.detach(activeNode)
        activeNode.parentNode = null
    }

    /** Publishes the active node and keeps its focused list item visible. */
    override fun build(context: BuildContext): Widget {
        /** Caller-owned or State-owned node published for this build. */
        val node = activeNode
        context.watch(node)
        ensureFocusedItemVisible()
        return FocusNodeScope(
            node = node,
            child = widget.child,
            key = widget.key?.let { "$it-node-scope" },
        )
    }

    /** Applies mutable public configuration to the retained node. */
    private fun configureNode() {
        activeNode.canRequestFocus = widget.canRequestFocus
        activeNode.onKeyEvent = widget.onKeyEvent
        activeNode.onTextInput = widget.onTextInput
    }

    /** Links this node to the nearest enclosing Focus without creating self-referential cycles. */
    private fun configureParentNode() {
        /** 需要刷新其快捷键冒泡父节点的活跃节点。 */
        val node = activeNode
        node.parentNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()
            ?.node
            ?.takeUnless { parent -> parent === node }
    }

    /** 把节点挂载到最近的 scope，或本 runtime 唯一的根 scope。 */
    private fun attachToScope() {
        /** 最近的显式 scope；没有时使用本 runtime 自己的根 scope。 */
        val scope = context.getInheritedWidgetOfExactType<FocusScopeWidget>()?.node
            ?: context.requirePixelFocusOwner().rootScope
        if (attachedScope === scope) return
        /** Active node moved between scopes only when the resolved scope changes. */
        val node = activeNode
        attachedScope?.detach(node)
        attachedScope = scope
        scope.attach(node)
    }

    /** Honors the first eligible autofocus candidate for the runtime or a hosted overlay route. */
    private fun requestAutofocusIfAvailable() {
        /** 作为该 widget autofocus 候选提交的活跃节点。 */
        val node = activeNode
        /** 用于定位正确 runtime 与 modal 条目的已挂载 scope。 */
        val scope = attachedScope ?: return
        if (widget.autofocus) scope.owner?.requestAutofocus(node, scope)
    }

    /** Scrolls a newly focused list item into its viewport at most once per target. */
    private fun ensureFocusedItemVisible() {
        /** Current list item requested by the public Focus configuration. */
        val target = widget.scrollTarget
        if (!activeNode.isFocused) {
            ensuredScrollTarget = null
            return
        }
        if (target == null || ensuredScrollTarget == target) return
        if (ensureVisibleIfReady(target)) {
            ensuredScrollTarget = target
        }
    }

    /**
     * Scrolls [target] only after its viewport and item geometry are available.
     *
     * @return `true` after issuing a scroll, or `false` when a later build must retry.
     */
    private fun ensureVisibleIfReady(target: PixelFocusScrollTarget): Boolean {
        if (target.state.viewportHeightPx <= 0) return false
        /** Lazy-sliver index selecting the specialized geometry and controller path. */
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

/** 定义 `FocusNodeScope` 在 `PixelFocus` 中承担的数据与行为边界。
 *
 * Inherited boundary exposing the nearest [FocusNode] for shortcut bubbling and components.
 */
@PixelArtifactInternalApi
public class FocusNodeScope(
    /** 公开 `PixelFocus` 的 `node` 配置或运行值。
 *
 * Node represented by the enclosing Focus widget.
 */
    public val node: FocusNode,
    /** 公开 `PixelFocus` 的 `child` 配置或运行值。
 *
 * Declarative subtree that may consume or extend the inherited node.
 */
    public override val child: Widget,
    /** 公开 `PixelFocus` 的 `key` 配置或运行值。
 *
 * Stable retained identity derived from the public Focus key.
 */
    public override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    /** 更新 `PixelFocus` 的 `updateShouldNotify` 状态并保持派生数据一致。
 *
 * Notifies descendants only when the boundary starts exposing a different node.
 */
    public override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return (oldWidget as? FocusNodeScope)?.node !== node
    }
}

/** 集中提供 `PixelFocus` 的 `OverlayFocusScopeFactory` 共享入口。
 *
 * Internal factory that assigns canonical route order without creating modal focus semantics.
 */
@PixelArtifactInternalApi
public object OverlayFocusScopeFactory {
    /**
 * 创建或解析 `PixelFocus` 的 `create` 结果，并在返回前校验输入。
 *
     * Wraps one hosted overlay route in a retained focus scope with opener restoration.
     *
     * The scope lets a route above an active modal receive direct focus, autofocus, and keyboard
     * traversal while leaving the lower modal responsible for background isolation.
     */
    public fun create(
        active: Boolean,
        order: PixelOverlayFocusOrder,
        child: Widget,
        key: Any? = null,
    ): Widget = OverlayFocusScopeWidget(
        active = active,
        order = order,
        child = child,
        key = key,
    )
}

/** Declarative configuration for one hosted route's non-trapping focus layer. */
private class OverlayFocusScopeWidget(
    /** Whether the route remains logically active and eligible for focus. */
    val active: Boolean,
    /** Current canonical order within the exact overlay host State. */
    val order: PixelOverlayFocusOrder,
    /** Declarative route subtree mounted below the dedicated focus scope. */
    val child: Widget,
    /** Stable retained identity spanning route rebuilds and visual exit. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the State that registers route order and focus restoration. */
    override fun createState(): State<out StatefulWidget> = OverlayFocusScopeState()
}

/** Owns one hosted-overlay focus scope and its runtime-local registration token. */
private class OverlayFocusScopeState : State<OverlayFocusScopeWidget>() {
    /** Dedicated scope carrying route order without trapping traversal. */
    private val scopeNode: FocusScopeNode = FocusScopeNode()

    /** Runtime owner currently associated with [activationToken]. */
    private var activeOwner: PixelFocusOwner? = null

    /** Exact focus-layer registration removed on logical close or disposal. */
    private var activationToken: PixelOverlayFocusToken? = null

    /** 在后代焦点 widget 解析其 scope 之前，先登记当前 route 的层级顺序。 */
    override fun build(context: BuildContext): Widget {
        /** 把焦点状态与其它 Host 或测试 runtime 隔离开的 runtime owner。 */
        val owner = context.requirePixelFocusOwner()
        /** Parent scope containing the route opener and application fallback controls. */
        val parentScope = context.getInheritedWidgetOfExactType<FocusScopeWidget>()?.node
            ?: owner.rootScope
        if (activeOwner !== owner) deactivateCurrentLayer()
        if (widget.active) {
            scopeNode.setFocusBlocked(false)
            scopeNode.setOverlayFocusOrder(widget.order)
            /** Existing registration identity retained while only canonical order changes. */
            val token = activationToken
            if (token == null) {
                activationToken = owner.activateOverlayFocusLayer(
                    scope = scopeNode,
                    parentScope = parentScope,
                    order = widget.order,
                )
                activeOwner = owner
            } else {
                owner.updateOverlayFocusLayer(token = token, order = widget.order)
            }
        } else {
            deactivateCurrentLayer()
            scopeNode.setOverlayFocusOrder(null)
            scopeNode.setFocusBlocked(true)
        }
        return FocusScope(
            child = widget.child,
            node = scopeNode,
            traversalPolicy = ReadingOrderFocusTraversalPolicy,
            key = widget.key?.let { "$it-focus-scope" },
        )
    }

    /** Releases route focus ownership and restores its opener at the retained-tree boundary. */
    override fun dispose() {
        deactivateCurrentLayer()
    }

    /** Deactivates the exact owner/token pair at most once. */
    private fun deactivateCurrentLayer() {
        /** Runtime that created [activationToken], if the route is currently active. */
        val owner = activeOwner
        /** Exact focus-layer registration to remove from [owner]. */
        val token = activationToken
        if (owner != null && token != null) owner.deactivateOverlayFocusLayer(token)
        scopeNode.setOverlayFocusOrder(null)
        scopeNode.setFocusBlocked(true)
        activeOwner = null
        activationToken = null
    }
}

/** 集中提供 `PixelFocus` 的 `OverlayDismissKeyHandlerFactory` 共享入口。
 *
 * Internal factory for a Host-level Escape/Back interceptor that never requests focus.
 */
@PixelArtifactInternalApi
public object OverlayDismissKeyHandlerFactory {
    /**
 * 创建或解析 `PixelFocus` 的 `create` 结果，并在返回前校验输入。
 *
     * Registers [onDismissRequest] only while [enabled] and keeps [child] visually unchanged.
     *
     * @param enabled Whether the owning route controller currently has a dismiss consumer.
     * @param onDismissRequest Callback applying canonical route policy for one normalized key.
     * @param child Complete hosted overlay stack receiving no extra focus node or render object.
     * @param key Stable retained identity for registration lifecycle updates.
     */
    public fun create(
        enabled: Boolean,
        onDismissRequest: () -> Boolean,
        child: Widget,
        key: Any? = null,
    ): Widget = OverlayDismissKeyHandlerWidget(
        enabled = enabled,
        onDismissRequest = onDismissRequest,
        child = child,
        key = key,
    )
}

/** Declarative Host-level normalized dismiss-key registration. */
private class OverlayDismissKeyHandlerWidget(
    /** Whether this handler currently occupies the runtime dismiss stack. */
    val enabled: Boolean,
    /** Latest callback scanning the owning controller's canonical route order. */
    val onDismissRequest: () -> Boolean,
    /** Overlay presentation subtree passed through without a focus or render boundary. */
    val child: Widget,
    /** Stable identity spanning controller mutations while the Host remains mounted. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the runtime-local registration owner. */
    override fun createState(): State<out StatefulWidget> = OverlayDismissKeyHandlerState()
}

/** Retains one Host normalized dismiss handler and removes it at every inactive boundary. */
private class OverlayDismissKeyHandlerState : State<OverlayDismissKeyHandlerWidget>() {
    /** 最近的 runtime owner；即使控制器策略临时禁用输入也继续持有。 */
    private var currentOwner: PixelFocusOwner? = null

    /** 在禁用、owner 变更或释放时被移除的 runtime 局部精确 token。 */
    private var registrationToken: PixelDismissKeyHandlerToken? = null

    /** 该子树在 Host 与测试 runtime 之间迁移时重新绑定。 */
    override fun didChangeDependencies() {
        /** 接收归一化 Host 按键分发的最近 runtime 局部焦点 owner。 */
        val owner = context.requirePixelFocusOwner()
        syncRegistration(owner)
    }

    /** Adds or removes the stack entry when controller policy crosses the enabled boundary. */
    override fun didUpdateWidget(oldWidget: OverlayDismissKeyHandlerWidget) {
        if (oldWidget.enabled != widget.enabled) {
            syncRegistration(currentOwner, force = true)
        }
    }

    /** Passes the overlay stack through without introducing focusability or render geometry. */
    override fun build(context: BuildContext): Widget = widget.child

    /** Releases the exact runtime registration before this retained boundary disappears. */
    override fun dispose() {
        unregisterCurrentHandler()
        currentOwner = null
    }

    /** Synchronizes one enabled registration with [nextOwner]. */
    private fun syncRegistration(nextOwner: PixelFocusOwner?, force: Boolean = false) {
        if (!force && nextOwner === currentOwner) return
        unregisterCurrentHandler()
        currentOwner = nextOwner
        if (!widget.enabled || nextOwner == null) return
        registrationToken = nextOwner.registerDismissKeyHandler {
            widget.onDismissRequest()
        }
    }

    /** Removes the current owner/token pair at most once. */
    private fun unregisterCurrentHandler() {
        /** Runtime that owns the current registration, when one exists. */
        val owner = currentOwner
        /** Exact registration token issued by [owner], when one exists. */
        val token = registrationToken
        if (owner != null && token != null) owner.unregisterDismissKeyHandler(token)
        registrationToken = null
    }
}

/** 集中提供 `PixelFocus` 的 `ModalFocusScopeFactory` 共享入口。
 *
 * Internal factory that keeps modal-focus construction out of the published top-level JVM API.
 */
@PixelArtifactInternalApi
public object ModalFocusScopeFactory {
    /**
 * 创建或解析 `PixelFocus` 的 `create` 结果，并在返回前校验输入。
 *
     * Gives an active modal subtree exclusive focus, Escape/Back dismissal, and opener restoration.
     *
     * This primitive is shared by Dialog, Menu, and Popover. [active] represents logical
     * visibility, so retained exit animation keeps painting after focus has already returned.
     *
     * @param active Whether this presentation currently owns modal focus.
     * @param onDismissRequest Callback invoked for Escape or gamepad Back while topmost.
     * @param consumeUnhandledDismissRequest Whether a missing callback still consumes the key.
     * @param child Declarative modal subtree mounted below the dedicated focus scope.
     * @param coalesceNestedMenu Whether this presentation's own Menu reuses the same modal layer.
     * @param coalesceNestedModal Whether standard nested modal surfaces reuse this route owner.
     * @param key Optional retained identity spanning logical enter and exit transitions.
     */
    public fun create(
        active: Boolean,
        onDismissRequest: (() -> Unit)?,
        child: Widget,
        consumeUnhandledDismissRequest: Boolean = true,
        coalesceNestedMenu: Boolean = false,
        coalesceNestedModal: Boolean = false,
        key: Any? = null,
    ): Widget = ModalFocusScopeWidget(
        active = active,
        onDismissRequest = onDismissRequest,
        child = child,
        consumeUnhandledDismissRequest = consumeUnhandledDismissRequest,
        coalesceNestedMenu = coalesceNestedMenu,
        coalesceNestedModal = coalesceNestedModal,
        key = key,
    )
}

/**
 * 集中提供 `PixelFocus` 的 `StandaloneModalBoundaryFactory` 共享入口。
 *
 * Builds the focus and platform-Back boundaries owned by one standalone modal presentation.
 *
 * Unified overlay routes deliberately use [ModalFocusScopeFactory] directly because
 * [PixelOverlayHost] owns their canonical Back policy. Standard components use this factory only
 * when they do not coalesce into an enclosing route or popup presentation, preventing two handlers
 * from observing the same Back commit.
 */
@PixelArtifactInternalApi
public object StandaloneModalBoundaryFactory {
    /**
 * 创建或解析 `PixelFocus` 的 `create` 结果，并在返回前校验输入。
 *
     * Wraps [child] in one modal focus scope and one discrete platform-Back handler.
     *
     * @param active Whether this standalone presentation currently owns modal interaction.
     * @param onDismissRequest Controlled callback invoked once for Back or keyboard dismissal.
     * @param child Declarative modal subtree mounted below both retained boundaries.
     * @param consumeUnhandledDismissRequest Whether a missing callback still traps dismissal.
     * @param coalesceNestedMenu Whether a Menu inside this presentation shares its modal owner.
     * @param coalesceNestedModal Whether nested standard modal surfaces share this owner.
     * @param key Optional stable identity spanning controlled visibility changes.
     */
    public fun create(
        active: Boolean,
        onDismissRequest: (() -> Unit)?,
        child: Widget,
        consumeUnhandledDismissRequest: Boolean = true,
        coalesceNestedMenu: Boolean = false,
        coalesceNestedModal: Boolean = false,
        key: Any? = null,
    ): Widget {
        /** Focus boundary retaining keyboard dismissal and opener-restoration semantics. */
        val focusedChild = ModalFocusScopeFactory.create(
            active = active,
            onDismissRequest = onDismissRequest,
            consumeUnhandledDismissRequest = consumeUnhandledDismissRequest,
            coalesceNestedMenu = coalesceNestedMenu,
            coalesceNestedModal = coalesceNestedModal,
            child = child,
            key = key?.let { "$it-focus" },
        )
        /** Whether the standalone modal must occupy a platform Back stack entry. */
        val handlesBack = active &&
            (onDismissRequest != null || consumeUnhandledDismissRequest)
        return PixelBackHandler(
            enabled = handlesBack,
            onBack = {
                /** Latest controlled dismiss callback captured by this declarative configuration. */
                val dismissRequest = onDismissRequest
                if (dismissRequest != null) {
                    dismissRequest()
                    true
                } else {
                    consumeUnhandledDismissRequest
                }
            },
            child = focusedChild,
            key = key?.let { "$it-back" },
        )
    }
}

/** Declarative configuration for one retained modal focus boundary. */
private class ModalFocusScopeWidget(
    /** Whether this presentation currently owns modal focus. */
    val active: Boolean,
    /** Callback consumed by Escape or gamepad Back while topmost. */
    val onDismissRequest: (() -> Unit)?,
    /** Declarative modal content mounted below a dedicated focus scope. */
    val child: Widget,
    /** Whether a missing callback still traps Escape/Back for a standalone modal. */
    val consumeUnhandledDismissRequest: Boolean,
    /** Whether a Menu in this exact presentation should reuse the existing modal token. */
    val coalesceNestedMenu: Boolean,
    /** Whether standard modal surfaces in this route should reuse the existing modal token. */
    val coalesceNestedModal: Boolean,
    /** Stable identity retained across logical enter and exit. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the modal activation and restoration state. */
    override fun createState(): State<out StatefulWidget> = ModalFocusScopeState()
}

/** Owns one modal scope and its runtime-local activation token. */
private class ModalFocusScopeState : State<ModalFocusScopeWidget>() {
    /** Dedicated scope that traps traversal inside this modal presentation. */
    private val scopeNode: FocusScopeNode = FocusScopeNode()

    /** Runtime owner currently associated with [activationToken]. */
    private var activeOwner: PixelFocusOwner? = null

    /** Exact modal activation restored on logical close or disposal. */
    private var activationToken: PixelModalFocusToken? = null

    /** 在后代构建当前帧之前，激活或停用该 modal 的焦点。 */
    override fun build(context: BuildContext): Widget {
        /** 必须把该 modal 栈与其它 runtime 隔离开的 runtime owner。 */
        val owner = context.requirePixelFocusOwner()
        /** Scope containing the opener and serving as this modal scope's ancestry parent. */
        val parentScope = context.getInheritedWidgetOfExactType<FocusScopeWidget>()?.node
            ?: owner.rootScope
        if (activeOwner !== owner) deactivateCurrentModal()
        if (widget.active) {
            scopeNode.setFocusBlocked(false)
            /** Existing activation identity retained across active rebuilds, if already registered. */
            val token = activationToken
            if (token == null) {
                activationToken = owner.activateModal(
                    scope = scopeNode,
                    parentScope = parentScope,
                    onDismissRequest = widget.onDismissRequest,
                    consumeUnhandledDismissRequest = widget.consumeUnhandledDismissRequest,
                )
                activeOwner = owner
            } else {
                owner.updateModal(
                    token = token,
                    onDismissRequest = widget.onDismissRequest,
                    consumeUnhandledDismissRequest = widget.consumeUnhandledDismissRequest,
                )
            }
        } else {
            deactivateCurrentModal()
            scopeNode.setFocusBlocked(true)
        }
        return PixelModalFocusPresence(
            coalesceNestedMenu = widget.coalesceNestedMenu,
            coalesceNestedModal = widget.coalesceNestedModal,
            child = FocusScope(
                child = widget.child,
                node = scopeNode,
                traversalPolicy = ReadingOrderFocusTraversalPolicy,
                key = widget.key?.let { "$it-focus-scope" },
            ),
            key = widget.key?.let { "$it-presence" },
        )
    }

    /** Restores the opener if this boundary leaves the retained tree while active. */
    override fun dispose() {
        deactivateCurrentModal()
    }

    /** Deactivates the exact owner/token pair at most once. */
    private fun deactivateCurrentModal() {
        /** Runtime that created [activationToken], if this boundary is currently active. */
        val owner = activeOwner
        /** Exact modal-stack entry to remove from [owner]. */
        val token = activationToken
        if (owner != null && token != null) owner.deactivateModal(token)
        scopeNode.setFocusBlocked(true)
        activeOwner = null
        activationToken = null
    }
}

/** 定义 `PixelModalFocusPresence` 在 `PixelFocus` 中承担的数据与行为边界。
 *
 * Nearest modal marker that lets a Popover coalesce only its own nested Menu presentation.
 */
@PixelArtifactInternalApi
public class PixelModalFocusPresence(
    /** 公开 `PixelFocus` 的 `coalesceNestedMenu` 配置或运行值。
 *
 * Whether a nested Menu represents this same presentation instead of another modal layer.
 */
    public val coalesceNestedMenu: Boolean,
    /** 公开 `PixelFocus` 的 `coalesceNestedModal` 配置或运行值。
 *
 * Whether a nested Dialog, BottomSheet, or Popover reuses this route's modal owner.
 */
    public val coalesceNestedModal: Boolean = false,
    /** 公开 `PixelFocus` 的 `child` 配置或运行值。
 *
 * Declarative modal subtree receiving this nearest-presentation marker.
 */
    public override val child: Widget,
    /** 公开 `PixelFocus` 的 `key` 配置或运行值。
 *
 * Stable retained marker identity.
 */
    public override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    /** 更新 `PixelFocus` 的 `updateShouldNotify` 状态并保持派生数据一致。
 *
 * Notifies only when the nearest presentation changes its Menu coalescing policy.
 */
    public override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        val oldPresence = oldWidget as PixelModalFocusPresence
        return oldPresence.coalesceNestedMenu != coalesceNestedMenu ||
            oldPresence.coalesceNestedModal != coalesceNestedModal
    }
}

/** Inherited runtime focus owner injected once by [com.purride.pixelui.internal.PixelUiRuntime]. */
internal class PixelFocusOwnerScope(
    /** Runtime-local focus state consumed by scopes, Hosts, and testing. */
    val owner: PixelFocusOwner,
    /** Declarative root retained below this owner boundary. */
    override val child: Widget,
    /** 该边界的稳定身份标识。 */
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    /** 仅当另一个 runtime owner 替换该边界时才通知后代。 */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return (oldWidget as? PixelFocusOwnerScope)?.owner !== owner
    }
}

/**
 * 解析当前 build context 所属的 runtime-local 焦点 owner。
 *
 * 每个 retained build runtime 都会在根部注入 [PixelFocusOwnerScope]，因此挂载中的 widget 必然
 * 能解析到唯一 owner。缺失时说明该子树没有经由 runtime 挂载，此时直接失败，而不是回退到某个
 * 进程级共享 owner——那样会让两个 Host 互相看到对方的焦点。
 */
internal fun BuildContext.requirePixelFocusOwner(): PixelFocusOwner {
    return checkNotNull(getInheritedWidgetOfExactType<PixelFocusOwnerScope>()?.owner) {
        "Focus widgets must be mounted inside a PixelUiRuntime that installs PixelFocusOwnerScope"
    }
}

/** Computes a non-negative cyclic index for traversal. */
private fun Int.floorMod(divisor: Int): Int {
    /** Language remainder, which may be negative when the receiver is negative. */
    val remainder = this % divisor
    return if (remainder < 0) remainder + divisor else remainder
}
