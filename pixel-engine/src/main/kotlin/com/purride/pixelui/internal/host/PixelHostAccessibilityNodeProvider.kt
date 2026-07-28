package com.purride.pixelui.internal.host

import android.graphics.Rect
import android.graphics.RectF
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Trace
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelGridGeometry
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelGraphemeBoundaryMap
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelSemanticsActions
import com.purride.pixelui.PixelSemanticsCollectionInfo
import com.purride.pixelui.PixelSemanticsCollectionItemInfo
import com.purride.pixelui.PixelSemanticsLiveRegion
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelSemanticsRangeInfo
import com.purride.pixelui.PixelSemanticsSelectionMode
import com.purride.pixelui.PixelTextEditAction
import com.purride.pixelui.clipboardTextOrNull
import com.purride.pixelui.internal.PixelListTarget
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelRect
import com.purride.pixelui.internal.PixelRenderResult
import com.purride.pixelui.internal.PixelSemanticsTarget
import com.purride.pixelui.internal.PixelTextInputTarget
import java.util.IdentityHashMap
import kotlin.math.roundToInt

/**
 * Android virtual-view bridge for the retained pixel semantics tree.
 *
 * The provider owns Android-only integer ids and focus state. Semantic ids remain process-unique
 * `Long` values owned by retained render objects, while virtual ids are monotonically allocated
 * per Host and are never reassigned to another live logical node.
 */
