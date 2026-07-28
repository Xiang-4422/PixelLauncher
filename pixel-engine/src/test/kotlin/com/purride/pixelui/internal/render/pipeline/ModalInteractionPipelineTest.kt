package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelRefreshIndicatorState
import com.purride.pixelui.state.PixelTextFieldController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks modal target filtering to the completed render tree rather than collection order. */
class ModalInteractionPipelineTest {
    /** Every later-sibling target category is removed after the active modal is selected. */
    @Test
    fun activeModalFiltersEveryLaterSiblingTargetAndPrivateMarker() {
        /** Complete target producer owned by the active modal scope. */
        val modalChild = ModalTestBox(width = 2, height = 2, label = "MODAL")
        /** Active boundary carrying the newest modal activation token. */
        val modal = RenderModalInteractionScope(child = modalChild, active = true)
        /** Complete background producer deliberately collected after the modal boundary. */
        val laterBackground = ModalTestBox(width = 8, height = 8, label = "BACKGROUND")
        /** Paint-order root reproducing a later sibling after an inline Popover. */
        val root = RenderStack(children = listOf(modal, laterBackground))
        /** Pipeline owner that completes and globally filters every target category. */
        val owner = PipelineOwner(root = root)

        /** Completed frame exposed to Host and PixelTester clients. */
        val result = owner.render(logicalWidth = 8, logicalHeight = 8)

        assertOnlyTargetsFrom(result = result, source = modalChild, label = "MODAL")
    }

    /** A pointer outside modal bounds still cannot fall through to a later background sibling. */
    @Test
    fun activeModalSuppressesLaterSiblingHitOutsideItsOwnBounds() {
        /** Small modal child that does not geometrically contain the tested point. */
        val modalChild = ModalTestBox(width = 2, height = 2, label = "MODAL")
        /** Active modal boundary that contributes an unconditional private hit marker. */
        val modal = RenderModalInteractionScope(child = modalChild, active = true)
        /** Full-size background that would otherwise receive the outside point. */
        val laterBackground = ModalTestBox(width = 8, height = 8, label = "BACKGROUND")
        /** Paint-order root reproducing a later interactive sibling. */
        val root = RenderStack(children = listOf(modal, laterBackground))
        /** Pipeline owner responsible for final modal ancestry filtering. */
        val owner = PipelineOwner(root = root)
        owner.render(logicalWidth = 8, logicalHeight = 8)

        /** Point lies outside the 2x2 modal child but inside the 8x8 Host/background. */
        val result = owner.hitTest(x = 7, y = 7)

        assertTrue(result.hits.isEmpty())
    }

    /** Disabling logical modal ownership restores all normal background channels immediately. */
    @Test
    fun inactiveBoundaryRestoresBackgroundTargets() {
        /** Modal content retained solely to verify the active-to-inactive transition. */
        val modalChild = ModalTestBox(width = 2, height = 2, label = "MODAL")
        /** Boundary initially active and then retargeted to retained visual-only ownership. */
        val modal = RenderModalInteractionScope(child = modalChild, active = true)
        /** Later background sibling restored on logical close. */
        val laterBackground = ModalTestBox(width = 8, height = 8, label = "BACKGROUND")
        /** Paint-order root shared across both frames. */
        val root = RenderStack(children = listOf(modal, laterBackground))
        /** Pipeline owner retaining the same render objects across the transition. */
        val owner = PipelineOwner(root = root)
        owner.render(logicalWidth = 8, logicalHeight = 8)

        modal.update(active = false)
        /** First frame after logical close must already include normal background targets. */
        val result = owner.render(logicalWidth = 8, logicalHeight = 8)

        assertEquals(2, result.clickTargets.size)
        assertEquals(listOf("MODAL", "BACKGROUND"), result.semanticsNodes.map(PixelSemanticsNode::label))
    }

    /** The greatest nested activation token excludes targets owned only by its outer modal. */
    @Test
    fun nestedActiveModalKeepsOnlyInnermostTargets() {
        /** Outer modal created first so its activation token is older than the nested token. */
        val outerModal = RenderModalInteractionScope(child = null, active = true)
        /** Target producer that belongs to the outer modal but not its active nested modal. */
        val outerContent = ModalTestBox(width = 4, height = 4, label = "OUTER")
        /** Target producer owned by both the outer modal and the newer inner modal. */
        val innerContent = ModalTestBox(width = 2, height = 2, label = "INNER")
        /** Newer nested modal whose activation token must win the completed-session selection. */
        val innerModal = RenderModalInteractionScope(child = innerContent, active = true)
        /** Outer presentation combines its own targets with the newer nested modal subtree. */
        val outerPresentation = RenderStack(children = listOf(outerContent, innerModal))
        outerModal.setRenderObjectChild(outerPresentation)
        /** Later background proves the selected inner token applies across the whole render tree. */
        val laterBackground = ModalTestBox(width = 8, height = 8, label = "BACKGROUND")
        /** Pipeline owner completing targets from nested and later-sibling sources together. */
        val owner = PipelineOwner(root = RenderStack(children = listOf(outerModal, laterBackground)))

        /** Frame whose exported targets must all originate at the innermost producer. */
        val result = owner.render(logicalWidth = 8, logicalHeight = 8)

        assertOnlyTargetsFrom(result = result, source = innerContent, label = "INNER")
    }

