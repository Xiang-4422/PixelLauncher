package com.purride.pixelui.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BottomSheet
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Container
import com.purride.pixelui.Dialog
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.PixelBackDispatcher
import com.purride.pixelui.PixelBackHost
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelMotionRole
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.PixelOverlayBarrier
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayDismissAction
import com.purride.pixelui.PixelOverlayDismissPolicy
import com.purride.pixelui.PixelOverlayDismissReason
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.PixelOverlayLayer
import com.purride.pixelui.PixelOverlayLifecycle
import com.purride.pixelui.PixelOverlayMotion
import com.purride.pixelui.PixelOverlayOutcome
import com.purride.pixelui.PixelOverlaySurface
import com.purride.pixelui.PixelPopupRoute
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.Popover
import com.purride.pixelui.Semantics
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.VoidCallback
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Behavior coverage for typed overlay routes, ordering, policy, and permanent identity. */
class PixelOverlayLifecycleTest {
    /** A route chooses one typed completion and ignores every later terminal call. */
    @Test
    fun typedCompletionIsExactlyOnceAndRetainedAfterDisposal() {
        val controller = PixelOverlayController()
        val outcomes = mutableListOf<PixelOverlayOutcome<Int>>()
        val entry = controller.show(
            PixelPopupRoute(
                content = Text("PICKER"),
                onOutcome = outcomes::add,
            ),
        )

        assertEquals(PixelOverlayLifecycle.Active, entry.lifecycle)
        assertNull(entry.outcome)
        assertTrue(entry.complete(7))
        assertFalse(entry.complete(8))
        assertFalse(entry.dismiss(PixelOverlayDismissReason.Programmatic))

        assertEquals(0, controller.size)
        assertEquals(PixelOverlayLifecycle.Disposed, entry.lifecycle)
        assertEquals(PixelOverlayOutcome.Completed(7), entry.outcome)
        assertEquals(listOf(PixelOverlayOutcome.Completed(7)), outcomes)
    }

    /** Barrier semantics closes the owning route and reports the precise dismissal reason. */
    @Test
    fun barrierDismissesOnlyItsEntryWithReason() {
        val tester = PixelTester()
        val controller = PixelOverlayController()
        val outcomes = mutableListOf<PixelOverlayOutcome<Unit>>()
        tester.pumpWidget(
            widget = PixelOverlayHost(controller = controller, child = Text("HOME")),
            logicalWidth = 64,
            logicalHeight = 24,
        )
        val entry = controller.show(
            PixelPopupRoute(
                content = SizedBox(width = 8, height = 8, child = Text("CARD")),
                layer = PixelOverlayLayer.Modal,
                barrier = PixelOverlayBarrier(color = PixelColor.Transparent),
                modal = true,
                onOutcome = outcomes::add,
            ),
        )
        tester.pumpFrame(0)

        val dismissNode = tester.semanticsNodesByLabel("Dismiss").single()
        assertTrue(tester.performSemanticsAction(dismissNode.id, PixelSemanticsAction.DISMISS))

        assertEquals(0, controller.size)
        assertEquals(PixelOverlayLifecycle.Disposed, entry.lifecycle)
        assertEquals(
            listOf(PixelOverlayOutcome.Dismissed(PixelOverlayDismissReason.Barrier)),
            outcomes,
        )
        tester.dispose()
    }

