package com.purride.pixelui

import com.purride.pixelui.internal.SemanticsWidget

/** 定义 `PixelSemanticRole` 在 `PixelSemantics` 中承担的数据与行为边界。
 *
 * Android-compatible semantic roles exposed by one virtual accessibility node.
 */
public enum class PixelSemanticRole {
    /** Read-only text content. */
    TEXT,

    /** Activatable push button. */
    BUTTON,

    /** Editable or read-only text input. */
    TEXT_FIELD,

    /** Two-state checkbox. */
    CHECKBOX,

    /** Two-state switch. */
    SWITCH,

    /** Mutually exclusive radio button. */
    RADIO_BUTTON,

    /** Adjustable numeric range. */
    SLIDER,

    /** Read-only determinate or indeterminate progress indicator. */
    PROGRESS_BAR,

    /** Selectable tab. */
    TAB,

    /** Informational image. */
    IMAGE,

    /** Activatable hyperlink. */
    LINK,

    /** Collection container such as a list or grid. */
    LIST,

    /** One item inside a collection. */
    LIST_ITEM,

    /** Scrollable viewport without collection metadata. */
    SCROLL_VIEW,

    /** Modal or modeless dialog surface. */
    DIALOG,

    /** Menu container. */
    MENU,

    /** Activatable menu entry. */
    MENU_ITEM,

    /** Node without a more specific platform role. */
    GENERIC,
}

/** 定义 `PixelSemanticsLiveRegion` 在 `PixelSemantics` 中承担的数据与行为边界。
 *
 * Announcement priority used when a semantic node changes without direct focus.
 */
public enum class PixelSemanticsLiveRegion {
    /** Changes are not announced automatically. */
    NONE,

    /** Changes are announced after the current utterance. */
    POLITE,

    /** Changes may interrupt the current utterance. */
    ASSERTIVE,
}

/** 定义 `PixelSemanticsSelectionMode` 在 `PixelSemantics` 中承担的数据与行为边界。
 *
 * Selection policy declared by a semantic collection.
 */
public enum class PixelSemanticsSelectionMode {
    /** The collection does not expose selectable items. */
    NONE,

    /** At most one item can be selected. */
    SINGLE,

    /** Multiple items can be selected. */
    MULTIPLE,
}

/**
 * 定义 `PixelSemanticsRangeInfo` 在 `PixelSemantics` 中承担的数据与行为边界。
 *
 * Numeric range metadata for sliders and other adjustable controls.
 *
 * @property current Current value, clamped by the caller to [minimum]..[maximum].
 * @property minimum Inclusive lower bound.
 * @property maximum Inclusive upper bound.
 * @property steps Number of discrete values between the two endpoints, or `0` for continuous input.
 */
public data class PixelSemanticsRangeInfo(
    public val current: Float,
    public val minimum: Float = 0f,
    public val maximum: Float = 1f,
    public val steps: Int = 0,
) {
    init {
        require(minimum.isFinite() && maximum.isFinite() && current.isFinite()) {
            "Semantic range values must be finite."
        }
        require(maximum >= minimum) { "Semantic range maximum must be >= minimum." }
        require(current in minimum..maximum) { "Semantic range current must be within its bounds." }
        require(steps >= 0) { "Semantic range steps must be >= 0." }
    }
}

/**
 * 定义 `PixelSemanticsCollectionInfo` 在 `PixelSemantics` 中承担的数据与行为边界。
 *
 * Row and column metadata for a collection container.
 *
 * @property rowCount Logical row count, or `-1` when it is not known yet.
 * @property columnCount Logical column count, or `-1` when it is not known yet.
 * @property hierarchical Whether items may contain nested collection levels.
 * @property selectionMode Selection policy announced for the collection.
 */
public data class PixelSemanticsCollectionInfo(
    public val rowCount: Int,
    public val columnCount: Int,
    public val hierarchical: Boolean = false,
    public val selectionMode: PixelSemanticsSelectionMode = PixelSemanticsSelectionMode.NONE,
) {
    init {
        require(rowCount >= -1) { "Semantic collection rowCount must be >= -1." }
        require(columnCount >= -1) { "Semantic collection columnCount must be >= -1." }
    }
}

/**
 * 定义 `PixelSemanticsCollectionItemInfo` 在 `PixelSemantics` 中承担的数据与行为边界。
 *
 * Position and span metadata for one item inside a semantic collection.
 *
 * @property rowIndex Zero-based item row.
 * @property rowSpan Number of occupied rows.
 * @property columnIndex Zero-based item column.
 * @property columnSpan Number of occupied columns.
 * @property heading Whether the item acts as a collection heading.
 * @property selected Whether this item is currently selected.
 */
