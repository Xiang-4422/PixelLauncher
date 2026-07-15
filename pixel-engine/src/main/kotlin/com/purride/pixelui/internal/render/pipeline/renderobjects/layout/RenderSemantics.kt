package com.purride.pixelui.internal

import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsActions
import com.purride.pixelui.PixelSemanticsCollectionInfo
import com.purride.pixelui.PixelSemanticsCollectionItemInfo
import com.purride.pixelui.PixelSemanticsCustomAction
import com.purride.pixelui.PixelSemanticsLiveRegion
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelSemanticsRangeInfo

/**
 * Retained semantic boundary that exports one stable node and applies descendant ownership rules.
 *
 * The render pipeline still collects into a flat target list. This boundary reconstructs the
 * direct semantic relationship locally: child roots are targets whose [PixelSemanticsNode.parentId]
 * is still null, while nested boundaries have already attached their own descendants.
 */
@Suppress("LongParameterList")
internal class RenderSemantics(
    child: RenderBox? = null,
    /** Primary spoken label owned by this boundary. */
    private var label: String,
    /** Platform role mapped by the Android accessibility bridge. */
    private var role: PixelSemanticRole,
    /** Whether mutating actions are currently allowed. */
    private var enabled: Boolean,
    /** Whether this node owns input focus. */
    private var focused: Boolean,
    /** Current value announced separately from [label]. */
    private var value: String?,
    /** Input or usage hint. */
    private var hint: String?,
    /** Validation error associated with this boundary. */
    private var error: String?,
    /** Current selection state. */
    private var selected: Boolean,
    /** Checked state, or null for a non-checkable node. */
    private var checked: Boolean?,
    /** Expanded state, or null when expansion is not applicable. */
    private var expanded: Boolean?,
    /** Inclusive text-selection start, or -1 when unavailable. */
    private var selectionStart: Int,
    /** Exclusive text-selection end, or -1 when unavailable. */
    private var selectionEnd: Int,
    /** Numeric range metadata for adjustable controls. */
    private var rangeInfo: PixelSemanticsRangeInfo?,
    /** Announcement priority for indirect content changes. */
    private var liveRegion: PixelSemanticsLiveRegion,
    /** Collection metadata when this boundary represents a list or grid. */
    private var collectionInfo: PixelSemanticsCollectionInfo?,
    /** Collection position metadata when this boundary represents an item. */
    private var collectionItemInfo: PixelSemanticsCollectionItemInfo?,
    /** Whether descendant properties and callbacks are folded into this node. */
    private var mergeDescendants: Boolean,
    /** Whether descendant semantic nodes are hidden without being merged. */
    private var excludeDescendants: Boolean,
    /** Typed callbacks owned directly by this boundary. */
    private var actions: PixelSemanticsActions,
) : SingleChildRenderObject() {
    init {
        require(!(mergeDescendants && excludeDescendants)) {
            "RenderSemantics cannot merge and exclude descendants at the same time."
        }
        setRenderObjectChild(child)
    }

    /** Updates every semantic property while preserving this render object's stable node id. */
    @Suppress("LongParameterList", "CyclomaticComplexMethod")
    fun updateSemantics(
        label: String,
        role: PixelSemanticRole,
        enabled: Boolean,
        focused: Boolean,
        value: String?,
        hint: String?,
        error: String?,
        selected: Boolean,
        checked: Boolean?,
        expanded: Boolean?,
        selectionStart: Int,
        selectionEnd: Int,
        rangeInfo: PixelSemanticsRangeInfo?,
        liveRegion: PixelSemanticsLiveRegion,
        collectionInfo: PixelSemanticsCollectionInfo?,
        collectionItemInfo: PixelSemanticsCollectionItemInfo?,
        mergeDescendants: Boolean,
        excludeDescendants: Boolean,
        actions: PixelSemanticsActions,
    ) {
        require(!(mergeDescendants && excludeDescendants)) {
            "RenderSemantics cannot merge and exclude descendants at the same time."
        }
        if (
            this.label == label &&
            this.role == role &&
            this.enabled == enabled &&
            this.focused == focused &&
            this.value == value &&
            this.hint == hint &&
            this.error == error &&
            this.selected == selected &&
            this.checked == checked &&
            this.expanded == expanded &&
            this.selectionStart == selectionStart &&
            this.selectionEnd == selectionEnd &&
            this.rangeInfo == rangeInfo &&
            this.liveRegion == liveRegion &&
            this.collectionInfo == collectionInfo &&
            this.collectionItemInfo == collectionItemInfo &&
            this.mergeDescendants == mergeDescendants &&
            this.excludeDescendants == excludeDescendants &&
            this.actions == actions
        ) {
            return
        }
        this.label = label
        this.role = role
        this.enabled = enabled
        this.focused = focused
        this.value = value
        this.hint = hint
        this.error = error
        this.selected = selected
        this.checked = checked
        this.expanded = expanded
        this.selectionStart = selectionStart
        this.selectionEnd = selectionEnd
        this.rangeInfo = rangeInfo
        this.liveRegion = liveRegion
        this.collectionInfo = collectionInfo
        this.collectionItemInfo = collectionItemInfo
        this.mergeDescendants = mergeDescendants
        this.excludeDescendants = excludeDescendants
        this.actions = actions
        markNeedsPaint()
    }

    /** Lays out the semantic boundary with exactly the rendered child's geometry. */
    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        size = renderChild?.size ?: RenderSize(constraints.maxWidth, constraints.maxHeight)
    }

    /** Paints the child without adding any visual artifact. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX, offsetY)
    }

    /** Forwards pointer hit testing unchanged because semantics is not a visual layer. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        renderChild?.hitTest(localX, localY, result)
    }

    /** Forwards click targets without changing gesture ownership. */
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) = renderChild?.collectClickTargets(offsetX, offsetY, targets) ?: Unit

    /** Forwards pager targets without changing scroll ownership. */
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) = renderChild?.collectPagerTargets(offsetX, offsetY, targets) ?: Unit

    /** Forwards list targets without changing scroll ownership. */
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) = renderChild?.collectListTargets(offsetX, offsetY, targets) ?: Unit

    /** Forwards scrollbar targets without changing drag ownership. */
    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) = renderChild?.collectScrollbarTargets(offsetX, offsetY, targets) ?: Unit

    /** Forwards pull-to-refresh targets without changing gesture ownership. */
    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) = renderChild?.collectRefreshTargets(offsetX, offsetY, targets) ?: Unit

    /** Forwards text-input targets without changing IME ownership. */
    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) = renderChild?.collectTextInputTargets(offsetX, offsetY, targets) ?: Unit

    /** Forwards slider targets without changing direct-manipulation ownership. */
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) = renderChild?.collectSliderTargets(offsetX, offsetY, targets) ?: Unit

    /**
     * Exports this boundary, then attaches, merges, or excludes the separately collected subtree.
     */
    override fun collectSemantics(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSemanticsTarget>,
    ) {
        /** The retained id remains unchanged across property updates and keyed sibling reorders. */
        val nodeId = semanticNodeId()
        /** Descendants are isolated so boundary policy can be applied atomically. */
        val descendantTargets = mutableListOf<PixelSemanticsTarget>()
        if (!excludeDescendants) {
            renderChild?.collectSemantics(offsetX, offsetY, descendantTargets)
        }
        if (mergeDescendants) {
            targets += mergedTarget(nodeId, offsetX, offsetY, descendantTargets)
            return
        }
        targets += ownTarget(nodeId, offsetX, offsetY)
        descendantTargets.forEach { descendant ->
            targets += if (descendant.node.parentId == null) {
                descendant.copy(node = descendant.node.copy(parentId = nodeId))
            } else {
                descendant
            }
        }
    }

    /** Builds this boundary's unmerged target and keeps executable actions beside the snapshot. */
    private fun ownTarget(nodeId: Long, offsetX: Int, offsetY: Int): PixelSemanticsTarget {
        /** Duplicate custom ids are collapsed deterministically before snapshot/callback export. */
        val effectiveActions = actions.withUniqueCustomIds()
        return PixelSemanticsTarget(
            node = snapshotNode(
                nodeId = nodeId,
                offsetX = offsetX,
                offsetY = offsetY,
                effectiveLabel = label,
                effectiveValue = value,
                effectiveHint = hint,
                effectiveError = error,
                effectiveFocused = focused,
                effectiveSelected = selected,
                effectiveChecked = checked,
                effectiveExpanded = expanded,
                effectiveSelectionStart = selectionStart,
                effectiveSelectionEnd = selectionEnd,
                effectiveRangeInfo = rangeInfo,
                effectiveLiveRegion = liveRegion,
                effectiveCollectionInfo = collectionInfo,
                effectiveCollectionItemInfo = collectionItemInfo,
                effectiveActions = effectiveActions,
            ),
            source = this,
            actions = effectiveActions,
        )
    }

    /** Folds descendant spoken properties and callbacks into one independently addressable node. */
    private fun mergedTarget(
        nodeId: Long,
        offsetX: Int,
        offsetY: Int,
        descendants: List<PixelSemanticsTarget>,
    ): PixelSemanticsTarget {
        /** Parent callbacks win; descendants fill only capabilities the boundary did not define. */
        val mergedActions = descendants.fold(actions.withUniqueCustomIds()) { accumulated, descendant ->
            accumulated.withFallback(descendant.actions)
        }
        /** Preorder distinct labels avoid the common Button + child Text duplicate utterance. */
        val mergedLabel = mergeSpokenStrings(label, descendants.map { descendant -> descendant.node.label })
        return PixelSemanticsTarget(
            node = snapshotNode(
                nodeId = nodeId,
                offsetX = offsetX,
                offsetY = offsetY,
                effectiveLabel = mergedLabel,
                effectiveValue = value ?: descendants.firstNotNullOfOrNull { it.node.value },
                effectiveHint = hint ?: descendants.firstNotNullOfOrNull { it.node.hint },
                effectiveError = error ?: descendants.firstNotNullOfOrNull { it.node.error },
                effectiveFocused = focused || descendants.any { it.node.focused },
                effectiveSelected = selected || descendants.any { it.node.selected },
                effectiveChecked = checked ?: descendants.firstNotNullOfOrNull { it.node.checked },
                effectiveExpanded = expanded ?: descendants.firstNotNullOfOrNull { it.node.expanded },
                effectiveSelectionStart = selectionStart.takeIf { it >= 0 }
                    ?: descendants.firstOrNull { it.node.selectionStart >= 0 }?.node?.selectionStart
                    ?: -1,
                effectiveSelectionEnd = selectionEnd.takeIf { it >= 0 }
                    ?: descendants.firstOrNull { it.node.selectionEnd >= 0 }?.node?.selectionEnd
                    ?: -1,
                effectiveRangeInfo = rangeInfo ?: descendants.firstNotNullOfOrNull { it.node.rangeInfo },
                effectiveLiveRegion = descendants.fold(liveRegion) { priority, descendant ->
                    maxOf(priority, descendant.node.liveRegion)
                },
                effectiveCollectionInfo = collectionInfo
                    ?: descendants.firstNotNullOfOrNull { it.node.collectionInfo },
                effectiveCollectionItemInfo = collectionItemInfo
                    ?: descendants.firstNotNullOfOrNull { it.node.collectionItemInfo },
                effectiveActions = mergedActions,
            ),
            source = this,
            actions = mergedActions,
            characterBoundsForRange = descendants.firstNotNullOfOrNull { descendant ->
                descendant.characterBoundsForRange
            },
        )
    }

    /** Creates the immutable frame snapshot shared by merged and ordinary collection paths. */
    @Suppress("LongParameterList")
    private fun snapshotNode(
        nodeId: Long,
        offsetX: Int,
        offsetY: Int,
        effectiveLabel: String,
        effectiveValue: String?,
        effectiveHint: String?,
        effectiveError: String?,
        effectiveFocused: Boolean,
        effectiveSelected: Boolean,
        effectiveChecked: Boolean?,
        effectiveExpanded: Boolean?,
        effectiveSelectionStart: Int,
        effectiveSelectionEnd: Int,
        effectiveRangeInfo: PixelSemanticsRangeInfo?,
        effectiveLiveRegion: PixelSemanticsLiveRegion,
        effectiveCollectionInfo: PixelSemanticsCollectionInfo?,
        effectiveCollectionItemInfo: PixelSemanticsCollectionItemInfo?,
        effectiveActions: PixelSemanticsActions,
    ): PixelSemanticsNode {
        return PixelSemanticsNode(
            label = effectiveLabel,
            role = role,
            enabled = enabled,
            focused = effectiveFocused,
            left = offsetX,
            top = offsetY,
            width = size.width,
            height = size.height,
            id = nodeId,
            value = effectiveValue,
            hint = effectiveHint,
            error = effectiveError,
            selected = effectiveSelected,
            checked = effectiveChecked,
            expanded = effectiveExpanded,
            selectionStart = effectiveSelectionStart,
            selectionEnd = effectiveSelectionEnd,
            rangeInfo = effectiveRangeInfo,
            liveRegion = effectiveLiveRegion,
            collectionInfo = effectiveCollectionInfo,
            collectionItemInfo = effectiveCollectionItemInfo,
            actions = effectiveActions.capabilitySet(),
            customActionLabels = effectiveActions.customActions.associate { custom ->
                custom.id to custom.label
            },
        )
    }

    /** Current render-box child retained for layout, paint, interaction, and semantics collection. */
    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/** Joins nonblank unique spoken fragments while retaining deterministic preorder. */
