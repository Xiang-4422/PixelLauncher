package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelSemanticsActions
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelRefreshIndicatorState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import kotlin.math.max

/**
 * 像素渲染阶段使用的二维尺寸。
 */
internal data class PixelSize(
    val width: Int,
    val height: Int,
)

/**
 * 像素渲染阶段使用的矩形区域。
 */
public data class PixelRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    /**
     * 矩形右边界的开区间坐标。
     */
    val right: Int
        get() = left + width

    /**
     * 矩形下边界的开区间坐标。
     */
    val bottom: Int
        get() = top + height

    /**
     * 判断指定点是否位于当前矩形内部。
     */
    public fun contains(x: Int, y: Int): Boolean {
        return x in left until right && y in top until bottom
    }

    /**
     * 按四边内缩矩形，并保证结果不会产生负尺寸。
     */
    public fun inset(
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int,
    ): PixelRect {
        val nextLeft = (left + paddingLeft).coerceAtMost(right)
        val nextTop = (top + paddingTop).coerceAtMost(bottom)
        val nextRight = (right - paddingRight).coerceAtLeast(nextLeft)
        val nextBottom = (bottom - paddingBottom).coerceAtLeast(nextTop)
        return PixelRect(
            left = nextLeft,
            top = nextTop,
            width = nextRight - nextLeft,
            height = nextBottom - nextTop,
        )
    }

    /**
     * 平移当前矩形。
     */
    public fun translate(deltaX: Int, deltaY: Int): PixelRect {
        return PixelRect(
            left = left + deltaX,
            top = top + deltaY,
            width = width,
            height = height,
        )
    }

    /**
     * 计算当前矩形与另一个矩形的交集。
     */
    public fun intersect(other: PixelRect): PixelRect? {
        val nextLeft = max(left, other.left)
        val nextTop = max(top, other.top)
        val nextRight = minOf(right, other.right)
        val nextBottom = minOf(bottom, other.bottom)
        if (nextRight <= nextLeft || nextBottom <= nextTop) {
            return null
        }
        return PixelRect(
            left = nextLeft,
            top = nextTop,
            width = nextRight - nextLeft,
            height = nextBottom - nextTop,
        )
    }
}

/**
 * pipeline 内部使用的最大尺寸约束。
 */
internal data class PixelConstraints(
    val maxWidth: Int,
    val maxHeight: Int,
) {
    /**
     * 按四边 padding 收缩约束。
     */
    fun shrink(
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int,
    ): PixelConstraints {
        return PixelConstraints(
            maxWidth = (maxWidth - paddingLeft - paddingRight).coerceAtLeast(0),
            maxHeight = (maxHeight - paddingTop - paddingBottom).coerceAtLeast(0),
        )
    }
}

/**
 * 点击命中目标。
 */
