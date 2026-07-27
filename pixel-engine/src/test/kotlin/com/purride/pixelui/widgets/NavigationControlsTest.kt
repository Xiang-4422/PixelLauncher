package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.Expanded
import com.purride.pixelui.NavigationBar
import com.purride.pixelui.NavigationRail
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelColorRole
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelMultiStackNavigator
import com.purride.pixelui.PixelMultiStackNavigatorController
import com.purride.pixelui.PixelNavigationDestination
import com.purride.pixelui.PixelRouteRequest
import com.purride.pixelui.PixelTypedNavigatorStack
import com.purride.pixelui.PixelRouteTransition
import com.purride.pixelui.testRouteRequest
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelSemanticsSelectionMode
import com.purride.pixelui.PixelStateProperty
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Row
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Production contracts for controlled and multi-stack-bound NavigationBar/NavigationRail. */
class NavigationControlsTest {
    /** Bar uses stable ids, skips disabled destinations, survives reorder, and remains one Tab stop. */
    @Test
    fun navigationBarKeyboardUsesStableBusinessIdentityAcrossReorder() {
        /** Caller-owned dynamic destinations; Search must never receive directional selection. */
        var destinations = destinations()
        /** Caller-owned selected id updated only after an observed request. */
        var selectedId = "home"
        /** Ordered stable ids emitted by keyboard activation. */
        val requests = mutableListOf<String>()
        /** Off-screen retained runtime preserving group focus through reordering. */
        val tester = PixelTester()
        try {
            /** Builds the current controlled bar followed by a traversal sentinel. */
            fun buildTree(): Widget = Column(
                children = listOf(
                    NavigationBar(
                        destinations = destinations,
                        selectedId = selectedId,
                        onSelected = { requestedId -> requests += requestedId },
                        key = "main-bar",
                    ),
                    OutlinedButton(text = "NEXT", onPressed = {}, key = "next"),
                ),
            )

            tester.pumpWidget(buildTree(), logicalWidth = 120, logicalHeight = 64)
            /** Normal semantic activation delivers the same stable id as pointer and keyboard. */
            val settingsActionNode = tester.semanticsNodesByLabel("Settings").single()
            assertTrue(
                tester.performSemanticsAction(settingsActionNode.id, PixelSemanticsAction.CLICK),
            )
            assertEquals(listOf("settings"), requests)
            requests.clear()
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("Home").single().focused)

            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertEquals(listOf("settings"), requests)
            selectedId = "settings"
            tester.pumpWidget(buildTree(), logicalWidth = 120, logicalHeight = 64)
            assertTrue(tester.semanticsNodesByLabel("Settings").single().focused)
            /** Retained semantic identity paired with the Settings business id before reordering. */
            val settingsSemanticId = tester.semanticsNodesByLabel("Settings").single().id
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertEquals(listOf("settings", "settings"), requests)

            /** Reordering moves Settings to index zero without changing its retained/action id. */
            destinations = listOf(destinations[2], destinations[0], destinations[1])
            tester.pumpWidget(buildTree(), logicalWidth = 120, logicalHeight = 64)
            /** Reordered node keeps its id but publishes the new current collection position. */
            val reorderedSettings = tester.semanticsNodesByLabel("Settings").single()
            assertEquals(settingsSemanticId, reorderedSettings.id)
            assertTrue(reorderedSettings.focused)
            assertEquals(0, reorderedSettings.collectionItemInfo?.columnIndex)
            assertTrue(tester.pressKey(PixelKey.ARROW_LEFT))
            assertEquals("home", requests.last())

            /** A second Tab leaves the complete bar, proving no destination owns another stop. */
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("NEXT").single().focused)
        } finally {
            tester.dispose()
        }
    }

    /** Rail exposes vertical SINGLE metadata and only Up/Down consume directional input. */
    @Test
    fun navigationRailUsesVerticalKeyboardAndCollectionMetadata() {
        /** Caller-owned selected id rebuilt after each request. */
        var selectedId = "home"
        /** Ordered vertical selection and activation requests. */
        val requests = mutableListOf<String>()
        /** Runtime used for semantics and keyboard assertions. */
        val tester = PixelTester()
        try {
            /** Builds the current controlled rail under a stable focus identity. */
            fun buildRail(): Widget = NavigationRail(
                destinations = destinations(),
                selectedId = selectedId,
                onSelected = { requestedId -> requests += requestedId },
                semanticLabel = "Primary rail",
                key = "primary-rail",
            )

            tester.pumpWidget(buildRail(), logicalWidth = 80, logicalHeight = 96)
            /** Collection node dimensions match a vertical three-item rail. */
            val collection = tester.semanticsNodesByLabel("Primary rail").single()
            assertEquals(3, collection.collectionInfo?.rowCount)
            assertEquals(1, collection.collectionInfo?.columnCount)
            assertEquals(PixelSemanticsSelectionMode.SINGLE, collection.collectionInfo?.selectionMode)
            /** Current visual positions are metadata only; item identities remain labels and ids. */
            val items = tester.semanticsNodes().filter { node -> node.role == PixelSemanticRole.TAB }
            assertEquals(listOf(0, 1, 2), items.map { node -> node.collectionItemInfo?.rowIndex })
            assertEquals(listOf(true, false, false), items.map { node -> node.selected })

            assertTrue(tester.pressKey(PixelKey.TAB))
            /** Focus traversal may consume an off-axis key, but the Rail must not select from it. */
            tester.pressKey(PixelKey.ARROW_LEFT)
            assertTrue(requests.isEmpty())
            assertTrue(tester.pressKey(PixelKey.ARROW_UP))
            assertEquals("settings", requests.last())
            selectedId = "settings"
            tester.pumpWidget(buildRail(), logicalWidth = 80, logicalHeight = 96)
            assertTrue(tester.pressKey(PixelKey.ARROW_DOWN))
            assertEquals("home", requests.last())
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertEquals("settings", requests.last())
        } finally {
            tester.dispose()
        }
    }

    /** Semantic clicks and Loading/Disabled states share the same capability and focus contract. */
    @Test
    fun navigationStatesGateActionsWhileLoadingRetainsFocus() {
        /** Semantic selection trace expected to remain empty while Loading. */
        val requests = mutableListOf<String>()
        /** Runtime preserving the same group focus identity across capability changes. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = NavigationBar(
                    destinations = destinations(),
                    selectedId = "home",
                    onSelected = { id -> requests += id },
                    states = PixelControlStateSet.of(PixelControlState.Loading),
                    key = "loading-bar",
                ),
                logicalWidth = 120,
                logicalHeight = 40,
            )
            assertTrue(tester.pressKey(PixelKey.TAB))
            /** Selected Loading item keeps group focus but exports no mutation capability. */
            val loadingHome = tester.semanticsNodesByLabel("Home").single()
            assertTrue(loadingHome.focused)
            assertFalse(loadingHome.enabled)
            assertEquals("LOADING", loadingHome.value)
            assertFalse(PixelSemanticsAction.CLICK in loadingHome.actions)
            assertFalse(tester.pressKey(PixelKey.ENTER))
            assertFalse(tester.performSemanticsAction(loadingHome.id, PixelSemanticsAction.CLICK))
            assertTrue(requests.isEmpty())

            tester.pumpWidget(
                widget = NavigationBar(
                    destinations = destinations(),
                    selectedId = "home",
                    onSelected = { id -> requests += id },
                    enabled = false,
                    key = "loading-bar",
                ),
                logicalWidth = 120,
                logicalHeight = 40,
            )
            /** Disabled releases the previously retained focus and removes traversal. */
            assertFalse(tester.semanticsNodesByLabel("Home").single().focused)
            assertFalse(tester.pressKey(PixelKey.TAB))
        } finally {
            tester.dispose()
        }
    }

    /** Bar and rail consume only their independent theme token families. */
    @Test
    fun navigationFamiliesResolveIndependentThemeTokens() {
        /** Exact Bar-only fill sentinel. */
        val barColor = PixelColor.fromRgb(211, 37, 91)
        /** Exact Rail-only fill sentinel. */
        val railColor = PixelColor.fromRgb(31, 197, 149)
        /** Scheme assigning the two independent semantic roles to exact test colors. */
        val colors = PixelColorScheme.Dark.copy(danger = barColor, warning = railColor)
        /** Complete theme replacing only navigation family container channels. */
        val theme = PixelThemeTokens.Default.copy(
            colors = colors,
            components = PixelComponentTokens.Default.copy(
                navigationBar = PixelComponentTokens.Default.navigationBar.copy(
                    containerColor = PixelStateProperty.constant(PixelColorRole.Danger),
                ),
                navigationRail = PixelComponentTokens.Default.navigationRail.copy(
                    containerColor = PixelStateProperty.constant(PixelColorRole.Warning),
                ),
            ),
        )
        /** Runtime sampling exact family-specific output pixels. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = theme,
                    child = NavigationBar(destinations(), "home", {}),
                ),
                logicalWidth = 120,
                logicalHeight = 40,
            )
            assertTrue(tester.hasPixel(barColor))
            assertFalse(tester.hasPixel(railColor))

            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = theme,
                    child = NavigationRail(destinations(), "home", {}),
                ),
                logicalWidth = 80,
                logicalHeight = 96,
            )
            assertTrue(tester.hasPixel(railColor))
            assertFalse(tester.hasPixel(barColor))
        } finally {
            tester.dispose()
        }
    }

    /** Controller binding switches losslessly, reselects one stack, and preserves Back fallback. */
    @Test
    fun controllerBoundBarPreservesIndependentStacksAndDelegatesReselectAndBack() {
        /** Multi-stack owner initially presenting Home. */
        val controller = PixelMultiStackNavigatorController(initialStackId = "home")
        /** Runtime mounting both Navigator stacks and their bound bottom bar. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Column(
                    children = listOf(
                        Expanded(
                            child = PixelMultiStackNavigator(
                                stacks = listOf(
                                    stack(id = "home", label = "HOME ROOT"),
                                    stack(id = "settings", label = "SETTINGS ROOT"),
                                ),
                                controller = controller,
                                vsync = tester.vsync,
                                defaultTransition = PixelRouteTransition.None,
                            ),
                        ),
                        NavigationBar(
                            destinations = destinations().filter { destination ->
                                destination.id != "search"
                            },
                            controller = controller,
                            popToRootOnReselect = true,
                            animated = false,
                            key = "stack-bar",
                        ),
                    ),
                ),
                logicalWidth = 120,
                logicalHeight = 80,
            )
            /** Both independently mounted child states receive one retained detail entry. */
            val homeNavigator = checkNotNull(controller.navigatorState("home"))
            val settingsNavigator = checkNotNull(controller.navigatorState("settings"))
            homeNavigator.push(route(name = "home-detail", label = "HOME DETAIL"))
            settingsNavigator.push(route(name = "settings-detail", label = "SETTINGS DETAIL"))
            tester.pumpAndSettle()
            assertEquals(2, homeNavigator.entries.size)
            assertEquals(2, settingsNavigator.entries.size)

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertEquals("settings", controller.activeStackId)
            assertEquals(2, homeNavigator.entries.size)
            assertEquals(2, settingsNavigator.entries.size)
            assertTrue(tester.semanticsNodesByLabel("Settings").single().selected)

            /** Reselecting Settings clears only its own foreground entry. */
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertEquals(2, homeNavigator.entries.size)
            assertEquals(1, settingsNavigator.entries.size)
            /** Secondary-root Back returns to Home without clearing Home's inactive stack. */
            assertTrue(controller.handleBack())
            tester.pumpFrame(0)
            assertEquals("home", controller.activeStackId)
            assertEquals(2, homeNavigator.entries.size)
            /** Active-stack Back now pops the retained Home detail. */
            assertTrue(controller.handleBack())
            tester.pumpAndSettle()
            assertEquals(1, homeNavigator.entries.size)
        } finally {
            tester.dispose()
        }
    }

    /** Dynamic destination contracts reject inaccessible or ambiguous identity before mounting. */
    @Test
    fun invalidDestinationContractsFailFast() {
        /** Shared opaque icon used by every fail-fast construction. */
        val icon = icon()
        assertThrows(IllegalArgumentException::class.java) {
            PixelNavigationDestination(id = " ", label = "Home", icon = icon)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelNavigationDestination(id = "home", label = "\t", icon = icon)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NavigationBar(emptyList(), "home", {})
        }
        assertThrows(IllegalArgumentException::class.java) {
            NavigationBar(
                destinations = listOf(
                    PixelNavigationDestination("same", "One", icon),
                    PixelNavigationDestination("same", "Two", icon),
                ),
                selectedId = "same",
                onSelected = {},
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NavigationRail(
                destinations = listOf(
                    PixelNavigationDestination("one", "Same", icon),
                    PixelNavigationDestination("two", "Same", icon),
                ),
                selectedId = "one",
                onSelected = {},
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NavigationBar(destinations(), "missing", {})
        }
        assertThrows(IllegalArgumentException::class.java) {
            NavigationRail(destinations(), "home", {}, semanticLabel = " ")
        }
    }

    /** Creates three stable destinations with one disabled middle entry. */
    private fun destinations(): List<PixelNavigationDestination> {
        /** Reused immutable icon mask for compact test fixtures. */
        val icon = icon()
        return listOf(
            PixelNavigationDestination(id = "home", label = "Home", icon = icon),
            PixelNavigationDestination(id = "search", label = "Search", icon = icon, enabled = false),
            PixelNavigationDestination(id = "settings", label = "Settings", icon = icon),
        )
    }

    /** 创建一个独立保留的 Navigator 栈根。 */
    private fun stack(id: String, label: String): PixelTypedNavigatorStack<Unit, Any?> {
        return PixelTypedNavigatorStack(
            id = id,
            initialRequest = route(name = "$id-root", label = label),
        )
    }

    /** 为栈深度断言创建一个确定性的无动画路由。 */
    private fun route(name: String, label: String): PixelRouteRequest<Unit, Any?> {
        return testRouteRequest(
            name = name,
            transition = PixelRouteTransition.None,
            builder = { Text(label) },
        )
    }

    /** Creates a tiny opaque alpha-mask icon for all navigation fixtures. */
    private fun icon(): PixelIconData {
        /** Opaque pixels whose RGB is replaced by active navigation content tokens. */
        val pixels = IntArray(9) { PixelColor.White.argb }
        return PixelIconData(PixelBitmap(width = 3, height = 3, pixels = pixels))
    }
}