public data class PixelSemanticsCollectionItemInfo(
    public val rowIndex: Int,
    public val rowSpan: Int = 1,
    public val columnIndex: Int,
    public val columnSpan: Int = 1,
    public val heading: Boolean = false,
    public val selected: Boolean = false,
) {
    init {
        require(rowIndex >= 0) { "Semantic collection item rowIndex must be >= 0." }
        require(columnIndex >= 0) { "Semantic collection item columnIndex must be >= 0." }
        require(rowSpan > 0) { "Semantic collection item rowSpan must be > 0." }
        require(columnSpan > 0) { "Semantic collection item columnSpan must be > 0." }
    }
}

/** 定义 `PixelSemanticsAction` 在 `PixelSemantics` 中承担的数据与行为边界。
 *
 * Actions that an accessibility service may request from a semantic node.
 */
public enum class PixelSemanticsAction {
    /** Activate the node. */
    CLICK,

    /** Activate the node's long-press behavior. */
    LONG_CLICK,

    /** Move a viewport toward later content. */
    SCROLL_FORWARD,

    /** Move a viewport toward earlier content. */
    SCROLL_BACKWARD,

    /** Replace editable text. */
    SET_TEXT,

    /** Change a text selection. */
    SET_SELECTION,

    /** Change a numeric range value. */
    SET_PROGRESS,

    /** Dismiss a transient surface. */
    DISMISS,

    /** Expand a collapsed control. */
    EXPAND,

    /** Collapse an expanded control. */
    COLLAPSE,

    /** Invoke an application-defined action. */
    CUSTOM,
}

/**
 * 定义 `PixelSemanticsCustomAction` 在 `PixelSemantics` 中承担的数据与行为边界。
 *
 * Application-defined semantic action with a stable identifier and spoken label.
 *
 * @property id Stable identifier within the owning semantic node.
 * @property label Localized label announced by the accessibility service.
 * @property onInvoke Callback returning whether the action was handled.
 */
public data class PixelSemanticsCustomAction(
    public val id: String,
    public val label: String,
    public val onInvoke: () -> Boolean,
) {
    init {
        require(id.isNotBlank()) { "Semantic custom action id must not be blank." }
        require(label.isNotBlank()) { "Semantic custom action label must not be blank." }
    }
}

/**
 * 定义 `PixelSemanticsActions` 在 `PixelSemantics` 中承担的数据与行为边界。
 *
 * Typed callbacks owned by one semantic node.
 *
 * A callback is advertised to Android only when it is non-null. Returning `false` tells the
 * accessibility service that the request could not be completed.
 */
