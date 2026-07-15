package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.BottomSheet
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.Dialog
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.Semantics
import com.purride.pixelui.SizedBox
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.Stack
import com.purride.pixelui.Transform
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.internal.SafeOverlayAlignment
import com.purride.pixelui.internal.SafeOverlayViewportWidget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies public Dialog and BottomSheet safe geometry and modal isolation contracts. */
class SafeOverlayComponentsTest {
    /** Oversized Dialog content is constrained and clipped to stable and IME-safe bounds. */
    @Test
    fun dialogClipsOversizedContentToMergedSafeViewport() {
        /** Deterministic logical viewport width used by MediaQuery and the retained pipeline. */
        val width = 40
        /** Deterministic logical viewport height used by MediaQuery and the retained pipeline. */
        val height = 30
        /** Stable system-bar and cutout exclusions. */
        val viewPadding = PixelWindowInsets(left = 3, top = 2, right = 4, bottom = 3)
        /** Transient IME exclusion whose bottom edge wins over stable navigation padding. */
        val viewInsets = PixelWindowInsets(bottom = 10)
        /** Expected per-side-maximum safe rectangle right edge. */
        val safeRight = width - viewPadding.right
        /** Expected per-side-maximum safe rectangle bottom edge. */
        val safeBottom = height - viewInsets.bottom
        /** Offscreen runtime used for geometry and pixel assertions. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = mediaQuery(
                    width = width,
                    height = height,
                    viewPadding = viewPadding,
                    viewInsets = viewInsets,
                    child = Dialog(
                        content = Transform.translate(
                            offset = IntOffset(x = -8, y = -8),
                            child = Container(
                                width = 100,
                                height = 100,
                                fillColor = PixelColor.fromRgb(220, 40, 40),
                                child = OutlinedButton(text = "OVERSIZED CONTENT", onPressed = { }),
                            ),
                        ),
                        key = "safe-dialog",
                    ),
                ),
                logicalWidth = width,
                logicalHeight = height,
            )

            /** Dialog surface node expands only to the available safe viewport. */
            val dialogNode = tester.semanticsNodesByLabel("Dialog").single()
            assertEquals(viewPadding.left, dialogNode.left)
            assertEquals(viewPadding.top, dialogNode.top)
            assertEquals(safeRight, dialogNode.left + dialogNode.width)
            assertEquals(safeBottom, dialogNode.top + dialogNode.height)
            /** Every descendant semantic rectangle is clipped to identical safe geometry. */
            tester.semanticsNodes().forEach { node ->
                assertInsideSafeViewport(
                    node = node,
                    left = viewPadding.left,
                    top = viewPadding.top,
                    right = safeRight,
                    bottom = safeBottom,
                )
            }
            /** Translated oversized click geometry is intersected instead of leaking outside. */
            tester.renderResult?.clickTargets.orEmpty().forEach { target ->
                assertTrue(target.bounds.left >= viewPadding.left)
                assertTrue(target.bounds.top >= viewPadding.top)
                assertTrue(target.bounds.right <= safeRight)
                assertTrue(target.bounds.bottom <= safeBottom)
            }
            /** Pixels outside each excluded edge remain untouched despite oversized content. */
            assertEquals(PixelColor.Transparent, tester.pixelAt(viewPadding.left - 1, viewPadding.top))
            assertEquals(PixelColor.Transparent, tester.pixelAt(viewPadding.left, viewPadding.top - 1))
            assertEquals(PixelColor.Transparent, tester.pixelAt(safeRight, viewPadding.top))
            assertEquals(PixelColor.Transparent, tester.pixelAt(viewPadding.left, safeBottom))
        } finally {
            tester.dispose()
        }
    }

    /** BottomSheet fills safe width and moves its bottom edge to the top of a newly visible IME. */
    @Test
    fun bottomSheetRelayoutsAboveImeAndFillsSafeWidth() {
        /** Shared logical viewport width. */
        val width = 42
        /** Shared logical viewport height. */
        val height = 32
        /** Stable left, top, right, and navigation exclusions. */
        val viewPadding = PixelWindowInsets(left = 2, top = 1, right = 5, bottom = 3)
        /** Retained runtime reused across hidden and visible IME configurations. */
        val tester = PixelTester()
        try {
            /** Public BottomSheet subtree reused across MediaQuery updates. */
            val sheet = BottomSheet(
                content = Semantics(
                    label = "SHEET CONTENT",
                    child = SizedBox(width = 6, height = 4),
                ),
                key = "safe-sheet",
            )
            tester.pumpWidget(
                widget = mediaQuery(
                    width = width,
                    height = height,
                    viewPadding = viewPadding,
                    child = sheet,
                ),
                logicalWidth = width,
                logicalHeight = height,
            )

            /** Hidden-IME sheet geometry is pinned above stable navigation padding. */
            val beforeIme = tester.semanticsNodesByLabel("Bottom sheet").single()
            assertEquals(viewPadding.left, beforeIme.left)
            assertEquals(width - viewPadding.left - viewPadding.right, beforeIme.width)
            assertEquals(height - viewPadding.bottom, beforeIme.top + beforeIme.height)

            tester.pumpWidget(
                widget = mediaQuery(
                    width = width,
                    height = height,
                    viewPadding = viewPadding,
                    viewInsets = PixelWindowInsets(bottom = 14),
                    child = sheet,
                ),
                logicalWidth = width,
                logicalHeight = height,
            )

            /** Visible-IME sheet retains width and moves its complete surface above the keyboard. */
            val aboveIme = tester.semanticsNodesByLabel("Bottom sheet").single()
            assertEquals(beforeIme.width, aboveIme.width)
            assertEquals(height - 14, aboveIme.top + aboveIme.height)
            assertTrue(aboveIme.top < beforeIme.top)
        } finally {
            tester.dispose()
        }
    }

    /** A severely constrained Dialog remains inside safe bounds and keeps its dismiss action. */
    @Test
    fun smallWindowDialogRemainsSafelyDismissible() {
        /** Number of controlled dismiss requests dispatched by accessibility. */
        var dismissRequests = 0
        /** Tiny viewport whose IME leaves only three logical rows available. */
        val width = 12
        /** Tiny viewport height used to force severe vertical clipping. */
        val height = 10
        /** Stable cutout and horizontal system-bar exclusions. */
        val viewPadding = PixelWindowInsets(left = 2, top = 2, right = 2, bottom = 1)
        /** IME exclusion that wins over stable bottom padding. */
        val viewInsets = PixelWindowInsets(bottom = 5)
        /** Isolated runtime used to execute the semantic dismiss callback. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = mediaQuery(
                    width = width,
                    height = height,
                    viewPadding = viewPadding,
                    viewInsets = viewInsets,
                    child = Dialog(
                        content = SizedBox(width = 80, height = 80),
                        onDismissRequest = { dismissRequests += 1 },
                        key = "tiny-dialog",
                    ),
                ),
                logicalWidth = width,
                logicalHeight = height,
            )

            /** Even a clipped surface retains one visible, executable Dialog semantic node. */
            val dialogNode = tester.semanticsNodesByLabel("Dialog").single()
            assertInsideSafeViewport(
                node = dialogNode,
                left = viewPadding.left,
                top = viewPadding.top,
                right = width - viewPadding.right,
                bottom = height - viewInsets.bottom,
            )
            assertTrue(dialogNode.actions.contains(PixelSemanticsAction.DISMISS))
            assertTrue(tester.performSemanticsAction(dialogNode.id, PixelSemanticsAction.DISMISS))
            assertEquals(1, dismissRequests)
        } finally {
            tester.dispose()
        }
    }

    /** Dialog and BottomSheet preserve a touchable footer when long content meets a visible IME. */
    @Test
    fun longDialogAndBottomSheetContentKeepsFooterTouchableAboveIme() {
        /** Public safe-surface factories exercised with identical long body and footer contracts. */
        val overlays: List<(Widget, List<Widget>, () -> Unit, Any) -> Widget> = listOf(
            { body, actions, dismiss, key ->
                Dialog(
                    title = Semantics(label = "LONG DIALOG TITLE", child = SizedBox(width = 18, height = 3)),
                    content = body,
                    actions = actions,
                    onDismissRequest = dismiss,
                    key = key,
                )
            },
            { body, actions, dismiss, key ->
                BottomSheet(
                    title = Semantics(label = "LONG SHEET TITLE", child = SizedBox(width = 18, height = 3)),
                    content = body,
                    actions = actions,
                    onDismissRequest = dismiss,
                    key = key,
                )
            },
        )
        overlays.forEachIndexed { index, overlay ->
            /** Number of controlled close requests delivered through the visible footer action. */
            var closeRequests = 0
            /** Unique action label used to resolve both semantics and the physical click target. */
            val actionLabel = "CLOSE OVERLAY $index"
            /** Shared close callback proving the footer can request removal of the controlled UI. */
            val close: () -> Unit = { closeRequests += 1 }
            /** Long semantic body whose later rows cannot all fit above the IME. */
            val longBody = Column(
                children = List(12) { row ->
                    Semantics(
                        label = "OVERLAY $index BODY ROW $row",
                        child = SizedBox(width = 20, height = 3),
                    )
                },
                spacing = 1,
            )
            /** Overlay instance retained until its controlled close callback is observed. */
            val presentation = overlay(
                longBody,
                listOf(OutlinedButton(text = actionLabel, onPressed = close)),
                close,
                "long-overlay-$index",
            )
            /** Independent runtime avoids modal focus ownership crossing factory cases. */
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    widget = mediaQuery(
                        width = 40,
                        height = 30,
                        viewPadding = PixelWindowInsets(left = 2, top = 2, right = 2, bottom = 2),
                        viewInsets = PixelWindowInsets(bottom = 10),
                        child = presentation,
                    ),
                    logicalWidth = 40,
                    logicalHeight = 30,
                )

                /** Footer semantics remain completely above the keyboard after body compression. */
                val actionNode = tester.semanticsNodesByLabel(actionLabel).single()
                assertInsideSafeViewport(
                    node = actionNode,
                    left = 2,
                    top = 2,
                    right = 38,
                    bottom = 20,
                )
                /** At least one late body row is clipped, proving the footer did not follow overflow. */
                assertTrue(tester.semanticsNodesByLabel("OVERLAY $index BODY ROW 11").isEmpty())

                tester.tap(find.byText(actionLabel))

                /** Physical touch reaches the footer action exactly once despite long body targets. */
                assertEquals(1, closeRequests)
                tester.pumpWidget(
                    widget = mediaQuery(
                        width = 40,
                        height = 30,
                        viewPadding = PixelWindowInsets(left = 2, top = 2, right = 2, bottom = 2),
                        viewInsets = PixelWindowInsets(bottom = 10),
                        child = SizedBox(width = 0, height = 0),
                    ),
                    logicalWidth = 40,
                    logicalHeight = 30,
                )
                /** Controlled removal after the close request leaves no safe-surface semantic node. */
                assertTrue(tester.semanticsNodesByLabel(actionLabel).isEmpty())
            } finally {
                tester.dispose()
            }
        }
    }

    /** SafeOverlay clips both scrollbar track and thumb to one identical visible rectangle. */
    @Test
    fun safeOverlayClipsScrollbarTrackAndThumbConsistently() {
        /** Controller synchronizing the deliberately oversized single-child scroll body. */
        val controller = PixelListController()
        /** Scroll state kept at the top so translating upward partially clips the thumb. */
        val state = controller.create()
        /** Stable safe top edge used by the target geometry assertions. */
        val safeTop = 4
        /** Stable safe bottom edge used by the target geometry assertions. */
        val safeBottom = 26
        /** Offscreen runtime exposing internal scrollbar interaction snapshots. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = mediaQuery(
                    width = 30,
                    height = 30,
                    viewPadding = PixelWindowInsets(left = 3, top = safeTop, right = 3, bottom = 4),
                    child = SafeOverlayViewportWidget(
                        child = Transform.translate(
                            offset = IntOffset(x = 0, y = -2),
                            child = Scrollbar(
                                child = SingleChildScrollView(
                                    child = SizedBox(width = 20, height = 80),
                                    state = state,
                                    controller = controller,
                                ),
                                state = state,
                                width = 2,
                            ),
                        ),
                        alignment = SafeOverlayAlignment.Center,
                        fillSafeWidth = false,
                        key = "scrollbar-safe-overlay",
                    ),
                ),
                logicalWidth = 30,
                logicalHeight = 30,
            )

            /** Single visible scrollbar target exported after SafeOverlay clipping. */
            val target = tester.renderResult?.scrollbarTargets.orEmpty().single()
            assertEquals(safeTop, target.bounds.top)
            assertTrue(target.bounds.bottom <= safeBottom)
            assertEquals(safeTop, target.thumbBounds.top)
            assertTrue(target.thumbBounds.height in 1..target.bounds.height)
            assertTrue(target.thumbBounds.left >= target.bounds.left)
            assertTrue(target.thumbBounds.right <= target.bounds.right)
            assertTrue(target.thumbBounds.bottom <= target.bounds.bottom)
        } finally {
            tester.dispose()
        }
    }

    /** Dialog and BottomSheet both suppress background targets only while modal is true. */
    @Test
    fun dialogAndBottomSheetHonorModalIsolationFlag() {
        /** Public overlay factories exercised against the same background contract. */
        val overlays: List<(Boolean) -> Widget> = listOf(
            { modal ->
                Dialog(
                    content = OutlinedButton(text = "DIALOG ACTION", onPressed = { }),
                    modal = modal,
                    key = "isolation-dialog",
                )
            },
            { modal ->
                BottomSheet(
                    content = OutlinedButton(text = "SHEET ACTION", onPressed = { }),
                    modal = modal,
                    key = "isolation-sheet",
                )
            },
        )
        overlays.forEachIndexed { index, overlay ->
            /** Independent runtime prevents focus and modal activation tokens crossing cases. */
            val tester = PixelTester()
            /** Background callback count proving render-level pointer filtering, not only semantics. */
            var backgroundClicks = 0
            try {
                tester.pumpWidget(
                    widget = mediaQuery(
                        width = 64,
                        height = 40,
                        child = Stack(
                            children = listOf(
                                OutlinedButton(
                                    text = "BACKGROUND $index",
                                    onPressed = { backgroundClicks += 1 },
                                ),
                                overlay(true),
                            ),
                        ),
                    ),
                    logicalWidth = 64,
                    logicalHeight = 40,
                )

                /** Active modal boundary leaves only overlay descendants publicly interactive. */
                val modalSemantics = tester.dumpSemanticsTree()
                assertFalse(modalSemantics.contains("BACKGROUND $index"))
                assertThrows(IllegalStateException::class.java) {
                    tester.tap(find.byText("BACKGROUND $index"))
                }
                assertEquals(0, backgroundClicks)

                tester.pumpWidget(
                    widget = mediaQuery(
                        width = 64,
                        height = 40,
                        child = Stack(
                            children = listOf(
                                OutlinedButton(
                                    text = "BACKGROUND $index",
                                    onPressed = { backgroundClicks += 1 },
                                ),
                                overlay(false),
                            ),
                        ),
                    ),
                    logicalWidth = 64,
                    logicalHeight = 40,
                )

                /** Non-modal presentation coexists with normal background semantics and clicks. */
                val nonModalSemantics = tester.dumpSemanticsTree()
                assertTrue(nonModalSemantics.contains("BACKGROUND $index"))
                tester.tap(find.byText("BACKGROUND $index"))
                assertEquals(1, backgroundClicks)
            } finally {
                tester.dispose()
            }
        }
    }

    /** Creates an inherited MediaQuery matching the logical PixelTester viewport. */
    private fun mediaQuery(
        /** Logical viewport width. */
        width: Int,
        /** Logical viewport height. */
        height: Int,
        /** Stable system-bar and cutout padding. */
        viewPadding: PixelWindowInsets = PixelWindowInsets.Zero,
        /** Transient IME or other occlusion inset. */
        viewInsets: PixelWindowInsets = PixelWindowInsets.Zero,
        /** Widget receiving the inherited geometry. */
        child: Widget,
    ): Widget {
        return MediaQuery(
            data = MediaQueryData(
                logicalWidth = width,
                logicalHeight = height,
                screenProfile = ScreenProfile(
                    logicalWidth = width,
                    logicalHeight = height,
                    dotSizePx = 1,
                ),
                viewPadding = viewPadding,
                viewInsets = viewInsets,
                padding = viewPadding,
            ),
            child = child,
        )
    }

    /** Asserts one semantic rectangle remains entirely inside the supplied safe viewport. */
    private fun assertInsideSafeViewport(
        /** Semantic node whose geometry is under test. */
        node: PixelSemanticsNode,
        /** Inclusive safe left edge. */
        left: Int,
        /** Inclusive safe top edge. */
        top: Int,
        /** Exclusive safe right edge. */
        right: Int,
        /** Exclusive safe bottom edge. */
        bottom: Int,
    ) {
        assertTrue("${node.label} starts before safe left", node.left >= left)
        assertTrue("${node.label} starts before safe top", node.top >= top)
        assertTrue("${node.label} ends after safe right", node.left + node.width <= right)
        assertTrue("${node.label} ends after safe bottom", node.top + node.height <= bottom)
    }
}
