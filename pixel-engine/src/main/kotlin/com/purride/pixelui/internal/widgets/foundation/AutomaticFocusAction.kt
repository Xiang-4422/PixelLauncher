package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Builder
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.FocusNodeScope
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelKeyEvent
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.getInheritedWidgetOfExactType

/**
 * 为标准控件提供可保留的焦点节点和组件默认按键处理器。
 *
 * 显式 [focusNode] 的优先级高于继承节点；未提供显式节点时优先复用继承的 `Focus`，只有调用方
 * 没有包裹焦点边界时才创建私有节点。组件处理器是低优先级兜底，因此公开的
 * `Focus.onKeyEvent` 始终先获得消费事件的机会。
 *
 * 该函数仅用于 Pixel SDK 兄弟 artifact 之间互操作，不属于消费者稳定 API。
 */
@PixelArtifactInternalApi
public fun AutomaticFocusAction(
    enabled: Boolean,
    autofocus: Boolean = false,
    focusNode: FocusNode? = null,
    debugLabel: String? = null,
    onKeyEvent: ((PixelKeyEvent) -> Boolean)? = null,
    key: Any? = null,
    builder: (BuildContext, FocusNode) -> Widget,
): Widget = AutomaticFocusActionWidget(
    enabled = enabled,
    autofocus = autofocus,
    focusNode = focusNode,
    debugLabel = debugLabel,
    onKeyEvent = onKeyEvent,
    builder = builder,
    key = key?.let(::AutomaticFocusActionKey),
)

/** Stable wrapper identity derived from a caller-owned component key. */
private data class AutomaticFocusActionKey(
    /** Original component identity retained below the focus-only wrapper. */
    val componentKey: Any,
)

/** Declarative configuration for one automatically focusable standard control. */
private data class AutomaticFocusActionWidget(
    /** Whether traversal and component actions are currently available. */
    val enabled: Boolean,
    /** Whether the effective node should request initial focus. */
    val autofocus: Boolean,
    /** Optional caller-owned focus node. */
    val focusNode: FocusNode?,
    /** Diagnostic label used only when this widget creates its own node. */
    val debugLabel: String?,
    /** Latest component-default key handler. */
    val onKeyEvent: ((PixelKeyEvent) -> Boolean)?,
    /** Child factory that receives the effective retained node. */
    val builder: (BuildContext, FocusNode) -> Widget,
    /** Retained identity for the focus-only wrapper. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the private node and handler binding owner retained by this control. */
    override fun createState(): State<out StatefulWidget> = AutomaticFocusActionState()
}

/** Owns an implicit node while preserving an explicit or inherited node when one exists. */
private class AutomaticFocusActionState : State<AutomaticFocusActionWidget>() {
    /** Private node used only when neither the API nor an ancestor supplies one. */
    private val ownedNode: FocusNode by lazy { FocusNode(debugLabel = widget.debugLabel) }

    /** Stable identity used to update and remove this component's default handler. */
    private val handlerOwner: Any = Any()

    /** Node currently carrying [handlerOwner]'s action and enabled-state bindings. */
    private var boundNode: FocusNode? = null

    /** Removes component-owned bindings without changing caller-owned focus configuration. */
    override fun dispose() {
        unbindNode()
    }

    /** Resolves the effective node, refreshes its handler, and inserts a scope only when needed. */
    override fun build(context: BuildContext): Widget {
        val inheritedNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        val effectiveNode = resolveEffectiveNode(inheritedNode)
        bindNode(effectiveNode)
        val focusedChild = Builder(
            key = widget.key?.let { "$it-content" },
        ) { focusedContext ->
            focusedContext.watch(effectiveNode)
            widget.builder(focusedContext, effectiveNode)
        }
        if (effectiveNode === inheritedNode) return focusedChild
        return Focus(
            node = effectiveNode,
            autofocus = widget.autofocus,
            canRequestFocus = widget.enabled,
            child = focusedChild,
            key = widget.key?.let { "$it-focus" },
        )
    }

    /** Selects an explicit node, one unclaimed ancestor node, or this control's private node. */
    private fun resolveEffectiveNode(inheritedNode: FocusNode?): FocusNode {
        /** Caller-supplied node that must remain exclusive to this automatic control. */
        val explicitNode = widget.focusNode
        if (explicitNode != null) {
            check(explicitNode.canClaimAutomaticControl(handlerOwner)) {
                "An explicit FocusNode cannot represent multiple automatic controls"
            }
            return explicitNode
        }
        if (
            inheritedNode != null &&
            (boundNode === inheritedNode || inheritedNode.canClaimAutomaticControl(handlerOwner))
        ) {
            return inheritedNode
        }
        return ownedNode
    }

    /** Updates component focusability and its lower-priority action on the effective node. */
    private fun bindNode(node: FocusNode) {
        if (boundNode !== node) {
            unbindNode()
            node.claimAutomaticControl(handlerOwner)
            boundNode = node
        }
        node.bindFocusability(handlerOwner, widget.enabled)
        val handler = widget.onKeyEvent
        if (handler == null) {
            node.unbindDefaultKeyHandler(handlerOwner)
        } else {
            node.bindDefaultKeyHandler(handlerOwner, handler)
        }
    }

    /** Detaches this widget's action and enabled gate from its previous effective node. */
    private fun unbindNode() {
        val previousNode = boundNode
        previousNode?.unbindDefaultKeyHandler(handlerOwner)
        previousNode?.unbindFocusability(handlerOwner)
        previousNode?.releaseAutomaticControl(handlerOwner)
        boundNode = null
    }
}

/** Creates the standard Enter/Space activation mapping around one shared control action. */
internal fun activationKeyHandler(
    action: () -> Boolean,
): (PixelKeyEvent) -> Boolean = { event ->
    when (event.key) {
        PixelKey.ENTER,
        PixelKey.SPACE,
        -> action()
        else -> false
    }
}
