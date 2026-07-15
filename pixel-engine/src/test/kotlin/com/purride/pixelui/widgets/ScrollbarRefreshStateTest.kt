package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelColorRole
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelComponentColorTokens
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelFocusIndicatorTokens
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelSizeTokens
import com.purride.pixelui.PixelSpacingTokens
import com.purride.pixelui.PixelStateMap
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.SwipeRefreshScaffold
import com.purride.pixelui.Text
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** State, token, semantics, and interaction-owner contracts for Scrollbar and RefreshIndicator. */
class ScrollbarRefreshStateTest {
    /** Legacy sentinels resolve theme labels and geometry while explicit labels remain dominant. */
    @Test
    fun legacySentinelsResolveThemeLabelsAndFoundationGeometry() {
        /** Custom localizable labels proving mounted theme lookup. */
        val labels = PixelLabelTokens.Default.copy(
            scrollbar = "TOKEN SCROLLBAR",
            refresh = "TOKEN REFRESH",
        )
        /** Custom width proving the omitted historical one-pixel scrollbar width is tokenized. */
        val scrollbarTokens = PixelComponentTokens.Default.scrollbar.copy(minimumWidth = 3)
        /** Custom indicator height used as one threshold floor. */
        val refreshTokens = PixelComponentTokens.Default.refresh.copy(minimumHeight = 5)
        /** Custom compact height proving the historical twelve-pixel threshold is tokenized. */
        val sizes = PixelSizeTokens.Default.copy(compactControlHeight = 17)
        /** Theme containing every sentinel override under test. */
        val tokens = PixelThemeTokens.Default.copy(
            labels = labels,
            sizes = sizes,
            components = PixelComponentTokens.Default.copy(
                scrollbar = scrollbarTokens,
                refresh = refreshTokens,
            ),
        )
        /** Scroll controller for the token-width scrollbar. */
        val listController = PixelListController()
        /** Scroll state for the token-width scrollbar. */
        val listState = listController.create()
        /** Refresh controller for the token-threshold indicator. */
        val refreshController = PixelRefreshIndicatorController()
        /** Refresh state for the token-threshold indicator. */
        val refreshState = refreshController.create()
        /** Off-screen runtime inspecting final semantics and target geometry. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = tokens,
                    child = Column(
                        children = listOf(
                            SizedBox(
                                height = 12,
                                child = scrollableScrollbar(
                                    state = listState,
                                    controller = listController,
                                    key = "token-scrollbar",
                                ),
                            ),
                            SizedBox(
                                height = 12,
                                child = RefreshIndicator(
                                    child = Text("BODY"),
                                    state = refreshState,
                                    controller = refreshController,
                                    onRefresh = {},
                                    key = "token-refresh",
                                ),
                            ),
                        ),
                    ),
                ),
                logicalWidth = 48,
                logicalHeight = 24,
            )

            assertEquals(1, tester.semanticsNodesByLabel("TOKEN SCROLLBAR").size)
            assertEquals(1, tester.semanticsNodesByLabel("TOKEN REFRESH").size)
            assertEquals(3, tester.renderResult!!.scrollbarTargets.single().bounds.width)
            assertEquals(17, tester.renderResult!!.refreshTargets.single().thresholdPx)

            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = tokens,
                    child = RefreshIndicator(
                        child = Text("BODY"),
                        state = refreshState,
                        controller = refreshController,
                        onRefresh = {},
                        semanticLabel = "EXPLICIT REFRESH",
                    ),
                ),
                logicalWidth = 48,
                logicalHeight = 12,
            )
            assertEquals(1, tester.semanticsNodesByLabel("EXPLICIT REFRESH").size)
            assertTrue(tester.semanticsNodesByLabel("TOKEN REFRESH").isEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Scrollbar resolves semantic states, pointer feedback, and avoids an actionless Tab stop. */
    @Test
    fun scrollbarStateMatrixPointerFeedbackAndTraversalContract() {
        /** Persistent states and expected thumb colors from the custom state map. */
        val expectations = linkedMapOf(
            PixelControlStateSet.Normal to NormalColor,
            PixelControlStateSet.of(PixelControlState.Selected) to SelectedColor,
            PixelControlStateSet.of(PixelControlState.Disabled) to DisabledColor,
            PixelControlStateSet.of(PixelControlState.Error) to ErrorColor,
            PixelControlStateSet.of(PixelControlState.Loading) to LoadingColor,
        )
        expectations.forEach { (states, expectedColor) ->
            /** Independent scroll owner preventing state leakage between matrix rows. */
            val controller = PixelListController()
            /** Independent scroll geometry for this matrix row. */
            val state = controller.create()
            /** Isolated off-screen runtime for this matrix row. */
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    widget = stateTheme(
                        Scrollbar(
                            child = listViewport(state, controller),
                            state = state,
                            states = states,
                            key = "scrollbar",
                        ),
                    ),
                    logicalWidth = 40,
                    logicalHeight = 18,
                )
                /** Final semantic node for state-specific structured fields. */
                val node = tester.semanticsNodesByLabel("STATE SCROLLBAR").single()
                assertTrue("Missing state color $expectedColor for $states", tester.hasPixel(expectedColor))
                assertEquals(PixelControlState.Selected in states, node.selected)
                assertEquals(
                    if (PixelControlState.Error in states) "STATE ERROR" else null,
                    node.error,
                )
                assertEquals(
                    if (PixelControlState.Loading in states) "STATE LOADING" else null,
                    node.value,
                )
                /** Disabled and Loading withdraw the drag target; Error remains retryable. */
                val expectedTargetCount = if (
                    PixelControlState.Disabled in states || PixelControlState.Loading in states
                ) 0 else 1
                assertEquals(expectedTargetCount, tester.renderResult!!.scrollbarTargets.size)
            } finally {
                tester.dispose()
            }
        }

