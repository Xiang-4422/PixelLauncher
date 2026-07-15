package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.Dropdown
import com.purride.pixelui.Menu
import com.purride.pixelui.PixelBackDispatcher
import com.purride.pixelui.PixelBackHost
import com.purride.pixelui.PixelColorRole
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelStateMap
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.Tooltip
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks overlay-family token propagation and combined Loading interaction behavior. */
class OverlayThemeStateTest {
    /** Loading Menu traps Escape/Back without invoking any controlled mutation callback. */
    @Test
    fun loadingMenuConsumesDismissInputsWithoutMutationAndRetainsFocus() {
        /** Platform Back dispatcher matching PixelHostView's discrete callback path. */
        val dispatcher = PixelBackDispatcher()
        /** Number of controlled dismissal callbacks observed by every dismissal channel. */
        var dismissCount = 0
        /** Number of row activations observed while the row is Loading. */
        var activationCount = 0
        /** Deterministic retained runtime exposing focus, semantics, keys, and Back. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelBackHost(
                    dispatcher = dispatcher,
                    child = Menu(
                        items = listOf(
                            PixelMenuItem(
                                label = "ACTION",
                                onSelected = { activationCount += 1 },
                            ),
                        ),
                        states = PixelControlStateSet.of(PixelControlState.Loading),
                        onDismissRequest = { dismissCount += 1 },
                        key = "loading-menu",
                    ),
                ),
                logicalWidth = 80,
                logicalHeight = 32,
            )

            /** Loading row remains the modal focus target but exports no mutation capability. */
            val rowBeforeDismiss = tester.semanticsNodesByLabel("ACTION").single()
            assertTrue(rowBeforeDismiss.focused)
            assertFalse(rowBeforeDismiss.enabled)
            assertFalse(PixelSemanticsAction.CLICK in rowBeforeDismiss.actions)
            /** Collection-level semantic dismissal is removed by the same capability gate. */
            val menuBeforeDismiss = tester.semanticsNodes().single { node ->
                node.role == PixelSemanticRole.MENU
            }
            assertFalse(menuBeforeDismiss.enabled)
            assertFalse(PixelSemanticsAction.DISMISS in menuBeforeDismiss.actions)
            assertFalse(
                tester.performSemanticsAction(
                    rowBeforeDismiss.id,
                    PixelSemanticsAction.CLICK,
                ),
            )

            assertTrue(tester.pressKey(PixelKey.ESCAPE))
            assertEquals(0, dismissCount)
            assertEquals(0, activationCount)
            assertTrue(tester.semanticsNodesByLabel("ACTION").single().focused)

            assertTrue(dispatcher.handleBack())
            assertEquals(0, dismissCount)
            assertEquals(0, activationCount)
            assertTrue(tester.semanticsNodesByLabel("ACTION").single().focused)
        } finally {
            tester.dispose()
        }
        assertFalse(dispatcher.hasRegisteredHandlers)
    }

    /** Expanded Loading Dropdown makes collapse, row activation, Escape, and Back inert. */
    @Test
    fun loadingExpandedDropdownMakesEveryCloseChannelInertAndRetainsPopupFocus() {
        /** Platform Back dispatcher matching production standalone Popover ownership. */
        val dispatcher = PixelBackDispatcher()
        /** Number of controlled expansion mutations observed by all close channels. */
        var toggleCount = 0
        /** Number of popup-row callbacks observed while the Dropdown is Loading. */
        var selectionCount = 0
        /** Deterministic retained runtime for the expanded Popover/Menu composition. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelBackHost(
                    dispatcher = dispatcher,
                    child = Dropdown(
                        label = "Mode",
                        selectedText = "A",
                        expanded = true,
                        onToggle = { toggleCount += 1 },
                        items = listOf(
                            PixelMenuItem("A", onSelected = { selectionCount += 1 }),
                        ),
                        states = PixelControlStateSet.of(PixelControlState.Loading),
                        key = "loading-dropdown",
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 48,
            )

            /** The modal presentation isolates its anchor and every background semantic node. */
            assertTrue(tester.semanticsNodesByLabel("Mode").isEmpty())
            /** Loading popup row remains focused inside the modal trap but cannot activate. */
            val row = tester.semanticsNodesByLabel("A").single()
            assertTrue(row.focused)
            assertFalse(row.enabled)
            assertFalse(tester.performSemanticsAction(row.id, PixelSemanticsAction.CLICK))

            assertFalse(tester.pressKey(PixelKey.ENTER))
            assertTrue(tester.pressKey(PixelKey.ESCAPE))
            assertTrue(dispatcher.handleBack())
            assertEquals(0, toggleCount)
            assertEquals(0, selectionCount)
            assertTrue(tester.semanticsNodesByLabel("A").single().focused)
        } finally {
            tester.dispose()
        }
        assertFalse(dispatcher.hasRegisteredHandlers)
    }

    /** Collapsed Loading Dropdown remains keyboard-focusable but cannot expand. */
    @Test
    fun loadingCollapsedDropdownRetainsAnchorFocusWithoutExpandActions() {
        /** Number of controlled expansion mutations observed by key and semantics adapters. */
        var toggleCount = 0
        /** Deterministic focus traversal and semantics runtime. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Dropdown(
                    label = "Mode",
                    selectedText = "A",
                    expanded = false,
                    onToggle = { toggleCount += 1 },
                    items = listOf(PixelMenuItem("A", onSelected = {})),
                    states = PixelControlStateSet.of(PixelControlState.Loading),
                    key = "collapsed-loading-dropdown",
                ),
                logicalWidth = 96,
                logicalHeight = 24,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            /** Focus is independent from semantic mutation availability. */
            val anchor = tester.semanticsNodesByLabel("Mode").single()
            assertTrue(anchor.focused)
            assertFalse(anchor.enabled)
            assertFalse(PixelSemanticsAction.EXPAND in anchor.actions)
            assertFalse(tester.performSemanticsAction(anchor.id, PixelSemanticsAction.EXPAND))
            assertFalse(tester.pressKey(PixelKey.ENTER))
            assertEquals(0, toggleCount)
            assertTrue(tester.semanticsNodesByLabel("Mode").single().focused)
        } finally {
            tester.dispose()
        }
    }

    /** Legacy factories treat unchanged defaults as sentinels under an explicit token theme. */
    @Test
    fun legacyOverlayFactoriesAdoptComponentTokensInsideTheme() {
        /** Unique Menu surface color resolved from its custom Normal role. */
        val menuColor = PixelColor.fromRgb(19, 61, 103)
        /** Unique Dropdown surface color resolved from its custom Normal role. */
        val dropdownColor = PixelColor.fromRgb(137, 73, 17)
        /** Unique Tooltip surface color resolved from its custom Normal role. */
        val tooltipColor = PixelColor.fromRgb(31, 119, 67)
        /** Custom scheme assigns sentinel concrete values to three independent semantic roles. */
        val colors = PixelThemeTokens.Default.colors.copy(
            danger = menuColor,
            warning = dropdownColor,
            primary = tooltipColor,
        )
        /** Component graph maps each overlay family to a distinct role in Normal state. */
        val components = PixelComponentTokens.Default.copy(
            menu = PixelComponentTokens.Default.menu.copy(
                containerColor = PixelStateMap<PixelColorRole?>(PixelColorRole.Danger),
            ),
            dropdown = PixelComponentTokens.Default.dropdown.copy(
                containerColor = PixelStateMap<PixelColorRole?>(PixelColorRole.Warning),
            ),
            tooltip = PixelComponentTokens.Default.tooltip.copy(
                containerColor = PixelStateMap<PixelColorRole?>(PixelColorRole.Primary),
            ),
        )
        /** Localized collection label proving the old default string also defers to tokens. */
        val labels = PixelLabelTokens.Default.copy(menu = "COMMANDS")
        /** Complete custom graph mounted above unchanged legacy factory calls. */
        val tokens = PixelThemeTokens.Default.copy(
            colors = colors,
            components = components,
            labels = labels,
        )
        /** Off-screen renderer used for exact ARGB propagation assertions. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = tokens,
                    child = Column(
                        children = listOf(
                            Menu(
                                items = listOf(PixelMenuItem("OPEN", onSelected = {})),
                                modal = false,
                            ),
                            Dropdown(
                                label = "Mode",
                                selectedText = "A",
                                expanded = false,
                                onToggle = {},
                                items = listOf(PixelMenuItem("A", onSelected = {})),
                            ),
                            Tooltip(
                                message = "HELP",
                                visible = true,
                                child = SizedBox(width = 4, height = 4, child = Text("?")),
                            ),
                        ),
                    ),
                ),
                logicalWidth = 120,
                logicalHeight = 72,
            )

            assertTrue(tester.hasPixel(menuColor))
            assertTrue(tester.hasPixel(dropdownColor))
            assertTrue(tester.hasPixel(tooltipColor))
            assertEquals(1, tester.semanticsNodesByLabel("COMMANDS").size)
        } finally {
            tester.dispose()
        }
    }

    /** Expanded Dropdown popup rows and surface consume Dropdown tokens, never standalone Menu tokens. */
    @Test
    fun expandedDropdownPopupUsesDropdownTokenFamily() {
        /** Sentinel color that must paint both the Dropdown anchor and popup family. */
        val dropdownColor = PixelColor.fromRgb(29, 149, 211)
        /** Sentinel standalone Menu color that must not leak into this composition. */
        val menuColor = PixelColor.fromRgb(217, 37, 91)
        /** Theme graph assigning mutually exclusive concrete roles to the two families. */
        val tokens = PixelThemeTokens.Default.copy(
            colors = PixelThemeTokens.Default.colors.copy(
                primary = dropdownColor,
                danger = menuColor,
            ),
            components = PixelComponentTokens.Default.copy(
                dropdown = PixelComponentTokens.Default.dropdown.copy(
                    containerColor = PixelStateMap<PixelColorRole?>(PixelColorRole.Primary),
                ),
                menu = PixelComponentTokens.Default.menu.copy(
                    containerColor = PixelStateMap<PixelColorRole?>(PixelColorRole.Danger),
                ),
            ),
        )
        /** Off-screen retained runtime exposing popup pixels after anchor placement. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = tokens,
                    child = Dropdown(
                        label = "Mode",
                        selectedText = "A",
                        expanded = true,
                        onToggle = {},
                        items = listOf(PixelMenuItem("A", onSelected = {})),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 48,
            )

            assertTrue(tester.hasPixel(dropdownColor))
            assertFalse(tester.hasPixel(menuColor))
        } finally {
            tester.dispose()
        }
    }
}