    /** Back skips passive upper layers, dismisses the next eligible route, and honors Consume. */
    @Test
    fun backUsesLayerOrderAndExplicitPolicy() {
        val tester = PixelTester()
        val dispatcher = PixelBackDispatcher()
        val controller = PixelOverlayController()
        tester.pumpWidget(
            widget = PixelBackHost(
                dispatcher = dispatcher,
                child = PixelOverlayHost(controller = controller, child = Text("HOME")),
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )
        val dismissible = controller.show(
            PixelPopupRoute<Unit>(
                content = Text("LOWER"),
                layer = PixelOverlayLayer.Modal,
            ),
        )
        val passive = controller.show(
            PixelPopupRoute<Unit>(
                content = Text("PASSIVE"),
                layer = PixelOverlayLayer.System,
                dismissPolicy = PixelOverlayDismissPolicy.Passive,
            ),
        )
        tester.pumpFrame(0)

        assertTrue(dispatcher.handleBack())
        tester.pumpFrame(0)
        assertEquals(
            PixelOverlayOutcome.Dismissed(PixelOverlayDismissReason.Back),
            dismissible.outcome,
        )
        assertEquals(PixelOverlayLifecycle.Active, passive.lifecycle)

        val locked = controller.show(
            PixelPopupRoute<Unit>(
                content = Text("LOCKED"),
                layer = PixelOverlayLayer.System,
                dismissPolicy = PixelOverlayDismissPolicy(
                    back = PixelOverlayDismissAction.Consume,
                    barrierTap = PixelOverlayDismissAction.Ignore,
                ),
            ),
        )
        tester.pumpFrame(0)

        assertTrue(dispatcher.handleBack())
        assertEquals(PixelOverlayLifecycle.Active, locked.lifecycle)
        assertEquals(2, controller.size)
        tester.dispose()
    }

    /** Paint, Back, semantics, focus, and Escape all follow canonical layer/insertion z-order. */
    @Test
    fun canonicalTopModalWinsWhenLowerLayerMountsLater() {
        val tester = PixelTester()
        val dispatcher = PixelBackDispatcher()
        val controller = PixelOverlayController()
        val systemFocus = FocusNode("system-modal")
        val popupFocus = FocusNode("popup-modal")
        val secondSystemFocus = FocusNode("second-system-modal")
        val systemColor = PixelColor.fromRgb(220, 40, 40)
        val popupColor = PixelColor.fromRgb(40, 80, 220)
        tester.pumpWidget(
            widget = PixelBackHost(
                dispatcher = dispatcher,
                child = PixelOverlayHost(
                    controller = controller,
                    child = Container(width = 24, height = 24, fillColor = PixelColor.Black),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 24,
        )
        val system = controller.show(
            modalFixtureRoute(
                label = "SYSTEM MODAL",
                node = systemFocus,
                color = systemColor,
                layer = PixelOverlayLayer.System,
            ),
        )
        tester.pumpFrame(0)
        assertTrue(systemFocus.isFocused)

        val popup = controller.show(
            modalFixtureRoute(
                label = "POPUP MODAL",
                node = popupFocus,
                color = popupColor,
                layer = PixelOverlayLayer.Popup,
            ),
        )
        tester.pumpFrame(0)

        assertEquals(systemColor, tester.pixelAt(10, 10))
        assertTrue(systemFocus.isFocused)
        assertFalse(popupFocus.isFocused)
        assertFalse(popupFocus.requestFocus())
        assertEquals(1, tester.semanticsNodesByLabel("SYSTEM MODAL").size)
        assertTrue(tester.semanticsNodesByLabel("POPUP MODAL").isEmpty())

        assertTrue(dispatcher.handleBack())
        tester.pumpFrame(0)
        assertEquals(PixelOverlayOutcome.Dismissed(PixelOverlayDismissReason.Back), system.outcome)
        assertEquals(PixelOverlayLifecycle.Active, popup.lifecycle)
        assertEquals(popupColor, tester.pixelAt(10, 10))
        assertTrue(popupFocus.isFocused)
        assertEquals(1, tester.semanticsNodesByLabel("POPUP MODAL").size)

        val secondSystem = controller.show(
            modalFixtureRoute(
                label = "SECOND SYSTEM MODAL",
                node = secondSystemFocus,
                color = systemColor,
                layer = PixelOverlayLayer.System,
            ),
        )
        tester.pumpFrame(0)
        assertTrue(secondSystemFocus.isFocused)
        assertTrue(tester.pressKey(PixelKey.ESCAPE))
        tester.pumpFrame(0)

        assertEquals(
            PixelOverlayOutcome.Dismissed(PixelOverlayDismissReason.DismissRequest),
            secondSystem.outcome,
        )
        assertEquals(PixelOverlayLifecycle.Active, popup.lifecycle)
        assertTrue(popupFocus.isFocused)
        tester.dispose()
    }

    /** Escape scans canonical routes even when focus belongs to a lower modal that ignores Back. */
    @Test
    fun escapeDismissesHigherNonModalRouteBeforeIgnoredModalOwner() {
        /** Runtime test host used to dispatch the normalized Escape event. */
        val tester = PixelTester()
        /** Controller containing a lower modal focus owner and a higher non-modal route. */
        val controller = PixelOverlayController()
        /** Focus node proving the lower modal remains the keyboard dispatch owner. */
        val modalFocus = FocusNode("ignored-modal")
        tester.pumpWidget(
            widget = PixelOverlayHost(controller = controller, child = Text("HOME")),
            logicalWidth = 32,
            logicalHeight = 20,
        )
        /** Lower modal deliberately configured to let canonical dismissal scanning continue. */
        val modal = controller.show(
            PixelPopupRoute<Unit>(
                content = Focus(
                    node = modalFocus,
                    autofocus = true,
                    child = Text("IGNORED MODAL"),
                ),
                layer = PixelOverlayLayer.Modal,
                dismissPolicy = PixelOverlayDismissPolicy.Passive,
                modal = true,
            ),
        )
        /** Higher System route that must receive Escape despite not owning modal focus. */
        val system = controller.show(
            PixelPopupRoute<Unit>(
                content = Text("SYSTEM NOTICE"),
                layer = PixelOverlayLayer.System,
            ),
        )
        tester.pumpFrame(0)

        assertTrue(modalFocus.isFocused)
        assertTrue(tester.pressKey(PixelKey.ESCAPE))
        tester.pumpFrame(0)

        assertEquals(
            PixelOverlayOutcome.Dismissed(PixelOverlayDismissReason.DismissRequest),
            system.outcome,
        )
        assertEquals(PixelOverlayLifecycle.Active, modal.lifecycle)
        assertTrue(modalFocus.isFocused)
        /** Once the higher consumer is gone, the passive route must let Escape reach the host. */
        assertFalse(tester.pressKey(PixelKey.ESCAPE))
        assertEquals(PixelOverlayLifecycle.Active, modal.lifecycle)
        tester.dispose()
    }

    /** A higher non-modal route stays painted, semantic, and clickable above an active modal. */
    @Test
    fun higherNonModalRouteRemainsInteractiveAboveActiveModal() {
        /** Runtime test host used to inspect every exported interaction channel. */
        val tester = PixelTester()
        /** Controller whose canonical route order drives both paint and modal filtering. */
        val controller = PixelOverlayController()
        /** Observable counter proving the higher route receives its own pointer event. */
        var systemTapCount = 0
        /** Color expected at the overlap point from the higher System route. */
        val systemColor = PixelColor.fromRgb(40, 200, 80)
        tester.pumpWidget(
            widget = PixelOverlayHost(
                controller = controller,
                child = Semantics(
                    label = "BACKGROUND ACTION",
                    child = GestureDetector(
                        onTap = {},
                        child = SizedBox(width = 24, height = 20),
                    ),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 20,
        )
        /** Active lower modal that must continue isolating the application background. */
        controller.show(
            PixelPopupRoute<Unit>(
                content = Semantics(
                    label = "ACTIVE MODAL",
                    child = Container(
                        width = 16,
                        height = 16,
                        fillColor = PixelColor.fromRgb(180, 40, 40),
                    ),
                ),
                layer = PixelOverlayLayer.Modal,
                dismissPolicy = PixelOverlayDismissPolicy.Locked,
                modal = true,
            ),
        )
        /** Later System route intentionally remains non-modal and directly interactive. */
        controller.show(
            PixelPopupRoute<Unit>(
                content = Semantics(
                    label = "SYSTEM ACTION",
                    child = GestureDetector(
                        onTap = { systemTapCount += 1 },
                        key = "system-action",
                        child = Container(
                            width = 8,
                            height = 8,
                            fillColor = systemColor,
                        ),
                    ),
                ),
                layer = PixelOverlayLayer.System,
                dismissPolicy = PixelOverlayDismissPolicy.Passive,
                modal = false,
            ),
        )
        tester.pumpFrame(0)

        assertEquals(systemColor, tester.pixelAt(2, 2))
        assertEquals(1, tester.semanticsNodesByLabel("ACTIVE MODAL").size)
        assertEquals(1, tester.semanticsNodesByLabel("SYSTEM ACTION").size)
        assertTrue(tester.semanticsNodesByLabel("BACKGROUND ACTION").isEmpty())
        tester.tap(find.byKey("system-action"))
        assertEquals(1, systemTapCount)
        tester.dispose()
    }

    /** A higher non-modal route can own keyboard focus and restores the lower modal on close. */
    @Test
    fun higherNonModalRouteFocusRestoresLowerModalOwner() {
        /** Runtime test host used for direct focus, keyboard dispatch, and restoration assertions. */
        val tester = PixelTester()
        /** Controller whose Modal and System routes establish canonical focus order. */
        val controller = PixelOverlayController()
        /** Background node proving the lower modal continues isolating application focus. */
        val backgroundFocus = FocusNode("background")
        /** Lower modal node expected to regain focus after the System route closes. */
        val modalFocus = FocusNode("lower-modal")
        /** Higher non-modal node that requests focus explicitly after mounting. */
        val systemFocus = FocusNode("higher-system")
        /** Keyboard action count proving dispatch begins at the higher route while it owns focus. */
        var systemEnterCount = 0
        tester.pumpWidget(
            widget = PixelOverlayHost(
                controller = controller,
                child = Focus(
                    node = backgroundFocus,
                    autofocus = true,
                    child = Text("BACKGROUND"),
                ),
            ),
            logicalWidth = 40,
            logicalHeight = 20,
        )
        /** Lower active modal that owns background isolation throughout the test. */
        val modal = controller.show(
            PixelPopupRoute<Unit>(
                content = Focus(
                    node = modalFocus,
                    autofocus = true,
                    child = Text("LOWER MODAL"),
                ),
                layer = PixelOverlayLayer.Modal,
                dismissPolicy = PixelOverlayDismissPolicy.Locked,
                modal = true,
            ),
        )
        /** Higher route that remains non-modal and therefore never joins the modal focus stack. */
        val system = controller.show(
            PixelPopupRoute<Unit>(
                content = Focus(
                    node = systemFocus,
                    onKeyEvent = { event ->
                        if (event.key == PixelKey.ENTER) {
                            systemEnterCount += 1
                            true
                        } else {
                            false
                        }
                    },
                    child = Text("HIGHER SYSTEM"),
                ),
                layer = PixelOverlayLayer.System,
                dismissPolicy = PixelOverlayDismissPolicy.Passive,
                modal = false,
            ),
        )
        tester.pumpFrame(0)

        assertTrue(modalFocus.isFocused)
        assertFalse(backgroundFocus.requestFocus())
        assertTrue(systemFocus.requestFocus())
        assertTrue(systemFocus.isFocused)
        assertTrue(tester.pressKey(PixelKey.ENTER))
        assertEquals(1, systemEnterCount)

        assertTrue(system.dismiss(PixelOverlayDismissReason.Handle))
        tester.pumpFrame(0)

        assertEquals(PixelOverlayLifecycle.Disposed, system.lifecycle)
        assertEquals(PixelOverlayLifecycle.Active, modal.lifecycle)
        assertFalse(systemFocus.isFocused)
        assertTrue(modalFocus.isFocused)
        assertFalse(backgroundFocus.requestFocus())
        tester.dispose()
    }

    /** Higher non-modal autofocus receives Escape while dismissal still scans canonical routes. */
    @Test
    fun higherNonModalAutofocusUsesCanonicalEscapeAndRestoresModal() {
        /** Runtime test host dispatching normalized autofocus and Escape behavior. */
        val tester = PixelTester()
        /** Controller containing a passive lower modal and dismissible higher System route. */
        val controller = PixelOverlayController()
        /** Lower modal focus restored after canonical Escape removes the higher route. */
        val modalFocus = FocusNode("passive-lower-modal")
        /** Higher non-modal autofocus target proving focus eligibility follows paint order. */
        val systemFocus = FocusNode("autofocus-system")
        tester.pumpWidget(
            widget = PixelOverlayHost(controller = controller, child = Text("HOME")),
            logicalWidth = 40,
            logicalHeight = 20,
        )
        /** Passive modal that ignores Escape but continues isolating the application background. */
        val modal = controller.show(
            PixelPopupRoute<Unit>(
                content = Focus(
                    node = modalFocus,
                    autofocus = true,
                    child = Text("PASSIVE MODAL"),
                ),
                layer = PixelOverlayLayer.Modal,
                dismissPolicy = PixelOverlayDismissPolicy.Passive,
                modal = true,
            ),
        )
        /** Dismissible higher route whose autofocus must not replace modal ownership semantics. */
        val system = controller.show(
            PixelPopupRoute<Unit>(
                content = Focus(
                    node = systemFocus,
                    autofocus = true,
                    child = Text("AUTOFOCUS SYSTEM"),
                ),
                layer = PixelOverlayLayer.System,
                modal = false,
            ),
        )
        tester.pumpFrame(0)

        assertTrue(systemFocus.isFocused)
        assertFalse(modalFocus.isFocused)
        assertTrue(tester.pressKey(PixelKey.ESCAPE))
        tester.pumpFrame(0)

        assertEquals(
            PixelOverlayOutcome.Dismissed(PixelOverlayDismissReason.DismissRequest),
            system.outcome,
        )
        assertEquals(PixelOverlayLifecycle.Active, modal.lifecycle)
        assertTrue(modalFocus.isFocused)
        tester.dispose()
    }

    /** A lower modal inserted later cannot steal focus from an existing higher System route. */
    @Test
    fun laterLowerModalPreservesHigherNonModalFocusAcrossItsLifecycle() {
        /** Runtime test host exercising canonical order independently from insertion order. */
        val tester = PixelTester()
        /** Controller that inserts System first and the canonically lower Modal second. */
        val controller = PixelOverlayController()
        /** Higher System autofocus target that must remain primary throughout lower modal changes. */
        val systemFocus = FocusNode("existing-system")
        /** Later Modal autofocus target that must remain ineligible while System owns focus. */
        val modalFocus = FocusNode("later-lower-modal")
        tester.pumpWidget(
            widget = PixelOverlayHost(controller = controller, child = Text("HOME")),
            logicalWidth = 40,
            logicalHeight = 20,
        )
        /** Existing higher non-modal route mounted before the lower modal. */
        val system = controller.show(
            PixelPopupRoute<Unit>(
                content = Focus(
                    node = systemFocus,
                    autofocus = true,
                    child = Text("SYSTEM"),
                ),
                layer = PixelOverlayLayer.System,
                dismissPolicy = PixelOverlayDismissPolicy.Passive,
                modal = false,
            ),
        )
        tester.pumpFrame(0)
        assertTrue(systemFocus.isFocused)

        /** Later-inserted modal whose lower layer must not override the System route. */
        val modal = controller.show(
            PixelPopupRoute<Unit>(
                content = Focus(
                    node = modalFocus,
                    autofocus = true,
                    child = Text("LOWER MODAL"),
                ),
                layer = PixelOverlayLayer.Modal,
                modal = true,
            ),
        )
        tester.pumpFrame(0)

        assertTrue(systemFocus.isFocused)
        assertFalse(modalFocus.isFocused)
        assertTrue(modal.dismiss(PixelOverlayDismissReason.Handle))
        tester.pumpFrame(0)

        assertEquals(PixelOverlayLifecycle.Disposed, modal.lifecycle)
        assertEquals(PixelOverlayLifecycle.Active, system.lifecycle)
        assertTrue(systemFocus.isFocused)
        tester.dispose()
    }

    /** A standalone non-modal route can autofocus but leaves Tab traversal free to reach the app. */
    @Test
    fun standaloneNonModalRouteDoesNotCreateFocusTrap() {
        /** Runtime test host used to prove traversal remains non-modal. */
        val tester = PixelTester()
        /** Controller containing only one non-modal Popup route. */
        val controller = PixelOverlayController()
        /** Application focus target that must remain reachable by Tab. */
        val backgroundFocus = FocusNode("standalone-background")
        /** Overlay autofocus target that must not trap subsequent traversal. */
        val popupFocus = FocusNode("standalone-popup")
        tester.pumpWidget(
            widget = PixelOverlayHost(
                controller = controller,
                child = Focus(
                    node = backgroundFocus,
                    autofocus = true,
                    child = Text("BACKGROUND"),
                ),
            ),
            logicalWidth = 40,
            logicalHeight = 20,
        )
        /** Standalone non-modal route that deliberately requests autofocus. */
        val popup = controller.show(
            PixelPopupRoute<Unit>(
                content = Focus(
                    node = popupFocus,
                    autofocus = true,
                    child = Text("POPUP"),
                ),
                dismissPolicy = PixelOverlayDismissPolicy.Passive,
                modal = false,
            ),
        )
        tester.pumpFrame(0)

        assertTrue(popupFocus.isFocused)
        assertTrue(tester.pressKey(PixelKey.TAB))
        assertTrue(backgroundFocus.isFocused)
        assertFalse(popupFocus.isFocused)

        assertTrue(popup.dismiss(PixelOverlayDismissReason.Handle))
        tester.pumpFrame(0)
        assertEquals(PixelOverlayLifecycle.Disposed, popup.lifecycle)
        assertTrue(backgroundFocus.isFocused)
        tester.dispose()
    }

    /** Standard and custom surfaces absorb internal blank taps while keeping route barrier usable. */
    @Test
    fun modalRouteCoalescesNestedOwnersAndAbsorbsSurfaceTaps() {
        val tester = PixelTester()
        val controller = PixelOverlayController()
        tester.pumpWidget(
            widget = PixelOverlayHost(controller = controller, child = Text("HOME")),
            logicalWidth = 64,
            logicalHeight = 32,
        )
        val fixtures = listOf(
            SurfaceFixture(
                widget = Dialog(
                    content = SizedBox(width = 10, height = 4),
                    key = "dialog-surface",
                ),
                surfaceKey = "dialog-surface-overlay-surface",
                semanticLabel = "Dialog",
            ),
            SurfaceFixture(
                widget = BottomSheet(
                    content = SizedBox(width = 10, height = 4),
                    key = "sheet-surface",
                ),
                surfaceKey = "sheet-surface-overlay-surface",
                semanticLabel = "Bottom sheet",
            ),
            SurfaceFixture(
                widget = Center(
                    child = PixelOverlaySurface(
                        child = Container(
                            width = 12,
                            height = 6,
                            fillColor = PixelColor.fromRgb(80, 80, 80),
                        ),
                        key = "custom-surface",
                    ),
                ),
                surfaceKey = "custom-surface",
                semanticLabel = null,
            ),
        )

        fixtures.forEach { fixture ->
            val entry = controller.show(
                PixelPopupRoute<Unit>(
                    content = fixture.widget,
                    layer = PixelOverlayLayer.Modal,
                    barrier = PixelOverlayBarrier(color = PixelColor.Transparent),
                    modal = true,
                ),
            )
            tester.pumpFrame(0)

            fixture.semanticLabel?.let { label ->
                assertEquals(1, tester.semanticsNodesByLabel(label).size)
            }
            val dismissNode = tester.semanticsNodesByLabel("Dismiss").single()
            tester.tap(find.byKey(fixture.surfaceKey))
            assertEquals(PixelOverlayLifecycle.Active, entry.lifecycle)
            assertTrue(tester.performSemanticsAction(dismissNode.id, PixelSemanticsAction.DISMISS))
            assertEquals(PixelOverlayDismissReason.Barrier, dismissedReason(entry.outcome))
        }
        tester.dispose()
    }

    /** Popover automatically absorbs taps inside its measured follower surface. */
    @Test
    fun popoverSurfaceDoesNotTapThroughToDismissBarrier() {
        val tester = PixelTester()
        var dismissCount = 0
        tester.pumpWidget(
            widget = Popover(
                anchor = Text("ANCHOR"),
                content = Container(
                    width = 12,
                    height = 6,
                    fillColor = PixelColor.fromRgb(80, 80, 80),
                    key = "popover-surface",
                ),
                expanded = true,
                dismissible = true,
                onDismiss = { dismissCount += 1 },
                modal = true,
            ),
            logicalWidth = 64,
            logicalHeight = 32,
        )
        tester.pumpFrame(0)

        tester.tap(find.byKey("popover-surface-overlay-surface"))
        assertEquals(0, dismissCount)
        val dismissNode = tester.semanticsNodesByLabel("Dismiss").single()
        assertTrue(tester.performSemanticsAction(dismissNode.id, PixelSemanticsAction.DISMISS))
        assertEquals(1, dismissCount)
        tester.dispose()
    }

    /** Clear resolves callbacks in visual top-to-bottom order and disposes every active entry. */
    @Test
    fun clearUsesLayerThenInsertionOrderForOutcomes() {
        val controller = PixelOverlayController()
        val callbackOrder = mutableListOf<String>()
        val lowerFirst = controller.show(
            routeWithDismissCallback("lower-first", PixelOverlayLayer.Popup, callbackOrder),
        )
        val modal = controller.show(
            routeWithDismissCallback("modal", PixelOverlayLayer.Modal, callbackOrder),
        )
        val lowerLast = controller.show(
            routeWithDismissCallback("lower-last", PixelOverlayLayer.Popup, callbackOrder),
        )

        controller.clear()

        assertEquals(listOf("modal", "lower-last", "lower-first"), callbackOrder)
        assertEquals(PixelOverlayLifecycle.Disposed, lowerFirst.lifecycle)
        assertEquals(PixelOverlayLifecycle.Disposed, modal.lifecycle)
        assertEquals(PixelOverlayLifecycle.Disposed, lowerLast.lifecycle)
        assertEquals(0, controller.size)
    }

    /** A hosted synchronous entry delivers its outcome only after descendant State disposal. */
    @Test
    fun hostedOutcomeWaitsForActualPresentationUnmount() {
        val tester = PixelTester()
        val controller = PixelOverlayController()
        var childDisposed = false
        var callbackObservedDisposal: Boolean? = null
        tester.pumpWidget(
            widget = PixelOverlayHost(controller = controller, child = Text("HOME")),
            logicalWidth = 64,
            logicalHeight = 24,
        )
        val entry = controller.show(
            PixelPopupRoute<Unit>(
                content = DisposalProbeWidget { childDisposed = true },
                onOutcome = { callbackObservedDisposal = childDisposed },
            ),
        )
        tester.pumpFrame(0)

        assertTrue(entry.dismiss(PixelOverlayDismissReason.Handle))
        assertEquals(PixelOverlayLifecycle.Removing, entry.lifecycle)
        assertFalse(childDisposed)
        assertNull(callbackObservedDisposal)
        tester.pumpFrame(0)

        assertTrue(childDisposed)
        assertEquals(PixelOverlayLifecycle.Disposed, entry.lifecycle)
        assertEquals(true, callbackObservedDisposal)
        tester.dispose()
    }

    /** A throwing outcome is reported after overlay render, focus, semantics, and queues detach. */
    @Test
    fun throwingHostedOutcomeLeavesNoPresentationResidueAndHostRemainsReusable() {
        /** Runtime host whose retained teardown and later replacement are exercised. */
        val tester = PixelTester()
        /** Controller retaining the typed entry after its presentation reaches Disposed. */
        val controller = PixelOverlayController()
        /** Focus node proving the failed callback cannot retain modal focus ownership. */
        val throwingFocus = FocusNode("throwing-overlay")
        /** Focus node proving a later presentation can acquire the same runtime normally. */
        val replacementFocus = FocusNode("replacement-overlay")
        /** Exactly-once callback counter retained even though the callback raises an error. */
        var outcomeCount = 0
        /** Click counter proving the replacement exports a live interaction target. */
        var replacementTapCount = 0
        /** Background color expected after the failed presentation has fully disappeared. */
        val backgroundColor = PixelColor.fromRgb(8, 12, 16)
        /** Replacement color expected after the same Host presents a later route. */
        val replacementColor = PixelColor.fromRgb(40, 180, 90)

        try {
            tester.pumpWidget(
                widget = PixelOverlayHost(
                    controller = controller,
                    child = Semantics(
                        label = "HOST BACKGROUND",
                        child = Container(
                            width = 24,
                            height = 20,
                            fillColor = backgroundColor,
                        ),
                    ),
                ),
                logicalWidth = 24,
                logicalHeight = 20,
            )
            /** Clean retained Element topology used to detect a stale presentation shell. */
            val baselineElementTree = tester.dumpElementTree()
            /** Clean render topology used to detect render objects retained by failed disposal. */
            val baselineRenderTree = tester.dumpRenderTree()
            /** Route whose post-unmount outcome deliberately fails in user code. */
            val entry = controller.show(
                PixelPopupRoute<Unit>(
                    content = Focus(
                        node = throwingFocus,
                        autofocus = true,
                        child = Semantics(
                            label = "THROWING OVERLAY",
                            child = Container(
                                width = 16,
                                height = 16,
                                fillColor = PixelColor.fromRgb(190, 40, 40),
                            ),
                        ),
                    ),
                    layer = PixelOverlayLayer.Modal,
                    modal = true,
                    onOutcome = {
                        outcomeCount += 1
                        error("throwing-overlay-outcome")
                    },
                ),
            )
            tester.pumpFrame(0)
            assertTrue(throwingFocus.isFocused)
            assertEquals(1, tester.semanticsNodesByLabel("THROWING OVERLAY").size)

            assertTrue(entry.dismiss(PixelOverlayDismissReason.Handle))
            /** Failure must escape only after the keyed presentation and descendants unmount. */
            val failure = assertThrows(IllegalStateException::class.java) {
                tester.pumpFrame(0)
            }
            assertEquals("throwing-overlay-outcome", failure.message)
            assertEquals(1, outcomeCount)
            assertEquals(PixelOverlayLifecycle.Disposed, entry.lifecycle)
            assertEquals(0, controller.size)
            assertFalse(throwingFocus.isFocused)
            assertEquals(baselineElementTree, tester.dumpElementTree())
            assertEquals(baselineRenderTree, tester.dumpRenderTree())
            assertEquals(0, tester.scheduler.pendingCount)
            assertEquals(0, tester.vsync.liveTickerCount)

            // The failed pass committed retained teardown; one later frame publishes that clean tree.
            tester.pumpFrame(0)
            assertTrue(tester.semanticsNodesByLabel("THROWING OVERLAY").isEmpty())
            assertEquals(1, tester.semanticsNodesByLabel("HOST BACKGROUND").size)
            assertEquals(backgroundColor, tester.pixelAt(2, 2))

            /** Later route proving presentation slots, focus owner, render targets, and callbacks recover. */
            val replacement = controller.show(
                PixelPopupRoute<Unit>(
                    content = Focus(
                        node = replacementFocus,
                        autofocus = true,
                        child = Semantics(
                            label = "REPLACEMENT OVERLAY",
                            child = GestureDetector(
                                onTap = { replacementTapCount += 1 },
                                key = "replacement-overlay-action",
                                child = Container(
                                    width = 10,
                                    height = 10,
                                    fillColor = replacementColor,
                                ),
                            ),
                        ),
                    ),
                    layer = PixelOverlayLayer.Modal,
                    modal = true,
                ),
            )
            tester.pumpFrame(0)

            assertTrue(replacementFocus.isFocused)
            assertEquals(replacementColor, tester.pixelAt(2, 2))
            assertEquals(1, tester.semanticsNodesByLabel("REPLACEMENT OVERLAY").size)
            tester.tap(find.byKey("replacement-overlay-action"))
            assertEquals(1, replacementTapCount)
            assertTrue(replacement.dismiss(PixelOverlayDismissReason.Handle))
            tester.pumpFrame(0)
            assertEquals(PixelOverlayLifecycle.Disposed, replacement.lifecycle)
            assertFalse(replacementFocus.isFocused)
            assertEquals(0, tester.scheduler.pendingCount)
            assertEquals(0, tester.vsync.liveTickerCount)
        } finally {
            tester.dispose()
        }
    }

    /** A failing external listener cannot strand hosted removal or a ready unhosted outcome. */
    @Test
    fun throwingControllerListenerStillNotifiesHostAndDrainsReadyOutcomes() {
        /** Controller whose throwing listener is deliberately registered before the Host watcher. */
        val hostedController = PixelOverlayController()
        /** Focus node proving the mounted route loses keyboard ownership despite listener failure. */
        val hostedFocus = FocusNode("throwing-listener-route")
        /** Hosted callback count expected only after the presentation unmounts. */
        var hostedOutcomeCount = 0
        /** Route created before Host mount so listener registration order is deterministic. */
        val hostedEntry = hostedController.show(
            PixelPopupRoute<Unit>(
                content = Focus(
                    node = hostedFocus,
                    autofocus = true,
                    child = Text("THROWING LISTENER ROUTE"),
                ),
                modal = true,
                onOutcome = { hostedOutcomeCount += 1 },
            ),
        )
        /** External observer failure that must not block the later Host listener. */
        val listenerFailure = IllegalStateException("controller-listener-failure")
        hostedController.addListener(VoidCallback { throw listenerFailure })
        /** Runtime mounting its own controller watcher after the throwing observer. */
        val tester = PixelTester()
        tester.pumpWidget(
            widget = PixelOverlayHost(controller = hostedController, child = Text("HOME")),
            logicalWidth = 64,
            logicalHeight = 24,
        )
        assertTrue(hostedFocus.isFocused)

        /** Failure reported to the mutator only after every listener received the close. */
        var observedFailure: Throwable? = null
        try {
            hostedEntry.dismiss(PixelOverlayDismissReason.Handle)
        } catch (failure: Throwable) {
            observedFailure = failure
        }
        assertTrue(observedFailure === listenerFailure)
        assertEquals(PixelOverlayLifecycle.Removing, hostedEntry.lifecycle)
        tester.pumpFrame(0)

        assertEquals(PixelOverlayLifecycle.Disposed, hostedEntry.lifecycle)
        assertEquals(1, hostedOutcomeCount)
        assertFalse(hostedFocus.isFocused)
        assertFalse(tester.exists(find.byText("THROWING LISTENER ROUTE")))
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()

        /** Unhosted controller proving notification failure cannot skip an already-ready outcome. */
        val unhostedController = PixelOverlayController()
        /** Exactly-once callback count for an entry that can dispose synchronously. */
        var unhostedOutcomeCount = 0
        /** Entry with no mounted presentation and therefore an immediately ready outcome. */
        val unhostedEntry = unhostedController.show(
            PixelPopupRoute<Unit>(
                content = Text("UNHOSTED"),
                onOutcome = { unhostedOutcomeCount += 1 },
            ),
        )
        unhostedController.addListener(VoidCallback { error("unhosted-listener-failure") })
        try {
            unhostedEntry.dismiss(PixelOverlayDismissReason.Handle)
        } catch (_: IllegalStateException) {
            // The listener failure remains observable after the ready outcome has been delivered.
        }
        assertEquals(PixelOverlayLifecycle.Disposed, unhostedEntry.lifecycle)
        assertEquals(1, unhostedOutcomeCount)
    }

    /** Reusing one Throwable across callbacks cannot strand the outcome drain reentrancy guard. */
    @Test
    fun repeatedOutcomeFailureStillDrainsEveryReadyEntryAndAllowsLaterDelivery() {
        /** Controller whose unhosted entries all become ready during one synchronous clear. */
        val controller = PixelOverlayController()
        /** Shared failure deliberately thrown by two independent user callbacks. */
        val sharedFailure = IllegalStateException("shared-outcome-failure")
        /** Callback order proving clear keeps draining after repeated identical failures. */
        val callbacks = mutableListOf<String>()
        /** Lowest entry, delivered last by top-to-bottom clear order. */
        val lower = controller.show(
            PixelPopupRoute<Unit>(
                content = Text("LOWER"),
                onOutcome = {
                    callbacks += "lower"
                    throw sharedFailure
                },
            ),
        )
        /** Middle entry that establishes [sharedFailure] as the aggregate primary error. */
        val middle = controller.show(
            PixelPopupRoute<Unit>(
                content = Text("MIDDLE"),
                onOutcome = {
                    callbacks += "middle"
                    throw sharedFailure
                },
            ),
        )
        /** Highest entry proving a successful callback also runs before either failure. */
        val upper = controller.show(
            PixelPopupRoute<Unit>(
                content = Text("UPPER"),
                onOutcome = { callbacks += "upper" },
            ),
        )

        /** Error observed only after all three ready callbacks leave the drain queue. */
        var observedFailure: Throwable? = null
        try {
            controller.clear()
        } catch (failure: Throwable) {
            observedFailure = failure
        }

        assertTrue(observedFailure === sharedFailure)
        assertTrue(sharedFailure.suppressed.isEmpty())
        assertEquals(listOf("upper", "middle", "lower"), callbacks)
        assertEquals(PixelOverlayLifecycle.Disposed, lower.lifecycle)
        assertEquals(PixelOverlayLifecycle.Disposed, middle.lifecycle)
        assertEquals(PixelOverlayLifecycle.Disposed, upper.lifecycle)

        /** Later entry proving the guard was reset even though the previous drain threw. */
        var laterOutcomeCount = 0
        /** Fresh entry whose immediate dismissal must start and finish another drain. */
        val later = controller.show(
            PixelPopupRoute<Unit>(
                content = Text("LATER"),
                onOutcome = { laterOutcomeCount += 1 },
            ),
        )
        assertTrue(later.dismiss(PixelOverlayDismissReason.Handle))
        assertEquals(PixelOverlayLifecycle.Disposed, later.lifecycle)
        assertEquals(1, laterOutcomeCount)
    }

    /** A shared controller delivers only after every attached Host unmounts its presentation. */
    @Test
    fun outcomeWaitsForAllAttachedHostPresentations() {
        val tester = PixelTester()
        val controller = PixelOverlayController()
        var disposalCount = 0
        var callbackDisposalCount: Int? = null
        val lifecycleEvents = mutableListOf<String>()
        tester.pumpWidget(
            widget = Stack(
                children = listOf(
                    PixelOverlayHost(controller = controller, child = Text("HOME-A"), key = "host-a"),
                    PixelOverlayHost(controller = controller, child = Text("HOME-B"), key = "host-b"),
                ),
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )
        val entry = controller.show(
            PixelPopupRoute<Unit>(
                content = DisposalProbeWidget {
                    disposalCount += 1
                    lifecycleEvents += "dispose:$disposalCount"
                },
                onOutcome = {
                    callbackDisposalCount = disposalCount
                    lifecycleEvents += "callback:$disposalCount"
                },
            ),
        )
        tester.pumpFrame(0)

        assertTrue(entry.dismiss(PixelOverlayDismissReason.Handle))
        assertEquals(PixelOverlayLifecycle.Removing, entry.lifecycle)
        tester.pumpFrame(0)

        assertEquals(2, disposalCount)
        assertEquals(PixelOverlayLifecycle.Disposed, entry.lifecycle)
        assertEquals(2, callbackDisposalCount)
        assertEquals(listOf("dispose:1", "dispose:2", "callback:2"), lifecycleEvents)
        tester.dispose()
    }

    /** Later fast exits cannot overtake an earlier slow exit in outcome delivery order. */
    @Test
    fun outcomesKeepLogicalCloseOrderAcrossDifferentExitDurations() {
        val tester = PixelTester()
        val controller = PixelOverlayController()
        val callbackOrder = mutableListOf<String>()
        tester.pumpWidget(
            widget = PixelMotionTheme(
                data = PixelMotionThemeData.Default.copy(
                    dialogEnter = lifecycleMotionSpec(),
                    dialogExit = lifecycleMotionSpec(),
                ),
                child = PixelMotionScope(
                    vsync = tester.vsync,
                    settings = PixelMotionSettings.Default,
                    child = PixelOverlayHost(controller = controller, child = Text("HOME")),
                ),
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )
        val slow = controller.show(
            PixelPopupRoute<Unit>(
                content = SizedBox(width = 4, height = 4),
                motion = PixelOverlayMotion.Dialog,
                onOutcome = { callbackOrder += "slow" },
            ),
        )
        val fast = controller.show(
            PixelPopupRoute<Unit>(
                content = SizedBox(width = 3, height = 3),
                motion = PixelOverlayMotion.None,
                onOutcome = { callbackOrder += "fast" },
            ),
        )
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(MotionDurationMillis)

        assertTrue(slow.dismiss(PixelOverlayDismissReason.Handle))
        assertTrue(fast.dismiss(PixelOverlayDismissReason.Handle))
        assertTrue(callbackOrder.isEmpty())
        tester.pumpFrame(0)
        tester.pumpFrame(0)

        assertEquals(PixelOverlayLifecycle.Removing, slow.lifecycle)
        assertEquals(PixelOverlayLifecycle.Disposed, fast.lifecycle)
        assertTrue(callbackOrder.isEmpty())
        tester.pumpFrame(MotionDurationMillis)
        tester.pumpFrame(0)

        assertEquals(PixelOverlayLifecycle.Disposed, slow.lifecycle)
        assertEquals(listOf("slow", "fast"), callbackOrder)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    /** Same-type unkeyed entries preserve their own State when a lower sibling is removed. */
    @Test
    fun permanentIdentityPreventsSameTypeStateMigration() {
        val tester = PixelTester()
        val controller = PixelOverlayController()
        tester.pumpWidget(
            widget = PixelOverlayHost(controller = controller, child = Text("HOME")),
            logicalWidth = 64,
            logicalHeight = 24,
        )
        val first = controller.show(StickyLabelWidget("FIRST"))
        controller.show(StickyLabelWidget("SECOND"))
        tester.pumpFrame(0)
        assertTrue(tester.exists(find.byText("FIRST")))
        assertTrue(tester.exists(find.byText("SECOND")))

        assertTrue(first.dismiss(PixelOverlayDismissReason.Handle))
        tester.pumpFrame(0)

        assertFalse(tester.exists(find.byText("FIRST")))
        assertTrue(tester.exists(find.byText("SECOND")))
        tester.dispose()
    }

    /** Controller-local id reuse cannot retain State when a host switches controllers. */
    @Test
    fun permanentIdentitySurvivesControllerSwitchWithSameLocalId() {
        val tester = PixelTester()
        val firstController = PixelOverlayController()
        val secondController = PixelOverlayController()
        firstController.show(StickyLabelWidget("CONTROLLER-A"))
        secondController.show(StickyLabelWidget("CONTROLLER-B"))
        tester.pumpWidget(
            widget = PixelOverlayHost(
                controller = firstController,
                child = Text("HOME"),
                key = "host",
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )
        assertTrue(tester.exists(find.byText("CONTROLLER-A")))

        tester.pumpWidget(
            widget = PixelOverlayHost(
                controller = secondController,
                child = Text("HOME"),
                key = "host",
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )

        assertFalse(tester.exists(find.byText("CONTROLLER-A")))
        assertTrue(tester.exists(find.byText("CONTROLLER-B")))
        tester.dispose()
    }

    /** Dialog motion exposes Removing until the retained exit reaches its terminal frame. */
    @Test
    fun dialogMotionAdvancesRemovingToDisposedAfterExit() {
        val tester = PixelTester()
        val controller = PixelOverlayController()
        tester.pumpWidget(
            widget = PixelMotionTheme(
                data = PixelMotionThemeData.Default.copy(
                    dialogEnter = lifecycleMotionSpec(),
                    dialogExit = lifecycleMotionSpec(),
                ),
                child = PixelMotionScope(
                    vsync = tester.vsync,
                    settings = PixelMotionSettings.Default,
                    child = PixelOverlayHost(
                        controller = controller,
                        child = Text("HOME"),
                    ),
                ),
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )
        val entry = controller.show(
            PixelPopupRoute<Unit>(
                content = SizedBox(width = 4, height = 4),
                motion = PixelOverlayMotion.Dialog,
            ),
        )
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(MotionDurationMillis)

        assertTrue(entry.dismiss(PixelOverlayDismissReason.Handle))
        assertEquals(PixelOverlayLifecycle.Removing, entry.lifecycle)
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(MotionDurationMillis)

        assertEquals(PixelOverlayLifecycle.Disposed, entry.lifecycle)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    /** Creates one route whose dismissal callback records its stable label. */
    private fun routeWithDismissCallback(
        label: String,
        layer: PixelOverlayLayer,
        callbackOrder: MutableList<String>,
    ): PixelPopupRoute<Unit> {
        return PixelPopupRoute(
            content = Text(label),
            layer = layer,
            onOutcome = { outcome ->
                if (outcome is PixelOverlayOutcome.Dismissed) callbackOrder += label
            },
        )
    }

    /** Returns the deterministic linear motion used by lifecycle timing assertions. */
    private fun lifecycleMotionSpec(): PixelMotionSpec {
        return PixelMotionSpec(
            duration = MotionDurationMillis.milliseconds,
            curve = Curves.Linear,
            transition = PixelMotionTransitionPreset.Fade,
            role = PixelMotionRole.Spatial,
        )
    }

    /** Creates one colored focusable modal fixture at an explicit overlay layer. */
    private fun modalFixtureRoute(
        label: String,
        node: FocusNode,
        color: PixelColor,
        layer: PixelOverlayLayer,
    ): PixelPopupRoute<Unit> {
        return PixelPopupRoute(
            content = Container(
                width = 12,
                height = 12,
                fillColor = color,
                child = Focus(
                    node = node,
                    autofocus = true,
                    child = Semantics(
                        label = label,
                        child = SizedBox(width = 1, height = 1),
                    ),
                ),
            ),
            layer = layer,
            barrier = PixelOverlayBarrier(color = PixelColor.Transparent),
            modal = true,
        )
    }

    /** Returns the reason stored in one dismissal outcome, or null for completion. */
    private fun dismissedReason(outcome: PixelOverlayOutcome<Unit>?): PixelOverlayDismissReason? {
        return (outcome as? PixelOverlayOutcome.Dismissed)?.reason
    }

    /** Surface fixture used to apply the same nested-owner and barrier assertions. */
    private data class SurfaceFixture(
        /** Standard or explicitly marked modal surface. */
        val widget: Widget,
        /** Finder key whose measured bounds must absorb an internal tap. */
        val surfaceKey: Any,
        /** Optional standard semantic label expected to survive modal filtering. */
        val semanticLabel: String?,
    )

    /** Stateful fixture that intentionally ignores later widget labels after initialization. */
    private class StickyLabelWidget(
        /** Label captured exactly once by the corresponding State. */
        val label: String,
    ) : StatefulWidget() {
        /** Creates the State used to detect accidental element reuse. */
        override fun createState(): State<out StatefulWidget> = StickyLabelState()
    }

    /** Retains one initial label so incorrect sibling/controller reuse is observable. */
    private class StickyLabelState : State<StickyLabelWidget>() {
        /** Initial widget label retained for the full lifetime of this State. */
        private lateinit var retainedLabel: String

        /** Captures the label only on first mount. */
        override fun initState() {
            retainedLabel = widget.label
        }

        /** Builds the immutable label retained by this State. */
        override fun build(context: BuildContext): Widget = Text(retainedLabel)
    }

    /** Stateful fixture that reports the exact descendant-unmount boundary. */
    private class DisposalProbeWidget(
        /** Callback invoked from the retained State's dispose hook. */
        val onDisposed: () -> Unit,
    ) : StatefulWidget() {
        /** Creates the State that owns the disposal probe. */
        override fun createState(): State<out StatefulWidget> = DisposalProbeState()
    }

    /** Executes one externally observable callback when its presentation unmounts. */
    private class DisposalProbeState : State<DisposalProbeWidget>() {
        /** Builds a fixed child so only lifecycle behavior affects the test. */
        override fun build(context: BuildContext): Widget = Text("PROBE")

        /** Reports disposal after the State's descendants have unmounted. */
        override fun dispose() {
            widget.onDisposed()
        }
    }

    /** Deterministic motion timing constants. */
    private companion object {
        /** Duration of both dialog enter and exit segments. */
        const val MotionDurationMillis: Long = 40L
    }
}
