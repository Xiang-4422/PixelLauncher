package com.purride.pixelui.internal

import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsNode
import java.util.concurrent.atomic.AtomicLong

/**
 * Retained boundary that gives an active modal subtree exclusive interaction and semantics.
 *
 * Every collected target already retains its source render object. The completed render session
 * walks that source ancestry, selects the greatest active modal token, and removes targets that
 * do not belong to that scope. This remains correct when later Row/Column siblings are collected
 * after the modal widget. The child stays laid out and painted when [active] is false so retained
 * exit motion can finish normally.
 */
internal class RenderModalInteractionScope(
    /** Retained modal presentation subtree forwarded through every render channel. */
    child: RenderBox? = null,
    /** Whether this subtree currently owns modal interaction and semantic traversal. */
    private var active: Boolean,
    /** Canonical OverlayHost presentation order, or `null` for standalone modal boundaries. */
    private var overlayOrder: Long? = null,
) : SingleChildRenderObject() {
    /** Monotonic ownership token assigned whenever this boundary becomes logically active. */
    internal var activeModalToken: Long? = if (active) NextModalToken.getAndIncrement() else null
        private set

    init {
        setRenderObjectChild(child)
    }

    /** Updates logical modal ownership and optional host order without replacing the child subtree. */
    fun update(active: Boolean, overlayOrder: Long? = this.overlayOrder) {
        /** Whether activation changed and therefore requires a fresh ownership token. */
        val activationChanged = this.active != active
        /** Whether canonical route order changed while the render object was retained. */
        val orderChanged = this.overlayOrder != overlayOrder
        if (!activationChanged && !orderChanged) return
        this.active = active
        this.overlayOrder = overlayOrder
        if (activationChanged) {
            activeModalToken = if (active) NextModalToken.getAndIncrement() else null
        }
        markNeedsPaint()
    }

    /** Returns the canonical OverlayHost order attached to this retained presentation. */
    internal fun presentationOrder(): Long? = overlayOrder

    /** Gives the child the same constraints and adopts its constrained size. */
    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        /** Child dimensions before this boundary reapplies the incoming constraints. */
        val childSize = renderChild?.size ?: RenderSize.Zero
        size = RenderSize(
            width = constraints.constrainWidth(childSize.width),
            height = constraints.constrainHeight(childSize.height),
        )
    }

    /** Paints the modal presentation without adding a visual effect. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX, offsetY)
    }

    /** Visits descendants and adds this scope as a session-level modal hit marker when active. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        renderChild?.hitTest(localX, localY, result)
        // Parent Flex/Stack objects visit every child for an in-host point. An unconditional marker
        // is therefore required to suppress a later sibling even when the pointer lies outside the
        // modal presentation's own measured rectangle.
        if (active) result.add(this)
    }

    /** Forwards click targets; the completed session applies modal ancestry filtering. */
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        renderChild?.collectClickTargets(offsetX, offsetY, targets)
    }

    /** Forwards pager targets; the completed session applies modal ancestry filtering. */
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        renderChild?.collectPagerTargets(offsetX, offsetY, targets)
    }

    /** Forwards list targets; the completed session applies modal ancestry filtering. */
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        renderChild?.collectListTargets(offsetX, offsetY, targets)
    }

    /** Forwards scrollbar targets; the completed session applies modal ancestry filtering. */
    override fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        renderChild?.collectScrollbarTargets(offsetX, offsetY, targets)
    }

    /** Forwards refresh targets; the completed session applies modal ancestry filtering. */
    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        renderChild?.collectRefreshTargets(offsetX, offsetY, targets)
    }

    /** Forwards text-input targets; the completed session applies modal ancestry filtering. */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        renderChild?.collectTextInputTargets(offsetX, offsetY, targets)
    }

    /** Forwards slider targets; the completed session applies modal ancestry filtering. */
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        renderChild?.collectSliderTargets(offsetX, offsetY, targets)
    }

    /** Forwards semantics and appends a private marker so an empty modal still isolates background. */
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        renderChild?.collectSemantics(offsetX, offsetY, targets)
        if (active) {
            targets += PixelSemanticsTarget(
                node = PixelSemanticsNode(
                    id = semanticNodeId(ModalMarkerSlot),
                    label = "",
                    role = PixelSemanticRole.GENERIC,
                    enabled = false,
                    focused = false,
                    left = offsetX,
                    top = offsetY,
                    width = size.width,
                    height = size.height,
                ),
                source = this,
            )
        }
    }

    /** Current retained render child receiving layout, paint, and target collection. */
    private val renderChild: RenderBox?
        get() = child as? RenderBox

    /** Process-wide token source; tokens are identities and never exposed as public state. */
    private companion object {
        /** Monotonic activation sequence used to select the newest nested or sibling modal. */
        val NextModalToken: AtomicLong = AtomicLong(1L)

        /** Stable retained semantic-id slot reserved for the private active-modal marker. */
        val ModalMarkerSlot: Any = Any()
    }
}

