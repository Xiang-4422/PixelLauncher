package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsActions
import com.purride.pixelui.PixelSemanticsCollectionInfo
import com.purride.pixelui.PixelSemanticsCollectionItemInfo
import com.purride.pixelui.PixelSemanticsLiveRegion
import com.purride.pixelui.PixelSemanticsRangeInfo
import com.purride.pixelui.Widget

/** Retained widget carrying the complete public semantics configuration into the render tree. */
@Suppress("LongParameterList")
internal data class SemanticsWidget(
    /** Primary spoken label. */
    val label: String,
    /** Platform accessibility role. */
    val role: PixelSemanticRole,
    /** Whether mutating actions are currently available. */
    val enabled: Boolean,
    /** Whether the node owns input focus. */
    val focused: Boolean,
    /** Current value announced separately from the label. */
    val value: String?,
    /** Usage or input hint. */
    val hint: String?,
    /** Validation error. */
    val error: String?,
    /** Current selection state. */
    val selected: Boolean,
    /** Checked state, or null for non-checkable nodes. */
    val checked: Boolean?,
    /** Expanded state, or null when expansion is not applicable. */
    val expanded: Boolean?,
    /** Text selection start, or -1 when unavailable. */
    val selectionStart: Int,
    /** Text selection end, or -1 when unavailable. */
    val selectionEnd: Int,
    /** Adjustable range metadata. */
    val rangeInfo: PixelSemanticsRangeInfo?,
    /** Automatic announcement policy. */
    val liveRegion: PixelSemanticsLiveRegion,
    /** Collection container metadata. */
    val collectionInfo: PixelSemanticsCollectionInfo?,
    /** Collection item metadata. */
    val collectionItemInfo: PixelSemanticsCollectionItemInfo?,
    /** Whether descendant nodes are folded into this boundary. */
    val mergeDescendants: Boolean,
    /** Whether descendant nodes are hidden from the semantic tree. */
    val excludeDescendants: Boolean,
    /** Typed action callbacks owned by this boundary. */
    val actions: PixelSemanticsActions,
    /** Rendered child subtree. */
    override val child: Widget,
    /** Retained element identity. */
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    /** Creates the retained semantic boundary. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSemantics(
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
        )
    }

    /** Updates one retained boundary without replacing its stable semantic identity. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSemantics).updateSemantics(
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
        )
    }
}
