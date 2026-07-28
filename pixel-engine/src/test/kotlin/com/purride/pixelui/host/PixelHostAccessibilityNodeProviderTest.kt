package com.purride.pixelui

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.purride.pixelcore.PixelGridGeometryResolver
import com.purride.pixelcore.PixelViewportPolicy
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.internal.PixelRect
import com.purride.pixelui.internal.PixelSemanticsTarget
import com.purride.pixelui.internal.host.PixelAccessibilityBounds
import com.purride.pixelui.internal.host.PixelAccessibilityChangeKind
import com.purride.pixelui.internal.host.PixelAccessibilityCustomActionRegistry
import com.purride.pixelui.internal.host.PixelAccessibilityScrollInfo
import com.purride.pixelui.internal.host.PixelAccessibilityScrollTargetKind
import com.purride.pixelui.internal.host.PixelAccessibilityVirtualIdRegistry
import com.purride.pixelui.internal.host.buildPixelAccessibilityNodeSnapshots
import com.purride.pixelui.internal.host.buildPixelAccessibilityTreeSnapshot
import com.purride.pixelui.internal.host.diffPixelAccessibilityTrees
import com.purride.pixelui.internal.host.pixelAccessibilityContentChangeTypes
import com.purride.pixelui.internal.host.pixelAccessibilityFirstVisibleItemIndex
import com.purride.pixelui.internal.host.pixelAccessibilityLastVisibleItemIndex
import com.purride.pixelui.internal.host.pixelAccessibilityScrollTargetKind
import com.purride.pixelui.internal.host.pixelAccessibilitySelectionEventSpec
import com.purride.pixelui.internal.host.pixelAccessibilityTextChangeEventSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM contract for semantic clipping, identity, hierarchy, actions, and event diffing. */
class PixelHostAccessibilityNodeProviderTest {
    /** 大型定高懒列表的可见范围使用对数查找且保持严格相交边界。 */
    @Test
    fun visibleItemBinarySearchMatchesViewportIntersectionContract() {
        /** 5,000 个互不重叠且每项高 8 像素的稳定逻辑起点。 */
        val itemTopOffsets = IntArray(5_000) { index -> index * 8 }
        /** 与起点数组一一对应的固定逻辑高度。 */
        val itemHeights = IntArray(5_000) { 8 }

        assertEquals(
            20,
            pixelAccessibilityFirstVisibleItemIndex(itemTopOffsets, itemHeights, 163.4f),
        )
        assertEquals(
            35,
            pixelAccessibilityLastVisibleItemIndex(itemTopOffsets, 283.4f),
        )
        assertEquals(
            2,
            pixelAccessibilityFirstVisibleItemIndex(itemTopOffsets, itemHeights, 16f),
        )
        assertEquals(
            3,
            pixelAccessibilityLastVisibleItemIndex(itemTopOffsets, 32f),
        )
    }

    /** 空数组、视口越界与缺失高度保持旧实现的无可见条目语义。 */
    @Test
    fun visibleItemBinarySearchHandlesEmptyAndOutOfRangeInputs() {
        /** 用于验证高度数组短于起点数组时缺失高度按零处理的起点。 */
        val itemTopOffsets = intArrayOf(0, 8, 16)
        /** 只覆盖首项的防御性高度数组。 */
        val partialHeights = intArrayOf(8)

        assertEquals(-1, pixelAccessibilityFirstVisibleItemIndex(intArrayOf(), intArrayOf(), 0f))
        assertEquals(-1, pixelAccessibilityLastVisibleItemIndex(intArrayOf(), 10f))
        assertEquals(-1, pixelAccessibilityFirstVisibleItemIndex(itemTopOffsets, partialHeights, 20f))
        assertEquals(-1, pixelAccessibilityLastVisibleItemIndex(itemTopOffsets, 0f))
        assertEquals(-1, pixelAccessibilityFirstVisibleItemIndex(itemTopOffsets, partialHeights, Float.NaN))
    }

    /** Legacy manually-created nodes still map geometry without relying on Android runtime methods. */
    @Test
    fun semanticsNodesMapToAndroidAccessibilitySnapshots() {
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 100,
            viewHeight = 80,
            profile = ScreenProfile(logicalWidth = 10, logicalHeight = 8, dotSizePx = 10),
            viewportPolicy = PixelViewportPolicy(),
            pixelGapEnabled = false,
            pixelGapRatio = 0f,
        )!!