    /** A retained child becomes a Host root when its semantic parent is outside the selected modal. */
    @Test
    fun filteredSemanticParentIsRepairedToNull() {
        /** Background semantic parent deliberately placed outside the active modal boundary. */
        val filteredParent = ModalTestBox(width = 8, height = 8, label = "FILTERED PARENT")
        /** Modal child retaining a parent id that will be absent after ancestry filtering. */
        val modalChild = ModalTestBox(
            width = 2,
            height = 2,
            label = "MODAL CHILD",
            semanticParentId = filteredParent.semanticId,
        )
        /** Active boundary that retains the child while excluding its declared semantic parent. */
        val modal = RenderModalInteractionScope(child = modalChild, active = true)
        /** Pipeline owner completing the invalid pre-filter parent relationship. */
        val owner = PipelineOwner(root = RenderStack(children = listOf(filteredParent, modal)))

        /** Only the child survives, so its missing parent reference must be cleared. */
        val result = owner.render(logicalWidth = 8, logicalHeight = 8)

        assertEquals(listOf("MODAL CHILD"), result.semanticsNodes.map(PixelSemanticsNode::label))
        assertNull(result.semanticsNodes.single().parentId)
    }

    /** The private semantic marker isolates background even when the modal has no child targets. */
    @Test
    fun emptyActiveModalMarkerFiltersEveryBackgroundTarget() {
        /** Empty active boundary whose private marker is its sole collected target source. */
        val emptyModal = RenderModalInteractionScope(child = null, active = true)
        /** Full target producer that must be excluded despite the modal having no public target. */
        val laterBackground = ModalTestBox(width = 8, height = 8, label = "BACKGROUND")
        /** Pipeline owner proving marker-only modal ownership survives completed-session selection. */
        val owner = PipelineOwner(root = RenderStack(children = listOf(emptyModal, laterBackground)))

        /** Frame expected to expose neither background targets nor the private modal marker. */
        val result = owner.render(logicalWidth = 8, logicalHeight = 8)

        assertNoTargets(result)
    }

    /** Asserts every target channel retained exactly one source and no private semantic marker. */
    private fun assertOnlyTargetsFrom(
        result: PixelRenderResult,
        source: RenderObject,
        label: String,
    ) {
        assertSame(source, result.clickTargets.single().source)
        assertSame(source, result.pagerTargets.single().source)
        assertSame(source, result.listTargets.single().source)
        assertSame(source, result.scrollbarTargets.single().source)
        assertSame(source, result.refreshTargets.single().source)
        assertSame(source, result.textInputTargets.single().source)
        assertSame(source, result.sliderTargets.single().source)
        assertEquals(listOf(label), result.semanticsNodes.map(PixelSemanticsNode::label))
        assertSame(source, result.semanticsTargets.single().source)
    }

    /** Asserts a marker-only modal exported no public interaction or semantic target. */
    private fun assertNoTargets(result: PixelRenderResult) {
        assertTrue(result.clickTargets.isEmpty())
        assertTrue(result.pagerTargets.isEmpty())
        assertTrue(result.listTargets.isEmpty())
        assertTrue(result.scrollbarTargets.isEmpty())
        assertTrue(result.refreshTargets.isEmpty())
        assertTrue(result.textInputTargets.isEmpty())
        assertTrue(result.sliderTargets.isEmpty())
        assertTrue(result.semanticsTargets.isEmpty())
        assertTrue(result.semanticsNodes.isEmpty())
    }

    /** Minimal render box exporting every deterministic interaction and semantic target category. */
    private class ModalTestBox(
        /** Requested logical width before parent constraint clamping. */
        private val width: Int,
        /** Requested logical height before parent constraint clamping. */
        private val height: Int,
        /** Stable semantic label identifying this source in assertions. */
        private val label: String,
        /** Optional parent id used to verify repair after modal semantic filtering. */
        private val semanticParentId: Long? = null,
    ) : RenderBox() {
        /** Pager controller retained solely to satisfy the exported target contract. */
        private val pagerController: PixelPagerController = PixelPagerController()

        /** Pager state paired with [pagerController] for deterministic target construction. */
        private val pagerState = pagerController.create(pageCount = 1)

        /** List controller shared by list and scrollbar targets from this source. */
        private val listController: PixelListController = PixelListController()

        /** List state paired with [listController] for deterministic target construction. */
        private val listState = listController.create()

        /** Pull-to-refresh controller retained solely by the refresh target. */
        private val refreshController: PixelRefreshIndicatorController =
            PixelRefreshIndicatorController()

        /** Pull-to-refresh state paired with [refreshController]. */
        private val refreshState: PixelRefreshIndicatorState = PixelRefreshIndicatorState()

        /** Text controller retained solely by the text-input target. */
        private val textController: PixelTextFieldController = PixelTextFieldController()

        /** Text state paired with [textController] for deterministic target construction. */
        private val textState = textController.create()

        /** Stable semantic id exposed so a filtered sibling can be declared as this node's parent. */
        val semanticId: Long
            get() = semanticNodeId()

        /** Resolves the fixed test size inside the supplied constraints. */
        override fun layout(constraints: RenderConstraints) {
            size = RenderSize(
                width = constraints.constrainWidth(width),
                height = constraints.constrainHeight(height),
            )
        }

        /** Test box has no visual requirement. */
        override fun paint(context: PaintContext, offsetX: Int, offsetY: Int): Unit = Unit

        /** Adds this source only when the point lies inside its own fixed bounds. */
        override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
            if (localX in 0 until size.width && localY in 0 until size.height) result.add(this)
        }