@Suppress("DEPRECATION", "LargeClass", "TooManyFunctions")
internal class PixelHostAccessibilityNodeProvider(
    /** Host View that owns the virtual accessibility subtree. */
    private val host: PixelHostView,
) : AccessibilityNodeProvider() {
    /** Monotonic mapping from retained semantic identity to Android virtual-view id. */
    private val virtualIdRegistry: PixelAccessibilityVirtualIdRegistry =
        PixelAccessibilityVirtualIdRegistry()

    /** Stable Android ids for application-defined accessibility actions. */
    private val customActionRegistry: PixelAccessibilityCustomActionRegistry =
        PixelAccessibilityCustomActionRegistry()

    /** Last render result identity used to avoid rebuilding the same semantic snapshot per query. */
    private var lastRenderResultToken: Any? = null

    /** Current clipped, parent-linked virtual tree. */
    private var currentTree: PixelAccessibilityTreeSnapshot = PixelAccessibilityTreeSnapshot.Empty

    /** 最近一次已经转换为 Android 事件的语义树；异步合并始终从该快照比较。 */
    private var lastDispatchedTree: PixelAccessibilityTreeSnapshot = PixelAccessibilityTreeSnapshot.Empty

    /** 是否已有同一个 Host 事件派发任务排入主线程消息队列。 */
    private var treeDispatchPosted: Boolean = false

    /** 复用同一个 Runnable，避免滚动帧为异步事件派发额外分配闭包。 */
    private val treeDispatchRunnable: Runnable = Runnable(::dispatchPendingTreeChanges)

    /** Android accessibility focus, independent from Pixel input focus. */
    private var accessibilityFocusedVirtualViewId: Int = INVALID_VIRTUAL_VIEW_ID

    /** Android input focus mirrored from semantic focus or explicit ACTION_FOCUS. */
    private var inputFocusedVirtualViewId: Int = INVALID_VIRTUAL_VIEW_ID

    /** Virtual node currently under TalkBack touch exploration. */
    private var hoveredVirtualViewId: Int = INVALID_VIRTUAL_VIEW_ID

    /** Whether the Host lifecycle currently permits accessibility actions and exploration. */
    private var hostInteractive: Boolean = false

    /** Optional synchronous event observer used only by instrumentation tests. */
    internal var eventObserverForTesting: ((AccessibilityEvent) -> Unit)? = null

    /** Creates either the Host node or one current virtual descendant. */
    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
        refreshSnapshot(dispatchChanges = false)
        return if (virtualViewId == HOST_VIEW_ID) {
            createHostNodeInfo()
        } else {
            createVirtualNodeInfo(virtualViewId)
        }
    }

    /**
     * Adds API 26 character locations derived from the paragraph's shared UTF-16/cluster geometry.
     *
     * Android requires one screen-space slot per requested UTF-16 code unit. Multiple code units
     * inside one grapheme intentionally share a rectangle, while truncated or clipped units remain
     * `null` in the returned array.
     */
    override fun addExtraDataToAccessibilityNodeInfo(
        virtualViewId: Int,
        info: AccessibilityNodeInfo,
        extraDataKey: String,
        arguments: Bundle,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (extraDataKey != AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY) return
        refreshSnapshot(dispatchChanges = false)
        /** Current semantic node addressed by the framework request. */
        val snapshot = currentTree.byVirtualId[virtualViewId] ?: return
        /** Exact CharSequence exposed through AccessibilityNodeInfo.text. */
        val exposedText = snapshot.exposedAccessibilityText()
        /** Requested first UTF-16 code-unit offset. */
        val start = arguments.getInt(
            AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX,
            -1,
        )
        /** Exact number of result slots required by the Android contract. */
        val length = arguments.getInt(
            AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
            -1,
        )
        if (
            start !in exposedText.indices ||
            length <= 0 ||
            length > AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_MAX_LENGTH
        ) {
            return
        }
        /** Shared paragraph resolver owned by either this semantic node or its TextField target. */
        val boundsResolver = resolveCharacterBoundsForRange(snapshot) ?: return
        /** Current physical grid transform shared with paint, pointer mapping, and semantic bounds. */
        val geometry = host.resolveGridGeometry() ?: return
        /** Logical rectangles returned in Android-compatible UTF-16 slot order. */
        val logicalBounds = boundsResolver(start, length)
        /** Host screen origin required because the stable key uses screen coordinates. */
        val screenOffset = IntArray(2)
        host.getLocationOnScreen(screenOffset)
        /** Nullable result array; off-screen and beyond-text code units deliberately remain null. */
        val screenBounds = arrayOfNulls<RectF>(length)
        logicalBounds.take(length).forEachIndexed { index, logicalRect ->
            /** Physical Host-space rectangle clipped to the visible semantic node. */
            val visibleBounds = logicalRect
                ?.toAccessibilityBounds(geometry)
                ?.intersect(snapshot.bounds)
                ?: return@forEachIndexed
            screenBounds[index] = RectF(
                (visibleBounds.left + screenOffset[0]).toFloat(),
                (visibleBounds.top + screenOffset[1]).toFloat(),
                (visibleBounds.right + screenOffset[0]).toFloat(),
                (visibleBounds.bottom + screenOffset[1]).toFloat(),
            )
        }
        info.extras.putParcelableArray(extraDataKey, screenBounds)
    }

    /** Finds visible descendants whose label, value, hint, or error contains [searched]. */
    override fun findAccessibilityNodeInfosByText(
        searched: String?,
        virtualViewId: Int,
    ): MutableList<AccessibilityNodeInfo> {
        refreshSnapshot(dispatchChanges = false)
        if (searched.isNullOrBlank()) return mutableListOf()
        val allowedIds = currentTree.descendantIdsOf(virtualViewId)
        return currentTree.nodes
            .asSequence()
            .filter { snapshot -> snapshot.virtualViewId in allowedIds }
            .filter(PixelAccessibilityNodeSnapshot::visibleToUser)
            .filter { snapshot -> snapshot.containsSpokenText(searched) }
            .mapNotNull { snapshot -> createVirtualNodeInfo(snapshot.virtualViewId) }
            .toMutableList()
    }

    /** Returns the currently focused virtual node for the requested Android focus channel. */
    override fun findFocus(focus: Int): AccessibilityNodeInfo? {
        refreshSnapshot(dispatchChanges = false)
        val virtualViewId = when (focus) {
            AccessibilityNodeInfo.FOCUS_ACCESSIBILITY -> accessibilityFocusedVirtualViewId
            AccessibilityNodeInfo.FOCUS_INPUT -> inputFocusedVirtualViewId
            else -> INVALID_VIRTUAL_VIEW_ID
        }
        return createVirtualNodeInfo(virtualViewId)
    }

    /** Routes platform and custom actions to the callbacks retained beside the semantic node. */
    override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        refreshSnapshot(dispatchChanges = false)
        if (virtualViewId == HOST_VIEW_ID) return host.performAccessibilityAction(action, arguments)
        val snapshot = currentTree.byVirtualId[virtualViewId] ?: return false
        return when (action) {
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> requestAccessibilityFocus(snapshot)
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> clearAccessibilityFocus(snapshot)
            AccessibilityNodeInfo.ACTION_FOCUS -> requestInputFocus(snapshot)
            AccessibilityNodeInfo.ACTION_CLEAR_FOCUS -> clearInputFocus(snapshot)
            else -> {
                if (!hostInteractive || !snapshot.node.enabled || !snapshot.visibleToUser) return false
                performMutatingAction(snapshot, action, arguments)
            }
        }
    }

    /** Reconciles one rendered semantics frame and emits property-specific Android events. */
    internal fun notifySemanticsChanged() {
        refreshSnapshot(dispatchChanges = true)
    }

    /**
     * Routes touchscreen hover to the deepest clipped virtual node.
     *
     * Real mouse and stylus hover remains owned by the pixel gesture router; touchscreen hover is
     * generated by Android touch exploration rather than by ordinary finger input.
     */
    internal fun dispatchTouchExplorationHover(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) return false
        refreshSnapshot(dispatchChanges = false)
        if (!hostInteractive) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE,
            -> {
                val nextVirtualViewId = currentTree.deepestNodeAt(
                    x = event.x.roundToInt(),
                    y = event.y.roundToInt(),
                )?.virtualViewId ?: INVALID_VIRTUAL_VIEW_ID
                updateHoveredVirtualView(nextVirtualViewId)
                nextVirtualViewId != INVALID_VIRTUAL_VIEW_ID
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                val wasHovering = hoveredVirtualViewId != INVALID_VIRTUAL_VIEW_ID
                updateHoveredVirtualView(INVALID_VIRTUAL_VIEW_ID)
                wasHovering
            }
            else -> false
        }
    }

    /** Applies Host lifecycle gating without discarding ids across an ordinary detach/reattach. */
    internal fun onHostInteractiveChanged(interactive: Boolean) {
        if (hostInteractive == interactive) return
        hostInteractive = interactive
        if (!interactive) {
            host.removeCallbacks(treeDispatchRunnable)
            treeDispatchPosted = false
            lastDispatchedTree = currentTree
            updateHoveredVirtualView(INVALID_VIRTUAL_VIEW_ID)
            clearAccessibilityFocus(currentTree.byVirtualId[accessibilityFocusedVirtualViewId], force = true)
            clearInputFocus(currentTree.byVirtualId[inputFocusedVirtualViewId], force = true)
        }
        sendWindowContentChanged(HOST_VIEW_ID, AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE)
    }

    /** Releases terminal Host-owned accessibility state and prevents stale virtual-id lookup. */
    internal fun dispose() {
        host.removeCallbacks(treeDispatchRunnable)
        treeDispatchPosted = false
        updateHoveredVirtualView(INVALID_VIRTUAL_VIEW_ID)
        accessibilityFocusedVirtualViewId = INVALID_VIRTUAL_VIEW_ID
        inputFocusedVirtualViewId = INVALID_VIRTUAL_VIEW_ID
        currentTree = PixelAccessibilityTreeSnapshot.Empty
        lastDispatchedTree = PixelAccessibilityTreeSnapshot.Empty
        lastRenderResultToken = null
        virtualIdRegistry.clear()
        customActionRegistry.clear()
        eventObserverForTesting = null
    }

    /** Exposes the immutable current tree to same-module JVM and instrumentation tests. */
    internal fun snapshotForTesting(): PixelAccessibilityTreeSnapshot {
        refreshSnapshot(dispatchChanges = false)
        return currentTree
    }

    /** Refreshes the cached tree once per committed render-result identity. */
    private fun refreshSnapshot(dispatchChanges: Boolean) {
        /** 当前 Host 已发布且尚未被 provider 消费的渲染结果。 */
        val renderResult = host.lastRenderResult
        if (renderResult === lastRenderResultToken) return
        /** 用于生成属性级事件差异的上一帧可访问性快照。 */
        val previous = currentTree
        /** 与绘制、输入和语义边界共享的当前物理网格变换。 */
        val geometry = host.resolveGridGeometry()
        currentTree = traceDiagnosticsSection(SEMANTICS_TREE_TRACE_SECTION) {
            if (renderResult == null || geometry == null) {
                virtualIdRegistry.reconcile(emptyList())
                customActionRegistry.reconcile(emptyList())
                PixelAccessibilityTreeSnapshot.Empty
            } else {
                /** 裁剪并分配稳定虚拟节点 id 后得到的基础语义树。 */
                val baseTree = buildPixelAccessibilityTreeSnapshot(
                    semanticsTargets = renderResult.semanticsTargets,
                    geometry = geometry,
                    virtualIdRegistry = virtualIdRegistry,
                    customActionRegistry = customActionRegistry,
                )
                traceDiagnosticsSection(SEMANTICS_SCROLL_TRACE_SECTION) {
                    baseTree.withResolvedScrollInfo(renderResult, geometry)
                }
            }
        }
        lastRenderResultToken = renderResult
        traceDiagnosticsSection(SEMANTICS_FOCUS_TRACE_SECTION) {
            reconcileFocusAfterSnapshot(previous, currentTree)
        }
        if (dispatchChanges) {
            scheduleTreeChangeDispatch(previous, currentTree)
        }
    }

    /**
     * 把生产语义事件移出当前 `View.draw` 临界路径，并在主线程繁忙时合并到最新树。
     *
     * instrumentation observer 继续同步接收事件，保持既有测试契约；真实平台事件复用一个
     * Runnable，连续滚动帧最多排队一次，派发时从最后已发送快照直接比较到最新快照。
     */
    private fun scheduleTreeChangeDispatch(
        previous: PixelAccessibilityTreeSnapshot,
        current: PixelAccessibilityTreeSnapshot,
    ) {
        if (eventObserverForTesting != null) {
            lastDispatchedTree = current
            traceDiagnosticsSection(SEMANTICS_DISPATCH_TRACE_SECTION) {
                dispatchTreeChanges(previous, current)
            }
            return
        }
        if (treeDispatchPosted) return
        treeDispatchPosted = host.post(treeDispatchRunnable)
        if (!treeDispatchPosted) {
            dispatchPendingTreeChanges()
        }
    }

    /** 在 draw 返回后的主线程队列中派发一个合并后的语义树差异。 */
    private fun dispatchPendingTreeChanges() {
        treeDispatchPosted = false
        /** 本轮事件比较的最后已派发起点。 */
        val previous = lastDispatchedTree
        /** 执行 Runnable 时可见的最新完整语义树。 */
        val current = currentTree
        if (previous === current) return
        lastDispatchedTree = current
        traceDiagnosticsSection(SEMANTICS_DISPATCH_TRACE_SECTION) {
            dispatchTreeChanges(previous, current)
        }
        if (currentTree !== current) {
            scheduleTreeChangeDispatch(current, currentTree)
        }
    }

    /** Clears focus channels whose logical node disappeared and mirrors explicit semantic focus. */
    private fun reconcileFocusAfterSnapshot(
        previous: PixelAccessibilityTreeSnapshot,
        current: PixelAccessibilityTreeSnapshot,
    ) {
        if (
            accessibilityFocusedVirtualViewId != INVALID_VIRTUAL_VIEW_ID &&
            accessibilityFocusedVirtualViewId !in current.byVirtualId
        ) {
            previous.byVirtualId[accessibilityFocusedVirtualViewId]?.let { removed ->
                sendVirtualEvent(removed, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
            }
            accessibilityFocusedVirtualViewId = INVALID_VIRTUAL_VIEW_ID
        }
        if (
            inputFocusedVirtualViewId != INVALID_VIRTUAL_VIEW_ID &&
            inputFocusedVirtualViewId !in current.byVirtualId
        ) {
            inputFocusedVirtualViewId = INVALID_VIRTUAL_VIEW_ID
        }
        if (hoveredVirtualViewId != INVALID_VIRTUAL_VIEW_ID && hoveredVirtualViewId !in current.byVirtualId) {
            previous.byVirtualId[hoveredVirtualViewId]?.let { removed ->
                sendVirtualEvent(removed, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
            }
            hoveredVirtualViewId = INVALID_VIRTUAL_VIEW_ID
        }
        val semanticallyFocused = current.nodes.firstOrNull { snapshot -> snapshot.node.focused }
        semanticallyFocused?.let { focused ->
            inputFocusedVirtualViewId = focused.virtualViewId
        }
        if (
            semanticallyFocused == null &&
            previous.byVirtualId[inputFocusedVirtualViewId]?.node?.focused == true
        ) {
            inputFocusedVirtualViewId = INVALID_VIRTUAL_VIEW_ID
        }
    }

    /** Enriches semantic viewport nodes from the real list or pager target in the same render frame. */
    private fun PixelAccessibilityTreeSnapshot.withResolvedScrollInfo(
        renderResult: PixelRenderResult,
        geometry: PixelGridGeometry,
    ): PixelAccessibilityTreeSnapshot {
        val enrichedNodes = nodes.map { snapshot ->
            snapshot.copy(scrollInfo = resolveScrollInfo(snapshot, renderResult, geometry))
        }
        return copy(
            nodes = enrichedNodes,
            byVirtualId = enrichedNodes.associateBy(PixelAccessibilityNodeSnapshot::virtualViewId),
        )
    }

    /** Resolves current scroll position and extent without synthesizing data for custom callbacks. */
    private fun resolveScrollInfo(
        snapshot: PixelAccessibilityNodeSnapshot,
        renderResult: PixelRenderResult?,
        geometry: PixelGridGeometry?,
    ): PixelAccessibilityScrollInfo? {
        if (renderResult == null || geometry == null) return null
        val exposesScroll = snapshot.actions.onScrollForward != null ||
            snapshot.actions.onScrollBackward != null || snapshot.node.role == PixelSemanticRole.LIST ||
            snapshot.node.role == PixelSemanticRole.SCROLL_VIEW
        if (!exposesScroll) return null
        val pagerTarget = renderResult.pagerTargets
            .filter { target -> target.bounds.contains(snapshot.centerLogicalX, snapshot.centerLogicalY) }
            .minByOrNull { target -> target.bounds.associationDistance(snapshot.node) }
        val listTarget = renderResult.listTargets
            .filter { target -> target.bounds.contains(snapshot.centerLogicalX, snapshot.centerLogicalY) }
            .minByOrNull { target -> target.bounds.associationDistance(snapshot.node) }
        val pagerDistance = pagerTarget?.bounds?.associationDistance(snapshot.node) ?: Long.MAX_VALUE
        val listDistance = listTarget?.bounds?.associationDistance(snapshot.node) ?: Long.MAX_VALUE
        return when (
            pixelAccessibilityScrollTargetKind(
                role = snapshot.node.role,
                listDistance = listDistance,
                pagerDistance = pagerDistance,
            )
        ) {
            PixelAccessibilityScrollTargetKind.LIST -> listTarget?.let { target ->
                traceDiagnosticsSection(SEMANTICS_LIST_INFO_TRACE_SECTION) {
                    target.toAccessibilityScrollInfo(geometry)
                }
            }
            PixelAccessibilityScrollTargetKind.PAGER -> pagerTarget?.toAccessibilityScrollInfo(geometry)
            null -> null
        }
    }

    /** 仅在显式 Host 帧诊断开启时写入嵌套 Perfetto 区间，并保证异常路径成对关闭。 */
    private inline fun <Result> traceDiagnosticsSection(
        name: String,
        block: () -> Result,
    ): Result {
        if (!host.frameDiagnosticsEnabled) return block()
        Trace.beginSection(name)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    /** Builds the real Host node with only direct semantic roots as virtual children. */
    private fun createHostNodeInfo(): AccessibilityNodeInfo {
        val info = AccessibilityNodeInfo.obtain(host)
        host.onInitializeAccessibilityNodeInfo(info)
        info.className = PixelHostView::class.java.name
        info.packageName = host.context.packageName
        info.isEnabled = hostInteractive
        currentTree.rootVirtualViewIds.forEach { childId -> info.addChild(host, childId) }
        return info
    }

    /** Maps a complete semantic snapshot into one framework AccessibilityNodeInfo. */
    private fun createVirtualNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
        val snapshot = currentTree.byVirtualId[virtualViewId] ?: return null
        val node = snapshot.node
        val info = AccessibilityNodeInfo.obtain()
        info.setSource(host, virtualViewId)
        snapshot.parentVirtualViewId?.let { parentId -> info.setParent(host, parentId) } ?: info.setParent(host)
        snapshot.childVirtualViewIds.forEach { childId -> info.addChild(host, childId) }
        info.className = node.role.androidClassName
        info.packageName = host.context.packageName
        populateSpokenProperties(info, snapshot)
        populateStateProperties(info, snapshot)
        populateCollectionProperties(info, node.collectionInfo, node.collectionItemInfo)
        populateBounds(info, snapshot)
        populateActions(info, snapshot)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            snapshot.exposedAccessibilityText().isNotEmpty() &&
            resolveCharacterBoundsForRange(snapshot) != null
        ) {
            info.availableExtraData = listOf(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            info.uniqueId = "pixel:${snapshot.identity.stableDebugId}"
        }
        return info
    }

    /** Populates text, description, hint, error, and live-region properties without duplication. */
    private fun populateSpokenProperties(
        info: AccessibilityNodeInfo,
        snapshot: PixelAccessibilityNodeSnapshot,
    ) {
        val node = snapshot.node
        val text = when (node.role) {
            PixelSemanticRole.TEXT -> node.value ?: node.label
            PixelSemanticRole.TEXT_FIELD -> node.value.orEmpty()
            PixelSemanticRole.LINK -> node.value ?: node.label
            else -> null
        }
        info.text = text
        info.contentDescription = node.label.takeIf { label -> label.isNotBlank() && label != text }
        info.error = node.error
        /** Android exposes validation state separately from the human-readable error message. */
        info.isContentInvalid = pixelAccessibilityContentInvalid(node.error)
        info.liveRegion = node.liveRegion.androidLiveRegion
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            text == null &&
            !node.androidStateDescription.isNullOrBlank()
        ) {
            info.text = node.androidStateDescription
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            info.hintText = node.hint
            info.isShowingHintText = text.isNullOrEmpty() && !node.hint.isNullOrEmpty()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.stateDescription = node.androidStateDescription
        } else if (!node.androidStateDescription.isNullOrBlank() && info.contentDescription.isNullOrBlank()) {
            info.contentDescription = node.androidStateDescription
        }
    }

    /** Populates focus, selection, checked, range, visibility, and role-specific state. */
    private fun populateStateProperties(
        info: AccessibilityNodeInfo,
        snapshot: PixelAccessibilityNodeSnapshot,
    ) {
        val node = snapshot.node
        val callbacks = snapshot.actions
        val effectiveEnabled = hostInteractive && node.enabled
        info.isEnabled = effectiveEnabled
        info.isVisibleToUser = snapshot.visibleToUser && hostInteractive
        info.isFocused = snapshot.virtualViewId == inputFocusedVirtualViewId || node.focused
        info.isAccessibilityFocused = snapshot.virtualViewId == accessibilityFocusedVirtualViewId
        info.isFocusable = node.role.isInputFocusable && effectiveEnabled
        info.isSelected = node.selected
        info.isCheckable = node.checked != null || node.role.isCheckableRole
        info.isChecked = node.checked == true
        info.isClickable = effectiveEnabled && callbacks.onClick != null
        info.isLongClickable = effectiveEnabled && callbacks.onLongClick != null
        info.isScrollable = effectiveEnabled &&
            (callbacks.onScrollForward != null || callbacks.onScrollBackward != null)
        info.isEditable = effectiveEnabled && callbacks.onSetText != null
        if (node.selectionStart >= 0 && node.selectionEnd >= node.selectionStart) {
            info.setTextSelection(node.selectionStart, node.selectionEnd)
        }
        node.rangeInfo?.let { range ->
            info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
                range.minimum,
                range.maximum,
                range.current,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            info.expandedState = when (node.expanded) {
                true -> AccessibilityNodeInfo.EXPANDED_STATE_FULL
                false -> AccessibilityNodeInfo.EXPANDED_STATE_COLLAPSED
                null -> AccessibilityNodeInfo.EXPANDED_STATE_UNDEFINED
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.isScreenReaderFocusable = snapshot.isSpeakable
            info.isHeading = node.collectionItemInfo?.heading == true
            if (node.role == PixelSemanticRole.DIALOG || node.role == PixelSemanticRole.MENU) {
                info.paneTitle = node.label.takeIf(String::isNotBlank)
            }
        }
    }

    /** Populates collection container and item metadata available throughout the minSdk range. */
    private fun populateCollectionProperties(
        info: AccessibilityNodeInfo,
        collection: PixelSemanticsCollectionInfo?,
        item: PixelSemanticsCollectionItemInfo?,
    ) {
        collection?.let { metadata ->
            info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
                metadata.rowCount.coerceAtLeast(0),
                metadata.columnCount.coerceAtLeast(0),
                metadata.hierarchical,
                metadata.selectionMode.androidSelectionMode,
            )
        }
        item?.let { metadata ->
            info.collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(
                metadata.rowIndex,
                metadata.rowSpan,
                metadata.columnIndex,
                metadata.columnSpan,
                metadata.heading,
                metadata.selected,
            )
        }
    }

    /** Populates parent-relative and screen-space bounds from the recursively clipped tree. */
    private fun populateBounds(info: AccessibilityNodeInfo, snapshot: PixelAccessibilityNodeSnapshot) {
        val parentBounds = snapshot.parentVirtualViewId
            ?.let(currentTree.byVirtualId::get)
            ?.bounds
        val relativeBounds = if (parentBounds == null) {
            snapshot.bounds
        } else {
            snapshot.bounds.relativeTo(parentBounds)
        }
        info.setBoundsInParent(relativeBounds.toRect())
        val screenOffset = IntArray(2)
        host.getLocationOnScreen(screenOffset)
        info.setBoundsInScreen(snapshot.bounds.offset(screenOffset[0], screenOffset[1]).toRect())
    }

    /** Advertises only actions backed by an enabled executable callback or provider state. */
    private fun populateActions(info: AccessibilityNodeInfo, snapshot: PixelAccessibilityNodeSnapshot) {
        val node = snapshot.node
        val actions = snapshot.actions
        if (info.isFocusable) {
            info.addAction(
                if (info.isFocused) AccessibilityNodeInfo.ACTION_CLEAR_FOCUS else AccessibilityNodeInfo.ACTION_FOCUS,
            )
        }
        if (snapshot.isSpeakable) {
            info.addAction(
                if (info.isAccessibilityFocused) {
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                } else {
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                },
            )
        }
        if (!hostInteractive || !node.enabled) return
        actions.onClick?.let { info.addAction(AccessibilityNodeInfo.ACTION_CLICK) }
        actions.onLongClick?.let { info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) }
        actions.onScrollForward?.let { info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }
        actions.onScrollBackward?.let { info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        actions.onSetText?.let { info.addAction(AccessibilityNodeInfo.ACTION_SET_TEXT) }
        actions.onSetSelection?.let { info.addAction(AccessibilityNodeInfo.ACTION_SET_SELECTION) }
        actions.onSetProgress?.let { info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS) }
        actions.onDismiss?.let { info.addAction(AccessibilityNodeInfo.ACTION_DISMISS) }
        if (node.expanded != true) actions.onExpand?.let { info.addAction(AccessibilityNodeInfo.ACTION_EXPAND) }
        if (node.expanded != false) actions.onCollapse?.let { info.addAction(AccessibilityNodeInfo.ACTION_COLLAPSE) }
        addTextEditingActions(info, snapshot)
        snapshot.customActions.forEach { custom ->
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(custom.androidActionId, custom.label))
        }
    }

    /** Advertises clipboard actions only when the resolved TextField target can handle them. */
    private fun addTextEditingActions(info: AccessibilityNodeInfo, snapshot: PixelAccessibilityNodeSnapshot) {
        if (snapshot.node.role != PixelSemanticRole.TEXT_FIELD) return
        val target = resolveTextInputTarget(snapshot) ?: return
        val hasSelection = target.state.selectionStart < target.state.selectionEnd
        if (hasSelection) info.addAction(AccessibilityNodeInfo.ACTION_COPY)
        if (hasSelection && !target.readOnly) info.addAction(AccessibilityNodeInfo.ACTION_CUT)
        if (!target.readOnly && !host.effectiveHostServices.clipboardTextOrNull().isNullOrEmpty()) {
            info.addAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
    }

    /** Dispatches one non-focus action and its corresponding virtual-node event. */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun performMutatingAction(
        snapshot: PixelAccessibilityNodeSnapshot,
        action: Int,
        arguments: Bundle?,
    ): Boolean {
        val handled = when (action) {
            AccessibilityNodeInfo.ACTION_CLICK -> performClick(snapshot)
            AccessibilityNodeInfo.ACTION_LONG_CLICK -> snapshot.actions.onLongClick?.invoke() == true
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> snapshot.actions.onScrollForward?.invoke() == true
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> snapshot.actions.onScrollBackward?.invoke() == true
            AccessibilityNodeInfo.ACTION_SET_TEXT -> {
                val text = arguments
                    ?.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE)
                    ?.toString() ?: return false
                snapshot.actions.onSetText?.invoke(text) == true
            }
            AccessibilityNodeInfo.ACTION_SET_SELECTION -> {
                val start = arguments?.getInt(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                    -1,
                ) ?: -1
                val end = arguments?.getInt(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                    -1,
                ) ?: -1
                start >= 0 && end >= 0 && snapshot.actions.onSetSelection?.invoke(start, end) == true
            }
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                val requested = arguments?.getFloat(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,
                    Float.NaN,
                ) ?: Float.NaN
                val range = snapshot.node.rangeInfo
                requested.isFinite() &&
                    range != null &&
                    requested in range.minimum..range.maximum &&
                    snapshot.actions.onSetProgress?.invoke(requested) == true
            }
            AccessibilityNodeInfo.ACTION_COPY -> performTextEditAction(snapshot, PixelTextEditAction.COPY)
            AccessibilityNodeInfo.ACTION_CUT -> performTextEditAction(snapshot, PixelTextEditAction.CUT)
            AccessibilityNodeInfo.ACTION_PASTE -> performTextEditAction(snapshot, PixelTextEditAction.PASTE)
            AccessibilityNodeInfo.ACTION_DISMISS -> snapshot.actions.onDismiss?.invoke() == true
            AccessibilityNodeInfo.ACTION_EXPAND ->
                snapshot.node.expanded != true && snapshot.actions.onExpand?.invoke() == true
            AccessibilityNodeInfo.ACTION_COLLAPSE ->
                snapshot.node.expanded != false && snapshot.actions.onCollapse?.invoke() == true
            else -> snapshot.customActions
                .firstOrNull { custom -> custom.androidActionId == action }
                ?.callback
                ?.invoke() == true
        }
        if (!handled) return false
        host.invalidate()
        dispatchActionEvent(snapshot, action, arguments)
        return true
    }

    /** Gives TextField click to Host input coordination before invoking its semantic callback. */
    private fun performClick(snapshot: PixelAccessibilityNodeSnapshot): Boolean {
        var handled = false
        if (snapshot.node.role == PixelSemanticRole.TEXT_FIELD) {
            resolveTextInputTarget(snapshot)?.let { target ->
                host.focusTextInput(target)
                if (inputFocusedVirtualViewId != snapshot.virtualViewId) {
                    inputFocusedVirtualViewId = snapshot.virtualViewId
                    sendVirtualEvent(snapshot, AccessibilityEvent.TYPE_VIEW_FOCUSED)
                }
                handled = true
            }
        }
        val callback = snapshot.actions.onClick
        if (callback != null) handled = callback.invoke() || handled
        return handled
    }

    /** Executes copy/cut/paste against the TextField target owned by this semantic source. */
    private fun performTextEditAction(
        snapshot: PixelAccessibilityNodeSnapshot,
        action: PixelTextEditAction,
    ): Boolean {
        val target = resolveTextInputTarget(snapshot) ?: return false
        /** 当前 Host 的 typed capability 集合；剪贴板缺失时按无内容降级。 */
        val hostServices = host.effectiveHostServices
        return when (action) {
            PixelTextEditAction.COPY -> {
                val selected = target.controller.selectedText(target.state)
                if (selected.isEmpty()) return false
                hostServices.writeClipboardText(selected)
                true
            }
            PixelTextEditAction.CUT -> {
                if (target.readOnly) return false
                val selected = target.controller.cutSelection(target.state) ?: return false
                hostServices.writeClipboardText(selected)
                target.onChanged?.invoke(target.state.text)
                true
            }
            PixelTextEditAction.PASTE -> {
                if (target.readOnly) return false
                val text = hostServices.clipboardTextOrNull().orEmpty()
                if (text.isEmpty()) return false
                target.controller.paste(target.state, text)
                target.onChanged?.invoke(target.state.text)
                true
            }
            PixelTextEditAction.SELECT_ALL -> false
        }
    }

    /** Resolves a TextField target by exact retained source before using geometric fallback. */
    private fun resolveTextInputTarget(snapshot: PixelAccessibilityNodeSnapshot): PixelTextInputTarget? {
        val targets = host.lastRenderResult?.textInputTargets.orEmpty()
        snapshot.source?.let { source ->
            targets.lastOrNull { target -> target.source === source }?.let { return it }
        }
        return targets.lastOrNull { target ->
            target.bounds.contains(snapshot.centerLogicalX, snapshot.centerLogicalY)
        }
    }

    /** Resolves paragraph character geometry without substituting whole-node semantic bounds. */
    private fun resolveCharacterBoundsForRange(
        snapshot: PixelAccessibilityNodeSnapshot,
    ): ((Int, Int) -> List<PixelRect?>)? {
        snapshot.characterBoundsForRange?.let { resolver -> return resolver }
        if (snapshot.node.role != PixelSemanticRole.TEXT_FIELD) return null
        return resolveTextInputTarget(snapshot)?.characterBoundsForRange
    }

    /** Updates accessibility focus and sends a virtual focus event. */
    private fun requestAccessibilityFocus(snapshot: PixelAccessibilityNodeSnapshot): Boolean {
        if (!hostInteractive || !snapshot.visibleToUser || !snapshot.isSpeakable) return false
        if (accessibilityFocusedVirtualViewId == snapshot.virtualViewId) return false
        currentTree.byVirtualId[accessibilityFocusedVirtualViewId]?.let { previous ->
            sendVirtualEvent(previous, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
        }
        accessibilityFocusedVirtualViewId = snapshot.virtualViewId
        sendVirtualEvent(snapshot, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
        host.invalidate()
        return true
    }

    /** Clears accessibility focus when [snapshot] owns it. */
    private fun clearAccessibilityFocus(
        snapshot: PixelAccessibilityNodeSnapshot?,
        force: Boolean = false,
    ): Boolean {
        if (snapshot == null || accessibilityFocusedVirtualViewId != snapshot.virtualViewId) return false
        if (!force && !hostInteractive) return false
        accessibilityFocusedVirtualViewId = INVALID_VIRTUAL_VIEW_ID
        sendVirtualEvent(snapshot, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
        host.invalidate()
        return true
    }

    /** Updates provider input focus and focuses a matching TextField through the Host coordinator. */
    private fun requestInputFocus(snapshot: PixelAccessibilityNodeSnapshot): Boolean {
        if (!hostInteractive || !snapshot.visibleToUser || !snapshot.node.enabled) return false
        if (inputFocusedVirtualViewId == snapshot.virtualViewId) return false
        if (snapshot.node.role == PixelSemanticRole.TEXT_FIELD) {
            resolveTextInputTarget(snapshot)?.let(host::focusTextInput)
        }
        inputFocusedVirtualViewId = snapshot.virtualViewId
        sendVirtualEvent(snapshot, AccessibilityEvent.TYPE_VIEW_FOCUSED)
        host.invalidate()
        return true
    }

    /** Clears provider input focus and the matching Pixel TextField focus. */
    private fun clearInputFocus(
        snapshot: PixelAccessibilityNodeSnapshot?,
        force: Boolean = false,
    ): Boolean {
        if (snapshot == null || inputFocusedVirtualViewId != snapshot.virtualViewId) return false
        if (!force && !hostInteractive) return false
        if (snapshot.node.role == PixelSemanticRole.TEXT_FIELD) host.clearFocusedTextInput()
        inputFocusedVirtualViewId = INVALID_VIRTUAL_VIEW_ID
        sendWindowContentChanged(
            snapshot.virtualViewId,
            pixelAccessibilityContentChangeTypes(
                PixelAccessibilityChangeKind.STATE,
                Build.VERSION.SDK_INT,
            ),
        )
        host.invalidate()
        return true
    }

    /** Sends balanced hover-exit and hover-enter events when touch exploration changes target. */
    private fun updateHoveredVirtualView(nextVirtualViewId: Int) {
        if (hoveredVirtualViewId == nextVirtualViewId) return
        currentTree.byVirtualId[hoveredVirtualViewId]?.let { previous ->
            sendVirtualEvent(previous, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
        }
        hoveredVirtualViewId = nextVirtualViewId
        currentTree.byVirtualId[nextVirtualViewId]?.let { next ->
            sendVirtualEvent(next, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
        }
    }

    /** Diffs two frame snapshots into precise virtual-node content, focus, text, and window events. */
    private fun dispatchTreeChanges(
        previous: PixelAccessibilityTreeSnapshot,
        current: PixelAccessibilityTreeSnapshot,
    ) {
        /** 当前帧相对上一帧需要发送的去重语义变化。 */
        val changes = traceDiagnosticsSection(SEMANTICS_DIFF_TRACE_SECTION) {
            diffPixelAccessibilityTrees(previous, current)
        }
        traceDiagnosticsSection(SEMANTICS_EVENTS_TRACE_SECTION) {
            changes.forEach { change ->
                val snapshot = current.byVirtualId[change.virtualViewId]
                    ?: previous.byVirtualId[change.virtualViewId]
                    ?: return@forEach
                when (change.kind) {
                PixelAccessibilityChangeKind.SUBTREE -> sendWindowContentChanged(
                    change.sourceVirtualViewId,
                    pixelAccessibilityContentChangeTypes(
                        PixelAccessibilityChangeKind.SUBTREE,
                        Build.VERSION.SDK_INT,
                    ),
                )
                PixelAccessibilityChangeKind.CONTENT -> sendWindowContentChanged(
                    snapshot.virtualViewId,
                    pixelAccessibilityContentChangeTypes(
                        PixelAccessibilityChangeKind.CONTENT,
                        Build.VERSION.SDK_INT,
                    ),
                )
                PixelAccessibilityChangeKind.STATE -> sendWindowContentChanged(
                    snapshot.virtualViewId,
                    pixelAccessibilityContentChangeTypes(
                        PixelAccessibilityChangeKind.STATE,
                        Build.VERSION.SDK_INT,
                    ),
                )
                PixelAccessibilityChangeKind.TEXT -> sendTextChangedEvent(snapshot, change.previousNode)
                PixelAccessibilityChangeKind.SELECTION -> sendSelectionChangedEvent(snapshot)
                PixelAccessibilityChangeKind.SCROLLED -> sendScrollEvent(snapshot)
                PixelAccessibilityChangeKind.FOCUS -> sendVirtualEvent(snapshot, AccessibilityEvent.TYPE_VIEW_FOCUSED)
                PixelAccessibilityChangeKind.SELECTED -> sendVirtualEvent(snapshot, AccessibilityEvent.TYPE_VIEW_SELECTED)
                    PixelAccessibilityChangeKind.WINDOW -> sendVirtualEvent(
                        snapshot,
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    )
                }
            }
        }
    }

    /** Sends the event corresponding to one successfully completed accessibility action. */
    private fun dispatchActionEvent(
        snapshot: PixelAccessibilityNodeSnapshot,
        action: Int,
        arguments: Bundle?,
    ) {
        when (action) {
            AccessibilityNodeInfo.ACTION_CLICK -> sendVirtualEvent(snapshot, AccessibilityEvent.TYPE_VIEW_CLICKED)
            AccessibilityNodeInfo.ACTION_LONG_CLICK -> sendVirtualEvent(
                snapshot,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            )
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
            -> sendScrollEvent(
                snapshot.copy(
                    scrollInfo = resolveScrollInfo(
                        snapshot = snapshot,
                        renderResult = host.lastRenderResult,
                        geometry = host.resolveGridGeometry(),
                    ),
                ),
            )
            AccessibilityNodeInfo.ACTION_SET_TEXT -> {
                val requestedText = arguments
                    ?.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE)
                    ?.toString()
                    ?: snapshot.node.value.orEmpty()
                sendTextChangedEvent(
                    snapshot.copy(node = snapshot.node.copy(value = requestedText)),
                    snapshot.node,
                )
            }
            AccessibilityNodeInfo.ACTION_CUT,
            AccessibilityNodeInfo.ACTION_PASTE,
            -> {
                val actualText = resolveTextInputTarget(snapshot)?.state?.text ?: snapshot.node.value.orEmpty()
                sendTextChangedEvent(
                    snapshot.copy(node = snapshot.node.copy(value = actualText)),
                    snapshot.node,
                )
            }
            AccessibilityNodeInfo.ACTION_COPY -> Unit
            AccessibilityNodeInfo.ACTION_SET_SELECTION -> {
                val actualState = resolveTextInputTarget(snapshot)?.state
                val actual = snapshot.node.copy(
                    value = actualState?.text ?: snapshot.node.value,
                    selectionStart = actualState?.selectionStart ?: snapshot.node.selectionStart,
                    selectionEnd = actualState?.selectionEnd ?: snapshot.node.selectionEnd,
                )
                sendSelectionChangedEvent(snapshot.copy(node = actual))
            }
            AccessibilityNodeInfo.ACTION_DISMISS -> sendVirtualEvent(
                snapshot,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            )
            else -> sendWindowContentChanged(
                snapshot.virtualViewId,
                pixelAccessibilityContentChangeTypes(
                    PixelAccessibilityChangeKind.STATE,
                    Build.VERSION.SDK_INT,
                ),
            )
        }
    }

    /** Sends a virtual-node text-change event with best-effort edit counts. */
    private fun sendTextChangedEvent(
        snapshot: PixelAccessibilityNodeSnapshot,
        previousNode: PixelSemanticsNode?,
    ) {
        val spec = pixelAccessibilityTextChangeEventSpec(
            before = previousNode?.value.orEmpty(),
            after = snapshot.node.value.orEmpty(),
        )
        sendVirtualEvent(snapshot, AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) { event ->
            event.beforeText = spec.beforeText
            event.fromIndex = spec.fromIndex
            event.removedCount = spec.removedCount
            event.addedCount = spec.addedCount
        }
    }

    /** Sends the current text selection and character count for one virtual TextField. */
    private fun sendSelectionChangedEvent(snapshot: PixelAccessibilityNodeSnapshot) {
        val spec = pixelAccessibilitySelectionEventSpec(snapshot.node)
        sendVirtualEvent(snapshot, AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) { event ->
            event.fromIndex = spec.fromIndex
            event.toIndex = spec.toIndex
            event.itemCount = spec.itemCount
        }
    }

    /** Sends one fully populated scroll event when a real list or pager target was resolved. */
    private fun sendScrollEvent(snapshot: PixelAccessibilityNodeSnapshot) {
        sendVirtualEvent(snapshot, AccessibilityEvent.TYPE_VIEW_SCROLLED) { event ->
            snapshot.scrollInfo?.let { scroll ->
                event.scrollX = scroll.scrollX
                event.scrollY = scroll.scrollY
                event.maxScrollX = scroll.maxScrollX
                event.maxScrollY = scroll.maxScrollY
                event.fromIndex = scroll.fromIndex
                event.toIndex = scroll.toIndex
                event.itemCount = scroll.itemCount
            }
        }
    }

    /** Sends TYPE_WINDOW_CONTENT_CHANGED from the Host or one virtual source. */
    private fun sendWindowContentChanged(virtualViewId: Int, contentChangeTypes: Int) {
        val snapshot = currentTree.byVirtualId[virtualViewId]
        if (snapshot == null) {
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            event.packageName = host.context.packageName
            event.className = PixelHostView::class.java.name
            event.setSource(host)
            event.contentChangeTypes = contentChangeTypes
            dispatchEvent(event)
        } else {
            sendVirtualEvent(snapshot, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) { event ->
                event.contentChangeTypes = contentChangeTypes
            }
        }
    }

    /** Creates and dispatches one event whose source is a virtual semantic node. */
    private fun sendVirtualEvent(
        snapshot: PixelAccessibilityNodeSnapshot,
        eventType: Int,
        configure: (AccessibilityEvent) -> Unit = {},
    ) {
        val event = AccessibilityEvent.obtain(eventType)
        event.packageName = host.context.packageName
        event.className = snapshot.node.role.androidClassName
        event.isEnabled = snapshot.node.enabled && hostInteractive
        event.isChecked = snapshot.node.checked == true
        event.isScrollable = snapshot.actions.onScrollForward != null || snapshot.actions.onScrollBackward != null
        snapshot.node.label.takeIf(String::isNotBlank)?.let { label ->
            event.contentDescription = label
        }
        snapshot.node.value?.let(event.text::add)
        event.setSource(host, snapshot.virtualViewId)
        configure(event)
        dispatchEvent(event)
    }

    /** Sends one event through the ViewParent and mirrors it to the instrumentation observer. */
    private fun dispatchEvent(event: AccessibilityEvent) {
        eventObserverForTesting?.invoke(event)
        val accessibilityManager = host.context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? AccessibilityManager
        if (accessibilityManager?.isEnabled == true) {
            try {
                host.parent?.requestSendAccessibilityEvent(host, event)
            } catch (_: IllegalStateException) {
                // Accessibility can be disabled between the manager query and ViewRoot dispatch.
            }
        }
    }

    private companion object {
        /** 构建、裁剪并索引一帧语义树的 Perfetto 区间。 */
        const val SEMANTICS_TREE_TRACE_SECTION: String = "pixel.semantics_tree"

        /** 为语义树补充列表或分页滚动信息的 Perfetto 区间。 */
        const val SEMANTICS_SCROLL_TRACE_SECTION: String = "pixel.semantics_scroll"

        /** 解析大型列表可见范围和滚动载荷的 Perfetto 区间。 */
        const val SEMANTICS_LIST_INFO_TRACE_SECTION: String = "pixel.semantics_list_info"

        /** 对齐 Android 输入焦点与可访问性焦点的 Perfetto 区间。 */
        const val SEMANTICS_FOCUS_TRACE_SECTION: String = "pixel.semantics_focus"

        /** 计算语义树差异并发送 Android 事件的 Perfetto 区间。 */
        const val SEMANTICS_DISPATCH_TRACE_SECTION: String = "pixel.semantics_dispatch"

        /** 纯比较前后语义树并生成变化记录的 Perfetto 区间。 */
        const val SEMANTICS_DIFF_TRACE_SECTION: String = "pixel.semantics_diff"

        /** 把语义变化转换为 Android AccessibilityEvent 的 Perfetto 区间。 */
        const val SEMANTICS_EVENTS_TRACE_SECTION: String = "pixel.semantics_events"

        /** Framework id used when AccessibilityNodeProvider addresses its real Host View. */
        const val HOST_VIEW_ID: Int = View.NO_ID

        /** Sentinel that cannot address a real virtual descendant. */
        const val INVALID_VIRTUAL_VIEW_ID: Int = Int.MIN_VALUE
    }
}

/** Returns whether a semantic validation message represents Android invalid-content state. */
internal fun pixelAccessibilityContentInvalid(error: String?): Boolean = !error.isNullOrBlank()

/** Stable identity used by the Android virtual-id registry. */
internal sealed interface PixelAccessibilityNodeIdentity {
    /** Human-readable identity included in API 33+ AccessibilityNodeInfo.uniqueId. */
    val stableDebugId: String

    /** Identity backed by a process-unique retained semantic id. */
    data class Semantic(
        /** Retained semantic id allocated by RenderObject.semanticNodeId. */
        val semanticId: Long,
    ) : PixelAccessibilityNodeIdentity {
        /** Stable diagnostic representation. */
        override val stableDebugId: String = semanticId.toString()
    }

}

/** Monotonic virtual-id allocator that never maps one active id to a different logical node. */
internal class PixelAccessibilityVirtualIdRegistry {
    /** Next positive Android virtual id; Host uses -1 and zero remains unused. */
    private var nextVirtualViewId: Int = 1

    /** Historical identities retained until Host dispose so temporary exclusion cannot change ids. */
    private val historicalIds: MutableMap<PixelAccessibilityNodeIdentity, Int> = mutableMapOf()

    /** Reconciles one clipped tree and returns its stable identity-to-id mapping. */
    fun reconcile(identities: List<PixelAccessibilityNodeIdentity>): Map<PixelAccessibilityNodeIdentity, Int> {
        require(identities.distinct().size == identities.size) {
            "A semantic tree cannot expose duplicate retained node identities."
        }
        identities.forEach { identity ->
            historicalIds.getOrPut(identity) {
                check(nextVirtualViewId < Int.MAX_VALUE) { "Pixel accessibility virtual-id space exhausted." }
                nextVirtualViewId++
            }
        }
        return identities.associateWith(historicalIds::getValue)
    }

    /** Clears terminal Host state; ordinary detach deliberately does not call this method. */
    fun clear() {
        historicalIds.clear()
        nextVirtualViewId = 1
    }
}

/** Stable key for one application-defined action inside one retained semantic node. */
internal data class PixelAccessibilityCustomActionIdentity(
    /** Owning semantic identity. */
    val nodeIdentity: PixelAccessibilityNodeIdentity,
    /** Stable node-local application action id. */
    val customActionId: String,
)

/** Monotonic allocator for Android custom action ids outside the framework action range. */
internal class PixelAccessibilityCustomActionRegistry {
    /** Next custom action id allocated from the positive application-private range. */
    private var nextActionId: Int = CUSTOM_ACTION_ID_BASE

    /** Historical custom identities retained until Host dispose to preserve temporary exclusions. */
    private val historicalIds: MutableMap<PixelAccessibilityCustomActionIdentity, Int> = mutableMapOf()

    /** Reconciles one frame and preserves ids for retained custom actions across reorder. */
    fun reconcile(
        identities: List<PixelAccessibilityCustomActionIdentity>,
    ): Map<PixelAccessibilityCustomActionIdentity, Int> {
        require(identities.distinct().size == identities.size) {
            "A semantic node cannot expose duplicate custom action ids."
        }
        identities.forEach { identity ->
            historicalIds.getOrPut(identity) {
                check(nextActionId < Int.MAX_VALUE) { "Pixel custom accessibility action-id space exhausted." }
                nextActionId++
            }
        }
        return identities.associateWith(historicalIds::getValue)
    }

    /** Clears terminal Host state. */
    fun clear() {
        historicalIds.clear()
        nextActionId = CUSTOM_ACTION_ID_BASE
    }

    private companion object {
        /** High positive range kept separate from Android framework action constants. */
        const val CUSTOM_ACTION_ID_BASE: Int = 0x3F000000
    }
}

/** Executable custom action mapped to one stable Android action id. */
internal data class PixelAccessibilityCustomActionSnapshot(
    /** Android action id advertised in AccessibilityNodeInfo. */
    val androidActionId: Int,
    /** Localized spoken action label. */
    val label: String,
    /** Application callback invoked by performAction. */
    val callback: () -> Boolean,
)

/** Real scroll event payload resolved from one list or pager render target. */
internal data class PixelAccessibilityScrollInfo(
    /** Current horizontal offset in Android physical pixels. */
    val scrollX: Int,
    /** Current vertical offset in Android physical pixels. */
    val scrollY: Int,
    /** Maximum horizontal offset in Android physical pixels. */
    val maxScrollX: Int,
    /** Maximum vertical offset in Android physical pixels. */
    val maxScrollY: Int,
    /** First currently visible logical item index, or -1 for an empty collection. */
    val fromIndex: Int,
    /** Last currently visible logical item index, or -1 for an empty collection. */
    val toIndex: Int,
    /** Exact logical item count owned by the render target. */
    val itemCount: Int,
)

/** Render-target family selected when nested scrollable bounds overlap one semantic viewport. */
internal enum class PixelAccessibilityScrollTargetKind {
    /** Vertical list or grid target. */
    LIST,

    /** Horizontal or vertical pager target. */
    PAGER,
}

/** Selects the role-compatible closest target without allowing an inner pager to own a LIST. */
internal fun pixelAccessibilityScrollTargetKind(
    role: PixelSemanticRole,
    listDistance: Long,
    pagerDistance: Long,
): PixelAccessibilityScrollTargetKind? {
    return when {
        role == PixelSemanticRole.LIST && listDistance != Long.MAX_VALUE ->
            PixelAccessibilityScrollTargetKind.LIST
        role == PixelSemanticRole.LIST -> null
        pagerDistance <= listDistance && pagerDistance != Long.MAX_VALUE ->
            PixelAccessibilityScrollTargetKind.PAGER
        listDistance != Long.MAX_VALUE -> PixelAccessibilityScrollTargetKind.LIST
        else -> null
    }
}

/** One fully mapped virtual node in the current Android accessibility tree. */
internal data class PixelAccessibilityNodeSnapshot(
    /** Stable logical identity used by the virtual id registry. */
    val identity: PixelAccessibilityNodeIdentity,
    /** Android virtual descendant id. */
    val virtualViewId: Int,
    /** Direct virtual parent, or null for a Host child. */
    val parentVirtualViewId: Int?,
    /** Direct virtual children in semantic preorder. */
    val childVirtualViewIds: List<Int>,
    /** Immutable public semantic properties for this frame. */
    val node: PixelSemanticsNode,
    /** Executable typed callbacks retained beside [node]. */
    val actions: PixelSemanticsActions,
    /** Stable Android ids and callbacks for application-defined actions. */
    val customActions: List<PixelAccessibilityCustomActionSnapshot>,
    /** Recursively clipped bounds in Host View coordinates. */
    val bounds: PixelAccessibilityBounds,
    /** Logical horizontal center used only for legacy target association. */
    val centerLogicalX: Int,
    /** Logical vertical center used only for legacy target association. */
    val centerLogicalY: Int,
    /** Retained render owner used for exact TextField target association. */
    val source: Any?,
    /** Absolute logical paragraph rectangles indexed by requested UTF-16 code units. */
    val characterBoundsForRange: ((Int, Int) -> List<PixelRect?>)? = null,
    /** Real scroll position and extent when this node owns a list or pager target. */
    val scrollInfo: PixelAccessibilityScrollInfo? = null,
    /** Whether this clipped node is discoverable in the current Host lifecycle. */
    val visibleToUser: Boolean = true,
) {
    /** Whether the node contains text or behavior a screen reader can announce. */
    val isSpeakable: Boolean
        get() = node.label.isNotBlank() || !node.value.isNullOrBlank() || node.actions.isNotEmpty()

    /** Matches a case-insensitive query against all spoken fields. */
    fun containsSpokenText(query: String): Boolean {
        return listOf(node.label, node.value, node.hint, node.error)
            .filterNotNull()
            .any { candidate -> candidate.contains(query, ignoreCase = true) }
    }
}

/** Immutable Host accessibility tree indexed by Android virtual id. */
internal data class PixelAccessibilityTreeSnapshot(
    /** Semantic preorder used for traversal and hit testing. */
    val nodes: List<PixelAccessibilityNodeSnapshot>,
    /** Fast lookup used by provider node creation and action routing. */
    val byVirtualId: Map<Int, PixelAccessibilityNodeSnapshot>,
    /** Direct Host children in semantic preorder. */
    val rootVirtualViewIds: List<Int>,
) {
    /** Returns the addressed node and all descendants, or every node for the Host id. */
    fun descendantIdsOf(virtualViewId: Int): Set<Int> {
        if (virtualViewId == View.NO_ID) return byVirtualId.keys
        if (virtualViewId !in byVirtualId) return emptySet()
        val result = linkedSetOf<Int>()
        fun visit(id: Int) {
            if (!result.add(id)) return
            byVirtualId[id]?.childVirtualViewIds?.forEach(::visit)
        }
        visit(virtualViewId)
        return result
    }

    /** Finds the deepest visible node containing one Host-space point. */
    fun deepestNodeAt(x: Int, y: Int): PixelAccessibilityNodeSnapshot? {
        return nodes.asReversed().firstOrNull { snapshot ->
            snapshot.visibleToUser && snapshot.bounds.contains(x, y) && snapshot.isSpeakable
        }
    }

    internal companion object {
        /** Empty tree shared before first render and after terminal disposal. */
        val Empty: PixelAccessibilityTreeSnapshot = PixelAccessibilityTreeSnapshot(
            nodes = emptyList(),
            byVirtualId = emptyMap(),
            rootVirtualViewIds = emptyList(),
        )
    }
}

/** Integer rectangle in Host View physical pixels. */
internal data class PixelAccessibilityBounds(
    /** Inclusive left edge. */
    val left: Int,
    /** Inclusive top edge. */
    val top: Int,
    /** Exclusive right edge. */
    val right: Int,
    /** Exclusive bottom edge. */
    val bottom: Int,
) {
    /** Converts to the Android rectangle representation. */
    fun toRect(): Rect = Rect(left, top, right, bottom)

    /** Returns the intersection or null when rectangles do not overlap. */
    fun intersect(other: PixelAccessibilityBounds): PixelAccessibilityBounds? {
        val nextLeft = maxOf(left, other.left)
        val nextTop = maxOf(top, other.top)
        val nextRight = minOf(right, other.right)
        val nextBottom = minOf(bottom, other.bottom)
        if (nextRight <= nextLeft || nextBottom <= nextTop) return null
        return PixelAccessibilityBounds(nextLeft, nextTop, nextRight, nextBottom)
    }

    /** Returns whether one Host-space point lies inside this rectangle. */
    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    /** Converts Host-space bounds into direct-parent coordinates. */
    fun relativeTo(parent: PixelAccessibilityBounds): PixelAccessibilityBounds =
        PixelAccessibilityBounds(left - parent.left, top - parent.top, right - parent.left, bottom - parent.top)

    /** Offsets this rectangle into screen space. */
    fun offset(dx: Int, dy: Int): PixelAccessibilityBounds =
        PixelAccessibilityBounds(left + dx, top + dy, right + dx, bottom + dy)
}

/** Temporary node used while recursively clipping and linking a semantic target tree. */
private data class PixelAccessibilityPendingNode(
    /** Stable identity resolved before Android id allocation. */
    val identity: PixelAccessibilityNodeIdentity,
    /** Original render target with immutable properties and callbacks. */
    val target: PixelSemanticsTarget,
    /** Unclipped physical bounds in Host coordinates. */
    val rawBounds: PixelAccessibilityBounds,
    /** Recursively clipped physical bounds in Host coordinates. */
    val clippedBounds: PixelAccessibilityBounds,
    /** Direct retained parent id, or null for a Host child. */
    val parentSemanticId: Long?,
)

/** Builds a clipped real tree and maps retained semantic ids to monotonic Android virtual ids. */
internal fun buildPixelAccessibilityTreeSnapshot(
    semanticsTargets: List<PixelSemanticsTarget>,
    geometry: PixelGridGeometry,
    virtualIdRegistry: PixelAccessibilityVirtualIdRegistry,
    customActionRegistry: PixelAccessibilityCustomActionRegistry,
): PixelAccessibilityTreeSnapshot {
    val semanticIds = semanticsTargets.map { target -> target.node.id }
    require(semanticIds.distinct().size == semanticIds.size) {
        "A semantic tree cannot expose duplicate retained semantic ids."
    }
    val targetsBySemanticId = semanticsTargets.associateBy { target -> target.node.id }
    val childTargetsByParentId = semanticsTargets
        .filter { target -> target.node.parentId != null && target.node.parentId in targetsBySemanticId }
        .groupBy { target -> target.node.parentId }
    val roots = semanticsTargets.filter { target ->
        target.node.parentId == null || target.node.parentId !in targetsBySemanticId
    }
    val contentClip = PixelAccessibilityBounds(
        left = geometry.originX.roundToInt(),
        top = geometry.originY.roundToInt(),
        right = (geometry.originX + geometry.contentWidth).roundToInt(),
        bottom = (geometry.originY + geometry.contentHeight).roundToInt(),
    )
    val pendingNodes = mutableListOf<PixelAccessibilityPendingNode>()
    val visitedTargets = IdentityHashMap<PixelSemanticsTarget, Boolean>()

    fun visit(target: PixelSemanticsTarget, inheritedClip: PixelAccessibilityBounds) {
        if (visitedTargets.put(target, true) != null) return
        val rawBounds = target.node.toAccessibilityBounds(geometry) ?: return
        val clippedBounds = rawBounds.intersect(inheritedClip) ?: return
        val identity = target.toAccessibilityIdentity()
        pendingNodes += PixelAccessibilityPendingNode(
            identity = identity,
            target = target,
            rawBounds = rawBounds,
            clippedBounds = clippedBounds,
            parentSemanticId = target.node.parentId,
        )
        childTargetsByParentId[target.node.id].orEmpty().forEach { child ->
            visit(child, clippedBounds)
        }
    }

    roots.forEach { root -> visit(root, contentClip) }
    val virtualIds = virtualIdRegistry.reconcile(pendingNodes.map(PixelAccessibilityPendingNode::identity))
    val semanticIdToVirtualId = pendingNodes
        .associate { pending -> pending.target.node.id to virtualIds.getValue(pending.identity) }
    val customIdentities = pendingNodes.flatMap { pending ->
        pending.target.actions.customActions.map { action ->
            PixelAccessibilityCustomActionIdentity(pending.identity, action.id)
        }
    }
    val customActionIds = customActionRegistry.reconcile(customIdentities)
    val childIdsByParent = pendingNodes.groupBy { pending ->
        pending.parentSemanticId?.let(semanticIdToVirtualId::get)
    }.mapValues { (_, children) -> children.map { child -> virtualIds.getValue(child.identity) } }

    val snapshots = pendingNodes.map { pending ->
        val virtualViewId = virtualIds.getValue(pending.identity)
        PixelAccessibilityNodeSnapshot(
            identity = pending.identity,
            virtualViewId = virtualViewId,
            parentVirtualViewId = pending.parentSemanticId?.let(semanticIdToVirtualId::get),
            childVirtualViewIds = childIdsByParent[virtualViewId].orEmpty(),
            node = pending.target.node,
            actions = pending.target.actions,
            customActions = pending.target.actions.customActions.map { action ->
                val identity = PixelAccessibilityCustomActionIdentity(pending.identity, action.id)
                PixelAccessibilityCustomActionSnapshot(
                    androidActionId = customActionIds.getValue(identity),
                    label = action.label,
                    callback = action.onInvoke,
                )
            },
            bounds = pending.clippedBounds,
            centerLogicalX = pending.target.node.left + pending.target.node.width / 2,
            centerLogicalY = pending.target.node.top + pending.target.node.height / 2,
            source = pending.target.source,
            characterBoundsForRange = pending.target.characterBoundsForRange,
        )
    }
    return PixelAccessibilityTreeSnapshot(
        nodes = snapshots,
        byVirtualId = snapshots.associateBy(PixelAccessibilityNodeSnapshot::virtualViewId),
        rootVirtualViewIds = childIdsByParent[null].orEmpty(),
    )
}

/** 仅供纯映射测试使用的入口：直接委托给唯一的树构建实现。 */
internal fun buildPixelAccessibilityNodeSnapshots(
    semanticsNodes: List<PixelSemanticsNode>,
    geometry: PixelGridGeometry,
): List<PixelAccessibilityNodeSnapshot> {
    return buildPixelAccessibilityTreeSnapshot(
        semanticsTargets = semanticsNodes.map { node -> PixelSemanticsTarget(node = node) },
        geometry = geometry,
        virtualIdRegistry = PixelAccessibilityVirtualIdRegistry(),
        customActionRegistry = PixelAccessibilityCustomActionRegistry(),
    ).nodes
}

/** Measures logical geometry mismatch so nested viewports select the closest owning target. */
private fun PixelRect.associationDistance(node: PixelSemanticsNode): Long {
    return kotlin.math.abs(left - node.left).toLong() +
        kotlin.math.abs(top - node.top).toLong() +
        kotlin.math.abs(width - node.width).toLong() +
        kotlin.math.abs(height - node.height).toLong()
}

/** Converts one real list target into an exact Android scroll-event payload. */
private fun PixelListTarget.toAccessibilityScrollInfo(
    geometry: PixelGridGeometry,
): PixelAccessibilityScrollInfo {
    val logicalOffset = state.scrollOffsetPx.takeIf(Float::isFinite) ?: 0f
    val logicalViewportEnd = logicalOffset + viewportHeightPx.coerceAtLeast(0)
    val itemCount = state.itemTopOffsetsPx.size
    /** 首个与当前逻辑视口相交的列表索引；有序布局使用对数查找。 */
    val candidateFromIndex = pixelAccessibilityFirstVisibleItemIndex(
        itemTopOffsetsPx = state.itemTopOffsetsPx,
        itemHeightsPx = state.itemHeightsPx,
        viewportStartPx = logicalOffset,
    )
    /** 最后一个起点仍位于当前逻辑视口内的列表索引。 */
    val candidateToIndex = pixelAccessibilityLastVisibleItemIndex(
        itemTopOffsetsPx = state.itemTopOffsetsPx,
        viewportEndPx = logicalViewportEnd,
    )
    /** 空视口或越界状态不能暴露半个无效可见范围。 */
    val hasVisibleRange = candidateFromIndex >= 0 && candidateToIndex >= candidateFromIndex
    val logicalMaxOffset = state.maxScrollOffsetPx
        .takeIf(Float::isFinite)
        ?.coerceAtLeast(0f)
        ?: (contentHeightPx - viewportHeightPx).coerceAtLeast(0).toFloat()
    return PixelAccessibilityScrollInfo(
        scrollX = 0,
        scrollY = (logicalOffset * geometry.cellSize).roundToInt(),
        maxScrollX = 0,
        maxScrollY = (logicalMaxOffset * geometry.cellSize).roundToInt(),
        fromIndex = if (hasVisibleRange) candidateFromIndex else -1,
        toIndex = if (hasVisibleRange) candidateToIndex else -1,
        itemCount = itemCount,
    )
}

/**
 * 在非重叠、按纵向起点排序的 List 布局中查找首个与视口相交的条目。
 *
 * RenderList/ListViewBuilder 维护的 offset 与 height 数组满足该布局契约；使用二分避免大型
 * 懒列表在每个 accessibility 帧扫描全部条目。缺失高度按零处理，与旧实现一致。
 */
internal fun pixelAccessibilityFirstVisibleItemIndex(
    itemTopOffsetsPx: IntArray,
    itemHeightsPx: IntArray,
    viewportStartPx: Float,
): Int {
    if (itemTopOffsetsPx.isEmpty() || !viewportStartPx.isFinite()) return -1
    /** 尚未排除的最小候选索引。 */
    var low = 0
    /** 尚未排除的最大候选索引。 */
    var high = itemTopOffsetsPx.lastIndex
    /** 当前已知满足 bottom > viewportStart 的最小索引。 */
    var result = -1
    while (low <= high) {
        /** 防止索引求中点时整数加法溢出。 */
        val middle = low + (high - low) / 2
        /** 防御性使用 Long 计算条目底边，避免恶意尺寸导致 Int 溢出。 */
        val itemBottom = itemTopOffsetsPx[middle].toLong() +
            itemHeightsPx.getOrElse(middle) { 0 }.coerceAtLeast(0).toLong()
        if (itemBottom.toDouble() > viewportStartPx.toDouble()) {
            result = middle
            high = middle - 1
        } else {
            low = middle + 1
        }
    }
    return result
}

/** 在按纵向起点排序的 List 布局中查找最后一个满足 top < viewportEnd 的条目。 */
internal fun pixelAccessibilityLastVisibleItemIndex(
    itemTopOffsetsPx: IntArray,
    viewportEndPx: Float,
): Int {
    if (itemTopOffsetsPx.isEmpty() || !viewportEndPx.isFinite()) return -1
    /** 尚未排除的最小候选索引。 */
    var low = 0
    /** 尚未排除的最大候选索引。 */
    var high = itemTopOffsetsPx.lastIndex
    /** 当前已知满足 top < viewportEnd 的最大索引。 */
    var result = -1
    while (low <= high) {
        /** 防止索引求中点时整数加法溢出。 */
        val middle = low + (high - low) / 2
        if (itemTopOffsetsPx[middle].toDouble() < viewportEndPx.toDouble()) {
            result = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return result
}

/** Converts one real pager target into an exact axis-aware Android scroll-event payload. */
private fun PixelPagerTarget.toAccessibilityScrollInfo(
    geometry: PixelGridGeometry,
): PixelAccessibilityScrollInfo {
    val pageCount = state.pageCount.coerceAtLeast(1)
    val currentPage = state.currentPage.coerceIn(0, pageCount - 1)
    val logicalViewport = when (axis) {
        PixelAxis.HORIZONTAL -> bounds.width
        PixelAxis.VERTICAL -> bounds.height
    }.coerceAtLeast(1)
    val physicalOffset = (currentPage * logicalViewport * geometry.cellSize).roundToInt()
    val physicalMaximum = ((pageCount - 1) * logicalViewport * geometry.cellSize).roundToInt()
    return PixelAccessibilityScrollInfo(
        scrollX = if (axis == PixelAxis.HORIZONTAL) physicalOffset else 0,
        scrollY = if (axis == PixelAxis.VERTICAL) physicalOffset else 0,
        maxScrollX = if (axis == PixelAxis.HORIZONTAL) physicalMaximum else 0,
        maxScrollY = if (axis == PixelAxis.VERTICAL) physicalMaximum else 0,
        fromIndex = currentPage,
        toIndex = currentPage,
        itemCount = pageCount,
    )
}

/** 把一个语义目标转换为其稳定的 retained 语义身份。 */
private fun PixelSemanticsTarget.toAccessibilityIdentity(): PixelAccessibilityNodeIdentity =
    PixelAccessibilityNodeIdentity.Semantic(node.id)

/** Converts logical semantic bounds to Host physical pixels without clipping. */
private fun PixelSemanticsNode.toAccessibilityBounds(
    geometry: PixelGridGeometry,
): PixelAccessibilityBounds? {
    if (width <= 0 || height <= 0) return null
    val leftPx = (geometry.originX + left * geometry.cellSize).roundToInt()
    val topPx = (geometry.originY + top * geometry.cellSize).roundToInt()
    val rightPx = (geometry.originX + (left + width) * geometry.cellSize).roundToInt()
    val bottomPx = (geometry.originY + (top + height) * geometry.cellSize).roundToInt()
    if (rightPx <= leftPx || bottomPx <= topPx) return null
    return PixelAccessibilityBounds(leftPx, topPx, rightPx, bottomPx)
}

/** Converts one absolute logical rectangle to Host physical pixels without clipping. */
private fun PixelRect.toAccessibilityBounds(
    geometry: PixelGridGeometry,
): PixelAccessibilityBounds? {
    if (width <= 0 || height <= 0) return null
    /** Physical left edge after logical grid scaling. */
    val leftPx = (geometry.originX + left * geometry.cellSize).roundToInt()
    /** Physical top edge after logical grid scaling. */
    val topPx = (geometry.originY + top * geometry.cellSize).roundToInt()
    /** Exclusive physical right edge after logical grid scaling. */
    val rightPx = (geometry.originX + (left + width) * geometry.cellSize).roundToInt()
    /** Exclusive physical bottom edge after logical grid scaling. */
    val bottomPx = (geometry.originY + (top + height) * geometry.cellSize).roundToInt()
    if (rightPx <= leftPx || bottomPx <= topPx) return null
    return PixelAccessibilityBounds(leftPx, topPx, rightPx, bottomPx)
}

/** Returns the exact text value assigned by [populateSpokenProperties]. */
private fun PixelAccessibilityNodeSnapshot.exposedAccessibilityText(): String {
    return when (node.role) {
        PixelSemanticRole.TEXT -> node.value ?: node.label
        PixelSemanticRole.TEXT_FIELD -> node.value.orEmpty()
        PixelSemanticRole.LINK -> node.value ?: node.label
        else -> ""
    }
}

/** Kinds of semantic frame changes that map to distinct Android accessibility events. */
internal enum class PixelAccessibilityChangeKind {
    /** Node insertion, removal, reorder, reparent, bounds, or collection structure change. */
    SUBTREE,

    /** Non-editable spoken content changed. */
    CONTENT,

    /** Enabled, checked, expanded, range, live region, or action state changed. */
    STATE,

    /** Editable text changed. */
    TEXT,

    /** Text selection changed. */
    SELECTION,

    /** Real list or pager scroll position, extent, or visible item window changed. */
    SCROLLED,

    /** Pixel input focus moved to this node. */
    FOCUS,

    /** Collection selection moved to this node. */
    SELECTED,

    /** Dialog or menu window/pane appeared or disappeared. */
    WINDOW,
}

/** Pure payload for Android TYPE_VIEW_TEXT_CHANGED edit metadata. */
internal data class PixelAccessibilityTextChangeEventSpec(
    /** Complete text before the edit. */
    val beforeText: String,
    /** First character index affected by the edit. */
    val fromIndex: Int,
    /** Number of removed characters after preserving common prefix and suffix. */
    val removedCount: Int,
    /** Number of inserted characters after preserving common prefix and suffix. */
    val addedCount: Int,
)

/** Pure payload for Android TYPE_VIEW_TEXT_SELECTION_CHANGED metadata. */
internal data class PixelAccessibilitySelectionEventSpec(
    /** Clamped inclusive selection start. */
    val fromIndex: Int,
    /** Clamped exclusive selection end. */
    val toIndex: Int,
    /** Current text character count. */
    val itemCount: Int,
)

/** Computes exact edit counts by removing both the unchanged prefix and unchanged suffix. */
internal fun pixelAccessibilityTextChangeEventSpec(
    before: String,
    after: String,
): PixelAccessibilityTextChangeEventSpec {
    val beforeBoundaries = PixelGraphemeBoundaryMap(before)
    val afterBoundaries = PixelGraphemeBoundaryMap(after)
    val sharedLimit = minOf(before.length, after.length)
    var prefixLength = 0
    while (prefixLength < sharedLimit && before[prefixLength] == after[prefixLength]) {
        prefixLength += 1
    }
    while (
        prefixLength > 0 &&
        (!beforeBoundaries.isBoundary(prefixLength) || !afterBoundaries.isBoundary(prefixLength))
    ) {
        prefixLength -= 1
    }
    var suffixLength = 0
    while (
        suffixLength < sharedLimit - prefixLength &&
        before[before.lastIndex - suffixLength] == after[after.lastIndex - suffixLength]
    ) {
        suffixLength += 1
    }
    while (
        suffixLength > 0 &&
        (
            !beforeBoundaries.isBoundary(before.length - suffixLength) ||
                !afterBoundaries.isBoundary(after.length - suffixLength)
            )
    ) {
        suffixLength -= 1
    }
    return PixelAccessibilityTextChangeEventSpec(
        beforeText = before,
        fromIndex = prefixLength,
        removedCount = before.length - prefixLength - suffixLength,
        addedCount = after.length - prefixLength - suffixLength,
    )
}

/** Computes a grapheme-safe selection event payload from potentially stale semantics. */
internal fun pixelAccessibilitySelectionEventSpec(
    node: PixelSemanticsNode,
): PixelAccessibilitySelectionEventSpec {
    val itemCount = node.value.orEmpty().length
    val selection = PixelGraphemeBoundaryMap(node.value.orEmpty()).expand(
        start = node.selectionStart,
        end = node.selectionEnd,
    )
    return PixelAccessibilitySelectionEventSpec(
        fromIndex = selection.start,
        toIndex = selection.end,
        itemCount = itemCount,
    )
}

/** Maps diff kinds to API-compatible TYPE_WINDOW_CONTENT_CHANGED content flags. */
internal fun pixelAccessibilityContentChangeTypes(
    kind: PixelAccessibilityChangeKind,
    sdkInt: Int,
): Int {
    return when (kind) {
        PixelAccessibilityChangeKind.SUBTREE -> AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
        PixelAccessibilityChangeKind.CONTENT -> AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
        PixelAccessibilityChangeKind.STATE -> if (sdkInt >= Build.VERSION_CODES.R) {
            AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
        } else {
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION
        }
        PixelAccessibilityChangeKind.TEXT,
        PixelAccessibilityChangeKind.SELECTION,
        PixelAccessibilityChangeKind.SCROLLED,
        PixelAccessibilityChangeKind.FOCUS,
        PixelAccessibilityChangeKind.SELECTED,
        PixelAccessibilityChangeKind.WINDOW,
        -> AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED
    }
}

/** One deduplicated semantic change consumed by the Android event dispatcher. */
internal data class PixelAccessibilityChange(
    /** Event family selected from the changed properties. */
    val kind: PixelAccessibilityChangeKind,
    /** Primary changed virtual node. */
    val virtualViewId: Int,
    /** Virtual source used for subtree changes, or Host id when null. */
    val sourceVirtualViewId: Int,
    /** Previous semantic node used for text edit counts. */
    val previousNode: PixelSemanticsNode? = null,
)

/** Diffs two immutable trees into stable, property-specific change records. */
internal fun diffPixelAccessibilityTrees(
    previous: PixelAccessibilityTreeSnapshot,
    current: PixelAccessibilityTreeSnapshot,
): List<PixelAccessibilityChange> {
    val changes = linkedSetOf<PixelAccessibilityChange>()
    val removedIds = previous.byVirtualId.keys - current.byVirtualId.keys
    val insertedIds = current.byVirtualId.keys - previous.byVirtualId.keys
    (removedIds + insertedIds).forEach { changedId ->
        val snapshot = current.byVirtualId[changedId] ?: previous.byVirtualId.getValue(changedId)
        changes += PixelAccessibilityChange(
            kind = if (snapshot.node.role.isWindowRole) {
                PixelAccessibilityChangeKind.WINDOW
            } else {
                PixelAccessibilityChangeKind.SUBTREE
            },
            virtualViewId = changedId,
            sourceVirtualViewId = snapshot.parentVirtualViewId ?: View.NO_ID,
        )
    }
    current.byVirtualId.forEach { (id, next) ->
        val old = previous.byVirtualId[id] ?: return@forEach
        if (
            old.parentVirtualViewId != next.parentVirtualViewId ||
            old.childVirtualViewIds != next.childVirtualViewIds ||
            old.bounds != next.bounds ||
            old.node.collectionInfo != next.node.collectionInfo ||
            old.node.collectionItemInfo != next.node.collectionItemInfo
        ) {
            changes += PixelAccessibilityChange(
                PixelAccessibilityChangeKind.SUBTREE,
                id,
                next.parentVirtualViewId ?: View.NO_ID,
            )
        }
        if (old.node.label != next.node.label || old.node.hint != next.node.hint || old.node.error != next.node.error) {
            changes += PixelAccessibilityChange(PixelAccessibilityChangeKind.CONTENT, id, id, old.node)
        }
        if (old.node.value != next.node.value) {
            changes += PixelAccessibilityChange(
                if (next.node.role == PixelSemanticRole.TEXT_FIELD) {
                    PixelAccessibilityChangeKind.TEXT
                } else {
                    PixelAccessibilityChangeKind.CONTENT
                },
                id,
                id,
                old.node,
            )
        }
        if (
            old.node.selectionStart != next.node.selectionStart ||
            old.node.selectionEnd != next.node.selectionEnd
        ) {
            changes += PixelAccessibilityChange(PixelAccessibilityChangeKind.SELECTION, id, id, old.node)
        }
        if (!old.node.focused && next.node.focused) {
            changes += PixelAccessibilityChange(PixelAccessibilityChangeKind.FOCUS, id, id, old.node)
        }
        if (old.node.selected != next.node.selected) {
            changes += PixelAccessibilityChange(
                if (next.node.selected) {
                    PixelAccessibilityChangeKind.SELECTED
                } else {
                    PixelAccessibilityChangeKind.STATE
                },
                id,
                id,
                old.node,
            )
        }
        if (old.scrollInfo != next.scrollInfo && next.scrollInfo != null) {
            changes += PixelAccessibilityChange(PixelAccessibilityChangeKind.SCROLLED, id, id, old.node)
        }
        if (
            old.node.enabled != next.node.enabled ||
            old.node.checked != next.node.checked ||
            old.node.expanded != next.node.expanded ||
            old.node.rangeInfo != next.node.rangeInfo ||
            old.node.liveRegion != next.node.liveRegion ||
            old.node.actions != next.node.actions ||
            old.node.customActionLabels != next.node.customActionLabels
        ) {
            changes += PixelAccessibilityChange(PixelAccessibilityChangeKind.STATE, id, id, old.node)
        }
    }
    return changes.toList()
}

/** Android class name corresponding to one public semantic role. */
private val PixelSemanticRole.androidClassName: String
    get() = when (this) {
        PixelSemanticRole.TEXT -> "android.widget.TextView"
        PixelSemanticRole.BUTTON -> "android.widget.Button"
        PixelSemanticRole.TEXT_FIELD -> "android.widget.EditText"
        PixelSemanticRole.CHECKBOX -> "android.widget.CheckBox"
        PixelSemanticRole.SWITCH -> "android.widget.Switch"
        PixelSemanticRole.RADIO_BUTTON -> "android.widget.RadioButton"
        PixelSemanticRole.SLIDER -> "android.widget.SeekBar"
        PixelSemanticRole.PROGRESS_BAR -> "android.widget.ProgressBar"
        PixelSemanticRole.TAB -> "android.widget.Button"
        PixelSemanticRole.IMAGE -> "android.widget.ImageView"
        PixelSemanticRole.LINK -> "android.widget.TextView"
        PixelSemanticRole.LIST -> "android.widget.ListView"
        PixelSemanticRole.LIST_ITEM -> "android.view.View"
        PixelSemanticRole.SCROLL_VIEW -> "android.widget.ScrollView"
        PixelSemanticRole.DIALOG -> "android.app.Dialog"
        PixelSemanticRole.MENU -> "android.widget.ListView"
        PixelSemanticRole.MENU_ITEM -> "android.view.MenuItem"
        PixelSemanticRole.GENERIC -> "android.view.View"
    }

/** Whether one role participates in Android input focus traversal. */
private val PixelSemanticRole.isInputFocusable: Boolean
    get() = when (this) {
        PixelSemanticRole.BUTTON,
        PixelSemanticRole.TEXT_FIELD,
        PixelSemanticRole.CHECKBOX,
        PixelSemanticRole.SWITCH,
        PixelSemanticRole.RADIO_BUTTON,
        PixelSemanticRole.SLIDER,
        PixelSemanticRole.TAB,
        PixelSemanticRole.LINK,
        PixelSemanticRole.MENU_ITEM,
        -> true
        PixelSemanticRole.TEXT,
        PixelSemanticRole.IMAGE,
        PixelSemanticRole.PROGRESS_BAR,
        PixelSemanticRole.LIST,
        PixelSemanticRole.LIST_ITEM,
        PixelSemanticRole.SCROLL_VIEW,
        PixelSemanticRole.DIALOG,
        PixelSemanticRole.MENU,
        PixelSemanticRole.GENERIC,
        -> false
    }

/** Whether one role implies checkable state even when a caller omitted an explicit value. */
private val PixelSemanticRole.isCheckableRole: Boolean
    get() = this == PixelSemanticRole.CHECKBOX ||
        this == PixelSemanticRole.SWITCH ||
        this == PixelSemanticRole.RADIO_BUTTON

/** Whether insertion/removal of one role changes the active accessibility window or pane. */
private val PixelSemanticRole.isWindowRole: Boolean
    get() = this == PixelSemanticRole.DIALOG || this == PixelSemanticRole.MENU

/** Android live-region constant corresponding to one public announcement policy. */
private val PixelSemanticsLiveRegion.androidLiveRegion: Int
    get() = when (this) {
        PixelSemanticsLiveRegion.NONE -> View.ACCESSIBILITY_LIVE_REGION_NONE
        PixelSemanticsLiveRegion.POLITE -> View.ACCESSIBILITY_LIVE_REGION_POLITE
        PixelSemanticsLiveRegion.ASSERTIVE -> View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
    }

/** Android collection selection-mode constant corresponding to one public policy. */
private val PixelSemanticsSelectionMode.androidSelectionMode: Int
    get() = when (this) {
        PixelSemanticsSelectionMode.NONE -> AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_NONE
        PixelSemanticsSelectionMode.SINGLE -> AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE
        PixelSemanticsSelectionMode.MULTIPLE -> AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_MULTIPLE
    }

/** Compact localized-independent state fallback; Android supplies role-specific checked wording. */
private val PixelSemanticsNode.androidStateDescription: String?
    get() = value?.takeIf { stateValue ->
        stateValue.isNotBlank() && role != PixelSemanticRole.TEXT &&
            role != PixelSemanticRole.TEXT_FIELD && role != PixelSemanticRole.LINK
    }