        val snapshots = buildPixelAccessibilityNodeSnapshots(
            semanticsNodes = listOf(
                PixelSemanticsNode(
                    label = "OK",
                    role = PixelSemanticRole.BUTTON,
                    enabled = true,
                    focused = true,
                    left = 2,
                    top = 1,
                    width = 3,
                    height = 2,
                    id = 1L,
                ),
            ),
            geometry = geometry,
        )

        val snapshot = snapshots.single()
        assertEquals(1, snapshot.virtualViewId)
        assertEquals("OK", snapshot.node.label)
        assertEquals(PixelSemanticRole.BUTTON, snapshot.node.role)
        assertTrue(snapshot.actions.onClick == null)
        assertEquals(PixelAccessibilityBounds(left = 20, top = 10, right = 50, bottom = 30), snapshot.bounds)
        assertEquals(3, snapshot.centerLogicalX)
        assertEquals(2, snapshot.centerLogicalY)
    }

    /** Zero-area nodes cannot become discoverable virtual descendants. */
    @Test
    fun zeroSizedSemanticsNodesAreSkipped() {
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 100,
            viewHeight = 80,
            profile = ScreenProfile(logicalWidth = 10, logicalHeight = 8, dotSizePx = 10),
            viewportPolicy = PixelViewportPolicy(),
            pixelGapEnabled = false,
            pixelGapRatio = 0f,
        )!!

        val snapshots = buildPixelAccessibilityNodeSnapshots(
            semanticsNodes = listOf(
                PixelSemanticsNode(
                    label = "EMPTY",
                    role = PixelSemanticRole.TEXT,
                    enabled = true,
                    focused = false,
                    left = 0,
                    top = 0,
                    width = 0,
                    height = 2,
                    id = 2L,
                ),
            ),
            geometry = geometry,
        )

        assertTrue(snapshots.isEmpty())
    }

    /** Character-location callbacks survive semantic snapshot construction without geometry drift. */
    @Test
    fun semanticSnapshotRetainsSharedParagraphCharacterResolver() {
        /** Absolute logical rectangle returned for both UTF-16 units of one combining grapheme. */
        val clusterRect = PixelRect(left = 1, top = 1, width = 2, height = 1)
        /** Resolver representing the exact UTF-16 slot contract exported by RenderText. */
        val resolver: (Int, Int) -> List<PixelRect?> = { _, length ->
            List(length) { clusterRect }
        }
        /** Snapshot carrying the same resolver beside immutable semantic properties. */
        val snapshot = buildTree(
            listOf(
                target(
                    id = 5L,
                    label = "e\u0301",
                    role = PixelSemanticRole.TEXT,
                    characterBoundsForRange = resolver,
                ),
            ),
        ).nodes.single()

        assertEquals(listOf(clusterRect, clusterRect), snapshot.characterBoundsForRange?.invoke(0, 2))
    }

    /** Retained ids survive reorder, temporary exclusion, and unrelated insertion without reuse. */
    @Test
    fun virtualIdsAreMonotonicAndStableAcrossTreeReconciliation() {
        val virtualIds = PixelAccessibilityVirtualIdRegistry()
        val customIds = PixelAccessibilityCustomActionRegistry()
        val first = buildTree(
            targets = listOf(target(id = 10L, label = "A"), target(id = 20L, label = "B")),
            virtualIds = virtualIds,
            customIds = customIds,
        )
        val firstA = first.nodes.single { it.node.id == 10L }.virtualViewId
        val firstB = first.nodes.single { it.node.id == 20L }.virtualViewId

        val reordered = buildTree(
            targets = listOf(
                target(id = 20L, label = "B"),
                target(id = 30L, label = "C"),
                target(id = 10L, label = "A"),
            ),
            virtualIds = virtualIds,
            customIds = customIds,
        )
        val reorderedA = reordered.nodes.single { it.node.id == 10L }.virtualViewId
        val reorderedB = reordered.nodes.single { it.node.id == 20L }.virtualViewId
        val insertedC = reordered.nodes.single { it.node.id == 30L }.virtualViewId

        buildTree(
            targets = listOf(target(id = 20L, label = "B")),
            virtualIds = virtualIds,
            customIds = customIds,
        )
        val restored = buildTree(
            targets = listOf(target(id = 10L, label = "A"), target(id = 40L, label = "D")),
            virtualIds = virtualIds,
            customIds = customIds,
        )

        assertEquals(firstA, reorderedA)
        assertEquals(firstB, reorderedB)
        assertTrue(insertedC > maxOf(firstA, firstB))
        assertEquals(firstA, restored.nodes.single { it.node.id == 10L }.virtualViewId)
        assertTrue(restored.nodes.single { it.node.id == 40L }.virtualViewId > insertedC)
    }

    /** Every descendant is clipped by both the Host content rectangle and each semantic parent. */
    @Test
    fun semanticHierarchyUsesRealParentsAndRecursiveClipping() {
        val parent = target(
            id = 100L,
            label = "LIST",
            left = 2,
            top = 1,
            width = 4,
            height = 4,
            role = PixelSemanticRole.LIST,
        )
        val clippedChild = target(
            id = 101L,
            parentId = 100L,
            label = "ITEM",
            left = 0,
            top = 0,
            width = 9,
            height = 8,
        )
        val excludedGrandchild = target(
            id = 102L,
            parentId = 101L,
            label = "OUTSIDE",
            left = 8,
            top = 7,
            width = 2,
            height = 1,
        )

        val tree = buildTree(listOf(parent, clippedChild, excludedGrandchild))
        val parentSnapshot = tree.nodes.single { it.node.id == 100L }
        val childSnapshot = tree.nodes.single { it.node.id == 101L }

        assertEquals(listOf(parentSnapshot.virtualViewId), tree.rootVirtualViewIds)
        assertEquals(parentSnapshot.virtualViewId, childSnapshot.parentVirtualViewId)
        assertEquals(listOf(childSnapshot.virtualViewId), parentSnapshot.childVirtualViewIds)
        assertEquals(parentSnapshot.bounds, childSnapshot.bounds)
        assertFalse(tree.nodes.any { it.node.id == 102L })
    }

    /** Custom Android action ids remain stable and callbacks stay associated after reorder. */
    @Test
    fun customActionsKeepStableAndroidIdsAndExecutableCallbacks() {
        var invocationCount = 0
        val actions = PixelSemanticsActions(
            customActions = listOf(
                PixelSemanticsCustomAction(
                    id = "archive",
                    label = "Archive",
                    onInvoke = {
                        invocationCount += 1
                        true
                    },
                ),
            ),
        )
        val virtualIds = PixelAccessibilityVirtualIdRegistry()
        val customIds = PixelAccessibilityCustomActionRegistry()
        val first = buildTree(
            listOf(target(id = 1L, label = "ONE", actions = actions)),
            virtualIds,
            customIds,
        )
        val firstAction = first.nodes.single().customActions.single()
        val second = buildTree(
            listOf(
                target(id = 2L, label = "TWO"),
                target(id = 1L, label = "ONE", actions = actions),
            ),
            virtualIds,
            customIds,
        )
        val secondAction = second.nodes.single { it.node.id == 1L }.customActions.single()

        assertEquals(firstAction.androidActionId, secondAction.androidActionId)
        assertNotEquals(0, secondAction.androidActionId)
        assertTrue(secondAction.callback())
        assertEquals(1, invocationCount)
    }

    /** Frame diff separates editable text, selection, and control-state event families. */
    @Test
    fun semanticDiffClassifiesTextSelectionAndStateChanges() {
        val virtualIds = PixelAccessibilityVirtualIdRegistry()
        val customIds = PixelAccessibilityCustomActionRegistry()
        val previous = buildTree(
            listOf(
                target(
                    id = 7L,
                    label = "NAME",
                    role = PixelSemanticRole.TEXT_FIELD,
                    value = "A",
                    selectionStart = 1,
                    selectionEnd = 1,
                ),
            ),
            virtualIds,
            customIds,
        )
        val current = buildTree(
            listOf(
                target(
                    id = 7L,
                    label = "NAME",
                    role = PixelSemanticRole.TEXT_FIELD,
                    value = "AB",
                    selectionStart = 2,
                    selectionEnd = 2,
                    checked = true,
                ),
            ),
            virtualIds,
            customIds,
        )

        val kinds = diffPixelAccessibilityTrees(previous, current).map { change -> change.kind }.toSet()

        assertTrue(PixelAccessibilityChangeKind.TEXT in kinds)
        assertTrue(PixelAccessibilityChangeKind.SELECTION in kinds)
        assertTrue(PixelAccessibilityChangeKind.STATE in kinds)
    }

    /** Selection gain and loss both produce an observable event family. */
    @Test
    fun selectedStateChangesProduceBidirectionalEvents() {
        val virtualIds = PixelAccessibilityVirtualIdRegistry()
        val customIds = PixelAccessibilityCustomActionRegistry()
        val unselected = buildTree(
            listOf(target(id = 8L, label = "ROW", selected = false)),
            virtualIds,
            customIds,
        )
        val selected = buildTree(
            listOf(target(id = 8L, label = "ROW", selected = true)),
            virtualIds,
            customIds,
        )
        val unselectedAgain = buildTree(
            listOf(target(id = 8L, label = "ROW", selected = false)),
            virtualIds,
            customIds,
        )

        assertTrue(
            diffPixelAccessibilityTrees(unselected, selected)
                .any { change -> change.kind == PixelAccessibilityChangeKind.SELECTED },
        )
        assertTrue(
            diffPixelAccessibilityTrees(selected, unselectedAgain)
                .any { change -> change.kind == PixelAccessibilityChangeKind.STATE },
        )
    }

    /** Content-change flags remain exact across the API 30 state-description boundary. */
    @Test
    fun contentChangeTypesMatchEachDiffFamilyAndSdkLevel() {
        assertEquals(
            AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE,
            pixelAccessibilityContentChangeTypes(PixelAccessibilityChangeKind.SUBTREE, 24),
        )
        assertEquals(
            AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT,
            pixelAccessibilityContentChangeTypes(PixelAccessibilityChangeKind.CONTENT, 24),
        )
        assertEquals(
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION,
            pixelAccessibilityContentChangeTypes(PixelAccessibilityChangeKind.STATE, Build.VERSION_CODES.Q),
        )
        assertEquals(
            AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION,
            pixelAccessibilityContentChangeTypes(PixelAccessibilityChangeKind.STATE, Build.VERSION_CODES.R),
        )
        assertEquals(
            AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED,
            pixelAccessibilityContentChangeTypes(PixelAccessibilityChangeKind.SCROLLED, 24),
        )
    }

    /** Text edit counts exclude unchanged prefix and suffix for replacement and insertion. */
    @Test
    fun textChangeEventSpecReportsExactEditCounts() {
        assertEquals(
            com.purride.pixelui.internal.host.PixelAccessibilityTextChangeEventSpec(
                beforeText = "HELLO",
                fromIndex = 2,
                removedCount = 2,
                addedCount = 2,
            ),
            pixelAccessibilityTextChangeEventSpec(before = "HELLO", after = "HEXXO"),
        )
        assertEquals(
            com.purride.pixelui.internal.host.PixelAccessibilityTextChangeEventSpec(
                beforeText = "AB",
                fromIndex = 1,
                removedCount = 0,
                addedCount = 2,
            ),
            pixelAccessibilityTextChangeEventSpec(before = "AB", after = "AXYB"),
        )
    }

    /** Text-change metadata never reports an index inside a surrogate pair or decomposed cluster. */
    @Test
    fun textChangeEventSpecUsesWholeGraphemeRanges() {
        assertEquals(
            com.purride.pixelui.internal.host.PixelAccessibilityTextChangeEventSpec(
                beforeText = "😀X",
                fromIndex = 0,
                removedCount = 2,
                addedCount = 2,
            ),
            pixelAccessibilityTextChangeEventSpec(before = "😀X", after = "😁X"),
        )
        assertEquals(
            com.purride.pixelui.internal.host.PixelAccessibilityTextChangeEventSpec(
                beforeText = "eX",
                fromIndex = 0,
                removedCount = 1,
                addedCount = 2,
            ),
            pixelAccessibilityTextChangeEventSpec(before = "eX", after = "e\u0301X"),
        )
    }

    /** Selection event payload clamps invalid semantics into the current text range. */
    @Test
    fun selectionEventSpecClampsPayloadToTextLength() {
        val node = target(
            id = 9L,
            label = "FIELD",
            role = PixelSemanticRole.TEXT_FIELD,
            value = "ABCD",
            selectionStart = -5,
            selectionEnd = 99,
        ).node

        val spec = pixelAccessibilitySelectionEventSpec(node)

        assertEquals(0, spec.fromIndex)
        assertEquals(4, spec.toIndex)
        assertEquals(4, spec.itemCount)
    }

    /** Selection metadata snaps stale interior offsets to the same boundary rule as TextField. */
    @Test
    fun selectionEventSpecNormalizesInteriorGraphemeOffsets() {
        val node = target(
            id = 10L,
            label = "FIELD",
            role = PixelSemanticRole.TEXT_FIELD,
            value = "e\u0301😀",
            selectionStart = 1,
            selectionEnd = 3,
        ).node

        val spec = pixelAccessibilitySelectionEventSpec(node)

        assertEquals(0, spec.fromIndex)
        assertEquals(4, spec.toIndex)
        assertEquals(4, spec.itemCount)
    }

    /** Scroll payload changes emit SCROLLED and preserve every real event field. */
    @Test
    fun scrollInfoChangesProduceScrolledDiffWithExactPayload() {
        val baseTree = buildTree(
            listOf(
                target(
                    id = 10L,
                    label = "LIST",
                    role = PixelSemanticRole.LIST,
                    actions = PixelSemanticsActions(onScrollForward = { true }),
                ),
            ),
        )
        val beforeInfo = PixelAccessibilityScrollInfo(
            scrollX = 0,
            scrollY = 20,
            maxScrollX = 0,
            maxScrollY = 100,
            fromIndex = 2,
            toIndex = 5,
            itemCount = 20,
        )
        val afterInfo = beforeInfo.copy(scrollY = 50, fromIndex = 5, toIndex = 8)
        val previous = baseTree.withOnlyNodeScrollInfo(beforeInfo)
        val current = baseTree.withOnlyNodeScrollInfo(afterInfo)

        assertTrue(
            diffPixelAccessibilityTrees(previous, current)
                .any { change -> change.kind == PixelAccessibilityChangeKind.SCROLLED },
        )
        assertEquals(afterInfo, current.nodes.single().scrollInfo)
    }

    /** A LIST always chooses its list target even when a nested pager is geometrically closer. */
    @Test
    fun listRoleCannotStealNestedPagerScrollPayload() {
        assertEquals(
            PixelAccessibilityScrollTargetKind.LIST,
            pixelAccessibilityScrollTargetKind(
                role = PixelSemanticRole.LIST,
                listDistance = 20L,
                pagerDistance = 0L,
            ),
        )
        assertEquals(
            PixelAccessibilityScrollTargetKind.PAGER,
            pixelAccessibilityScrollTargetKind(
                role = PixelSemanticRole.SCROLL_VIEW,
                listDistance = 20L,
                pagerDistance = 0L,
            ),
        )
    }

    /** Builds a deterministic physical grid used by every pure tree test. */
    private fun geometry() = PixelGridGeometryResolver.resolve(
        viewWidth = 100,
        viewHeight = 80,
        profile = ScreenProfile(logicalWidth = 10, logicalHeight = 8, dotSizePx = 10),
        viewportPolicy = PixelViewportPolicy(),
        pixelGapEnabled = false,
        pixelGapRatio = 0f,
    )!!

    /** Builds one tree with optionally shared registries for multi-frame identity assertions. */
    private fun buildTree(
        targets: List<PixelSemanticsTarget>,
        virtualIds: PixelAccessibilityVirtualIdRegistry = PixelAccessibilityVirtualIdRegistry(),
        customIds: PixelAccessibilityCustomActionRegistry = PixelAccessibilityCustomActionRegistry(),
    ) = buildPixelAccessibilityTreeSnapshot(
        semanticsTargets = targets,
        geometry = geometry(),
        virtualIdRegistry = virtualIds,
        customActionRegistry = customIds,
    )

    /** Rebuilds a one-node tree index after attaching a pure scroll payload. */
    private fun com.purride.pixelui.internal.host.PixelAccessibilityTreeSnapshot.withOnlyNodeScrollInfo(
        scrollInfo: PixelAccessibilityScrollInfo,
    ): com.purride.pixelui.internal.host.PixelAccessibilityTreeSnapshot {
        val node = nodes.single().copy(scrollInfo = scrollInfo)
        return copy(nodes = listOf(node), byVirtualId = mapOf(node.virtualViewId to node))
    }

    /** Creates one complete target while keeping individual tests focused on relevant fields. */
    @Suppress("LongParameterList")
    private fun target(
        id: Long,
        label: String,
        parentId: Long? = null,
        left: Int = 0,
        top: Int = 0,
        width: Int = 4,
        height: Int = 3,
        role: PixelSemanticRole = PixelSemanticRole.BUTTON,
        value: String? = null,
        selectionStart: Int = -1,
        selectionEnd: Int = -1,
        checked: Boolean? = null,
        selected: Boolean = false,
        actions: PixelSemanticsActions = PixelSemanticsActions(),
        /** Optional exact UTF-16 paragraph geometry retained beside this semantic target. */
        characterBoundsForRange: ((Int, Int) -> List<PixelRect?>)? = null,
    ): PixelSemanticsTarget {
        return PixelSemanticsTarget(
            node = PixelSemanticsNode(
                label = label,
                role = role,
                enabled = true,
                focused = false,
                left = left,
                top = top,
                width = width,
                height = height,
                id = id,
                parentId = parentId,
                value = value,
                checked = checked,
                selected = selected,
                selectionStart = selectionStart,
                selectionEnd = selectionEnd,
                actions = actions.capabilitySet(),
                customActionLabels = actions.customActions.associate { action -> action.id to action.label },
            ),
            actions = actions,
            characterBoundsForRange = characterBoundsForRange,
        )
    }
}