        /** Exports one click target tied to this render source for ancestry filtering. */
        override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
            targets += PixelClickTarget(
                bounds = PixelRect(offsetX, offsetY, size.width, size.height),
                onClick = { },
                source = this,
            )
        }

        /** Exports one pager target tied to this render source for ancestry filtering. */
        override fun collectPagerTargets(
            offsetX: Int,
            offsetY: Int,
            targets: MutableList<PixelPagerTarget>,
        ) {
            targets += PixelPagerTarget(
                bounds = targetBounds(offsetX, offsetY),
                axis = PixelAxis.HORIZONTAL,
                state = pagerState,
                controller = pagerController,
                onPageChanged = null,
                onPageDragStart = null,
                source = this,
            )
        }

        /** Exports one list target tied to this render source for ancestry filtering. */
        override fun collectListTargets(
            offsetX: Int,
            offsetY: Int,
            targets: MutableList<PixelListTarget>,
        ) {
            targets += PixelListTarget(
                bounds = targetBounds(offsetX, offsetY),
                viewportHeightPx = size.height,
                contentHeightPx = size.height,
                state = listState,
                controller = listController,
                source = this,
            )
        }

        /** Exports one scrollbar target tied to this render source for ancestry filtering. */
        override fun collectScrollbarTargets(
            offsetX: Int,
            offsetY: Int,
            targets: MutableList<PixelScrollbarTarget>,
        ) {
            targets += PixelScrollbarTarget(
                bounds = targetBounds(offsetX, offsetY),
                thumbBounds = targetBounds(offsetX, offsetY),
                viewportHeightPx = size.height,
                contentHeightPx = size.height,
                state = listState,
                controller = listController,
                source = this,
            )
        }

        /** Exports one refresh target tied to this render source for ancestry filtering. */
        override fun collectRefreshTargets(
            offsetX: Int,
            offsetY: Int,
            targets: MutableList<PixelRefreshTarget>,
        ) {
            targets += PixelRefreshTarget(
                bounds = targetBounds(offsetX, offsetY),
                thresholdPx = 1,
                enabled = true,
                sourceListState = listState,
                state = refreshState,
                controller = refreshController,
                onRefresh = { },
                source = this,
            )
        }

        /** Exports one text-input target tied to this render source for ancestry filtering. */
        override fun collectTextInputTargets(
            offsetX: Int,
            offsetY: Int,
            targets: MutableList<PixelTextInputTarget>,
        ) {
            targets += PixelTextInputTarget(
                bounds = targetBounds(offsetX, offsetY),
                state = textState,
                controller = textController,
                readOnly = false,
                autofocus = false,
                minLines = 1,
                maxLines = 1,
                inputType = PixelInputType.TEXT,
                action = PixelTextInputAction.DONE,
                onChanged = null,
                onSubmitted = null,
                source = this,
            )
        }

        /** Exports one slider target tied to this render source for ancestry filtering. */
        override fun collectSliderTargets(
            offsetX: Int,
            offsetY: Int,
            targets: MutableList<PixelSliderTarget>,
        ) {
            targets += PixelSliderTarget(
                bounds = targetBounds(offsetX, offsetY),
                onDrag = { _ -> },
                onRelease = { _ -> },
                source = this,
            )
        }

        /** Exports one semantic node tied to this render source for ancestry filtering. */
        override fun collectSemantics(
            offsetX: Int,
            offsetY: Int,
            targets: MutableList<PixelSemanticsTarget>,
        ) {
            targets += PixelSemanticsTarget(
                node = PixelSemanticsNode(
                    id = semanticNodeId(),
                    label = label,
                    role = PixelSemanticRole.BUTTON,
                    enabled = true,
                    focused = false,
                    left = offsetX,
                    top = offsetY,
                    width = size.width,
                    height = size.height,
                    parentId = semanticParentId,
                ),
                source = this,
            )
        }

        /** Resolves this box's current global target bounds for every exported interaction channel. */
        private fun targetBounds(offsetX: Int, offsetY: Int): PixelRect {
            return PixelRect(offsetX, offsetY, size.width, size.height)
        }
    }
}
