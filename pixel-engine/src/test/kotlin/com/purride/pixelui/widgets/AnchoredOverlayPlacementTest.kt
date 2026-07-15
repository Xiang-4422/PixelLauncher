package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.ClipRect
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.Directionality
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.PixelPopoverPlacement
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.Popover
import com.purride.pixelui.Positioned
import com.purride.pixelui.Semantics
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.TextDirection
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks anchored popup geometry to the real Host coordinate and safe-viewport contracts. */
class AnchoredOverlayPlacementTest {
    /** Every viewport corner flips or shifts the popup without entering system or IME insets. */
    @Test
    fun fourCornersRemainInsideCombinedSafeViewport() {
        /** Host width shared by the widget tree, MediaQuery, and assertions. */
        val width = 80
        /** Host height shared by the widget tree, MediaQuery, and assertions. */
        val height = 60
        /** Stable and transient exclusions whose per-edge maximum forms the safe viewport. */
        val media = mediaQueryData(
            width = width,
            height = height,
            viewPadding = PixelWindowInsets(left = 3, top = 4, right = 5, bottom = 6),
            viewInsets = PixelWindowInsets(bottom = 10),
        )
        /** Off-screen runtime used to inspect resolved semantic rectangles. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = MediaQuery(
                    data = media,
                    child = Stack(
                        children = listOf(
                            Positioned(child = anchoredPopover("top-left"), left = 0, top = 0),
                            Positioned(child = anchoredPopover("top-right"), right = 0, top = 0),
                            Positioned(child = anchoredPopover("bottom-left"), left = 0, bottom = 0),
                            Positioned(child = anchoredPopover("bottom-right"), right = 0, bottom = 0),
                        ),
                    ),
                ),
                logicalWidth = width,
                logicalHeight = height,
            )

            /** Safe left including the Popover's default one-pixel viewport margin. */
            val safeLeft = 4
            /** Safe top including the Popover's default one-pixel viewport margin. */
            val safeTop = 5
            /** Exclusive safe right after stable inset and Popover margin. */
            val safeRight = 74
            /** Exclusive safe bottom after the larger IME inset and Popover margin. */
            val safeBottom = 49
            /** Popup labels expected exactly once in the root-level portal layer. */
            val labels = listOf("top-left", "top-right", "bottom-left", "bottom-right")
            labels.forEach { label ->
                /** Collision-resolved semantic rectangle for this corner's popup. */
                val node = tester.semanticsNodesByLabel(label).single()
                assertTrue("$label left=${node.left}", node.left >= safeLeft)
                assertTrue("$label top=${node.top}", node.top >= safeTop)
                assertTrue("$label right=${node.left + node.width}", node.left + node.width <= safeRight)
                assertTrue("$label bottom=${node.top + node.height}", node.top + node.height <= safeBottom)
            }
            /** Bottom-left popup must flip above its anchor instead of merely clipping below it. */
            val bottomLeft = tester.semanticsNodesByLabel("bottom-left").single()
            /** Bottom-right popup must use the same deterministic vertical flip decision. */
            val bottomRight = tester.semanticsNodesByLabel("bottom-right").single()
            assertTrue(bottomLeft.top < height - 6)
            assertTrue(bottomRight.top < height - 6)
        } finally {
            tester.dispose()
        }
    }

    /** A popup nested below ClipRect paints and exports semantics outside the local clip. */
    @Test
    fun rootPortalEscapesLocalClipAndRetainsGlobalAnchorOrigin() {
        /** Number of popup activations delivered outside the six-pixel ancestor clip. */
        var popupActivations = 0
        /** Off-screen runtime used to validate the portal's lifted semantic geometry. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = MediaQuery(
                    data = mediaQueryData(width = 80, height = 60),
                    child = Stack(
                        children = listOf(
                            Positioned(
                                left = 30,
                                top = 20,
                                width = 6,
                                height = 6,
                                child = ClipRect(
                                    child = anchoredPopover(
                                        label = "clipped-popup",
                                        onTap = { popupActivations += 1 },
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                logicalWidth = 80,
                logicalHeight = 60,
            )

            /** Popup exported by the root layer rather than truncated to the six-pixel clip. */
            val popup = tester.semanticsNodesByLabel("clipped-popup").single()
            assertEquals(30, popup.left)
            assertEquals(28, popup.top)
            assertEquals(20, popup.width)
            assertEquals(12, popup.height)
            assertEquals(PixelColor.Black, tester.pixelAt(31, 29))
            tester.tap(find.byKey("clipped-popup-action"))
            assertEquals(1, popupActivations)
        } finally {
            tester.dispose()
        }
    }

    /** Logical Start alignment resolves to the anchor's right edge in an RTL subtree. */
    @Test
    fun startAlignmentMirrorsInRightToLeftDirectionality() {
        /** Off-screen runtime used to inspect the direction-resolved popup rectangle. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = MediaQuery(
                    data = mediaQueryData(width = 80, height = 60),
                    child = Directionality(
                        textDirection = TextDirection.RTL,
                        child = Stack(
                            children = listOf(
                                Positioned(
                                    left = 40,
                                    top = 10,
                                    child = anchoredPopover("rtl-popup"),
                                ),
                            ),
                        ),
                    ),
                ),
                logicalWidth = 80,
                logicalHeight = 60,
            )

            /** Popup whose right edge must equal the six-pixel anchor's right edge. */
            val popup = tester.semanticsNodesByLabel("rtl-popup").single()
            assertEquals(26, popup.left)
            assertEquals(46, popup.left + popup.width)
        } finally {
            tester.dispose()
        }
    }

    /** Rotation-sized resize and a later IME inset both trigger collision-safe re-placement. */
    @Test
    fun resizeAndImeChangesRelayoutRetainedPopup() {
        /** Mutable inherited window geometry retained under one Popover State instance. */
        val media = ValueNotifier(mediaQueryData(width = 80, height = 60))
        /** Stable root widget watching window geometry changes without replacing the Popover key. */
        val root = ValueListenableBuilder(media) { _, data ->
            MediaQuery(
                data = data,
                child = Stack(
                    children = listOf(
                        Positioned(
                            right = 0,
                            bottom = 0,
                            child = anchoredPopover("resized-popup"),
                        ),
                    ),
                ),
            )
        }
        /** Off-screen runtime retained across both logical viewport sizes. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(root, logicalWidth = 80, logicalHeight = 60)

            media.value = mediaQueryData(width = 48, height = 80)
            tester.pumpWidget(root, logicalWidth = 48, logicalHeight = 80)
            /** Popup rectangle after portrait-style resize. */
            val resized = tester.semanticsNodesByLabel("resized-popup").single()
            assertTrue(resized.left + resized.width <= 47)
            assertTrue(resized.top + resized.height <= 79)

            media.value = mediaQueryData(
                width = 48,
                height = 80,
                viewInsets = PixelWindowInsets(bottom = 30),
            )
            tester.pumpFrame(deltaMs = 0)
            /** Popup rectangle shifted above the newly obscuring IME region. */
            val withIme = tester.semanticsNodesByLabel("resized-popup").single()
            assertTrue(withIme.top + withIme.height <= 49)
            assertTrue(withIme.top < resized.top)
        } finally {
            tester.dispose()
        }
    }

    /** Oversized popup content is constrained to the remaining safe area in a tiny window. */
    @Test
    fun smallWindowConstrainsOversizedPopup() {
        /** Off-screen runtime used to inspect the constrained semantic rectangle. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = MediaQuery(
                    data = mediaQueryData(width = 16, height = 12),
                    child = Stack(
                        children = listOf(
                            Positioned(right = 0, bottom = 0, child = anchoredPopover("small-popup")),
                        ),
                    ),
                ),
                logicalWidth = 16,
                logicalHeight = 12,
            )

            /** Popup shrunk by child constraints to the one-pixel-margin safe rectangle. */
            val popup = tester.semanticsNodesByLabel("small-popup").single()
            assertTrue(popup.left >= 1)
            assertTrue(popup.top >= 1)
            assertTrue(popup.left + popup.width <= 15)
            assertTrue(popup.top + popup.height <= 11)
        } finally {
            tester.dispose()
        }
    }

    /** Public Int extremes saturate instead of wrapping popup constraints or resolved offsets. */
    @Test
    fun extremeInsetsMarginsAndOffsetsRemainInsideViewport() {
        assertExtremeGeometryInsideViewport(
            label = "negative-extremes",
            insets = PixelWindowInsets(
                left = Int.MIN_VALUE,
                top = Int.MIN_VALUE,
                right = Int.MIN_VALUE,
                bottom = Int.MIN_VALUE,
            ),
            contentOffset = IntOffset(Int.MAX_VALUE, Int.MIN_VALUE),
            viewportMargin = Int.MIN_VALUE,
        )
        assertExtremeGeometryInsideViewport(
            label = "positive-extremes",
            insets = PixelWindowInsets(
                left = Int.MAX_VALUE,
                top = Int.MAX_VALUE,
                right = Int.MAX_VALUE,
                bottom = Int.MAX_VALUE,
            ),
            contentOffset = IntOffset(Int.MIN_VALUE, Int.MAX_VALUE),
            viewportMargin = Int.MAX_VALUE,
        )
    }

    /** Scrolling a clipped viewport updates the global anchor and follower in the same frame. */
    @Test
    fun popupTracksAnchorThroughSingleChildScrollViewport() {
        /** Scroll controller used to move the anchor without rebuilding its Popover State. */
        val controller = PixelListController()
        /** Retained list state whose offset drives the scratch-buffer paint transform. */
        val state = controller.create()
        /** Off-screen runtime used to inspect pre-scroll and post-scroll popup positions. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = MediaQuery(
                    data = mediaQueryData(width = 80, height = 60),
                    child = Stack(
                        children = listOf(
                            Positioned(
                                left = 20,
                                top = 10,
                                width = 40,
                                height = 30,
                                child = SingleChildScrollView(
                                    state = state,
                                    controller = controller,
                                    child = Column(
                                        children = listOf(
                                            SizedBox(width = 40, height = 20),
                                            anchoredPopover("scroll-popup"),
                                            SizedBox(width = 40, height = 40),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                logicalWidth = 80,
                logicalHeight = 60,
            )
            /** Initial popup top derived from viewport origin, spacer, and anchor offset. */
            val initialTop = tester.semanticsNodesByLabel("scroll-popup").single().top

            controller.scrollTo(
                state = state,
                targetOffsetPx = 10f,
                viewportHeightPx = 30,
                contentHeightPx = 66,
            )
            tester.pumpFrame(deltaMs = 0)

            /** Updated top proves the portal inherited the scroll scratch-buffer transform. */
            val scrolledTop = tester.semanticsNodesByLabel("scroll-popup").single().top
            assertEquals(10, initialTop - scrolledTop)
        } finally {
            tester.dispose()
        }
    }

    /** Builds one fixed-size non-modal Popover with a semantic rectangle for geometry assertions. */
    private fun anchoredPopover(
        label: String,
        onTap: (() -> Unit)? = null,
    ): Widget {
        /** Fixed popup surface optionally wrapped in a pointer target for portal hit-test checks. */
        val popupSurface = Container(
            width = 20,
            height = 12,
            fillColor = PixelColor.Black,
            borderColor = null,
        )
        /** Interactive content retains a stable finder key without changing measured geometry. */
        val popupContent = if (onTap == null) {
            popupSurface
        } else {
            GestureDetector(
                child = popupSurface,
                onTap = onTap,
                key = "$label-action",
            )
        }
        return Popover(
            anchor = Container(
                width = 6,
                height = 6,
                fillColor = PixelColor.White,
                borderColor = null,
            ),
            content = Semantics(
                label = label,
                child = popupContent,
            ),
            expanded = true,
            contentOffset = IntOffset(0, 8),
            modal = false,
            key = label,
        )
    }

    /** Pumps one adversarial public geometry tuple and verifies its complete semantic rectangle. */
    private fun assertExtremeGeometryInsideViewport(
        label: String,
        insets: PixelWindowInsets,
        contentOffset: IntOffset,
        viewportMargin: Int,
    ) {
        /** Small finite Host width that makes any wrapped extreme immediately observable. */
        val width = 16
        /** Small finite Host height that makes any wrapped extreme immediately observable. */
        val height = 12
        /** Runtime isolated per adversarial case so retained placement cannot mask a failure. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = MediaQuery(
                    data = mediaQueryData(
                        width = width,
                        height = height,
                        viewPadding = insets,
                        viewInsets = insets,
                    ),
                    child = Popover(
                        anchor = Container(
                            width = 6,
                            height = 6,
                            fillColor = PixelColor.White,
                            borderColor = null,
                        ),
                        content = Semantics(
                            label = label,
                            child = Container(
                                width = 20,
                                height = 20,
                                fillColor = PixelColor.Black,
                                borderColor = null,
                            ),
                        ),
                        expanded = true,
                        contentOffset = contentOffset,
                        modal = false,
                        placement = PixelPopoverPlacement.Auto,
                        viewportMargin = viewportMargin,
                        key = label,
                    ),
                ),
                logicalWidth = width,
                logicalHeight = height,
            )

            /** Saturated popup rectangle, including a valid zero extent for a collapsed safe area. */
            val popup = tester.semanticsNodesByLabel(label).single()
            assertTrue("$label left=${popup.left}", popup.left >= 0)
            assertTrue("$label top=${popup.top}", popup.top >= 0)
            assertTrue("$label width=${popup.width}", popup.width >= 0)
            assertTrue("$label height=${popup.height}", popup.height >= 0)
            /** Overflow-safe exclusive right edge derived from the exported semantic rectangle. */
            val right = popup.left.toLong() + popup.width.toLong()
            /** Overflow-safe exclusive bottom edge derived from the exported semantic rectangle. */
            val bottom = popup.top.toLong() + popup.height.toLong()
            assertTrue("$label right=$right", right <= width.toLong())
            assertTrue("$label bottom=$bottom", bottom <= height.toLong())
        } finally {
            tester.dispose()
        }
    }

    /** Creates deterministic MediaQuery geometry for one off-screen Host-sized test frame. */
    private fun mediaQueryData(
        width: Int,
        height: Int,
        viewPadding: PixelWindowInsets = PixelWindowInsets.Zero,
        viewInsets: PixelWindowInsets = PixelWindowInsets.Zero,
    ): MediaQueryData {
        return MediaQueryData(
            logicalWidth = width,
            logicalHeight = height,
            screenProfile = ScreenProfile(logicalWidth = width, logicalHeight = height, dotSizePx = 1),
            viewPadding = viewPadding,
            viewInsets = viewInsets,
        )
    }
}
