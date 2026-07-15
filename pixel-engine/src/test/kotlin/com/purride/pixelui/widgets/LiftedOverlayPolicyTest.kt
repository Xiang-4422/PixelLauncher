package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.ClipRect
import com.purride.pixelui.Container
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.Opacity
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.PixelOverlayLayer
import com.purride.pixelui.PixelPopoverPlacement
import com.purride.pixelui.PixelPopupRoute
import com.purride.pixelui.Popover
import com.purride.pixelui.Positioned
import com.purride.pixelui.Semantics
import com.purride.pixelui.Stack
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.internal.VisualOnlyWidget
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies root lifting preserves ancestor compositing, interaction gates, and tree paint order. */
class LiftedOverlayPolicyTest {
    /** A lifted presentation inherits group opacity even though it no longer paints into local scratch. */
    @Test
    fun liftedPresentationRetainsAncestorOpacity() {
        /** Runtime used to inspect both the root pixel and still-live semantic target. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = mediaRoot(
                    Opacity(
                        opacity = 0.5f,
                        child = coloredPopover(
                            label = "half-overlay",
                            color = PixelColor.fromRgb(255, 0, 0),
                            offset = IntOffset(0, 4),
                        ),
                    ),
                ),
                logicalWidth = 16,
                logicalHeight = 16,
            )

            assertEquals(PixelColor.fromArgb(128, 255, 0, 0), tester.pixelAt(0, 4))
            assertEquals(1, tester.semanticsNodesByLabel("half-overlay").size)
        } finally {
            tester.dispose()
        }
    }

    /** Visual-only exit ancestors retain lifted pixels but suppress every interaction/semantic export. */
    @Test
    fun liftedPresentationHonorsVisualOnlyAncestorGate() {
        /** Runtime used to inspect paint and target channels from the same retained subtree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = mediaRoot(
                    VisualOnlyWidget(
                        visualOnly = true,
                        child = coloredPopover(
                            label = "visual-only-overlay",
                            color = PixelColor.fromRgb(0, 255, 0),
                            offset = IntOffset(0, 4),
                            interactive = true,
                        ),
                    ),
                ),
                logicalWidth = 16,
                logicalHeight = 16,
            )

            assertEquals(PixelColor.fromRgb(0, 255, 0), tester.pixelAt(0, 4))
            assertTrue(tester.semanticsNodesByLabel("visual-only-overlay").isEmpty())
            assertTrue(tester.renderResult?.clickTargets.orEmpty().isEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Nested lifted content remains below a later sibling of its owning outer portal. */
    @Test
    fun nestedLayerUsesDepthFirstTreePaintOrder() {
        /** Transparent one-pixel anchor shared by every zero-offset popup. */
        val transparentAnchor = Container(
            width = 1,
            height = 1,
            fillColor = PixelColor.Transparent,
            borderColor = null,
        )
        /** Nested green presentation discovered only while the red outer layer paints. */
        val nested = Popover(
            anchor = Container(
                width = 1,
                height = 1,
                fillColor = PixelColor.fromRgb(255, 0, 0),
                borderColor = null,
            ),
            content = Container(
                width = 2,
                height = 2,
                fillColor = PixelColor.fromRgb(0, 255, 0),
                borderColor = null,
            ),
            expanded = true,
            contentOffset = IntOffset(0, 0),
            modal = false,
            placement = PixelPopoverPlacement.Auto,
            viewportMargin = 0,
            key = "nested-green",
        )
        /** Outer portal whose layer owns [nested] as its complete content subtree. */
        val outer = Popover(
            anchor = transparentAnchor,
            content = nested,
            expanded = true,
            contentOffset = IntOffset(0, 0),
            modal = false,
            placement = PixelPopoverPlacement.Auto,
            viewportMargin = 0,
            key = "outer-red",
        )
        /** Later sibling that must paint above the outer layer and all of its nested layers. */
        val laterSibling = Popover(
            anchor = transparentAnchor,
            content = Container(
                width = 2,
                height = 2,
                fillColor = PixelColor.fromRgb(0, 0, 255),
                borderColor = null,
            ),
            expanded = true,
            contentOffset = IntOffset(0, 0),
            modal = false,
            placement = PixelPopoverPlacement.Auto,
            viewportMargin = 0,
            key = "later-blue",
        )
        /** Runtime used to observe the final overlap color at the common root coordinate. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = mediaRoot(Stack(children = listOf(outer, laterSibling))),
                logicalWidth = 16,
                logicalHeight = 16,
            )

            assertEquals(PixelColor.fromRgb(0, 0, 255), tester.pixelAt(0, 0))
        } finally {
            tester.dispose()
        }
    }

    /** A normal later Stack sibling remains above a lower portal in paint, target, and semantics order. */
    @Test
    fun laterBaseSiblingReplaysAboveLiftedPresentationAcrossAllChannels() {
        /** Number of taps incorrectly delivered to the lower lifted presentation. */
        var lowerTaps = 0
        /** Number of taps correctly delivered to the later in-flow Stack sibling. */
        var higherTaps = 0
        /** Lower red popup that registers a root-lifted presentation during Stack traversal. */
        val lowerPopup = Popover(
            anchor = Container(
                width = 1,
                height = 1,
                fillColor = PixelColor.Transparent,
                borderColor = null,
            ),
            content = Semantics(
                label = "lower-popup",
                child = GestureDetector(
                    child = Container(
                        width = 4,
                        height = 4,
                        fillColor = PixelColor.fromRgb(255, 0, 0),
                        borderColor = null,
                    ),
                    onTap = { lowerTaps += 1 },
                    key = "lower-popup-click",
                ),
            ),
            expanded = true,
            contentOffset = IntOffset(0, 0),
            modal = false,
            placement = PixelPopoverPlacement.Auto,
            viewportMargin = 0,
            key = "lower-popup",
        )
        /** Higher blue in-flow sibling that must be replayed after the lifted red plane. */
        val higherSibling = Semantics(
            label = "higher-sibling",
            child = GestureDetector(
                child = Container(
                    width = 4,
                    height = 4,
                    fillColor = PixelColor.fromRgb(0, 0, 255),
                    borderColor = null,
                ),
                onTap = { higherTaps += 1 },
                key = "higher-sibling-click",
            ),
        )
        /** Runtime used to observe pixels and exported target ordering from one completed frame. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = mediaRoot(Stack(children = listOf(lowerPopup, higherSibling))),
                logicalWidth = 16,
                logicalHeight = 16,
            )

            assertEquals(PixelColor.fromRgb(0, 0, 255), tester.pixelAt(0, 0))
            /** Semantic labels retain the exact bottom-to-top plane traversal order. */
            val labels = tester.renderResult?.semanticsNodes.orEmpty().mapNotNull { node -> node.label }
            assertTrue("lower-popup" in labels)
            assertTrue("higher-sibling" in labels)
            assertTrue(labels.indexOf("lower-popup") < labels.indexOf("higher-sibling"))
            tester.tap(find.byKey("higher-sibling-click"))
            assertEquals(0, lowerTaps)
            assertEquals(1, higherTaps)
        } finally {
            tester.dispose()
        }
    }

    /** Higher System routes cover a lower route's Popover for both modal and non-modal policies. */
    @Test
    fun higherSystemRouteStaysAboveLowerRoutePopoverWhetherModalOrNot() {
        verifyHigherSystemRouteAboveLowerPopover(modal = false)
        verifyHigherSystemRouteAboveLowerPopover(modal = true)
    }

    /** Opacity scratch preserves a higher modal System route above the lower lifted Popover. */
    @Test
    fun opacityWrappedHostKeepsHigherModalSystemAboveLowerPopover() {
        /** Controller whose hosted route Stack paints inside the opacity scratch buffer. */
        val controller = PixelOverlayController()
        /** Number of lower popup taps that must remain isolated by the higher modal route. */
        var lowerTaps = 0
        /** Number of taps delivered to the higher modal System route. */
        var higherTaps = 0
        /** Stable suffix distinguishing this scratch fixture's semantic and pointer targets. */
        val suffix = "opacity-modal"
        /** Runtime used to inspect inherited alpha plus modal target and semantic isolation. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = mediaRoot(
                    Opacity(
                        opacity = 0.5f,
                        child = hostedSurface(controller),
                    ),
                ),
                logicalWidth = 16,
                logicalHeight = 16,
            )
            showOverlappingHostedRoutes(
                controller = controller,
                modal = true,
                suffix = suffix,
                lowerWidth = 4,
                onLowerTap = { lowerTaps += 1 },
                onHigherTap = { higherTaps += 1 },
            )
            tester.pumpFrame(0)

            /** Translucent overlap whose stronger blue channel proves the System plane remains last. */
            val overlap = tester.pixelAt(0, 0)
            assertTrue(overlap.blue > overlap.red)
            assertTrue(overlap.alpha in 1..254)
            /** Modal filtering must retain only the higher route after plane-order sorting. */
            val labels = tester.renderResult?.semanticsNodes.orEmpty().mapNotNull { node -> node.label }
            assertTrue("lower-route-popup-$suffix" !in labels)
            assertTrue("higher-system-$suffix" in labels)
            tester.tap(find.byKey("higher-system-click-$suffix"))
            assertEquals(0, lowerTaps)
            assertEquals(1, higherTaps)
        } finally {
            tester.dispose()
        }
    }

    /** Combined clip and opacity keep System above only inside the clipped hosted route extent. */
    @Test
    fun clippedOpacityHostKeepsHigherNonModalSystemInsideClipAndAbovePopover() {
        /** Controller whose six-pixel hosted route Stack paints inside nested scratch buffers. */
        val controller = PixelOverlayController()
        /** Number of taps delivered to the lower popup outside the higher route's clip. */
        var lowerTaps = 0
        /** Number of taps delivered to the higher route inside its clipped overlap. */
        var higherTaps = 0
        /** Stable suffix distinguishing this combined scratch fixture's exported targets. */
        val suffix = "clip-opacity-nonmodal"
        /** Runtime used to compare inside-clip and escaped-popup pixels and pointer ownership. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = mediaRoot(
                    Stack(
                        children = listOf(
                            Positioned(
                                left = 0,
                                top = 0,
                                width = 6,
                                height = 6,
                                child = Opacity(
                                    opacity = 0.5f,
                                    child = ClipRect(child = hostedSurface(controller)),
                                ),
                            ),
                        ),
                    ),
                ),
                logicalWidth = 16,
                logicalHeight = 16,
            )
            showOverlappingHostedRoutes(
                controller = controller,
                modal = false,
                suffix = suffix,
                lowerWidth = 12,
                onLowerTap = { lowerTaps += 1 },
                onHigherTap = { higherTaps += 1 },
            )
            tester.pumpFrame(0)

            /** Inside overlap where the clipped higher blue plane must paint last. */
            val insideClip = tester.pixelAt(0, 0)
            /** Escaped popup pixel outside the six-pixel ancestor clip. */
            val outsideClip = tester.pixelAt(8, 1)
            assertTrue(insideClip.blue > insideClip.red)
            assertTrue(outsideClip.red > outsideClip.blue)
            /** Non-modal semantics keep both planes in their final bottom-to-top order. */
            val labels = tester.renderResult?.semanticsNodes.orEmpty().mapNotNull { node -> node.label }
            assertTrue("lower-route-popup-$suffix" in labels)
            assertTrue("higher-system-$suffix" in labels)
            assertTrue(
                labels.indexOf("lower-route-popup-$suffix") < labels.indexOf("higher-system-$suffix"),
            )
            tester.tap(find.byKey("higher-system-click-$suffix"))
            tester.tap(find.byKey("lower-route-click-$suffix"))
            assertEquals(1, lowerTaps)
            assertEquals(1, higherTaps)
        } finally {
            tester.dispose()
        }
    }