public data class PixelClickTarget(
    val bounds: PixelRect,
    val onClick: () -> Unit,
    /** Reports pointer press ownership changes for component micro-state animation. */
    val onPressedChanged: ((Boolean) -> Unit)? = null,
    /** Reports mouse or stylus hover ownership changes without consuming touch exploration. */
    val onHoveredChanged: ((Boolean) -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,
    val onDoubleTap: (() -> Unit)? = null,
    val onSwipeStart: (() -> Unit)? = null,
    val onSwipeUpdate: ((Int) -> Unit)? = null,
    val onSwipeEnd: ((Int) -> Unit)? = null,
    val onSwipeLeft: (() -> Unit)? = null,
    val onSwipeRight: (() -> Unit)? = null,
    val source: RenderObject? = null,
) {
    val hasSwipe: Boolean
        get() = onSwipeStart != null ||
            onSwipeUpdate != null ||
            onSwipeEnd != null ||
            onSwipeLeft != null ||
            onSwipeRight != null
}

/**
 * 分页视口命中目标。
 */
public data class PixelPagerTarget(
    val bounds: PixelRect,
    val axis: PixelAxis,
    val state: PixelPagerState,
    val controller: PixelPagerController,
    val onPageChanged: ((Int) -> Unit)?,
    val onPageDragStart: (() -> Unit)?,
    val source: RenderObject? = null,
)

/**
 * 列表视口命中目标。
 */
public data class PixelListTarget(
    val bounds: PixelRect,
    val viewportHeightPx: Int,
    val contentHeightPx: Int,
    val state: PixelListState,
    val controller: PixelListController,
    val source: RenderObject? = null,
)

/**
 * 滚动条命中目标。
 */
public data class PixelScrollbarTarget(
    /** Full logical hit bounds of the scrollbar track. */
    val bounds: PixelRect,
    /** Current proportional thumb bounds used to preserve pointer grab offset. */
    val thumbBounds: PixelRect,
    /** Visible list extent used by controller clamping. */
    val viewportHeightPx: Int,
    /** Total list extent used by controller clamping. */
    val contentHeightPx: Int,
    /** Mutable list state shared with the wrapped viewport. */
    val state: PixelListState,
    /** Controller that owns scrollbar drag mutation. */
    val controller: PixelListController,
    /** Optional retained callback for pressed-state ownership. */
    val onPressedChanged: ((Boolean) -> Unit)? = null,
    /** Optional retained callback for mouse/stylus hover ownership. */
    val onHoveredChanged: ((Boolean) -> Unit)? = null,
    /** Retained render identity used to migrate ownership between snapshots. */
    val source: RenderObject? = null,
)

/**
 * 下拉刷新命中目标。
 */
public data class PixelRefreshTarget(
    /** Full logical refresh-boundary hit bounds. */
    val bounds: PixelRect,
    /** Positive pull distance required to enter the armed phase. */
    val thresholdPx: Int,
    /** Whether this snapshot accepts a new pull lifecycle. */
    val enabled: Boolean,
    /** Optional wrapped list used to reject pulls away from its leading edge. */
    val sourceListState: PixelListState?,
    /** Mutable pull and refresh lifecycle state. */
    val state: PixelRefreshIndicatorState,
    /** Controller that owns lifecycle mutation and notifications. */
    val controller: PixelRefreshIndicatorController,
    /** Business callback invoked after a unique successful pull. */
    val onRefresh: () -> Unit,
    /** Optional retained callback for pressed-state ownership. */
    val onPressedChanged: ((Boolean) -> Unit)? = null,
    /** Optional retained callback for mouse/stylus hover ownership. */
    val onHoveredChanged: ((Boolean) -> Unit)? = null,
    /** Retained render identity used to migrate ownership between snapshots. */
    val source: RenderObject? = null,
) {
    /** 判断 `PixelRenderPrimitives` 是否满足 `canStartPull` 条件，不修改现有状态。
 *
 * Returns whether [deltaPx] may begin a new pull from the wrapped list's leading edge.
 */
    public fun canStartPull(deltaPx: Float): Boolean {
        if (!enabled || state.isRefreshing || deltaPx <= 0f) return false
        val listState = sourceListState ?: return true
        return listState.scrollOffsetPx <= 0f
    }
}

/**
 * 滑块命中目标。onDrag 在手指移动时调用（值 0..1），onRelease 在抬手时调用。
 */
public data class PixelSliderTarget(
    val bounds: PixelRect,
    val onDrag: (Float) -> Unit,
    val onRelease: (Float) -> Unit,
    /** Reports whether this slider currently owns an active pointer press. */
    val onPressedChanged: ((Boolean) -> Unit)? = null,
    /** Reports mouse or stylus hover entry and exit for slider visual feedback. */
    val onHoveredChanged: ((Boolean) -> Unit)? = null,
    val source: RenderObject? = null,
)

/**
 * 文本输入命中目标。
 */
public data class PixelTextInputTarget(
    /** Absolute logical hit and semantic bounds of the editable surface. */
    val bounds: PixelRect,
    /** Immutable editing value captured for this render frame. */
    val state: PixelTextFieldState,
    /** Controller that applies normalized editing commands. */
    val controller: PixelTextFieldController,
    /** Whether mutating text actions are disabled. */
    val readOnly: Boolean,
    /** Whether this target requests focus when first mounted. */
    val autofocus: Boolean,
    /** Minimum visible text line count. */
    val minLines: Int,
    /** Maximum visible text line count. */
    val maxLines: Int,
    /** Platform input classification requested by this target. */
    val inputType: com.purride.pixelui.PixelInputType,
    /** IME action requested by this target. */
    val action: PixelTextInputAction,
    /** Host-local focus node owning this target. */
    val focusNode: com.purride.pixelui.FocusNode? = null,
    /** Callback receiving accepted backing-text changes. */
    val onChanged: ((String) -> Unit)?,
    /** Callback receiving accepted IME submissions. */
    val onSubmitted: ((String) -> Unit)?,
    /** Maps an absolute logical point to a grapheme-safe UTF-16 boundary. */
    val textIndexAt: ((Int, Int) -> Int)? = null,
    /** Returns the absolute logical caret bounds for one UTF-16 boundary. */
    val caretBoundsForIndex: ((Int) -> PixelRect)? = null,
    /** Returns one absolute logical rectangle per requested UTF-16 code unit. */
    val characterBoundsForRange: ((Int, Int) -> List<PixelRect?>)? = null,
    /** Retained render owner used for exact target association. */
    val source: RenderObject? = null,
)

/** 定义 `PixelSemanticsTarget` 在 `PixelRenderPrimitives` 中承担的数据或执行职责，并保持公开不变量稳定。 */
public data class PixelSemanticsTarget(
    /** Immutable node properties exported in the current frame. */
    val node: PixelSemanticsNode,
    /** Retained render owner used to correlate actions and diagnostics. */
    val source: RenderObject? = null,
    /** Executable callbacks corresponding to [PixelSemanticsNode.actions]. */
    val actions: PixelSemanticsActions = PixelSemanticsActions(),
    /** Returns one absolute logical rectangle per requested UTF-16 code unit. */
    val characterBoundsForRange: ((Int, Int) -> List<PixelRect?>)? = null,
)

/**
 * pipeline 渲染结果。
 */
public data class PixelRenderResult(
    val buffer: PixelBuffer,
    val clickTargets: List<PixelClickTarget>,
    val pagerTargets: List<PixelPagerTarget>,
    val listTargets: List<PixelListTarget>,
    val scrollbarTargets: List<PixelScrollbarTarget>,
    val refreshTargets: List<PixelRefreshTarget>,
    val textInputTargets: List<PixelTextInputTarget>,
    val sliderTargets: List<PixelSliderTarget>,
    val semanticsNodes: List<PixelSemanticsNode>,
    val semanticsTargets: List<PixelSemanticsTarget> = emptyList(),
)

/**
 * 单次 pipeline 渲染期间收集的可变会话。
 */
internal data class PixelRenderSession(
    val buffer: PixelBuffer,
    val clickTargets: MutableList<PixelClickTarget> = mutableListOf(),
    val pagerTargets: MutableList<PixelPagerTarget> = mutableListOf(),
    val listTargets: MutableList<PixelListTarget> = mutableListOf(),
    val scrollbarTargets: MutableList<PixelScrollbarTarget> = mutableListOf(),
    val refreshTargets: MutableList<PixelRefreshTarget> = mutableListOf(),
    val textInputTargets: MutableList<PixelTextInputTarget> = mutableListOf(),
    val sliderTargets: MutableList<PixelSliderTarget> = mutableListOf(),
    val semanticsTargets: MutableList<PixelSemanticsTarget> = mutableListOf(),
) {
    /**
     * 固化当前会话为对外渲染结果。
     */
    fun toRenderResult(): PixelRenderResult {
        /** Every target source participates in selecting the newest active modal boundary. */
        val targetSources = buildList {
            addAll(clickTargets.map(PixelClickTarget::source))
            addAll(pagerTargets.map(PixelPagerTarget::source))
            addAll(listTargets.map(PixelListTarget::source))
            addAll(scrollbarTargets.map(PixelScrollbarTarget::source))
            addAll(refreshTargets.map(PixelRefreshTarget::source))
            addAll(textInputTargets.map(PixelTextInputTarget::source))
            addAll(sliderTargets.map(PixelSliderTarget::source))
            addAll(semanticsTargets.map(PixelSemanticsTarget::source))
        }
        /** Selected activation and route order represent the top logical modal for this frame. */
        val modalFilter = highestActiveModalFilter(targetSources)
        /** Click targets exported after a modal sibling are still removed by ancestry, not order. */
        val effectiveClickTargets = clickTargets.filterForModal(modalFilter, PixelClickTarget::source)
        /** Pager targets outside the active modal cannot retain gesture ownership. */
        val effectivePagerTargets = pagerTargets.filterForModal(modalFilter, PixelPagerTarget::source)
        /** List targets outside the active modal cannot retain scroll ownership. */
        val effectiveListTargets = listTargets.filterForModal(modalFilter, PixelListTarget::source)
        /** Scrollbar targets outside the active modal cannot retain drag ownership. */
        val effectiveScrollbarTargets = scrollbarTargets.filterForModal(modalFilter, PixelScrollbarTarget::source)
        /** Refresh targets outside the active modal cannot retain pull ownership. */
        val effectiveRefreshTargets = refreshTargets.filterForModal(modalFilter, PixelRefreshTarget::source)
        /** Text fields outside the active modal cannot retain IME ownership. */
        val effectiveTextInputTargets = textInputTargets.filterForModal(modalFilter, PixelTextInputTarget::source)
        /** Sliders outside the active modal cannot retain direct-manipulation ownership. */
        val effectiveSliderTargets = sliderTargets.filterForModal(modalFilter, PixelSliderTarget::source)
        /** Private modal markers select the scope but are never exposed to clients or Android. */
        val effectiveSemanticsTargets = semanticsTargets
            .filterNot { target -> target.source is RenderModalInteractionScope }
            .filterForModal(modalFilter, PixelSemanticsTarget::source)
            .repairParentsAfterModalFiltering()
        return PixelRenderResult(
            buffer = buffer,
            clickTargets = effectiveClickTargets,
            pagerTargets = effectivePagerTargets,
            listTargets = effectiveListTargets,
            scrollbarTargets = effectiveScrollbarTargets,
            refreshTargets = effectiveRefreshTargets,
            textInputTargets = effectiveTextInputTargets,
            sliderTargets = effectiveSliderTargets,
            semanticsNodes = effectiveSemanticsTargets.map(PixelSemanticsTarget::node),
            semanticsTargets = effectiveSemanticsTargets,
        )
    }
}

/** Keeps every target without a modal, otherwise only the modal and higher hosted routes. */
private fun <T> Iterable<T>.filterForModal(
    modalFilter: ActiveModalFilter?,
    source: (T) -> RenderObject?,
): List<T> {
    if (modalFilter == null) return toList()
    return filter { target -> sourceAllowedByModal(source(target), modalFilter) }
}

/** Removes parent references to semantic nodes excluded by the selected modal scope. */
private fun List<PixelSemanticsTarget>.repairParentsAfterModalFiltering(): List<PixelSemanticsTarget> {
    val retainedIds = mapTo(mutableSetOf()) { target -> target.node.id }
    return map { target ->
        val parentId = target.node.parentId
        if (parentId == null || parentId in retainedIds) {
            target
        } else {
            target.copy(node = target.node.copy(parentId = null))
        }
    }
}