/** Selected modal identity plus the host order that separates lower and higher route siblings. */
internal data class ActiveModalFilter(
    /** Monotonic token identifying the newest active modal boundary. */
    val token: Long,
    /** Canonical OverlayHost order inherited by that modal, when it belongs to a host route. */
    val overlayOrder: Long?,
)

/** Returns whether [source] belongs to the modal or to a canonically higher host route. */
internal fun sourceAllowedByModal(source: RenderObject?, filter: ActiveModalFilter): Boolean {
    /** Current source ancestor inspected while walking toward the render root. */
    var candidate = source
    /** Greatest host presentation order inherited by this source ancestry. */
    var sourceOrder: Long? = null
    while (candidate != null) {
        /** Modal boundary metadata carried by this source ancestor, when present. */
        val scope = candidate as? RenderModalInteractionScope
        if (scope?.activeModalToken == filter.token) return true
        /** Route order is monotonic, but maximum remains robust for nested retained boundaries. */
        val candidateOrder = scope?.presentationOrder()
        if (candidateOrder != null && (sourceOrder == null || candidateOrder > sourceOrder)) {
            sourceOrder = candidateOrder
        }
        candidate = candidate.parent
    }
    /** Only an explicitly higher OverlayHost route may remain interactive above the modal. */
    val modalOrder = filter.overlayOrder ?: return false
    return sourceOrder != null && sourceOrder > modalOrder
}

/** Returns the newest active modal plus its inherited OverlayHost order across [sources]. */
internal fun highestActiveModalFilter(sources: Iterable<RenderObject?>): ActiveModalFilter? {
    /** Newest modal activation encountered across all source ancestry chains. */
    var selectedToken: Long? = null
    /** OverlayHost order inherited by the currently selected modal activation. */
    var selectedOrder: Long? = null
    sources.forEach { source ->
        /** Ancestors retained so a selected nested modal can inherit its route wrapper's order. */
        val ancestry = mutableListOf<RenderObject>()
        /** Current source ancestor inspected while walking toward the render root. */
        var candidate = source
        while (candidate != null) {
            ancestry += candidate
            candidate = candidate.parent
        }
        /** Greatest route order on this source chain, shared by every nested boundary on it. */
        val ancestryOrder = ancestry
            .mapNotNull { ancestor ->
                (ancestor as? RenderModalInteractionScope)?.presentationOrder()
            }
            .maxOrNull()
        ancestry.forEach { ancestor ->
            /** Active modal identity owned by this ancestor, when it is a modal boundary. */
            val token = (ancestor as? RenderModalInteractionScope)?.activeModalToken
            /** Previous maximum retained to keep the nullable comparison explicit. */
            val previousToken = selectedToken
            if (token != null && (previousToken == null || token > previousToken)) {
                selectedToken = token
                selectedOrder = ancestryOrder
            }
        }
    }
    /** Immutable filter returned only when at least one active marker was collected. */
    val token = selectedToken ?: return null
    return ActiveModalFilter(token = token, overlayOrder = selectedOrder)
}