    /** Verifies one System-route modality while sharing identical overlap geometry and assertions. */
    private fun verifyHigherSystemRouteAboveLowerPopover(modal: Boolean) {
        /** Controller whose canonical route order places System above Popup. */
        val controller = PixelOverlayController()
        /** Number of taps incorrectly delivered to the lower route's lifted Popover. */
        var lowerTaps = 0
        /** Number of taps delivered to the higher System presentation. */
        var higherTaps = 0
        /** Runtime used to compare render, target, and semantic order in one hosted route stack. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = mediaRoot(
                    PixelOverlayHost(
                        controller = controller,
                        child = Container(
                            width = 16,
                            height = 16,
                            fillColor = PixelColor.Transparent,
                            borderColor = null,
                        ),
                    ),
                ),
                logicalWidth = 16,
                logicalHeight = 16,
            )
            controller.show(
                PixelPopupRoute<Unit>(
                    content = Popover(
                        anchor = Container(
                            width = 1,
                            height = 1,
                            fillColor = PixelColor.Transparent,
                            borderColor = null,
                        ),
                        content = Semantics(
                            label = "lower-route-popup-$modal",
                            child = GestureDetector(
                                child = Container(
                                    width = 4,
                                    height = 4,
                                    fillColor = PixelColor.fromRgb(255, 0, 0),
                                    borderColor = null,
                                ),
                                onTap = { lowerTaps += 1 },
                                key = "lower-route-click-$modal",
                            ),
                        ),
                        expanded = true,
                        contentOffset = IntOffset(0, 0),
                        modal = false,
                        placement = PixelPopoverPlacement.Auto,
                        viewportMargin = 0,
                    ),
                    layer = PixelOverlayLayer.Popup,
                    modal = false,
                ),
            )
            controller.show(
                PixelPopupRoute<Unit>(
                    content = Semantics(
                        label = "higher-system-$modal",
                        child = GestureDetector(
                            child = Container(
                                width = 4,
                                height = 4,
                                fillColor = PixelColor.fromRgb(0, 0, 255),
                                borderColor = null,
                            ),
                            onTap = { higherTaps += 1 },
                            key = "higher-system-click-$modal",
                        ),
                    ),
                    layer = PixelOverlayLayer.System,
                    modal = modal,
                ),
            )
            tester.pumpFrame(0)

            assertEquals(PixelColor.fromRgb(0, 0, 255), tester.pixelAt(0, 0))
            /** Final labels prove non-modal order and modal isolation use the same route z-order. */
            val labels = tester.renderResult?.semanticsNodes.orEmpty().mapNotNull { node -> node.label }
            if (modal) {
                assertTrue("lower-route-popup-$modal" !in labels)
                assertTrue("higher-system-$modal" in labels)
            } else {
                assertTrue("lower-route-popup-$modal" in labels)
                assertTrue("higher-system-$modal" in labels)
                assertTrue(
                    labels.indexOf("lower-route-popup-$modal") < labels.indexOf("higher-system-$modal"),
                )
            }
            tester.tap(find.byKey("higher-system-click-$modal"))
            assertEquals(0, lowerTaps)
            assertEquals(1, higherTaps)
        } finally {
            tester.dispose()
        }
    }

    /** Builds the transparent hosted surface shared by scratch-ancestor route tests. */
    private fun hostedSurface(controller: PixelOverlayController): Widget {
        return PixelOverlayHost(
            controller = controller,
            child = Container(
                width = 16,
                height = 16,
                fillColor = PixelColor.Transparent,
                borderColor = null,
            ),
        )
    }

    /** Adds one lower Popup route with a Popover and one overlapping higher System route. */
    private fun showOverlappingHostedRoutes(
        controller: PixelOverlayController,
        modal: Boolean,
        suffix: String,
        lowerWidth: Int,
        onLowerTap: () -> Unit,
        onHigherTap: () -> Unit,
    ) {
        controller.show(
            PixelPopupRoute<Unit>(
                content = Popover(
                    anchor = Container(
                        width = 1,
                        height = 1,
                        fillColor = PixelColor.Transparent,
                        borderColor = null,
                    ),
                    content = Semantics(
                        label = "lower-route-popup-$suffix",
                        child = GestureDetector(
                            child = Container(
                                width = lowerWidth,
                                height = 4,
                                fillColor = PixelColor.fromRgb(255, 0, 0),
                                borderColor = null,
                            ),
                            onTap = onLowerTap,
                            key = "lower-route-click-$suffix",
                        ),
                    ),
                    expanded = true,
                    contentOffset = IntOffset(0, 0),
                    modal = false,
                    placement = PixelPopoverPlacement.Auto,
                    viewportMargin = 0,
                ),
                layer = PixelOverlayLayer.Popup,
                modal = false,
            ),
        )
        controller.show(
            PixelPopupRoute<Unit>(
                content = Semantics(
                    label = "higher-system-$suffix",
                    child = GestureDetector(
                        child = Container(
                            width = 4,
                            height = 4,
                            fillColor = PixelColor.fromRgb(0, 0, 255),
                            borderColor = null,
                        ),
                        onTap = onHigherTap,
                        key = "higher-system-click-$suffix",
                    ),
                ),
                layer = PixelOverlayLayer.System,
                modal = modal,
            ),
        )
    }

    /** Builds one fixed-size popup with optional click ownership for target-gate assertions. */
    private fun coloredPopover(
        label: String,
        color: PixelColor,
        offset: IntOffset,
        interactive: Boolean = false,
    ): Widget {
        /** Opaque colored surface used as the lifted visual. */
        val surface = Container(width = 3, height = 3, fillColor = color, borderColor = null)
        /** Optional pointer boundary proving target suppression independently from semantics. */
        val content = if (interactive) {
            GestureDetector(child = surface, onTap = {}, key = "$label-click")
        } else {
            surface
        }
        return Popover(
            anchor = Container(
                width = 1,
                height = 1,
                fillColor = PixelColor.Transparent,
                borderColor = null,
            ),
            content = Semantics(label = label, child = content),
            expanded = true,
            contentOffset = offset,
            modal = false,
            placement = PixelPopoverPlacement.Auto,
            viewportMargin = 0,
            key = label,
        )
    }

    /** Wraps one test subtree in deterministic Host-size MediaQuery geometry. */
    private fun mediaRoot(child: Widget): Widget {
        return MediaQuery(
            data = MediaQueryData(
                logicalWidth = 16,
                logicalHeight = 16,
                screenProfile = ScreenProfile(logicalWidth = 16, logicalHeight = 16, dotSizePx = 1),
            ),
            child = child,
        )
    }
}