        /** Scroll owner for retained hover and press feedback. */
        val pointerController = PixelListController()
        /** Scroll state for retained hover and press feedback. */
        val pointerState = pointerController.create()
        /** Pointer runtime exercising both callback ownership channels. */
        val pointerTester = PixelTester()
        try {
            pointerTester.pumpWidget(
                widget = stateTheme(
                    Scrollbar(
                        child = listViewport(pointerState, pointerController),
                        state = pointerState,
                        states = PixelControlStateSet.Normal,
                        key = "scrollbar",
                    ),
                ),
                logicalWidth = 40,
                logicalHeight = 18,
            )
            pointerTester.hover(find.byKey("scrollbar"))
            assertTrue(pointerTester.hasPixel(HoveredColor))
            pointerTester.exitHover()
            /** Active pointer owner exposing Pressed until cancel. */
            val gesture = pointerTester.startGesture(find.byKey("scrollbar"))
            assertTrue(pointerTester.hasPixel(PressedColor))
            gesture.cancel()
            assertTrue(pointerTester.hasPixel(NormalColor))
        } finally {
            pointerTester.dispose()
        }

        /** Scrollbar has no controller-backed key action and must not consume a Tab stop. */
        val traversalController = PixelListController()
        /** Scroll state for the traversal contract. */
        val traversalState = traversalController.create()
        /** Runtime proving the next actionable control receives the first Tab. */
        val traversalTester = PixelTester()
        try {
            traversalTester.pumpWidget(
                widget = Column(
                    children = listOf(
                        SizedBox(
                            height = 12,
                            child = scrollableScrollbar(
                                state = traversalState,
                                controller = traversalController,
                                key = "passive-scrollbar",
                            ),
                        ),
                        OutlinedButton(text = "NEXT", onPressed = {}),
                    ),
                ),
                logicalWidth = 48,
                logicalHeight = 24,
            )
            assertTrue(traversalTester.pressKey(PixelKey.TAB))
            assertTrue(traversalTester.semanticsNodesByLabel("NEXT").single().focused)
            assertFalse(traversalTester.semanticsNodesByLabel("Scrollbar").single().focused)
        } finally {
            traversalTester.dispose()
        }
    }

    /** Refresh resolves all applicable states and keeps Error actionable while Loading is inert. */
    @Test
    fun refreshStateMatrixPointerKeyboardAndStructuredSemantics() {
        /** Persistent states and expected progress colors from the custom state map. */
        val expectations = linkedMapOf(
            PixelControlStateSet.Normal to NormalColor,
            PixelControlStateSet.of(PixelControlState.Selected) to SelectedColor,
            PixelControlStateSet.of(PixelControlState.Disabled) to DisabledColor,
            PixelControlStateSet.of(PixelControlState.Error) to ErrorColor,
            PixelControlStateSet.of(PixelControlState.Loading) to LoadingColor,
        )
        expectations.forEach { (states, expectedColor) ->
            /** Independent controller preventing controlled phase leakage. */
            val controller = PixelRefreshIndicatorController()
            /** Pull state with visible progress below the trigger threshold. */
            val state = controller.create().also { refreshState ->
                controller.startPull(refreshState)
                controller.updatePull(refreshState, distancePx = 4f, thresholdPx = 20)
            }
            /** Isolated runtime for this matrix row. */
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    widget = stateTheme(
                        RefreshIndicator(
                            child = Text("BODY"),
                            state = state,
                            controller = controller,
                            states = states,
                            onRefresh = {},
                            thresholdPx = 20,
                            key = "refresh",
                        ),
                    ),
                    logicalWidth = 40,
                    logicalHeight = 14,
                )
                /** Final semantic node for structured Error and Loading fields. */
                val node = tester.semanticsNodesByLabel("STATE REFRESH").single()
                assertTrue("Missing refresh state color $expectedColor for $states", tester.hasPixel(expectedColor))
                assertEquals(PixelControlState.Selected in states, node.selected)
                assertEquals(
                    if (PixelControlState.Error in states) "STATE ERROR" else null,
                    node.error,
                )
                assertEquals(
                    if (PixelControlState.Loading in states) "STATE LOADING" else null,
                    node.value,
                )
                assertEquals(
                    PixelControlState.Disabled !in states && PixelControlState.Loading !in states,
                    node.enabled,
                )
            } finally {
                tester.dispose()
            }
        }

        /** Controller for hover, press, and Error action behavior. */
        val pointerController = PixelRefreshIndicatorController()
        /** Visible below-threshold state for pointer color assertions. */
        val pointerState = pointerController.create().also { refreshState ->
            pointerController.startPull(refreshState)
            pointerController.updatePull(refreshState, distancePx = 4f, thresholdPx = 20)
        }
        /** Mutable callback count proving Error remains an actionable retry state. */
        var refreshCount = 0
        /** Runtime exercising both pointer channels and semantic retry. */
        val pointerTester = PixelTester()
        try {
            pointerTester.pumpWidget(
                widget = stateTheme(
                    RefreshIndicator(
                        child = Text("BODY"),
                        state = pointerState,
                        controller = pointerController,
                        states = PixelControlStateSet.Normal,
                        onRefresh = { refreshCount += 1 },
                        thresholdPx = 20,
                        key = "refresh",
                    ),
                ),
                logicalWidth = 40,
                logicalHeight = 14,
            )
            pointerTester.hover(find.byKey("refresh"))
            assertTrue(pointerTester.hasPixel(HoveredColor))
            pointerTester.exitHover()
            /** Pull gesture promoted after movement, producing Pressed feedback. */
            val gesture = pointerTester.startGesture(find.byKey("refresh"))
            gesture.moveBy(dx = 0, dy = 3)
            assertTrue(pointerTester.hasPixel(PressedColor))
            gesture.cancel()
            assertEquals(0f, pointerState.pullDistancePx, 0.001f)

            pointerTester.pumpWidget(
                widget = stateTheme(
                    RefreshIndicator(
                        child = Text("BODY"),
                        state = pointerState,
                        controller = pointerController,
                        states = PixelControlStateSet.of(PixelControlState.Error),
                        onRefresh = { refreshCount += 1 },
                        thresholdPx = 20,
                        key = "refresh",
                    ),
                ),
                logicalWidth = 40,
                logicalHeight = 14,
            )
            /** Error semantic node retaining the shared retry action. */
            val errorNode = pointerTester.semanticsNodesByLabel("STATE REFRESH").single()
            assertTrue(PixelSemanticsAction.CLICK in errorNode.actions)
            assertTrue(pointerTester.performSemanticsAction(errorNode.id, PixelSemanticsAction.CLICK))
            assertEquals(1, refreshCount)
        } finally {
            pointerTester.dispose()
        }

        /** Focus node proving Loading retains the existing action boundary without mutating. */
        val focusNode = FocusNode(debugLabel = "loading-refresh")
        /** Controller for keyboard Loading behavior. */
        val focusController = PixelRefreshIndicatorController()
        /** State retained while explicit Loading blocks lifecycle mutation. */
        val focusState = focusController.create()
        /** Runtime dispatching Tab and Enter through the automatic refresh focus boundary. */
        val focusTester = PixelTester()
        try {
            focusTester.pumpWidget(
                widget = stateTheme(
                    Focus(
                        node = focusNode,
                        child = RefreshIndicator(
                            child = Text("BODY"),
                            state = focusState,
                            controller = focusController,
                            states = PixelControlStateSet.of(PixelControlState.Loading),
                            onRefresh = { refreshCount += 1 },
                            key = "loading-refresh",
                        ),
                    ),
                ),
                logicalWidth = 40,
                logicalHeight = 14,
            )
            assertTrue(focusNode.requestFocus())
            focusTester.pumpFrame(0)
            /** Loading node remains focused but exports no activation. */
            val loadingNode = focusTester.semanticsNodesByLabel("STATE REFRESH").single()
            assertTrue(loadingNode.focused)
            assertFalse(loadingNode.enabled)
            assertEquals("STATE LOADING", loadingNode.value)
            assertTrue(focusTester.hasPixel(StateThemeTokens.colors.focus))
            assertFalse(focusTester.pressKey(PixelKey.ENTER))
            assertEquals(1, refreshCount)

            focusController.startPull(focusState)
            focusController.updatePull(focusState, distancePx = 4f, thresholdPx = 20)
            focusTester.pumpWidget(
                widget = stateTheme(
                    Focus(
                        node = focusNode,
                        child = RefreshIndicator(
                            child = Text("BODY"),
                            state = focusState,
                            controller = focusController,
                            states = PixelControlStateSet.of(PixelControlState.Error),
                            onRefresh = { refreshCount += 1 },
                            thresholdPx = 20,
                            key = "loading-refresh",
                        ),
                    ),
                ),
                logicalWidth = 40,
                logicalHeight = 14,
            )
            /** Error remains the base visual and semantic state while focus is independently additive. */
            val focusedErrorNode = focusTester.semanticsNodesByLabel("STATE REFRESH").single()
            assertTrue(focusedErrorNode.focused)
            assertEquals("STATE ERROR", focusedErrorNode.error)
            assertTrue(focusTester.hasPixel(ErrorColor))
            assertTrue(focusTester.hasPixel(StateThemeTokens.colors.focus))
        } finally {
            focusTester.dispose()
        }

        /** Explicit focus node proving the default high-contrast refresh focus layer is visible. */
        val highContrastFocusNode = FocusNode(debugLabel = "high-contrast-refresh")
        /** Controller backing a visible Error progress bar under the high-contrast preset. */
        val highContrastController = PixelRefreshIndicatorController()
        /** Visible partial pull ensuring both the Error base and focus outline can be inspected. */
        val highContrastState = highContrastController.create().also { refreshState ->
            highContrastController.startPull(refreshState)
            highContrastController.updatePull(refreshState, distancePx = 4f, thresholdPx = 20)
        }
        /** Runtime checking that the preset's focus color remains distinct from its Error surface. */
        val highContrastTester = PixelTester()
        try {
            highContrastTester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.HighContrastDark,
                    child = Focus(
                        node = highContrastFocusNode,
                        child = RefreshIndicator(
                            child = Text("BODY"),
                            state = highContrastState,
                            controller = highContrastController,
                            states = PixelControlStateSet.of(PixelControlState.Error),
                            onRefresh = {},
                            thresholdPx = 20,
                            key = "high-contrast-refresh",
                        ),
                    ),
                ),
                logicalWidth = 40,
                logicalHeight = 14,
            )
            assertTrue(highContrastFocusNode.requestFocus())
            highContrastTester.pumpFrame(0)
            /** Preset colors compared explicitly before checking both independent paint layers. */
            val highContrastColors = PixelThemeTokens.HighContrastDark.colors
            assertNotEquals(highContrastColors.danger, highContrastColors.focus)
            assertTrue(highContrastTester.hasPixel(highContrastColors.danger))
            assertTrue(highContrastTester.hasPixel(highContrastColors.focus))
        } finally {
            highContrastTester.dispose()
        }
    }

    /** Loading/Disabled target removal cancels owners and makes every stale pointer up inert. */
    @Test
    fun terminalTransitionsCancelOwnersAndStaleUp() {
        /** Mutable scrollbar states changed while a virtual drag remains active. */
        val scrollbarStates = ValueNotifier(PixelControlStateSet.Normal)
        /** Controller whose drag flag must be cleared by target disappearance. */
        val listController = PixelListController()
        /** List state used to assert drag cleanup and offset stability. */
        val listState = listController.create()
        /** Runtime reconciling active scrollbar ownership after Loading rebuild. */
        val scrollbarTester = PixelTester()
        try {
            scrollbarTester.pumpWidget(
                widget = stateTheme(
                    ValueListenableBuilder(scrollbarStates) { _, states ->
                        Scrollbar(
                            child = listViewport(listState, listController),
                            state = listState,
                            states = states,
                            key = "scrollbar",
                        )
                    },
                ),
                logicalWidth = 40,
                logicalHeight = 18,
            )
            /** Active scrollbar owner moved before its target is withdrawn. */
            val gesture = scrollbarTester.startGesture(find.byKey("scrollbar"))
            gesture.moveBy(dx = 0, dy = 8)
            assertTrue(listState.isDragging)
            scrollbarStates.value = PixelControlStateSet.of(PixelControlState.Loading)
            scrollbarTester.pumpFrame(0)
            /** Offset after forced cleanup, used to reject stale-up mutation. */
            val offsetAfterLoading = listState.scrollOffsetPx
            assertFalse(listState.isDragging)
            assertTrue(scrollbarTester.renderResult!!.scrollbarTargets.isEmpty())
            assertEquals("STATE LOADING", scrollbarTester.semanticsNodesByLabel("STATE SCROLLBAR").single().value)
            gesture.up()
            assertEquals(offsetAfterLoading, listState.scrollOffsetPx, 0.001f)
        } finally {
            scrollbarTester.dispose()
        }

        /** Mutable refresh states changed while a pull remains active. */
        val refreshStates = ValueNotifier(PixelControlStateSet.Normal)
        /** Explicit focus retained by Loading and released by Disabled. */
        val focusNode = FocusNode(debugLabel = "refresh-owner")
        /** Controller whose partial pull must be reset on target disappearance. */
        val refreshController = PixelRefreshIndicatorController()
        /** Refresh state used to assert pull-distance and armed cleanup. */
        val refreshState = refreshController.create()
        /** Business count rejecting stale-up refresh invocation. */
        var refreshCount = 0
        /** Runtime reconciling active refresh ownership across terminal states. */
        val refreshTester = PixelTester()
        try {
            refreshTester.pumpWidget(
                widget = stateTheme(
                    Focus(
                        node = focusNode,
                        child = ValueListenableBuilder(refreshStates) { _, states ->
                            RefreshIndicator(
                                child = Text("BODY"),
                                state = refreshState,
                                controller = refreshController,
                                states = states,
                                onRefresh = { refreshCount += 1 },
                                thresholdPx = 12,
                                key = "refresh",
                            )
                        },
                    ),
                ),
                logicalWidth = 40,
                logicalHeight = 14,
            )
            focusNode.requestFocus()
            refreshTester.pumpFrame(0)
            /** Active pull owner below threshold before Loading removes its target. */
            val gesture = refreshTester.startGesture(find.byKey("refresh"))
            gesture.moveBy(dx = 0, dy = 6)
            assertTrue(refreshState.pullDistancePx > 0f)
            refreshStates.value = PixelControlStateSet.of(PixelControlState.Loading)
            refreshTester.pumpFrame(0)
            /** Loading node proving focus retention and structured state. */
            val loadingNode = refreshTester.semanticsNodesByLabel("STATE REFRESH").single()
            assertTrue(loadingNode.focused)
            assertFalse(loadingNode.enabled)
            assertEquals("STATE LOADING", loadingNode.value)
            assertEquals(0f, refreshState.pullDistancePx, 0.001f)
            assertFalse(refreshState.isArmed)
            assertTrue(refreshTester.renderResult!!.refreshTargets.isEmpty())
            gesture.up()
            assertEquals(0, refreshCount)
            assertFalse(refreshState.isRefreshing)

            refreshStates.value = PixelControlStateSet.of(PixelControlState.Disabled)
            refreshTester.pumpFrame(0)
            assertFalse(focusNode.isFocused)
            assertFalse(refreshTester.semanticsNodesByLabel("STATE REFRESH").single().enabled)
        } finally {
            refreshTester.dispose()
        }
    }

    /** Explicit cancel routes reset scrollbar drag and refresh pull without invoking callbacks. */
    @Test
    fun cancelRoutesResetControlledInteractionState() {
        /** Scroll owner used by the explicit tester cancel route. */
        val listController = PixelListController()
        /** Scroll state whose dragging flag must return to false. */
        val listState = listController.create()
        /** Scrollbar cancel runtime. */
        val scrollbarTester = PixelTester()
        try {
            scrollbarTester.pumpWidget(
                widget = scrollableScrollbar(listState, listController, key = "scrollbar"),
                logicalWidth = 40,
                logicalHeight = 18,
            )
            scrollbarTester.cancelDrag(find.byKey("scrollbar"), dy = 8)
            assertFalse(listState.isDragging)
        } finally {
            scrollbarTester.dispose()
        }

        /** Controller used by the explicit refresh cancel route. */
        val refreshController = PixelRefreshIndicatorController()
        /** Refresh state whose pull and armed phase must reset. */
        val refreshState = refreshController.create()
        /** Business count proving an armed cancel never refreshes. */
        var refreshCount = 0
        /** Refresh cancel runtime. */
        val refreshTester = PixelTester()
        try {
            refreshTester.pumpWidget(
                widget = RefreshIndicator(
                    child = Text("BODY"),
                    state = refreshState,
                    controller = refreshController,
                    onRefresh = { refreshCount += 1 },
                    thresholdPx = 10,
                    key = "refresh",
                ),
                logicalWidth = 40,
                logicalHeight = 14,
            )
            refreshTester.cancelDrag(find.byKey("refresh"), dy = 14)
            assertEquals(0, refreshCount)
            assertFalse(refreshState.isRefreshing)
            assertFalse(refreshState.isArmed)
            assertEquals(0f, refreshState.pullDistancePx, 0.001f)
        } finally {
            refreshTester.dispose()
        }
    }

    /** Scaffold bar gaps resolve the mounted spacing scale instead of a literal pixel. */
    @Test
    fun swipeRefreshBarsUseThemeSpacing() {
        /** Three-pixel custom extra-small spacing under test. */
        val spacing = PixelSpacingTokens.Default.copy(extraSmall = 3)
        /** Unique body color used to locate the start after the top gap. */
        val bodyColor = PixelColor.fromRgb(41, 143, 211)
        /** Unique top-bar color. */
        val topColor = PixelColor.fromRgb(211, 43, 91)
        /** Unique bottom-bar color. */
        val bottomColor = PixelColor.fromRgb(73, 199, 67)
        /** Controller required by the scaffold refresh body. */
        val controller = PixelRefreshIndicatorController()
        /** Refresh state required by the scaffold refresh body. */
        val state = controller.create()
        /** Runtime inspecting exact vertical gap placement. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(spacing = spacing),
                    child = SwipeRefreshScaffold(
                        body = Container(fillColor = bodyColor),
                        state = state,
                        controller = controller,
                        states = PixelControlStateSet.Normal,
                        onRefresh = {},
                        topBar = Container(height = 2, fillColor = topColor),
                        bottomBar = Container(height = 2, fillColor = bottomColor),
                        key = "scaffold",
                    ),
                ),
                logicalWidth = 20,
                logicalHeight = 20,
            )
            /** Exact rows occupied by each unique scaffold layer. */
            val topRows = (0 until 20).filter { y -> tester.pixelAt(1, y) == topColor }
            /** Expanded refresh body rows after both tokenized gaps are reserved. */
            val bodyRows = (0 until 20).filter { y -> tester.pixelAt(1, y) == bodyColor }
            /** Exact rows occupied by the fixed bottom bar. */
            val bottomRows = (0 until 20).filter { y -> tester.pixelAt(1, y) == bottomColor }
            assertEquals(listOf(0, 1), topRows)
            assertEquals((5..14).toList(), bodyRows)
            assertEquals(listOf(18, 19), bottomRows)
            assertEquals(PixelColor.Transparent, tester.pixelAt(1, 3))
        } finally {
            tester.dispose()
        }
    }

    /** Builds a scrollable viewport with enough content to export a scrollbar target. */
    private fun listViewport(state: PixelListState, controller: PixelListController): Widget {
        return ListViewBuilder(
            itemCount = 20,
            itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
            itemExtent = 6,
            state = state,
            controller = controller,
        )
    }

    /** Builds the legacy Scrollbar facade around [listViewport]. */
    private fun scrollableScrollbar(
        state: PixelListState,
        controller: PixelListController,
        key: Any,
    ): Widget {
        return Scrollbar(
            child = listViewport(state, controller),
            state = state,
            key = key,
        )
    }

    /** Wraps [child] in custom state-role tokens with exact observable colors. */
    private fun stateTheme(child: Widget): Widget {
        return PixelTheme(tokens = StateThemeTokens, child = child)
    }

    private companion object {
        /** Normal foreground sentinel. */
        val NormalColor: PixelColor = PixelColor.fromRgb(231, 231, 231)

        /** Hovered foreground sentinel. */
        val HoveredColor: PixelColor = PixelColor.fromRgb(31, 191, 83)

        /** Pressed foreground sentinel. */
        val PressedColor: PixelColor = PixelColor.fromRgb(239, 223, 23)

        /** Selected foreground sentinel. */
        val SelectedColor: PixelColor = PixelColor.fromRgb(111, 91, 211)

        /** Disabled foreground sentinel. */
        val DisabledColor: PixelColor = PixelColor.fromRgb(89, 89, 89)

        /** Error foreground sentinel. */
        val ErrorColor: PixelColor = PixelColor.fromRgb(221, 47, 53)

        /** Loading foreground sentinel. */
        val LoadingColor: PixelColor = PixelColor.fromRgb(241, 151, 29)

        /** Additive focus-indicator sentinel. */
        val FocusColor: PixelColor = PixelColor.fromRgb(29, 211, 227)

        /** Shared track sentinel. */
        val TrackColor: PixelColor = PixelColor.fromRgb(17, 17, 17)

        /** Shared border sentinel. */
        val BorderColor: PixelColor = PixelColor.fromRgb(137, 137, 137)

        /** State map assigning every applicable base state a distinct semantic role. */
        val StateForegroundRoles = PixelStateMap<PixelColorRole?>(
            normal = PixelColorRole.OnSurface,
            PixelControlState.Hovered to PixelColorRole.Primary,
            PixelControlState.Pressed to PixelColorRole.Selection,
            PixelControlState.Selected to PixelColorRole.Inactive,
            PixelControlState.Disabled to PixelColorRole.OnDisabled,
            PixelControlState.Error to PixelColorRole.Danger,
            PixelControlState.Loading to PixelColorRole.Warning,
        )

        /** Component geometry and role tokens shared by both controls under test. */
        val StateComponentTokens: PixelComponentColorTokens = PixelComponentColorTokens(
            containerColor = PixelStateMap<PixelColorRole?>(normal = PixelColorRole.Track),
            contentColor = StateForegroundRoles,
            borderColor = PixelStateMap<PixelColorRole?>(normal = PixelColorRole.Outline),
            focusIndicator = PixelFocusIndicatorTokens(colorRole = PixelColorRole.Focus),
            minimumWidth = 2,
            minimumHeight = 3,
            borderWidth = 1,
            cornerRadius = 1,
        )

        /** Exact color scheme backing [StateForegroundRoles]. */
        val StateColors: PixelColorScheme = PixelColorScheme.Dark.copy(
            onSurface = NormalColor,
            primary = HoveredColor,
            selection = PressedColor,
            inactive = SelectedColor,
            onDisabled = DisabledColor,
            danger = ErrorColor,
            warning = LoadingColor,
            focus = FocusColor,
            track = TrackColor,
            outline = BorderColor,
        )

        /** Complete themed test graph including structured semantic labels. */
        val StateThemeTokens: PixelThemeTokens = PixelThemeTokens.Default.copy(
            colors = StateColors,
            labels = PixelLabelTokens.Default.copy(
                scrollbar = "STATE SCROLLBAR",
                refresh = "STATE REFRESH",
                error = "STATE ERROR",
                loading = "STATE LOADING",
            ),
            components = PixelComponentTokens.Default.copy(
                scrollbar = StateComponentTokens.copy(minimumHeight = 0),
                refresh = StateComponentTokens,
            ),
        )
    }
}