public data class PixelSemanticsActions(
    /** 保存 `PixelSemantics` 在 `onClick` 时调用的事件回调。
 *
 * Handles a normal activation.
 */
    public val onClick: (() -> Boolean)? = null,
    /** 保存 `PixelSemantics` 在 `onLongClick` 时调用的事件回调。
 *
 * Handles a long activation.
 */
    public val onLongClick: (() -> Boolean)? = null,
    /** 保存 `PixelSemantics` 在 `onScrollForward` 时调用的事件回调。
 *
 * Scrolls toward later content.
 */
    public val onScrollForward: (() -> Boolean)? = null,
    /** 保存 `PixelSemantics` 在 `onScrollBackward` 时调用的事件回调。
 *
 * Scrolls toward earlier content.
 */
    public val onScrollBackward: (() -> Boolean)? = null,
    /** 保存 `PixelSemantics` 在 `onSetText` 时调用的事件回调。
 *
 * Replaces editable text.
 */
    public val onSetText: ((String) -> Boolean)? = null,
    /** 保存 `PixelSemantics` 在 `onSetSelection` 时调用的事件回调。
 *
 * Changes the inclusive-start/exclusive-end text selection.
 */
    public val onSetSelection: ((Int, Int) -> Boolean)? = null,
    /** 保存 `PixelSemantics` 在 `onSetProgress` 时调用的事件回调。
 *
 * Changes a numeric range value.
 */
    public val onSetProgress: ((Float) -> Boolean)? = null,
    /** 保存 `PixelSemantics` 在 `onDismiss` 时调用的事件回调。
 *
 * Dismisses a transient surface.
 */
    public val onDismiss: (() -> Boolean)? = null,
    /** 保存 `PixelSemantics` 在 `onExpand` 时调用的事件回调。
 *
 * Expands a collapsed control.
 */
    public val onExpand: (() -> Boolean)? = null,
    /** 保存 `PixelSemantics` 在 `onCollapse` 时调用的事件回调。
 *
 * Collapses an expanded control.
 */
    public val onCollapse: (() -> Boolean)? = null,
    /** 公开 `PixelSemantics` 的 `customActions` 配置或运行值。
 *
 * Application-defined actions in stable display order.
 */
    public val customActions: List<PixelSemanticsCustomAction> = emptyList(),
) {
    init {
        require(customActions.map(PixelSemanticsCustomAction::id).distinct().size == customActions.size) {
            "Semantic custom action ids must be unique within one node."
        }
    }

    /** 执行 `PixelSemantics` 的 `capabilitySet` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the immutable capability set exported in [PixelSemanticsNode].
 */
    public fun capabilitySet(): Set<PixelSemanticsAction> = buildSet {
        if (onClick != null) add(PixelSemanticsAction.CLICK)
        if (onLongClick != null) add(PixelSemanticsAction.LONG_CLICK)
        if (onScrollForward != null) add(PixelSemanticsAction.SCROLL_FORWARD)
        if (onScrollBackward != null) add(PixelSemanticsAction.SCROLL_BACKWARD)
        if (onSetText != null) add(PixelSemanticsAction.SET_TEXT)
        if (onSetSelection != null) add(PixelSemanticsAction.SET_SELECTION)
        if (onSetProgress != null) add(PixelSemanticsAction.SET_PROGRESS)
        if (onDismiss != null) add(PixelSemanticsAction.DISMISS)
        if (onExpand != null) add(PixelSemanticsAction.EXPAND)
        if (onCollapse != null) add(PixelSemanticsAction.COLLAPSE)
        if (customActions.isNotEmpty()) add(PixelSemanticsAction.CUSTOM)
    }
}

/**
 * 定义 `PixelSemanticsNode` 在 `PixelSemantics` 中承担的数据与行为边界。
 *
 * Immutable semantic tree node produced by one rendered frame.
 *
 * [id] remains stable while the retained render node lives; [parentId] forms the virtual tree.
 * Geometry is expressed in logical pixel-engine coordinates.
 */
