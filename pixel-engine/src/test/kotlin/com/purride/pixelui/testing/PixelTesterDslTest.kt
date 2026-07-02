package com.purride.pixelui.testing

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.ActivityIndicator
import com.purride.pixelui.AppScaffold
import com.purride.pixelui.Axis
import com.purride.pixelui.AspectRatio
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Column
import com.purride.pixelui.ConfirmDialog
import com.purride.pixelui.ConstrainedBox
import com.purride.pixelui.Container
import com.purride.pixelui.Dialog
import com.purride.pixelui.Dropdown
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EmptyState
import com.purride.pixelui.Badge
import com.purride.pixelui.Divider
import com.purride.pixelui.FittedBox
import com.purride.pixelui.CustomScrollView
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.Gap
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.LoadStateView
import com.purride.pixelui.ListTile
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.ListViewSeparatedBuilder
import com.purride.pixelui.Menu
import com.purride.pixelui.OptionList
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PageView
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelBoxConstraints
import com.purride.pixelui.PixelDebugOverlay
import com.purride.pixelui.PixelHostFrameStats
import com.purride.pixelui.PixelInspectorAllocationSample
import com.purride.pixelui.PixelInspectorPanel
import com.purride.pixelui.PixelInspectorSnapshot
import com.purride.pixelui.PixelInspectorTargetCounts
import com.purride.pixelui.PixelInspectorTargetKind
import com.purride.pixelui.PixelInspectorTargetSnapshot
import com.purride.pixelui.PixelSliverAppBar
import com.purride.pixelui.PixelSliverList
import com.purride.pixelui.PixelSliverListBuilder
import com.purride.pixelui.PixelSliverPinnedHeader
import com.purride.pixelui.PixelAsyncSnapshot
import com.purride.pixelui.PixelTextButtonStyle
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextEditAction
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.Popover
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.Row
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.PixelScrollPhysics
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SelectionList
import com.purride.pixelui.SectionList
import com.purride.pixelui.SectionListSection
import com.purride.pixelui.ShortcutHint
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slider
import com.purride.pixelui.Slidable
import com.purride.pixelui.SlidableAction
import com.purride.pixelui.SlidableActionPane
import com.purride.pixelui.SlidableDirection
import com.purride.pixelui.SlidableMotion
import com.purride.pixelui.SliverAppBar
import com.purride.pixelui.SliverList
import com.purride.pixelui.SliverListBuilder
import com.purride.pixelui.SliverPinnedHeader
import com.purride.pixelui.Snackbar
import com.purride.pixelui.Stepper
import com.purride.pixelui.Switch
import com.purride.pixelui.SwipeRefreshScaffold
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextButtonStyle
import com.purride.pixelui.TextField
import com.purride.pixelui.Toast
import com.purride.pixelui.Tooltip
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.ValueAdjusterStyle
import com.purride.pixelui.Visibility
import com.purride.pixelui.Wrap
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.internal.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTesterDslTest {
    @Test
    fun pixelQueriesReadLastRenderedBuffer() {
        val tester = PixelTester()
        val color = PixelColor.fromRgb(255, 0, 0)

        tester.pumpWidget(
            widget = Container(width = 3, height = 2, fillColor = color),
            logicalWidth = 3,
            logicalHeight = 2,
        )

        assertEquals(color, tester.pixelAt(1, 1))
        assertTrue(tester.hasPixel(color))
        assertEquals("size=3x2\n***\n***\n", tester.dumpPixelsAsAscii())
        tester.dispose()
    }

    @Test
    fun textButtonUsesNaturalTextSizeUntilPaddingIsExplicit() {
        val tester = PixelTester()
        var tapped = 0

        tester.pumpWidget(
            widget = Row(
                children = listOf(
                    TextButton(text = "OK", onPressed = { tapped++ }, key = "text-button"),
                ),
            ),
            logicalWidth = 32,
            logicalHeight = 16,
        )
        val naturalBounds = requireNotNull(tester.renderResult)
            .clickTargets
            .single()
            .bounds
        tester.tap(find.byKey("text-button"))
        assertEquals(1, tapped)

        tester.pumpWidget(
            widget = Row(
                children = listOf(
                    TextButton(
                        text = "OK",
                        onPressed = {},
                        style = TextButtonStyle(padding = EdgeInsets.all(2)),
                        key = "padded-text-button",
                    ),
                ),
            ),
            logicalWidth = 32,
            logicalHeight = 16,
        )
        val paddedBounds = requireNotNull(tester.renderResult)
            .clickTargets
            .single()
            .bounds

        assertEquals(naturalBounds.width + 4, paddedBounds.width)
        assertEquals(naturalBounds.height + 4, paddedBounds.height)
        tester.dispose()
    }

    @Test
    fun publicWidgetModelsRemainConstructible() {
        val textButtonStyle = PixelTextButtonStyle(
            padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
        )
        val pinnedHeader = PixelSliverPinnedHeader(
            child = Text("HEADER"),
            key = "header",
        )
        val list = PixelSliverList(
            items = listOf(Text("ONE"), Text("TWO")),
            spacing = 2,
            key = "list",
        )
        val builder = PixelSliverListBuilder(
            itemCount = 3,
            itemBuilder = { index -> Text("ROW $index") },
            itemExtent = null,
            estimatedItemExtent = 5,
            spacing = 1,
            cacheExtent = 8,
            key = "builder",
        )
        val appBar = PixelSliverAppBar(
            child = Text("APP"),
            expandedHeight = 16,
            collapsedHeight = 6,
            floating = true,
            snap = false,
            stretch = true,
            stretchLimit = 4,
            key = "app-bar",
        )

        assertEquals(2, textButtonStyle.padding.left)
        assertEquals(1, textButtonStyle.padding.top)
        assertEquals("header", pinnedHeader.key)
        assertEquals(2, list.items.size)
        assertEquals(2, list.spacing)
        assertEquals(3, builder.itemCount)
        assertEquals(5, builder.estimatedItemExtent)
        assertEquals(8, builder.cacheExtent)
        assertEquals(16, appBar.expandedHeight)
        assertEquals(6, appBar.collapsedHeight)
        assertTrue(appBar.floating)
        assertTrue(appBar.stretch)
    }

    @Test
    fun tapByTextInvokesButtonCallback() {
        val tester = PixelTester()
        var tapped = 0

        tester.pumpWidget(
            widget = OutlinedButton(text = "OK", onPressed = { tapped++ }),
            logicalWidth = 24,
            logicalHeight = 10,
        )
        tester.tap(find.byText("OK"))

        assertEquals(1, tapped)
        tester.dispose()
    }

    @Test
    fun gestureStreamInvokesButtonCallbackOnUp() {
        val tester = PixelTester()
        var tapped = 0

        tester.pumpWidget(
            widget = OutlinedButton(text = "OK", onPressed = { tapped++ }),
            logicalWidth = 24,
            logicalHeight = 10,
        )

        val gesture = tester.startGesture(find.byText("OK"))
        assertEquals(0, tapped)
        gesture.up()

        assertEquals(1, tapped)
        tester.dispose()
    }

    @Test
    fun longPressInvokesGestureDetectorLongPressCallback() {
        val tester = PixelTester()
        var tapped = 0
        var longPressed = 0

        tester.pumpWidget(
            widget = GestureDetector(
                child = Text("OPEN"),
                onTap = { tapped++ },
                onLongPress = { longPressed++ },
                key = "gesture",
            ),
            logicalWidth = 32,
            logicalHeight = 10,
        )

        tester.longPress(find.byKey("gesture"))

        assertEquals(0, tapped)
        assertEquals(1, longPressed)
        tester.dispose()
    }

    @Test
    fun doubleTapInvokesGestureDetectorDoubleTapCallback() {
        val tester = PixelTester()
        var tapped = 0
        var doubleTapped = 0

        tester.pumpWidget(
            widget = GestureDetector(
                child = Text("OPEN"),
                onTap = { tapped++ },
                onDoubleTap = { doubleTapped++ },
                key = "gesture",
            ),
            logicalWidth = 32,
            logicalHeight = 10,
        )

        tester.doubleTap(find.byKey("gesture"))

        assertEquals(0, tapped)
        assertEquals(1, doubleTapped)
        tester.dispose()
    }

    @Test
    fun horizontalSwipeInvokesGestureDetectorSwipeCallback() {
        val tester = PixelTester()
        var tapped = 0
        var swipedLeft = 0
        var swipedRight = 0

        tester.pumpWidget(
            widget = GestureDetector(
                child = Text("OPEN"),
                onTap = { tapped++ },
                onSwipeLeft = { swipedLeft++ },
                onSwipeRight = { swipedRight++ },
                key = "gesture",
            ),
            logicalWidth = 32,
            logicalHeight = 10,
        )

        tester.startGesture(find.byKey("gesture")).moveBy(-8, 0).up()
        tester.startGesture(find.byKey("gesture")).moveBy(8, 0).up()

        assertEquals(0, tapped)
        assertEquals(1, swipedLeft)
        assertEquals(1, swipedRight)
        tester.dispose()
    }

    @Test
    fun slidableTapAndDismissStayDistinct() {
        val tester = PixelTester()
        var tapped = 0
        var dismissed: SlidableDirection? = null

        tester.pumpWidget(
            widget = Slidable(
                onTap = { tapped++ },
                endActionPane = SlidableActionPane(
                    children = listOf(
                        SlidableAction(
                            label = "READ",
                            backgroundColor = PixelColor.fromRgb(0, 160, 80),
                            foregroundColor = PixelColor.Black,
                            onPressed = {},
                        ),
                    ),
                    motion = SlidableMotion.BEHIND,
                    dismissible = true,
                    dismissThreshold = 0.4f,
                ),
                onDismissed = { dismissed = it },
                child = Text("OPEN"),
                key = "slidable",
            ),
            logicalWidth = 40,
            logicalHeight = 10,
        )

        tester.tap(find.byKey("slidable:gesture"))
        tester.startGesture(find.byKey("slidable:gesture")).moveBy(-18, 0).up()

        assertEquals(1, tapped)
        assertEquals(SlidableDirection.END, dismissed)
        tester.dispose()
    }

    @Test
    fun gestureStreamKeepsListDraggingUntilUp() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()

        tester.pumpWidget(
            widget = ListViewBuilder(
                itemCount = 20,
                itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                itemExtent = 6,
                state = state,
                controller = controller,
                key = "list",
            ),
            logicalWidth = 40,
            logicalHeight = 18,
        )

        val gesture = tester.startGesture(find.byKey("list"))
        gesture.moveBy(dx = 0, dy = -8)

        assertTrue(state.isDragging)
        assertTrue(state.scrollOffsetPx > 0f)

        gesture.up()

        assertFalse(state.isDragging)
        tester.dispose()
    }

    @Test
    fun gestureStreamCancelSettlesPagerBackToCurrentPage() {
        val tester = PixelTester()
        val controller = PixelPagerController(distanceThresholdFraction = 0.2f)
        val state = controller.create(pageCount = 2)

        tester.pumpWidget(
            widget = PageView(
                axis = Axis.HORIZONTAL,
                controller = controller,
                state = state,
                pages = listOf(Text("ONE"), Text("TWO")),
                key = "pager",
            ),
            logicalWidth = 30,
            logicalHeight = 10,
        )

        val gesture = tester.startGesture(find.byKey("pager"))
        gesture.moveBy(dx = -20, dy = 0)
        assertTrue(state.isDragging)

        gesture.cancel()
        tester.pumpAndSettle()

        assertEquals(0, state.currentPage)
        assertFalse(state.isDragging)
        assertFalse(state.isSettling)
        tester.dispose()
    }

    @Test
    fun pagerDragStartCallbackRunsBeforePageChanged() {
        val tester = PixelTester()
        val controller = PixelPagerController(distanceThresholdFraction = 0.2f)
        val state = controller.create(pageCount = 2)
        val events = mutableListOf<String>()

        tester.pumpWidget(
            widget = PageView(
                axis = Axis.HORIZONTAL,
                controller = controller,
                state = state,
                pages = listOf(Text("ONE"), Text("TWO")),
                onPageDragStart = { events += "drag" },
                onPageChanged = { page -> events += "page$page" },
                key = "pager",
            ),
            logicalWidth = 30,
            logicalHeight = 10,
        )

        val gesture = tester.startGesture(find.byKey("pager"))
        gesture.moveBy(dx = -20, dy = 0)
        assertEquals(listOf("drag"), events)

        gesture.up()
        tester.pumpAndSettle()
        assertEquals("drag", events.first())
        assertTrue(events.drop(1).contains("page1"))
        tester.dispose()
    }

    @Test
    fun gestureStreamsTrackIndependentPointerIds() {
        val tester = PixelTester()
        var first = 0
        var second = 0

        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    OutlinedButton(text = "A", onPressed = { first++ }),
                    OutlinedButton(text = "B", onPressed = { second++ }),
                ),
                spacing = 1,
            ),
            logicalWidth = 24,
            logicalHeight = 24,
        )

        val firstGesture = tester.startGesture(find.byText("A"), pointerId = 1)
        val secondGesture = tester.startGesture(find.byText("B"), pointerId = 2)
        secondGesture.up()
        firstGesture.up()

        assertEquals(1, first)
        assertEquals(1, second)
        tester.dispose()
    }

    @Test
    fun gestureStreamEstimatesListVelocityOnUp() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()

        tester.pumpWidget(
            widget = ListViewBuilder(
                itemCount = 20,
                itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                itemExtent = 6,
                state = state,
                controller = controller,
                key = "list",
            ),
            logicalWidth = 40,
            logicalHeight = 18,
        )

        tester.startGesture(find.byKey("list"))
            .moveBy(dx = 0, dy = -8, deltaMs = 40)
            .up()

        assertFalse(state.isDragging)
        assertTrue(state.isSettling)
        assertEquals(-200f, state.scrollVelocityPxPerSecond, 0.001f)
        tester.dispose()
    }

    @Test
    fun gestureStreamUsesEstimatedVelocityForPagerOnUp() {
        val tester = PixelTester()
        val controller = PixelPagerController(distanceThresholdFraction = 0.9f)
        val state = controller.create(pageCount = 2)

        tester.pumpWidget(
            widget = PageView(
                axis = Axis.HORIZONTAL,
                controller = controller,
                state = state,
                pages = listOf(Text("ONE"), Text("TWO")),
                key = "pager",
            ),
            logicalWidth = 30,
            logicalHeight = 10,
        )

        tester.startGesture(find.byKey("pager"))
            .moveBy(dx = -2, dy = 0, deltaMs = 1)
            .up()
        tester.pumpAndSettle()

        assertEquals(1, state.currentPage)
        tester.dispose()
    }

    @Test
    fun secondaryPointerHandsOffAfterPrimaryPointerEnds() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()

        tester.pumpWidget(
            widget = ListViewBuilder(
                itemCount = 20,
                itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                itemExtent = 6,
                state = state,
                controller = controller,
                key = "list",
            ),
            logicalWidth = 40,
            logicalHeight = 18,
        )

        val primary = tester.startGesture(find.byKey("list"), pointerId = 1)
        val secondary = tester.startGesture(find.byKey("list"), pointerId = 2)
        secondary.moveBy(dx = 0, dy = -12)
        assertEquals(0f, state.scrollOffsetPx, 0.001f)

        primary.up()
        secondary.moveBy(dx = 0, dy = -12)
        secondary.up()

        assertTrue(state.scrollOffsetPx > 0f)
        tester.dispose()
    }

    @Test
    fun semanticsTreeReportsTextButtonAndTextFieldState() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "NAME")
        controller.focus(state)

        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    Text("TITLE"),
                    OutlinedButton(text = "SAVE", onPressed = {}),
                    TextField(state = state, controller = controller, key = "field"),
                ),
                spacing = 1,
            ),
            logicalWidth = 60,
            logicalHeight = 30,
        )

        val semantics = tester.dumpSemanticsTree()
        assertTrue(semantics.contains("TEXT label=\"TITLE\""))
        assertTrue(semantics.contains("BUTTON label=\"SAVE\" enabled=true"))
        assertTrue(semantics.contains("TEXT_FIELD label=\"NAME\""))
        assertTrue(semantics.contains("focused=true"))
        tester.dispose()
    }

    @Test
    fun tapNthByTextInvokesMatchingButtonCallback() {
        val tester = PixelTester()
        var first = 0
        var second = 0

        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    OutlinedButton(text = "OK", onPressed = { first++ }),
                    OutlinedButton(text = "OK", onPressed = { second++ }),
                ),
                spacing = 1,
            ),
            logicalWidth = 24,
            logicalHeight = 24,
        )
        tester.tap(find.byText("OK").nth(1))

        assertEquals(0, first)
        assertEquals(1, second)
        tester.dispose()
    }

    @Test
    fun dragByKeyMovesListThroughRenderTarget() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()

        tester.pumpWidget(
            widget = ListViewBuilder(
                itemCount = 20,
                itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                itemExtent = 6,
                state = state,
                controller = controller,
                key = "list",
            ),
            logicalWidth = 40,
            logicalHeight = 18,
        )
        tester.drag(find.byKey("list"), dx = 0, dy = -12)

        assertTrue(state.scrollOffsetPx > 0f)
        tester.dispose()
    }

    @Test
    fun flingByKeyStartsListSettlingWithVelocity() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()

        tester.pumpWidget(
            widget = ListViewBuilder(
                itemCount = 20,
                itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                itemExtent = 6,
                state = state,
                controller = controller,
                key = "list",
            ),
            logicalWidth = 40,
            logicalHeight = 18,
        )
        tester.fling(find.byKey("list"), dx = 0, dy = -4, velocityPxPerSecond = -120f)

        assertFalse(state.isDragging)
        assertTrue(state.isSettling)
        assertEquals(-120f, state.scrollVelocityPxPerSecond, 0.001f)
        tester.dispose()
    }

    @Test
    fun dragByKeyMovesPagerThroughRenderTarget() {
        val tester = PixelTester()
        val controller = PixelPagerController(distanceThresholdFraction = 0.2f)
        val state = controller.create(pageCount = 2)

        tester.pumpWidget(
            widget = PageView(
                axis = Axis.HORIZONTAL,
                controller = controller,
                state = state,
                pages = listOf(Text("ONE"), Text("TWO")),
                key = "pager",
            ),
            logicalWidth = 30,
            logicalHeight = 10,
        )
        tester.drag(find.byKey("pager"), dx = -20, dy = 0)
        tester.pumpAndSettle()

        assertEquals(1, state.currentPage)
        tester.dispose()
    }

    @Test
    fun flingByKeyMovesPagerThroughVelocityPath() {
        val tester = PixelTester()
        val controller = PixelPagerController(distanceThresholdFraction = 0.9f)
        val state = controller.create(pageCount = 2)

        tester.pumpWidget(
            widget = PageView(
                axis = Axis.HORIZONTAL,
                controller = controller,
                state = state,
                pages = listOf(Text("ONE"), Text("TWO")),
                key = "pager",
            ),
            logicalWidth = 30,
            logicalHeight = 10,
        )
        tester.fling(find.byKey("pager"), dx = -2, dy = 0, velocityPxPerSecond = -90f)
        tester.pumpAndSettle()

        assertEquals(1, state.currentPage)
        tester.dispose()
    }

    @Test
    fun cancelDragByKeySettlesPagerBackToCurrentPage() {
        val tester = PixelTester()
        val controller = PixelPagerController(distanceThresholdFraction = 0.2f)
        val state = controller.create(pageCount = 2)

        tester.pumpWidget(
            widget = PageView(
                axis = Axis.HORIZONTAL,
                controller = controller,
                state = state,
                pages = listOf(Text("ONE"), Text("TWO")),
                key = "pager",
            ),
            logicalWidth = 30,
            logicalHeight = 10,
        )
        tester.cancelDrag(find.byKey("pager"), dx = -20, dy = 0)
        tester.pumpAndSettle()

        assertEquals(0, state.currentPage)
        assertFalse(state.isDragging)
        assertFalse(state.isSettling)
        tester.dispose()
    }

    @Test
    fun dragByKeyMovesGridThroughListTarget() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()
        val built = mutableSetOf<Int>()

        tester.pumpWidget(
            widget = GridViewBuilder(
                itemCount = 100,
                itemBuilder = { index ->
                    built += index
                    SizedBox(width = 8, height = 4, child = Text("$index"))
                },
                cellWidth = 8,
                cellHeight = 4,
                spacing = 1,
                runSpacing = 1,
                state = state,
                controller = controller,
                key = "grid",
            ),
            logicalWidth = 40,
            logicalHeight = 14,
        )

        assertTrue("GridViewBuilder should build a window, not every item", built.size < 100)
        tester.drag(find.byKey("grid"), dx = 0, dy = -10)

        assertTrue(state.scrollOffsetPx > 0f)
        tester.dispose()
    }

    @Test
    fun dragByKeyMovesCustomScrollViewThroughListTarget() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()

        tester.pumpWidget(
            widget = CustomScrollView(
                slivers = listOf(
                    SliverPinnedHeader(
                        child = SizedBox(height = 5, child = Text("PIN")),
                    ),
                    SliverList(
                        items = List(20) { index -> SizedBox(height = 5, child = Text("ROW $index")) },
                        spacing = 1,
                    ),
                ),
                state = state,
                controller = controller,
                key = "custom",
            ),
            logicalWidth = 48,
            logicalHeight = 18,
        )
        tester.drag(find.byKey("custom"), dx = 0, dy = -12)

        assertTrue(state.scrollOffsetPx > 0f)
        tester.dispose()
    }

    @Test
    fun pinnedSliverHeaderRemainsClickableAfterScroll() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()
        var taps = 0

        tester.pumpWidget(
            widget = CustomScrollView(
                slivers = listOf(
                    SliverPinnedHeader(
                        child = OutlinedButton(text = "PIN", onPressed = { taps++ }, key = "pin"),
                    ),
                    SliverList(
                        items = List(20) { index -> SizedBox(height = 5, child = Text("ROW $index")) },
                        spacing = 1,
                    ),
                ),
                state = state,
                controller = controller,
                key = "custom",
            ),
            logicalWidth = 48,
            logicalHeight = 18,
        )
        tester.drag(find.byKey("custom"), dx = 0, dy = -12)
        tester.tap(find.byKey("pin"))

        assertTrue(state.scrollOffsetPx > 0f)
        assertEquals(1, taps)
        tester.dispose()
    }

    @Test
    fun sliverAppBarCollapsesAndRemainsClickable() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()
        var taps = 0

        tester.pumpWidget(
            widget = CustomScrollView(
                slivers = listOf(
                    SliverAppBar(
                        expandedHeight = 16,
                        collapsedHeight = 8,
                        child = Column(
                            children = listOf(
                                SizedBox(height = 8),
                                OutlinedButton(text = "BAR", onPressed = { taps++ }, key = "bar"),
                            ),
                        ),
                    ),
                    SliverList(
                        items = List(20) { index -> SizedBox(height = 5, child = Text("ROW $index")) },
                        spacing = 1,
                    ),
                ),
                state = state,
                controller = controller,
                key = "custom",
            ),
            logicalWidth = 48,
            logicalHeight = 20,
        )
        tester.drag(find.byKey("custom"), dx = 0, dy = -12)
        tester.tap(find.byKey("bar"))

        assertTrue(state.scrollOffsetPx > 0f)
        assertEquals(1, taps)
        tester.dispose()
    }

    @Test
    fun multiplePinnedSliversStackWithoutOverlap() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 20f)
        val appBarColor = PixelColor.fromRgb(220, 70, 70)
        val firstHeaderColor = PixelColor.fromRgb(70, 200, 100)
        val secondHeaderColor = PixelColor.fromRgb(70, 110, 220)

        tester.pumpWidget(
            widget = CustomScrollView(
                slivers = listOf(
                    SliverAppBar(
                        expandedHeight = 12,
                        collapsedHeight = 4,
                        child = Container(width = 32, height = 12, fillColor = appBarColor),
                    ),
                    SliverPinnedHeader(
                        child = Container(width = 32, height = 5, fillColor = firstHeaderColor),
                    ),
                    SliverPinnedHeader(
                        child = Container(width = 32, height = 4, fillColor = secondHeaderColor),
                    ),
                    SliverList(
                        items = List(20) { SizedBox(height = 5, child = Text("ROW")) },
                    ),
                ),
                state = state,
                controller = controller,
            ),
            logicalWidth = 32,
            logicalHeight = 18,
        )

        val pixels = tester.renderResult!!.buffer.pixels
        assertEquals(appBarColor.argb, pixels[1 * 32 + 1])
        assertEquals(firstHeaderColor.argb, pixels[5 * 32 + 1])
        assertEquals(secondHeaderColor.argb, pixels[10 * 32 + 1])
        tester.dispose()
    }

    @Test
    fun floatingSliverAppBarRevealsDuringReverseScroll() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 20f)
        val appBarColor = PixelColor.fromRgb(220, 70, 70)
        val rowColor = PixelColor.fromRgb(60, 160, 90)

        tester.pumpWidget(
            widget = CustomScrollView(
                slivers = listOf(
                    SliverAppBar(
                        expandedHeight = 12,
                        collapsedHeight = 4,
                        floating = true,
                        child = Container(width = 32, height = 12, fillColor = appBarColor),
                    ),
                    SliverList(
                        items = List(20) {
                            Container(width = 32, height = 5, fillColor = rowColor)
                        },
                    ),
                ),
                state = state,
                controller = controller,
                key = "floating",
            ),
            logicalWidth = 32,
            logicalHeight = 18,
        )
        assertEquals(rowColor.argb, tester.renderResult!!.buffer.pixels[6 * 32 + 1])

        tester.drag(find.byKey("floating"), dx = 0, dy = 4)

        assertEquals(appBarColor.argb, tester.renderResult!!.buffer.pixels[6 * 32 + 1])
        tester.dispose()
    }

    @Test
    fun snappingSliverAppBarSettlesToBoundary() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 3f)

        tester.pumpWidget(
            widget = CustomScrollView(
                slivers = listOf(
                    SliverAppBar(
                        expandedHeight = 12,
                        collapsedHeight = 4,
                        floating = true,
                        snap = true,
                        child = Container(width = 32, height = 12, fillColor = PixelColor.White),
                    ),
                    SliverList(items = List(20) { SizedBox(height = 5, child = Text("ROW")) }),
                ),
                state = state,
                controller = controller,
            ),
            logicalWidth = 32,
            logicalHeight = 18,
        )

        controller.endDrag(state, 0f, state.viewportHeightPx, state.contentHeightPx)
        tester.pumpAndSettle()

        assertEquals(0f, state.scrollOffsetPx, 0.001f)
        tester.dispose()
    }

    @Test
    fun stretchingSliverAppBarUsesTopOverscroll() {
        val tester = PixelTester()
        val controller = PixelListController(
            physics = PixelScrollPhysics(
                bounceEnabled = true,
                bounceOverscrollLimitPx = 8f,
                bounceResistance = 0.5f,
            ),
        )
        val state = controller.create()
        val appBarColor = PixelColor.fromRgb(220, 70, 70)
        val rowColor = PixelColor.fromRgb(60, 160, 90)

        tester.pumpWidget(
            widget = CustomScrollView(
                slivers = listOf(
                    SliverAppBar(
                        expandedHeight = 12,
                        collapsedHeight = 4,
                        stretch = true,
                        stretchLimit = 6,
                        child = Container(width = 32, fillColor = appBarColor),
                    ),
                    SliverList(
                        items = List(20) {
                            Container(width = 32, height = 5, fillColor = rowColor)
                        },
                    ),
                ),
                state = state,
                controller = controller,
            ),
            logicalWidth = 32,
            logicalHeight = 18,
        )
        assertEquals(rowColor.argb, tester.renderResult!!.buffer.pixels[13 * 32 + 1])

        controller.dragBy(
            state = state,
            deltaPx = 6f,
            viewportHeightPx = state.viewportHeightPx,
            contentHeightPx = state.contentHeightPx,
        )
        tester.pumpFrame(16)

        assertTrue(state.scrollOffsetPx < 0f)
        assertEquals(appBarColor.argb, tester.renderResult!!.buffer.pixels[13 * 32 + 1])
        tester.dispose()
    }

    @Test
    fun sliverAppBarRejectsInvalidExtents() {
        try {
            SliverAppBar(
                expandedHeight = 4,
                collapsedHeight = 8,
                child = Text("BAD"),
            )
            error("Expected invalid SliverAppBar extents to throw")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("collapsedHeight"))
        }

        try {
            SliverAppBar(
                expandedHeight = 8,
                collapsedHeight = 4,
                snap = true,
                child = Text("BAD"),
            )
            error("Expected snap without floating to throw")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("floating"))
        }
    }

    @Test
    fun sliverListBuilderBuildsWindowAndScrolls() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()
        val built = mutableSetOf<Int>()

        tester.pumpWidget(
            widget = CustomScrollView(
                slivers = listOf(
                    SliverAppBar(
                        expandedHeight = 8,
                        collapsedHeight = 4,
                        child = Text("BAR"),
                    ),
                    SliverListBuilder(
                        itemCount = 100,
                        itemExtent = 5,
                        cacheExtent = 1,
                        itemBuilder = { index ->
                            built += index
                            SizedBox(height = 5, child = Text("ROW $index"))
                        },
                    ),
                ),
                state = state,
                controller = controller,
                key = "custom",
            ),
            logicalWidth = 48,
            logicalHeight = 18,
        )

        assertTrue("SliverListBuilder should not build every item", built.size < 100)
        tester.drag(find.byKey("custom"), dx = 0, dy = -20)

        assertTrue(state.scrollOffsetPx > 0f)
        tester.dispose()
    }

    @Test
    fun sliverListBuilderSupportsEstimatedVariableHeights() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()
        val built = mutableSetOf<Int>()

        tester.pumpWidget(
            widget = CustomScrollView(
                slivers = listOf(
                    SliverListBuilder(
                        itemCount = 80,
                        estimatedItemExtent = 5,
                        cacheExtent = 1,
                        spacing = 1,
                        itemBuilder = { index ->
                            built += index
                            SizedBox(
                                height = if (index % 3 == 0) 9 else 5,
                                child = Text("ROW $index"),
                            )
                        },
                    ),
                ),
                state = state,
                controller = controller,
                key = "custom",
            ),
            logicalWidth = 48,
            logicalHeight = 18,
        )

        assertTrue("Estimated SliverListBuilder should not build every item", built.size < 80)
        assertTrue("Visible variable-height item should be measured", stateHasMeasuredItems(state))

        tester.drag(find.byKey("custom"), dx = 0, dy = -18)
        assertTrue(state.scrollOffsetPx > 0f)
        tester.dispose()
    }

    @Test
    fun scrollSliverItemIntoViewBuildsAndCorrectsRemoteVariableItem() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()
        val built = mutableSetOf<Int>()

        tester.pumpWidget(
            widget = CustomScrollView(
                slivers = listOf(
                    SliverAppBar(
                        expandedHeight = 8,
                        collapsedHeight = 4,
                        child = Container(width = 48, height = 8, fillColor = PixelColor.White),
                    ),
                    SliverPinnedHeader(
                        child = Container(width = 48, height = 5, fillColor = PixelColor.fromRgb(70, 110, 220)),
                    ),
                    SliverListBuilder(
                        itemCount = 80,
                        estimatedItemExtent = 5,
                        spacing = 1,
                        cacheExtent = 1,
                        itemBuilder = { index ->
                            built += index
                            SizedBox(
                                height = if (index == 60) 11 else 5,
                                child = Text("ROW $index"),
                            )
                        },
                    ),
                ),
                state = state,
                controller = controller,
            ),
            logicalWidth = 48,
            logicalHeight = 18,
        )

        assertEquals(13, state.sliverListGeometries.getValue(2).contentStartPx)
        controller.scrollSliverItemIntoView(state, sliverIndex = 2, itemIndex = 60)
        tester.pumpFrame(16)
        tester.pumpFrame(16)

        assertTrue(60 in built)
        assertEquals(11, state.sliverListGeometries.getValue(2).measuredItemHeightsPx[60])
        assertEquals(null, state.pendingSliverScrollIntoView)
        tester.dispose()
    }

    @Test
    fun separatedBuilderSupportsVariableItemAndSeparatorHeights() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()
        val builtItems = mutableSetOf<Int>()
        val builtSeparators = mutableSetOf<Int>()

        tester.pumpWidget(
            widget = ListViewSeparatedBuilder(
                itemCount = 100,
                itemBuilder = { index ->
                    builtItems += index
                    SizedBox(
                        height = if (index % 4 == 0) 9 else 5,
                        child = Text("ITEM $index"),
                    )
                },
                separatorBuilder = { index ->
                    builtSeparators += index
                    SizedBox(
                        height = if (index % 3 == 0) 3 else 1,
                        child = Container(fillColor = PixelColor.White),
                    )
                },
                state = state,
                controller = controller,
                estimatedItemExtent = 5,
                estimatedSeparatorExtent = 1,
                cacheExtent = 1,
                key = "separated",
            ),
            logicalWidth = 48,
            logicalHeight = 18,
        )

        assertTrue(builtItems.size < 100)
        assertTrue(builtSeparators.size < 99)
        assertEquals(9, state.measuredSeparatedVirtualHeightsPx[0])
        assertEquals(3, state.measuredSeparatedVirtualHeightsPx[1])

        controller.scrollItemIntoView(state, itemIndex = 70)
        tester.pumpFrame(16)
        tester.pumpFrame(16)

        assertTrue(70 in builtItems)
        assertTrue(state.measuredSeparatedVirtualHeightsPx[140] > 0)
        assertEquals(null, state.pendingScrollIntoViewItemIndex)
        tester.dispose()
    }

    @Test
    fun wrapAndConstraintWidgetsRenderExpectedPixels() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = Wrap(
                children = listOf(
                    SizedBox(width = 8, height = 4, child = Container(fillColor = PixelColor.fromRgb(255, 0, 0))),
                    SizedBox(width = 8, height = 4, child = Container(fillColor = PixelColor.fromRgb(0, 255, 0))),
                    SizedBox(width = 8, height = 4, child = Container(fillColor = PixelColor.fromRgb(0, 0, 255))),
                ),
                spacing = 1,
                runSpacing = 1,
            ),
            logicalWidth = 18,
            logicalHeight = 12,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(PixelColor.fromRgb(0, 0, 255).argb, buffer.getPixel(0, 5).argb)
        tester.dispose()
    }

    @Test
    fun aspectConstrainedAndFittedBoxRender() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = FittedBox(
                child = AspectRatio(
                    aspectRatio = 2f,
                    child = ConstrainedBox(
                        constraints = PixelBoxConstraints(minWidth = 8, minHeight = 4),
                        child = Container(fillColor = PixelColor.fromRgb(255, 0, 0)),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 20,
        )

        val lit = tester.renderResult!!.buffer.pixels.count { it == PixelColor.fromRgb(255, 0, 0).argb }
        assertTrue("FittedBox should paint scaled child pixels", lit > 0)
        tester.dispose()
    }

    @Test
    fun scrollbarPaintsThumbForScrollableContent() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()
        val thumb = PixelColor.fromRgb(10, 220, 240)

        tester.pumpWidget(
            widget = Scrollbar(
                state = state,
                thumbColor = thumb,
                width = 2,
                child = ListViewBuilder(
                    itemCount = 20,
                    itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                    itemExtent = 6,
                    state = state,
                    controller = controller,
                ),
            ),
            logicalWidth = 40,
            logicalHeight = 18,
        )

        val thumbPixels = tester.renderResult!!.buffer.pixels.count { it == thumb.argb }
        assertTrue("Scrollbar should paint a thumb when content is scrollable", thumbPixels > 0)
        tester.dispose()
    }

    @Test
    fun scrollbarThumbDragScrollsBoundListState() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()

        tester.pumpWidget(
            widget = Scrollbar(
                state = state,
                width = 2,
                key = "scrollbar",
                child = ListViewBuilder(
                    itemCount = 40,
                    itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                    itemExtent = 6,
                    state = state,
                    controller = controller,
                ),
            ),
            logicalWidth = 40,
            logicalHeight = 18,
        )

        tester.drag(find.byKey("scrollbar"), dx = 0, dy = 12)

        assertTrue("Dragging the scrollbar thumb should update the list scroll offset", state.scrollOffsetPx > 0f)
        tester.dispose()
    }

    @Test
    fun pullRefreshTriggersWhenListIsAtTopAndThresholdIsReached() {
        val tester = PixelTester()
        val listController = PixelListController()
        val listState = listController.create()
        val refreshController = PixelRefreshIndicatorController()
        val refreshState = refreshController.create()
        var refreshes = 0

        tester.pumpWidget(
            widget = RefreshIndicator(
                state = refreshState,
                controller = refreshController,
                thresholdPx = 10,
                onRefresh = { refreshes += 1 },
                key = "refresh",
                child = ListViewBuilder(
                    itemCount = 20,
                    itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                    state = listState,
                    controller = listController,
                    itemExtent = 6,
                    key = "list",
                ),
            ),
            logicalWidth = 48,
            logicalHeight = 24,
        )

        tester.drag(find.byKey("refresh"), dx = 0, dy = 14)

        assertEquals(1, refreshes)
        assertTrue(refreshState.isRefreshing)

        refreshController.completeRefresh(refreshState)
        tester.pumpFrame(16)
        assertEquals(0f, refreshState.pullDistancePx, 0.001f)
        tester.dispose()
    }

    @Test
    fun swipeRefreshScaffoldTriggersRefresh() {
        val tester = PixelTester()
        val listController = PixelListController()
        val listState = listController.create()
        val refreshController = PixelRefreshIndicatorController()
        val refreshState = refreshController.create()
        var refreshes = 0

        tester.pumpWidget(
            widget = SwipeRefreshScaffold(
                state = refreshState,
                controller = refreshController,
                thresholdPx = 10,
                onRefresh = { refreshes += 1 },
                key = "swipe",
                body = ListViewBuilder(
                    itemCount = 20,
                    itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                    state = listState,
                    controller = listController,
                    itemExtent = 6,
                    key = "list",
                ),
            ),
            logicalWidth = 48,
            logicalHeight = 24,
        )

        tester.drag(find.byKey("swipe"), dx = 0, dy = 14)

        assertEquals(1, refreshes)
        assertTrue(refreshState.isRefreshing)
        tester.dispose()
    }

    @Test
    fun pullRefreshBelowThresholdDoesNotTrigger() {
        val tester = PixelTester()
        val listController = PixelListController()
        val listState = listController.create()
        val refreshController = PixelRefreshIndicatorController()
        val refreshState = refreshController.create()
        var refreshes = 0

        tester.pumpWidget(
            widget = RefreshIndicator(
                state = refreshState,
                controller = refreshController,
                thresholdPx = 12,
                onRefresh = { refreshes += 1 },
                key = "refresh",
                child = ListViewBuilder(
                    itemCount = 20,
                    itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                    state = listState,
                    controller = listController,
                    itemExtent = 6,
                ),
            ),
            logicalWidth = 48,
            logicalHeight = 24,
        )

        tester.drag(find.byKey("refresh"), dx = 0, dy = 6)

        assertEquals(0, refreshes)
        assertFalse(refreshState.isRefreshing)
        assertEquals(0f, refreshState.pullDistancePx, 0.001f)
        tester.dispose()
    }

    @Test
    fun pullRefreshDoesNotTriggerWhenListIsScrolledAwayFromTop() {
        val tester = PixelTester()
        val listController = PixelListController()
        val listState = listController.create(initialScrollOffsetPx = 18f)
        val refreshController = PixelRefreshIndicatorController()
        val refreshState = refreshController.create()
        var refreshes = 0

        tester.pumpWidget(
            widget = RefreshIndicator(
                state = refreshState,
                controller = refreshController,
                thresholdPx = 10,
                onRefresh = { refreshes += 1 },
                key = "refresh",
                child = ListViewBuilder(
                    itemCount = 20,
                    itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                    state = listState,
                    controller = listController,
                    itemExtent = 6,
                    key = "list",
                ),
            ),
            logicalWidth = 48,
            logicalHeight = 24,
        )

        tester.drag(find.byKey("refresh"), dx = 0, dy = 14)

        assertEquals(0, refreshes)
        assertFalse(refreshState.isRefreshing)
        assertTrue("list should consume downward drag before refresh can arm", listState.scrollOffsetPx < 18f)
        tester.dispose()
    }

    @Test
    fun selectionControlsToggleThroughTap() {
        val tester = PixelTester()
        var checkbox = false
        var switch = false
        var tileTapped = 0

        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    Checkbox(checked = checkbox, onChanged = { checkbox = it }, key = "checkbox"),
                    Switch(checked = switch, onChanged = { switch = it }, key = "switch"),
                    ListTile(title = Text("Tile"), onTap = { tileTapped++ }, key = "tile"),
                ),
                spacing = 2,
            ),
            logicalWidth = 80,
            logicalHeight = 40,
        )

        tester.tap(find.byKey("checkbox"))
        tester.tap(find.byKey("switch"))
        tester.tap(find.byKey("tile"))

        assertTrue(checkbox)
        assertTrue(switch)
        assertEquals(1, tileTapped)
        tester.dispose()
    }

    @Test
    fun selectionListAndOptionListInvokeSelectedIndex() {
        val tester = PixelTester()
        var selectedIndex = -1
        var selectedValue = ""

        tester.pumpWidget(
            widget = SelectionList(
                items = listOf("ONE", "TWO"),
                selectedIndex = 1,
                onSelected = { index, item ->
                    selectedIndex = index
                    selectedValue = item
                },
                itemLabel = { it },
                key = "selection",
            ),
            logicalWidth = 64,
            logicalHeight = 32,
        )

        tester.tap(find.byKey("selection-0"))
        assertEquals(0, selectedIndex)
        assertEquals("ONE", selectedValue)

        tester.pumpWidget(
            widget = OptionList(
                options = listOf("A", "B"),
                selectedIndex = 0,
                onSelected = { selectedIndex = it },
                key = "options",
            ),
            logicalWidth = 64,
            logicalHeight = 32,
        )

        tester.tap(find.byKey("options-1"))
        assertEquals(1, selectedIndex)
        tester.dispose()
    }

    @Test
    fun sectionListKeepsSectionChildrenInteractive() {
        val tester = PixelTester()
        var tapped = 0

        tester.pumpWidget(
            widget = SectionList(
                sections = listOf(
                    SectionListSection(
                        header = Text("SYSTEM"),
                        children = listOf(
                            ListTile(
                                title = Text("WIFI"),
                                onTap = { tapped += 1 },
                                key = "wifi",
                            ),
                        ),
                        footer = Text("READY"),
                    ),
                ),
                key = "sections",
            ),
            logicalWidth = 96,
            logicalHeight = 64,
        )

        tester.tap(find.byKey("wifi"))
        assertEquals(1, tapped)
        tester.dispose()
    }

    @Test
    fun valueAdjusterAndStepperTriggerBoundedChanges() {
        val tester = PixelTester()
        var value = 5

        tester.pumpWidget(
            widget = ValueAdjuster(
                valueText = value.toString(),
                onDecrease = { value -= 1 },
                onIncrease = { value += 1 },
                key = "adjust",
            ),
            logicalWidth = 96,
            logicalHeight = 32,
        )

        val adjustTargets = requireNotNull(tester.renderResult).clickTargets.sortedBy { it.bounds.left }
        assertEquals(2, adjustTargets.size)
        val decreaseTarget = adjustTargets[0]
        val increaseTarget = adjustTargets[1]
        assertEquals(11, decreaseTarget.bounds.width)
        assertEquals(11, increaseTarget.bounds.width)
        assertEquals(decreaseTarget.bounds.height, increaseTarget.bounds.height)
        val buffer = requireNotNull(tester.renderResult).buffer

        val outerLeft = decreaseTarget.bounds.left - 1
        val outerTop = decreaseTarget.bounds.top - 1
        val outerRight = increaseTarget.bounds.right
        val outerBottom = decreaseTarget.bounds.bottom
        for (x in outerLeft..outerRight) {
            assertEquals(PixelColor.White, buffer.getPixel(x, outerTop))
            assertEquals(PixelColor.White, buffer.getPixel(x, outerBottom))
        }
        for (y in outerTop..outerBottom) {
            assertEquals(PixelColor.White, buffer.getPixel(outerLeft, y))
            assertEquals(PixelColor.White, buffer.getPixel(outerRight, y))
            assertEquals(PixelColor.White, buffer.getPixel(decreaseTarget.bounds.right, y))
            assertEquals(PixelColor.White, buffer.getPixel(increaseTarget.bounds.left - 1, y))
        }
        assertFalse(buffer.getPixel(decreaseTarget.bounds.right + 1, outerTop + 1) == PixelColor.White)

        val decreaseSymbol = symbolPixels(buffer, decreaseTarget.bounds, PixelColor.Black)
        val increaseSymbol = symbolPixels(buffer, increaseTarget.bounds, PixelColor.Black)
        assertEquals(5, decreaseSymbol.size)
        assertEquals(9, increaseSymbol.size)
        assertCenteredSymbol(decreaseSymbol, decreaseTarget.bounds, expectedWidth = 5, expectedHeight = 1)
        assertCenteredSymbol(increaseSymbol, increaseTarget.bounds, expectedWidth = 5, expectedHeight = 5)
        tester.tap(find.byKey("adjust-decrease"))
        tester.tap(find.byKey("adjust-increase"))
        assertEquals(5, value)

        val tallStyle = PixelTextStyle.Default.copy(lineHeight = 13)
        tester.pumpWidget(
            widget = PixelTheme(
                data = PixelThemeData(
                    textStyle = tallStyle,
                    buttonStyle = PixelButtonStyle.Default.copy(
                        borderColor = PixelColor.White,
                        textStyle = tallStyle,
                    ),
                ),
                child = ValueAdjuster(
                    valueText = "10PX",
                    onDecrease = { value -= 1 },
                    onIncrease = { value += 1 },
                    key = "tall-adjust",
                ),
            ),
            logicalWidth = 96,
            logicalHeight = 32,
        )
        val tallTargets = requireNotNull(tester.renderResult).clickTargets.sortedBy { it.bounds.left }
        val tallLeft = tallTargets[0]
        val tallRight = tallTargets[1]
        assertEquals(11, tallLeft.bounds.width)
        assertTrue("ValueAdjuster buttons must stretch when value text gets taller.", tallLeft.bounds.height > decreaseTarget.bounds.height)
        val tallBuffer = requireNotNull(tester.renderResult).buffer
        val tallTop = tallLeft.bounds.top - 1
        val tallBottom = tallLeft.bounds.bottom
        val tallLeftDividerX = tallLeft.bounds.right
        val tallRightDividerX = tallRight.bounds.left - 1
        listOf(tallTop, (tallTop + tallBottom) / 2, tallBottom).forEach { y ->
            assertEquals(PixelColor.White, tallBuffer.getPixel(tallLeftDividerX, y))
            assertEquals(PixelColor.White, tallBuffer.getPixel(tallRightDividerX, y))
        }
        assertCenteredSymbol(symbolPixels(tallBuffer, tallLeft.bounds, PixelColor.Black), tallLeft.bounds, expectedWidth = 5, expectedHeight = 1)
        assertCenteredSymbol(symbolPixels(tallBuffer, tallRight.bounds, PixelColor.Black), tallRight.bounds, expectedWidth = 5, expectedHeight = 5)

        val styledBorder = PixelColor.fromRgb(10, 90, 180)
        val styledSymbol = PixelColor.fromRgb(250, 250, 250)
        tester.pumpWidget(
            widget = ValueAdjuster(
                valueText = "7",
                onDecrease = { value -= 1 },
                onIncrease = { value += 1 },
                style = ValueAdjusterStyle(
                    borderColor = styledBorder,
                    buttonFillColor = styledBorder,
                    buttonSymbolColor = styledSymbol,
                    valueTextColor = styledSymbol,
                    disabledColor = PixelColor.fromRgb(80, 80, 80),
                    focusColor = styledBorder,
                ),
                key = "styled-adjust",
            ),
            logicalWidth = 96,
            logicalHeight = 32,
        )
        val styledTargets = requireNotNull(tester.renderResult).clickTargets.sortedBy { it.bounds.left }
        val styledBuffer = requireNotNull(tester.renderResult).buffer
        assertEquals(styledBorder, styledBuffer.getPixel(styledTargets[0].bounds.left, styledTargets[0].bounds.top))
        assertEquals(styledBorder, styledBuffer.getPixel(styledTargets[0].bounds.right, styledTargets[0].bounds.top - 1))
        assertCenteredSymbol(symbolPixels(styledBuffer, styledTargets[1].bounds, styledSymbol), styledTargets[1].bounds, expectedWidth = 5, expectedHeight = 5)

        tester.pumpWidget(
            widget = Stepper(
                value = 9,
                range = 0..10,
                step = 5,
                onChanged = { value = it },
                key = "stepper",
            ),
            logicalWidth = 96,
            logicalHeight = 32,
        )

        tester.tap(find.byKey("stepper-increase"))
        assertEquals(10, value)
        tester.dispose()
    }

    private fun symbolPixels(buffer: PixelBuffer, bounds: PixelRect, color: PixelColor): List<Pair<Int, Int>> {
        return buildList {
            for (y in bounds.top until bounds.bottom) {
                for (x in bounds.left until bounds.right) {
                    if (buffer.getPixel(x, y) == color) {
                        add(x to y)
                    }
                }
            }
        }
    }

    private fun assertCenteredSymbol(
        pixels: List<Pair<Int, Int>>,
        bounds: PixelRect,
        expectedWidth: Int,
        expectedHeight: Int,
    ) {
        assertTrue(pixels.isNotEmpty())
        val minX = pixels.minOf { it.first }
        val maxX = pixels.maxOf { it.first }
        val minY = pixels.minOf { it.second }
        val maxY = pixels.maxOf { it.second }
        assertEquals(expectedWidth, maxX - minX + 1)
        assertEquals(expectedHeight, maxY - minY + 1)
        assertEquals(minX - bounds.left, bounds.right - 1 - maxX)
        assertEquals(minY - bounds.top, bounds.bottom - 1 - maxY)
    }

    @Test
    fun shortcutHintRendersShortcutAndLabel() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = ShortcutHint(shortcut = "A", label = "OPEN"),
            logicalWidth = 64,
            logicalHeight = 24,
        )

        assertTrue(tester.exists(find.byText("A")))
        assertTrue(tester.exists(find.byText("OPEN")))
        tester.dispose()
    }

    @Test
    fun overlayControlsRenderAndInvokeCallbacks() {
        val tester = PixelTester()
        var menuSelection = ""
        var toggles = 0

        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    Popover(
                        anchor = Text("ANCHOR"),
                        content = Text("POP"),
                        expanded = true,
                        contentOffset = IntOffset(0, 8),
                    ),
                    Menu(
                        items = listOf(
                            PixelMenuItem(label = "COPY", onSelected = { menuSelection = "copy" }),
                        ),
                        key = "menu",
                    ),
                    Dropdown(
                        label = "MODE",
                        selectedText = "A",
                        expanded = true,
                        onToggle = { toggles += 1 },
                        items = listOf(
                            PixelMenuItem(label = "A", onSelected = { menuSelection = "a" }),
                            PixelMenuItem(label = "B", onSelected = { menuSelection = "b" }),
                        ),
                        key = "drop",
                    ),
                    Tooltip(
                        message = "HELP",
                        visible = true,
                        child = Text("TARGET"),
                    ),
                ),
                spacing = 10,
            ),
            logicalWidth = 120,
            logicalHeight = 120,
        )

        assertTrue(tester.exists(find.byText("POP")))
        assertTrue(tester.exists(find.byText("HELP")))
        tester.tap(find.byKey("menu-0"))
        assertEquals("copy", menuSelection)
        tester.tap(find.byKey("drop-anchor"))
        assertEquals(1, toggles)
        tester.tap(find.byKey("drop-menu-1"))
        assertEquals("b", menuSelection)
        tester.dispose()
    }

    @Test
    fun overlayFeedbackWidgetsRenderText() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    Dialog(title = Text("Title"), content = Text("Body"), actions = listOf(Text("OK"))),
                    Toast("Saved"),
                    Snackbar("Queued", action = Text("UNDO")),
                ),
                spacing = 2,
            ),
            logicalWidth = 120,
            logicalHeight = 80,
        )

        assertTrue(tester.renderResult!!.buffer.pixels.any { it != PixelColor.Transparent.argb })
        tester.dispose()
    }

    @Test
    fun visibilitySwitchesBetweenChildAndReplacement() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = Visibility(
                visible = false,
                child = Text("VISIBLE"),
                replacement = Text("HIDDEN"),
            ),
            logicalWidth = 48,
            logicalHeight = 10,
        )

        assertFalse(tester.exists(find.byText("VISIBLE")))
        assertTrue(tester.exists(find.byText("HIDDEN")))

        tester.pumpWidget(
            widget = Visibility(
                visible = true,
                child = Text("VISIBLE"),
                replacement = Text("HIDDEN"),
            ),
            logicalWidth = 48,
            logicalHeight = 10,
        )

        assertTrue(tester.exists(find.byText("VISIBLE")))
        assertFalse(tester.exists(find.byText("HIDDEN")))
        tester.dispose()
    }

    @Test
    fun confirmDialogRendersTextAndInvokesActions() {
        val tester = PixelTester()
        var confirmed = 0
        var cancelled = 0

        tester.pumpWidget(
            widget = ConfirmDialog(
                title = "DELETE",
                message = "THIS CANNOT BE UNDONE",
                onConfirm = { confirmed++ },
                onCancel = { cancelled++ },
                confirmText = "DELETE",
                cancelText = "BACK",
                width = 56,
                key = "confirm-dialog",
            ),
            logicalWidth = 80,
            logicalHeight = 48,
        )

        assertTrue(tester.exists(find.byText("DELETE")))
        assertTrue(tester.exists(find.byText("THIS CANNOT BE UNDONE")))
        tester.tap(find.byKey("confirm-dialog-cancel"))
        tester.tap(find.byKey("confirm-dialog-confirm"))

        assertEquals(1, cancelled)
        assertEquals(1, confirmed)
        tester.dispose()
    }

    @Test
    fun emptyStateRendersTextAndAction() {
        val tester = PixelTester()
        var retries = 0

        tester.pumpWidget(
            widget = EmptyState(
                title = "NO APPS",
                message = "PIN OR INSTALL APPS",
                action = TextButton(text = "RETRY", onPressed = { retries++ }),
                width = 48,
            ),
            logicalWidth = 64,
            logicalHeight = 32,
        )

        assertTrue(tester.exists(find.byText("NO APPS")))
        assertTrue(tester.exists(find.byText("PIN OR INSTALL APPS")))
        tester.tap(find.byText("RETRY"))
        assertEquals(1, retries)
        tester.dispose()
    }

    @Test
    fun loadStateViewMapsSnapshotToFallbacksAndContent() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = LoadStateView(
                snapshot = PixelAsyncSnapshot.Loading,
                content = { Text("CONTENT $it") },
                loading = Text("LOADING"),
            ),
            logicalWidth = 80,
            logicalHeight = 12,
        )
        assertTrue(tester.exists(find.byText("LOADING")))

        tester.pumpWidget(
            widget = LoadStateView(
                snapshot = PixelAsyncSnapshot.Success(emptyList<String>()),
                content = { Text("CONTENT ${it.size}") },
                isEmpty = { it.isEmpty() },
                empty = Text("EMPTY"),
            ),
            logicalWidth = 80,
            logicalHeight = 12,
        )
        assertTrue(tester.exists(find.byText("EMPTY")))

        tester.pumpWidget(
            widget = LoadStateView(
                snapshot = PixelAsyncSnapshot.Success(listOf("A")),
                content = { Text("CONTENT ${it.first()}") },
            ),
            logicalWidth = 80,
            logicalHeight = 12,
        )
        assertTrue(tester.exists(find.byText("CONTENT A")))

        tester.pumpWidget(
            widget = LoadStateView(
                snapshot = PixelAsyncSnapshot.Failure(IllegalStateException("BAD")),
                content = { Text("CONTENT $it") },
                error = { Text("ERROR ${it.message}") },
            ),
            logicalWidth = 80,
            logicalHeight = 12,
        )
        assertTrue(tester.exists(find.byText("ERROR BAD")))
        tester.dispose()
    }

    @Test
    fun tabsSegmentedAndProgressWidgetsRenderAndTap() {
        val tester = PixelTester()
        var tab = 0
        var segment = 0
        var dragged = 0f
        var released = 0f

        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    Tabs(labels = listOf("A", "B"), selectedIndex = tab, onSelected = { tab = it }),
                    SegmentedControl(
                        labels = listOf("ONE", "TWO"),
                        selectedIndex = segment,
                        onSelected = { segment = it },
                    ),
                    Slider(value = 0.25f, onDrag = { dragged = it }, onRelease = { released = it }, key = "slider"),
                    ProgressBar(progress = 0.5f),
                    ActivityIndicator(frame = 1),
                ),
                spacing = 2,
            ),
            logicalWidth = 100,
            logicalHeight = 50,
        )

        tester.tap(find.byText("B"))
        tester.renderResult!!.clickTargets.last().onClick()
        tester.drag(find.byKey("slider"), dx = 25, dy = 0)

        assertEquals(1, tab)
        assertEquals(1, segment)
        assertEquals(0.75f, dragged, 0.01f)
        assertEquals(0.75f, released, 0.01f)
        assertTrue(tester.renderResult!!.buffer.pixels.any { it != PixelColor.Transparent.argb })
        tester.dispose()
    }

    @Test
    fun scaffoldBadgeDividerAndGapRender() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = AppScaffold(
                title = Text("Title"),
                body = Column(
                    children = listOf(
                        Badge(child = Text("MAIL"), label = Text("3")),
                        Divider(),
                        Gap(height = 2),
                        Text("Body"),
                    ),
                ),
                bottomBar = Text("Bottom"),
            ),
            logicalWidth = 80,
            logicalHeight = 60,
        )

        assertTrue(tester.renderResult!!.buffer.pixels.any { it != PixelColor.Transparent.argb })
        tester.dispose()
    }

    @Test
    fun dragListAtEdgeHandsOffToPagerTarget() {
        val tester = PixelTester()
        val pagerController = PixelPagerController(distanceThresholdFraction = 0.2f)
        val pagerState = pagerController.create(pageCount = 2, currentPage = 1, axis = Axis.VERTICAL)
        val listController = PixelListController()
        val listState = listController.create()

        tester.pumpWidget(
            widget = PageView(
                axis = Axis.VERTICAL,
                controller = pagerController,
                state = pagerState,
                pages = listOf(
                    Text("FIRST"),
                    ListViewBuilder(
                        itemCount = 12,
                        itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                        itemExtent = 6,
                        state = listState,
                        controller = listController,
                        key = "list",
                    ),
                ),
                key = "pager",
            ),
            logicalWidth = 40,
            logicalHeight = 18,
        )

        tester.drag(find.byKey("list"), dx = 0, dy = 14)
        tester.pumpAndSettle()

        assertEquals(0, pagerState.currentPage)
        assertEquals(0f, listState.scrollOffsetPx, 0.001f)
        tester.dispose()
    }

    @Test
    fun enterTextComposeAndSubmitUseTextInputTarget() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create()
        var changed = ""
        var submitted = ""

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                placeholder = "NAME",
                onChanged = { changed = it },
                onSubmitted = { submitted = it },
                key = "field",
            ),
            logicalWidth = 40,
            logicalHeight = 12,
        )

        tester.enterText(find.byKey("field"), "AB")
        tester.composeText(find.byKey("field"), "C")
        tester.submitTextInput()

        assertEquals("ABC", state.text)
        assertEquals(2, state.compositionStart)
        assertEquals(3, state.compositionEnd)
        assertEquals("ABC", changed)
        assertEquals("ABC", submitted)
        tester.dispose()
    }

    @Test
    fun tapAndDragTextFieldFocusAndUpdateSelection() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "ABCD")

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                key = "field",
            ),
            logicalWidth = 48,
            logicalHeight = 12,
        )

        tester.tap(find.byKey("field"))
        assertTrue(state.isFocused)

        tester.drag(find.byKey("field"), dx = -20, dy = 0)
        assertTrue(state.selectionStart < state.text.length)
        tester.dispose()
    }

    @Test
    fun doubleTapTextFieldSelectsWord() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "HELLO")

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                key = "field",
            ),
            logicalWidth = 80,
            logicalHeight = 12,
        )

        tester.doubleTap(find.byKey("field"))

        assertTrue(state.isFocused)
        assertEquals("HELLO", state.text.substring(state.selectionStart, state.selectionEnd))
        tester.dispose()
    }

    @Test
    fun longPressTextFieldSelectsWord() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "HELLO")

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                key = "field",
            ),
            logicalWidth = 80,
            logicalHeight = 12,
        )

        tester.longPress(find.byKey("field"))

        assertTrue(state.isFocused)
        assertEquals("HELLO", state.text.substring(state.selectionStart, state.selectionEnd))
        tester.dispose()
    }

    @Test
    fun tapReadOnlyTextFieldDoesNotFocusOrShowEditableSelection() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "READ")

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                readOnly = true,
                key = "field",
            ),
            logicalWidth = 64,
            logicalHeight = 12,
        )

        tester.tap(find.byKey("field"))
        tester.doubleTap(find.byKey("field"))
        tester.longPress(find.byKey("field"))

        assertFalse(state.isFocused)
        assertEquals(4, state.selectionStart)
        assertEquals(4, state.selectionEnd)
        tester.dispose()
    }

    @Test
    fun tapDisabledTextFieldDoesNotFocusOrMutateSelection() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "DISABLED")

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                enabled = false,
                key = "field",
            ),
            logicalWidth = 72,
            logicalHeight = 12,
        )

        tester.tap(find.byKey("field"))
        tester.doubleTap(find.byKey("field"))
        tester.longPress(find.byKey("field"))

        assertFalse(state.isFocused)
        assertEquals(8, state.selectionStart)
        assertEquals(8, state.selectionEnd)
        tester.dispose()
    }

    @Test
    fun dragSelectionHandlesUpdatesTextFieldSelection() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "ABCDE", selectionStart = 1, selectionEnd = 3)
        controller.focus(state)

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                key = "field",
            ),
            logicalWidth = 80,
            logicalHeight = 12,
        )

        tester.dragSelectionEndHandle(find.byKey("field"), dx = 28, dy = 0)

        assertEquals(1, state.selectionStart)
        assertTrue("end handle should extend the selection", state.selectionEnd > 3)
        tester.dispose()
    }

    @Test
    fun dragSelectionEndHandleAcrossTextFieldLinesUsesRenderedCaretMapping() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "AA\nBBBB\nCC", selectionStart = 0, selectionEnd = 2)
        controller.focus(state)

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                minLines = 3,
                maxLines = 3,
                key = "field",
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )

        tester.dragSelectionEndHandle(find.byKey("field"), dx = 0, dy = 8)

        assertEquals(0, state.selectionStart)
        assertTrue("end handle should cross into the next rendered line", state.selectionEnd > 3)
        assertTrue(state.text.substring(state.selectionStart, state.selectionEnd).contains('\n'))
        tester.dispose()
    }

    @Test
    fun dragSelectionStartHandleAcrossTextFieldLinesUsesRenderedCaretMapping() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "AA\nBBBB\nCC", selectionStart = 3, selectionEnd = 7)
        controller.focus(state)

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                minLines = 3,
                maxLines = 3,
                key = "field",
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )

        tester.dragSelectionStartHandle(find.byKey("field"), dx = 0, dy = -8)

        assertEquals(7, state.selectionEnd)
        assertTrue("start handle should move to the previous rendered line", state.selectionStart < 3)
        tester.dispose()
    }

    @Test
    fun dragSelectionHandleRejectsReadOnlyTextField() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "ABCDE", selectionStart = 1, selectionEnd = 4)
        controller.focus(state)

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                readOnly = true,
                key = "field",
            ),
            logicalWidth = 80,
            logicalHeight = 12,
        )

        try {
            tester.dragSelectionEndHandle(find.byKey("field"), dx = 20, dy = 0)
            error("dragSelectionEndHandle should reject readOnly fields")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("readOnly"))
        } finally {
            assertEquals(1, state.selectionStart)
            assertEquals(4, state.selectionEnd)
            tester.dispose()
        }
    }

    @Test
    fun dragSelectionHandleRejectsDisabledTextField() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "ABCDE", selectionStart = 1, selectionEnd = 4)
        controller.focus(state)

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                enabled = false,
                key = "field",
            ),
            logicalWidth = 80,
            logicalHeight = 12,
        )

        try {
            tester.dragSelectionStartHandle(find.byKey("field"), dx = -20, dy = 0)
            error("dragSelectionStartHandle should reject disabled fields")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("readOnly"))
        } finally {
            assertEquals(1, state.selectionStart)
            assertEquals(4, state.selectionEnd)
            tester.dispose()
        }
    }

    @Test
    fun enterTextRejectsReadOnlyTextField() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create()

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                readOnly = true,
                key = "field",
            ),
            logicalWidth = 48,
            logicalHeight = 12,
        )

        try {
            tester.enterText(find.byKey("field"), "NOPE")
            error("enterText should reject readOnly fields")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("readOnly"))
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun enterTextRejectsDisabledTextField() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create()

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                enabled = false,
                key = "field",
            ),
            logicalWidth = 48,
            logicalHeight = 12,
        )

        try {
            tester.enterText(find.byKey("field"), "NOPE")
            error("enterText should reject disabled fields")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("readOnly"))
        } finally {
            assertEquals("", state.text)
            tester.dispose()
        }
    }

    @Test
    fun submitTextInputCoversImeActions() {
        val actions = listOf(
            PixelTextInputAction.DONE,
            PixelTextInputAction.GO,
            PixelTextInputAction.SEARCH,
            PixelTextInputAction.SEND,
            PixelTextInputAction.NEXT,
        )

        actions.forEach { action ->
            val tester = PixelTester()
            val controller = PixelTextFieldController()
            val state = controller.create(initialText = action.name)
            var submitted = ""

            tester.pumpWidget(
                widget = TextField(
                    state = state,
                    controller = controller,
                    textInputAction = action,
                    onSubmitted = { submitted = it },
                    key = "field",
                ),
                logicalWidth = 80,
                logicalHeight = 12,
            )

            tester.tap(find.byKey("field"))
            tester.submitTextInput()

            assertEquals(action.name, submitted)
            tester.dispose()
        }
    }

    @Test
    fun textEditActionsUseSelectionAndClipboard() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "hello world")
        var changed = ""
        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                onChanged = { changed = it },
                key = "field",
            ),
            logicalWidth = 64,
            logicalHeight = 12,
        )

        controller.setSelection(state, 0, 5)
        assertTrue(tester.performTextEditAction(find.byKey("field"), PixelTextEditAction.COPY))
        assertEquals("hello", tester.clipboardText)
        assertEquals("hello world", state.text)

        assertTrue(tester.performTextEditAction(find.byKey("field"), PixelTextEditAction.CUT))
        assertEquals(" world", state.text)
        assertEquals(" world", changed)

        assertTrue(
            tester.performTextEditAction(
                find.byKey("field"),
                PixelTextEditAction.PASTE,
                pasteText = "pixel",
            ),
        )
        assertEquals("pixel world", state.text)

        assertTrue(tester.performTextEditAction(find.byKey("field"), PixelTextEditAction.SELECT_ALL))
        assertEquals(0, state.selectionStart)
        assertEquals(state.text.length, state.selectionEnd)
        tester.dispose()
    }

    @Test
    fun textEditActionsRespectReadOnlyTargets() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "read only")
        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                readOnly = true,
                key = "field",
            ),
            logicalWidth = 64,
            logicalHeight = 12,
        )

        controller.setSelection(state, 0, 4)
        assertTrue(tester.performTextEditAction(find.byKey("field"), PixelTextEditAction.COPY))
        assertEquals("read", tester.clipboardText)
        assertTrue(tester.performTextEditAction(find.byKey("field"), PixelTextEditAction.SELECT_ALL))
        assertEquals(0, state.selectionStart)
        assertEquals(state.text.length, state.selectionEnd)

        try {
            tester.performTextEditAction(find.byKey("field"), PixelTextEditAction.CUT)
            error("readOnly cut should fail")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("readOnly"))
        }
        assertEquals("read only", state.text)

        try {
            tester.performTextEditAction(
                find.byKey("field"),
                PixelTextEditAction.PASTE,
                pasteText = "write",
            )
            error("readOnly paste should fail")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("readOnly"))
        }
        assertEquals("read only", state.text)
        tester.dispose()
    }

    @Test
    fun debugOverlayDisplaysInspectorAndTickerDiagnostics() {
        val tester = PixelTester()
        tester.pumpWidget(
            widget = PixelDebugOverlay(
                stats = PixelHostFrameStats(
                    deltaMs = 16,
                    fpsAvg = 60f,
                    paintTimeNanos = 1_000_000,
                    frameCount = 3,
                ),
                inspector = PixelInspectorSnapshot(
                    frameStats = null,
                    allocationSample = PixelInspectorAllocationSample(
                        usedHeapBytes = 4_096,
                        totalHeapBytes = 8_192,
                        maxHeapBytes = 16_384,
                    ),
                    targetCounts = PixelInspectorTargetCounts(
                        click = 2,
                        pager = 1,
                        list = 3,
                        scrollbar = 1,
                        refresh = 0,
                        textInput = 1,
                        slider = 1,
                        semantics = 4,
                    ),
                    targetSnapshots = emptyList(),
                    elementTree = "Element",
                    renderTree = "Render",
                    semanticsTree = "Semantics",
                    hasPendingBuild = true,
                    focusedTextInput = true,
                    activePagerCount = 1,
                    activeListCount = 2,
                    activeSlider = false,
                    activeScrollbar = false,
                    activeRefresh = false,
                ),
                activeTickerCount = 5,
            ),
            logicalWidth = 96,
            logicalHeight = 32,
        )

        assertTrue(tester.exists(find.byText("FPS 60")))
        assertTrue(tester.exists(find.byText("TGT C2 L3 P1 T1")))
        assertTrue(tester.exists(find.byText("SEM 4 PEND 1")))
        assertTrue(tester.exists(find.byText("ACT P1 L2")))
        assertTrue(tester.exists(find.byText("MEM 4K/16K")))
        assertTrue(tester.exists(find.byText("TICK 5")))
        tester.dispose()
    }

    @Test
    fun inspectorPanelSwitchesBetweenSnapshotTrees() {
        val tester = PixelTester()
        val selectedTarget = PixelInspectorTargetSnapshot(
            kind = PixelInspectorTargetKind.TEXT_INPUT,
            left = 4,
            top = 5,
            width = 30,
            height = 9,
            detail = "#0 readOnly=0 action=DONE",
            elementPath = "0:Root/0:TextFieldWidget",
            renderPath = "0:RenderRoot/0:RenderSurface",
        )
        tester.pumpWidget(
            widget = PixelInspectorPanel(
                snapshot = PixelInspectorSnapshot(
                    frameStats = PixelHostFrameStats(
                        deltaMs = 16,
                        fpsAvg = 60f,
                        paintTimeNanos = 2_000_000,
                        frameCount = 9,
                    ),
                    allocationSample = PixelInspectorAllocationSample(
                        usedHeapBytes = 1_024,
                        totalHeapBytes = 2_048,
                        maxHeapBytes = 4_096,
                    ),
                    targetCounts = PixelInspectorTargetCounts(
                        click = 1,
                        pager = 2,
                        list = 3,
                        scrollbar = 4,
                        refresh = 5,
                        textInput = 6,
                        slider = 7,
                        semantics = 8,
                    ),
                    targetSnapshots = listOf(selectedTarget),
                    elementTree = "RootElement\n  TextElement",
                    renderTree = "RenderRoot\n  RenderText",
                    semanticsTree = "SemanticsRoot\n  label=OK",
                    hasPendingBuild = true,
                    focusedTextInput = false,
                    activePagerCount = 1,
                    activeListCount = 0,
                    activeSlider = true,
                    activeScrollbar = false,
                    activeRefresh = true,
                ),
                maxTreeLines = 3,
                selectedTarget = selectedTarget,
            ),
            logicalWidth = 128,
            logicalHeight = 80,
        )

        assertTrue(tester.exists(find.byText("INSPECTOR")))
        assertTrue(tester.exists(find.byText("MEM 1K/4K")))
        tester.tap(find.byText("RENDER"))
        assertTrue(tester.exists(find.byText("RENDER TREE")))
        assertTrue(tester.exists(find.byText("RenderRoot")))
        tester.tap(find.byText("SEM"))
        assertTrue(tester.exists(find.byText("SEMANTICS TREE")))
        assertTrue(tester.exists(find.byText("  label=OK")))
        tester.tap(find.byText("TARGETS"))
        assertTrue(tester.exists(find.byText("TARGET BOUNDS")))
        assertTrue(tester.exists(find.byText("TEXT_INPUT 4,5 30x9 #0 readOnly=0 action=DONE")))
        assertTrue(tester.exists(find.byText("E 0:Root/0:TextFieldWidget")))
        assertTrue(tester.exists(find.byText("R 0:RenderRoot/0:RenderSurface")))
        tester.dispose()
    }

    @Test
    fun missingFinderFailureIncludesCandidatesAndWidgetTree() {
        val tester = PixelTester()
        tester.pumpWidget(
            widget = OutlinedButton(text = "OK", onPressed = {}),
            logicalWidth = 24,
            logicalHeight = 10,
        )

        try {
            tester.tap(find.byText("MISSING"))
            error("tap should fail for missing finder")
        } catch (error: IllegalStateException) {
            val message = error.message.orEmpty()
            assertTrue(message.contains("Finder diagnostics"))
            assertTrue(message.contains("matches=0"))
            assertTrue(message.contains("Widget tree"))
            assertTrue(message.contains("OutlinedButtonWidget"))
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun pumpWidgetRendersBuffer() {
        val tester = PixelTester()
        tester.pumpWidget(Text("A", color = PixelColor.White), 8, 8)
        val lit = tester.renderResult!!.buffer.pixels.count { it != PixelColor.Transparent.argb }

        assert(lit > 0)
        tester.dispose()
    }
}

private fun stateHasMeasuredItems(state: PixelListState): Boolean {
    return state.sliverListGeometries.values.any { geometry ->
        geometry.measuredItemHeightsPx.any { it > 0 }
    }
}