private fun mergeSpokenStrings(primary: String, descendants: List<String>): String {
    /** Linked insertion order is part of the stable merged-utterance contract. */
    val fragments = linkedSetOf<String>()
    if (primary.isNotBlank()) fragments += primary
    descendants.filterTo(fragments) { descendant -> descendant.isNotBlank() }
    return fragments.joinToString(separator = ", ")
}

/** Returns these callbacks with missing capabilities filled from [fallback]. */
private fun PixelSemanticsActions.withFallback(fallback: PixelSemanticsActions): PixelSemanticsActions {
    /** Custom ids are node-local; the owning boundary keeps precedence on collisions. */
    val mergedCustomActions = linkedMapOf<String, PixelSemanticsCustomAction>()
    customActions.forEach { action -> mergedCustomActions.putIfAbsent(action.id, action) }
    fallback.customActions.forEach { action -> mergedCustomActions.putIfAbsent(action.id, action) }
    return PixelSemanticsActions(
        onClick = onClick ?: fallback.onClick,
        onLongClick = onLongClick ?: fallback.onLongClick,
        onScrollForward = onScrollForward ?: fallback.onScrollForward,
        onScrollBackward = onScrollBackward ?: fallback.onScrollBackward,
        onSetText = onSetText ?: fallback.onSetText,
        onSetSelection = onSetSelection ?: fallback.onSetSelection,
        onSetProgress = onSetProgress ?: fallback.onSetProgress,
        onDismiss = onDismiss ?: fallback.onDismiss,
        onExpand = onExpand ?: fallback.onExpand,
        onCollapse = onCollapse ?: fallback.onCollapse,
        customActions = mergedCustomActions.values.toList(),
    )
}

/** Collapses duplicate node-local custom ids while retaining the first declared callback. */
private fun PixelSemanticsActions.withUniqueCustomIds(): PixelSemanticsActions {
    /** Linked order preserves the public custom action display order. */
    val uniqueActions = linkedMapOf<String, PixelSemanticsCustomAction>()
    customActions.forEach { action -> uniqueActions.putIfAbsent(action.id, action) }
    return if (uniqueActions.size == customActions.size) {
        this
    } else {
        copy(customActions = uniqueActions.values.toList())
    }
}