public data class PixelSemanticsNode(
    /** 公开 `PixelSemantics` 的 `label` 配置或运行值。
 *
 * Primary spoken label.
 */
    public val label: String,
    /** 公开 `PixelSemantics` 的 `role` 配置或运行值。
 *
 * Platform role mapping.
 */
    public val role: PixelSemanticRole,
    /** 表示 `PixelSemantics` 当前是否满足 `enabled` 对应条件。
 *
 * Whether actions may mutate the node.
 */
    public val enabled: Boolean,
    /** 公开 `PixelSemantics` 的 `focused` 配置或运行值。
 *
 * Whether the node owns input focus.
 */
    public val focused: Boolean,
    /** 公开 `PixelSemantics` 的 `left` 配置或运行值。
 *
 * Logical left coordinate.
 */
    public val left: Int,
    /** 公开 `PixelSemantics` 的 `top` 配置或运行值。
 *
 * Logical top coordinate.
 */
    public val top: Int,
    /** 定义 `PixelSemantics` 的 `width` 逻辑像素度量。
 *
 * Logical width.
 */
    public val width: Int,
    /** 定义 `PixelSemantics` 的 `height` 逻辑像素度量。
 *
 * Logical height.
 */
    public val height: Int,
    /** 公开 `PixelSemantics` 的 `id` 配置或运行值。
 *
 * 由 `RenderObject.semanticNodeId` 分配的稳定正整数标识。
 *
 * Stable positive identifier allocated by `RenderObject.semanticNodeId`.
 */
    public val id: Long,
    /** 公开 `PixelSemantics` 的 `parentId` 配置或运行值。
 *
 * Stable identifier of the direct semantic parent, or `null` for a Host child.
 */
    public val parentId: Long? = null,
    /** 公开 `PixelSemantics` 的 `value` 配置或运行值。
 *
 * Current value announced separately from [label].
 */
    public val value: String? = null,
    /** 公开 `PixelSemantics` 的 `hint` 配置或运行值。
 *
 * Input or usage hint.
 */
    public val hint: String? = null,
    /** 公开 `PixelSemantics` 的 `error` 配置或运行值。
 *
 * Validation error associated with the node.
 */
    public val error: String? = null,
    /** 公开 `PixelSemantics` 的 `selected` 配置或运行值。
 *
 * Whether the node is selected.
 */
    public val selected: Boolean = false,
    /** 公开 `PixelSemantics` 的 `checked` 配置或运行值。
 *
 * Checked state, or `null` when the node is not checkable.
 */
    public val checked: Boolean? = null,
    /** 公开 `PixelSemantics` 的 `expanded` 配置或运行值。
 *
 * Expanded state, or `null` when expansion is not applicable.
 */
    public val expanded: Boolean? = null,
    /** 公开 `PixelSemantics` 的 `selectionStart` 配置或运行值。
 *
 * Current selection start for text controls, or `-1` when unavailable.
 */
    public val selectionStart: Int = -1,
    /** 公开 `PixelSemantics` 的 `selectionEnd` 配置或运行值。
 *
 * Current selection end for text controls, or `-1` when unavailable.
 */
    public val selectionEnd: Int = -1,
    /** 公开 `PixelSemantics` 的 `rangeInfo` 配置或运行值。
 *
 * Numeric range information for adjustable controls.
 */
    public val rangeInfo: PixelSemanticsRangeInfo? = null,
    /** 公开 `PixelSemantics` 的 `liveRegion` 配置或运行值。
 *
 * Automatic announcement policy.
 */
    public val liveRegion: PixelSemanticsLiveRegion = PixelSemanticsLiveRegion.NONE,
    /** 公开 `PixelSemantics` 的 `collectionInfo` 配置或运行值。
 *
 * Collection metadata when this node is a list or grid.
 */
    public val collectionInfo: PixelSemanticsCollectionInfo? = null,
    /** 公开 `PixelSemantics` 的 `collectionItemInfo` 配置或运行值。
 *
 * Position metadata when this node belongs to a collection.
 */
    public val collectionItemInfo: PixelSemanticsCollectionItemInfo? = null,
    /** 公开 `PixelSemantics` 的 `actions` 配置或运行值。
 *
 * Typed action capabilities available in this frame.
 */
    public val actions: Set<PixelSemanticsAction> = emptySet(),
    /** 公开 `PixelSemantics` 的 `customActionLabels` 配置或运行值。
 *
 * Stable id-to-label pairs for custom actions, without executable callbacks.
 */
    public val customActionLabels: Map<String, String> = emptyMap(),
) {
    init {
        require(id > 0L) { "PixelSemanticsNode.id must be a positive allocated semantic id" }
    }
}

/**
 * 执行 `PixelSemantics` 的 `Semantics` 公开行为；具体参数、返回和副作用见下文。
 *
 * Adds one semantic boundary around [child].
 *
 * Existing call sites may keep using the first six parameters. State and action parameters are
 * optional so a simple label remains concise. [mergeDescendants] folds descendant labels and
 * actions into this node; [excludeDescendants] hides descendants without merging them.
 */
@Suppress("LongParameterList")
public fun Semantics(
    label: String,
    child: Widget,
    role: PixelSemanticRole = PixelSemanticRole.GENERIC,
    enabled: Boolean = true,
    focused: Boolean = false,
    key: Any? = null,
    value: String? = null,
    hint: String? = null,
    error: String? = null,
    selected: Boolean = false,
    checked: Boolean? = null,
    expanded: Boolean? = null,
    selectionStart: Int = -1,
    selectionEnd: Int = -1,
    rangeInfo: PixelSemanticsRangeInfo? = null,
    liveRegion: PixelSemanticsLiveRegion = PixelSemanticsLiveRegion.NONE,
    collectionInfo: PixelSemanticsCollectionInfo? = null,
    collectionItemInfo: PixelSemanticsCollectionItemInfo? = null,
    mergeDescendants: Boolean = false,
    excludeDescendants: Boolean = false,
    actions: PixelSemanticsActions = PixelSemanticsActions(),
): Widget {
    require(!(mergeDescendants && excludeDescendants)) {
        "Semantics cannot merge and exclude descendants at the same time."
    }
    return SemanticsWidget(
        label = label,
        role = role,
        enabled = enabled,
        focused = focused,
        value = value,
        hint = hint,
        error = error,
        selected = selected,
        checked = checked,
        expanded = expanded,
        selectionStart = selectionStart,
        selectionEnd = selectionEnd,
        rangeInfo = rangeInfo,
        liveRegion = liveRegion,
        collectionInfo = collectionInfo,
        collectionItemInfo = collectionItemInfo,
        mergeDescendants = mergeDescendants,
        excludeDescendants = excludeDescendants,
        actions = actions,
        child = child,
        key = key,
    )
}
