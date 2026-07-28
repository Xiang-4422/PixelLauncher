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

/** 在多个目标族之间复用的 modal 选择器，遍历祖先时不创建临时列表。 */
internal class ActiveModalFilterAccumulator {
    /** 当前已发现的最新 modal 激活序号。 */
    private var selectedToken: Long? = null

    /** 当前最新 modal 所在 OverlayHost 路由的最大展示顺序。 */
    private var selectedOrder: Long? = null

    /** 检查一个目标来源的完整祖先链，并合并到当前选择结果。 */
    fun inspect(source: RenderObject?) {
        /** 当前来源链中最新的 modal 激活序号。 */
        var chainToken: Long? = null
        /** 当前来源链中继承的最大 OverlayHost 展示顺序。 */
        var chainOrder: Long? = null
        /** 正在向根节点遍历的渲染对象。 */
        var candidate = source
        while (candidate != null) {
            /** 当前祖先在 modal 边界上携带的激活与路由信息。 */
            val scope = candidate as? RenderModalInteractionScope
            /** 当前祖先的激活序号。 */
            val token = scope?.activeModalToken
            /** 本链此前已发现的激活序号。 */
            val previousChainToken = chainToken
            if (token != null && (previousChainToken == null || token > previousChainToken)) {
                chainToken = token
            }
            /** 当前祖先携带的 OverlayHost 展示顺序。 */
            val order = scope?.presentationOrder()
            /** 本链此前已发现的最大展示顺序。 */
            val previousChainOrder = chainOrder
            if (order != null && (previousChainOrder == null || order > previousChainOrder)) {
                chainOrder = order
            }
            candidate = candidate.parent
        }
        /** 本链没有 active modal 时不影响全局选择。 */
        val candidateToken = chainToken ?: return
        /** 全局此前已选择的激活序号。 */
        val previousSelectedToken = selectedToken
        if (previousSelectedToken == null || candidateToken > previousSelectedToken) {
            selectedToken = candidateToken
            selectedOrder = chainOrder
        }
    }

    /** 固化当前选择结果；没有 active modal 时返回 null。 */
    fun toFilter(): ActiveModalFilter? {
        /** 已选择的最终激活序号。 */
        val token = selectedToken ?: return null
        return ActiveModalFilter(token = token, overlayOrder = selectedOrder)
    }
}

/** 返回全部来源中最新的 active modal 及其 OverlayHost 展示顺序。 */
internal fun highestActiveModalFilter(sources: Iterable<RenderObject?>): ActiveModalFilter? {
    /** 单次调用共享的无临时祖先列表选择器。 */
    val accumulator = ActiveModalFilterAccumulator()
    sources.forEach(accumulator::inspect)
    return accumulator.toFilter()
}
