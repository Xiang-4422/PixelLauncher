package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelBitmap
import com.purride.pixelui.BottomSheet
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Dialog
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.ListTile
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.Menu
import com.purride.pixelui.IconButton
import com.purride.pixelui.NavigationBar
import com.purride.pixelui.NavigationRail
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelColorRole
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelComponentColorTokens
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelFocusIndicatorTokens
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelNavigationDestination
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelStateMap
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Radio
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slidable
import com.purride.pixelui.Snackbar
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextField
import com.purride.pixelui.Toast
import com.purride.pixelui.Tooltip
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.Widget
import com.purride.pixelui.Dropdown
import com.purride.pixelui.Slider
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Production-factory render matrix for every standard component token family and control state. */
class ProductionComponentStateMatrixTest {
    /** The registry is exact, ordered like the public token graph, and contains no aliases or duplicates. */
    @Test
    fun productionRegistryIsCompleteAndUnique() {
        /** Family identifiers declared by the executable production registration table. */
        val actualFamilies = COMPONENT_REGISTRY.map(ComponentRegistration::family)
        assertEquals(EXPECTED_COMPONENT_FAMILIES, actualFamilies)
        assertEquals(EXPECTED_COMPONENT_FAMILIES.size, actualFamilies.toSet().size)
        assertEquals(25, actualFamilies.size)
        assertEquals(8, STATE_ROWS.size)
    }

    /** Every one of the 200 cells builds a real public component and paints its independent state color. */
    @Test
    fun everyProductionComponentBuildsAndRendersEveryState() {
        /** Theme mapping all component channels to eight exact, independently observable colors. */
        val theme = matrixTheme()
        /** Reused retained runtime for the complete 25-family by 8-state cross product. */
        val tester = PixelTester()
        try {
            COMPONENT_REGISTRY.forEach { registration ->
                STATE_ROWS.forEach { row ->
                    /**
                     * Canonical caller state for this cell. Focusable controls must derive Focused
                     * from the real traversal path below, rather than receiving a synthetic flag.
                     */
                    val states = if (
                        row.state == PixelControlState.Focused && registration.focusable
                    ) {
                        PixelControlStateSet.Normal
                    } else {
                        stateSet(row.state)
                    }
                    /** Stable per-cell identity preventing retained state from leaking between rows. */
                    val key = "production-${registration.family}-${row.state.name}"
                    /** Real production factory result for the registered component family. */
                    val component = buildProductionComponent(
                        registration = registration,
                        states = states,
                        key = key,
                    )
                    tester.pumpWidget(
                        widget = PixelTheme(tokens = theme, child = component),
                        logicalWidth = MATRIX_WIDTH,
                        logicalHeight = MATRIX_HEIGHT,
                    )

                    if (row.state == PixelControlState.Focused && registration.focusable) {
                        /** Public traversal result proving an actual mounted FocusNode acquired focus. */
                        val focused = tester.pressKey(PixelKey.TAB)
                        assertTrue("${registration.family} rejected real focus traversal", focused)
                    }

                    /** Component-owned semantic nodes selected independently from incidental child text. */
                    val componentNodes = componentSemanticNodes(registration, tester)
                    assertTrue(
                        "${registration.family} exported no registered semantics for ${row.state}",
                        componentNodes.isNotEmpty(),
                    )
                    if (row.state == PixelControlState.Focused && registration.focusable) {
                        assertTrue(
                            "${registration.family} never exposed its real focused semantic node",
                            componentNodes.any(PixelSemanticsNode::focused),
                        )
                    }

                    /** Exact color assigned to this state by the custom scheme. */
                    val expectedColor = theme.colors.resolve(row.role)
                    assertTrue(
                        "${registration.family} did not paint ${row.state} color $expectedColor",
                        tester.hasPixel(expectedColor),
                    )

                    /** Union of actions exported by only this registered component's semantic nodes. */
                    val actions = componentNodes.flatMap(PixelSemanticsNode::actions).toSet()
                    when (row.state) {
                        PixelControlState.Normal -> assertTrue(
                            "${registration.family} missed its normal semantic actions",
                            actions.containsAll(registration.normalActions),
                        )
                        PixelControlState.Loading,
                        PixelControlState.Disabled,
                        -> assertTrue(
                            "${registration.family} retained actions $actions in ${row.state}",
                            actions.isEmpty(),
                        )
                        else -> Unit
                    }
                }
            }
        } finally {
            tester.dispose()
        }
    }

    /** Every standard family paints at least two independently configured component color channels. */
    @Test
    fun everyProductionComponentPaintsIndependentColorChannels() {
        /** Theme assigning exact pairwise-distinct colors to container, content, and border. */
        val theme = independentChannelTheme()
        /** Reused retained runtime covering the complete public production registry. */
        val tester = PixelTester()
        try {
            COMPONENT_REGISTRY.forEach { registration ->
                /** Stable identity preventing retained state from crossing component families. */
                val key = "independent-channel-${registration.family}"
                /** Real Normal-state public component whose actual pixels are inspected. */
                val component = buildProductionComponent(
                    registration = registration,
                    states = PixelControlStateSet.Normal,
                    key = key,
                )
                tester.pumpWidget(
                    widget = PixelTheme(tokens = theme, child = component),
                    logicalWidth = MATRIX_WIDTH,
                    logicalHeight = MATRIX_HEIGHT,
                )

                /** Exact independent channel colors that survived through layout and paint. */
                val paintedChannels = INDEPENDENT_CHANNEL_COLORS.filter(tester::hasPixel)
                assertTrue(
                    "${registration.family} painted only ${paintedChannels.size} independent " +
                        "component color channel(s): $paintedChannels",
                    paintedChannels.size >= MINIMUM_OBSERVABLE_COLOR_CHANNELS,
                )
            }
        } finally {
            tester.dispose()
        }
    }

    /** Selects the semantic nodes explicitly registered as belonging to [registration]. */
    private fun componentSemanticNodes(
        registration: ComponentRegistration,
        tester: PixelTester,
    ): List<PixelSemanticsNode> {
        /** Exact accepted labels for the component group and any independently actionable children. */
        val labels = registration.observableLabels
        return tester.semanticsNodes().filter { node -> node.label in labels }
    }

    /** Builds one real public factory for [registration] without substituting generic token swatches. */
    private fun buildProductionComponent(
        registration: ComponentRegistration,
        states: PixelControlStateSet,
        key: Any,
    ): Widget {
        return when (registration.family) {
            "button" -> OutlinedButton(
                text = registration.primaryLabel,
                onPressed = {},
                states = states,
                key = key,
            )
            "textButton" -> TextButton(
                text = registration.primaryLabel,
                onPressed = {},
                states = states,
                key = key,
            )
            "iconButton" -> IconButton(
                icon = MATRIX_ICON,
                onPressed = {},
                semanticLabel = registration.primaryLabel,
                selected = PixelControlState.Selected in states,
                states = states,
                key = key,
            )
            "textField" -> {
                /** Controller backing the public editable text-field factory. */
                val controller = PixelTextFieldController()
                /** Non-empty controlled value exporting real edit and selection semantics. */
                val fieldState = controller.create(initialText = "FIELD")
                TextField(
                    state = fieldState,
                    controller = controller,
                    states = states,
                    onChanged = {},
                    semanticLabel = registration.primaryLabel,
                    key = key,
                )
            }
            "listTile" -> ListTile(
                title = Text("LIST TILE BODY"),
                states = states,
                onTap = {},
                semanticLabel = registration.primaryLabel,
                key = key,
            )
            "checkbox" -> Checkbox(
                checked = false,
                onChanged = {},
                states = states,
                semanticLabel = registration.primaryLabel,
                key = key,
            )
            "radio" -> Radio(
                selected = PixelControlState.Selected in states,
                onSelected = {},
                semanticLabel = registration.primaryLabel,
                states = states,
                key = key,
            )
            "switch" -> Switch(
                checked = false,
                onChanged = {},
                states = states,
                semanticLabel = registration.primaryLabel,
                key = key,
            )
            "slider" -> Slider(
                value = 0.5f,
                states = states,
                onDrag = {},
                onRelease = {},
                semanticLabel = registration.primaryLabel,
                key = key,
            )
            "tabs" -> Tabs(
                labels = listOf(registration.primaryLabel) + registration.additionalLabels,
                selectedIndex = 0,
                onSelected = {},
                states = states,
                key = key,
            )
            "segmented" -> SegmentedControl(
                labels = listOf(registration.primaryLabel) + registration.additionalLabels,
                selectedIndex = 0,
                onSelected = {},
                states = states,
                key = key,
            )
            "navigationBar" -> NavigationBar(
                destinations = matrixNavigationDestinations(registration),
                selectedId = MATRIX_NAVIGATION_PRIMARY_ID,
                onSelected = {},
                states = states,
                semanticLabel = "MATRIX NAVIGATION BAR",
                key = key,
            )
            "navigationRail" -> NavigationRail(
                destinations = matrixNavigationDestinations(registration),
                selectedId = MATRIX_NAVIGATION_PRIMARY_ID,
                onSelected = {},
                states = states,
                semanticLabel = "MATRIX NAVIGATION RAIL",
                key = key,
            )
            "valueAdjuster" -> ValueAdjuster(
                valueText = "1",
                onDecrease = {},
                onIncrease = {},
                states = states,
                label = registration.primaryLabel,
                key = key,
            )
            "menu" -> Menu(
                items = listOf(
                    PixelMenuItem(
                        label = registration.additionalLabels.single(),
                        onSelected = {},
                    ),
                ),
                states = states,
                semanticLabel = registration.primaryLabel,
                onDismissRequest = {},
                modal = false,
                key = key,
            )
            "dropdown" -> Dropdown(
                label = registration.primaryLabel,
                selectedText = "VALUE",
                expanded = false,
                onToggle = {},
                items = listOf(PixelMenuItem(label = "DROPDOWN ITEM", onSelected = {})),
                states = states,
                semanticLabel = registration.primaryLabel,
                key = key,
            )
            "slidable" -> Slidable(
                child = Text("SLIDABLE BODY"),
                states = states,
                onTap = {},
                semanticLabel = registration.primaryLabel,
                key = key,
            )
            "dialog" -> Dialog(
                content = Text("DIALOG BODY"),
                states = states,
                semanticLabel = registration.primaryLabel,
                onDismissRequest = {},
                modal = false,
                key = key,
            )
            "bottomSheet" -> BottomSheet(
                content = Text("BOTTOM SHEET BODY"),
                states = states,
                semanticLabel = registration.primaryLabel,
                onDismissRequest = {},
                modal = false,
                key = key,
            )
            "toast" -> Toast(
                message = registration.primaryLabel,
                states = states,
                key = key,
            )
            "snackbar" -> Snackbar(
                message = registration.primaryLabel,
                action = TextButton(
                    text = registration.additionalLabels.single(),
                    onPressed = {},
                    key = "$key-action",
                ),
                states = states,
                key = key,
            )
            "tooltip" -> Tooltip(
                message = "TOOLTIP BODY",
                visible = true,
                child = SizedBox(width = 12, height = 8),
                states = states,
                semanticLabel = registration.primaryLabel,
                key = key,
            )
            "progress" -> ProgressBar(
                progress = 0.5f,
                states = states,
                width = 48,
                height = 8,
                key = key,
            )
            "refresh" -> {
                /** Controller driving the real public refresh lifecycle. */
                val controller = PixelRefreshIndicatorController()
                /** Visible below-threshold pull state exposing every state-specific progress color. */
                val refreshState = controller.create().also { controlledState ->
                    controller.startPull(controlledState)
                    controller.updatePull(controlledState, distancePx = 5f, thresholdPx = 20)
                }
                RefreshIndicator(
                    child = SizedBox(width = 64, height = 24),
                    state = refreshState,
                    controller = controller,
                    states = states,
                    onRefresh = {},
                    thresholdPx = 20,
                    semanticLabel = registration.primaryLabel,
                    key = key,
                )
            }
            "scrollbar" -> {
                /** Controller paired with the overflowing production list viewport. */
                val controller = PixelListController()
                /** Controlled list state shared by the viewport and public Scrollbar. */
                val listState = controller.create()
                /** Overflowing real list required to produce a proportional thumb. */
                val list = ListViewBuilder(
                    itemCount = 20,
                    itemBuilder = { index -> SizedBox(height = 8, child = Text("ROW $index")) },
                    itemExtent = 8,
                    state = listState,
                    controller = controller,
                    key = "$key-list",
                )
                Scrollbar(
                    child = list,
                    state = listState,
                    states = states,
                    semanticLabel = registration.primaryLabel,
                    key = key,
                )
            }
            else -> error("Unregistered production component family: ${registration.family}")
        }
    }

    /** Creates two stable-id destinations whose semantic labels belong to [registration]. */
    private fun matrixNavigationDestinations(
        registration: ComponentRegistration,
    ): List<PixelNavigationDestination> {
        /** Second label required to expose both selected and unselected navigation item states. */
        val secondaryLabel = registration.additionalLabels.single()
        return listOf(
            PixelNavigationDestination(
                id = MATRIX_NAVIGATION_PRIMARY_ID,
                label = registration.primaryLabel,
                icon = MATRIX_ICON,
            ),
            PixelNavigationDestination(
                id = MATRIX_NAVIGATION_SECONDARY_ID,
                label = secondaryLabel,
                icon = MATRIX_ICON,
            ),
        )
    }

    /** Creates the canonical singleton set for [state], retaining Normal as the empty mask. */
    private fun stateSet(state: PixelControlState): PixelControlStateSet {
        return if (state == PixelControlState.Normal) {
            PixelControlStateSet.Normal
        } else {
            PixelControlStateSet.of(state)
        }
    }

    /** Creates one token family whose container, content, and border expose the same state color. */
    private fun matrixComponentTokens(): PixelComponentColorTokens {
        /** Eight-way role property reused by every independently painted component channel. */
        val colors = PixelStateMap<PixelColorRole?>(
            normal = PixelColorRole.Surface,
            PixelControlState.Hovered to PixelColorRole.SurfaceVariant,
            PixelControlState.Pressed to PixelColorRole.Selection,
            PixelControlState.Focused to PixelColorRole.Focus,
            PixelControlState.Selected to PixelColorRole.Inactive,
            PixelControlState.Disabled to PixelColorRole.Disabled,
            PixelControlState.Error to PixelColorRole.Danger,
            PixelControlState.Loading to PixelColorRole.Warning,
        )
        return PixelComponentColorTokens(
            containerColor = colors,
            contentColor = colors,
            borderColor = colors,
            focusIndicator = PixelFocusIndicatorTokens(colorRole = PixelColorRole.Focus),
            padding = EdgeInsets.all(2),
            minimumWidth = 18,
            minimumHeight = 10,
            borderWidth = 1,
            cornerRadius = 0,
        )
    }

    /** Creates one component family whose three color channels have independent exact sentinels. */
    private fun independentChannelTokens(): PixelComponentColorTokens {
        return PixelComponentColorTokens(
            containerColor = PixelStateMap(normal = PixelColorRole.SurfaceVariant),
            contentColor = PixelStateMap(normal = PixelColorRole.Danger),
            borderColor = PixelStateMap(normal = PixelColorRole.Warning),
            focusIndicator = PixelFocusIndicatorTokens(colorRole = PixelColorRole.Focus),
            padding = EdgeInsets.all(2),
            minimumWidth = 18,
            minimumHeight = 10,
            borderWidth = 1,
            cornerRadius = 0,
        )
    }

    /** Copies [tokens] into all 25 canonical component-family properties. */
    private fun componentGraph(tokens: PixelComponentColorTokens): PixelComponentTokens {
        return PixelComponentTokens.Default.copy(
            button = tokens,
            textButton = tokens,
            iconButton = tokens,
            textField = tokens,
            listTile = tokens,
            checkbox = tokens,
            radio = tokens,
            switch = tokens,
            slider = tokens,
            tabs = tokens,
            segmented = tokens,
            navigationBar = tokens,
            navigationRail = tokens,
            valueAdjuster = tokens,
            menu = tokens,
            dropdown = tokens,
            slidable = tokens,
            dialog = tokens,
            bottomSheet = tokens,
            toast = tokens,
            snackbar = tokens,
            tooltip = tokens,
            progress = tokens,
            refresh = tokens,
            scrollbar = tokens,
        )
    }

    /** Creates a theme proving independent component channels reach real rendered pixels. */
    private fun independentChannelTheme(): PixelThemeTokens {
        /** Scheme isolating channel sentinels from all incidental foundation text colors. */
        val colors = PixelColorScheme.Dark.copy(
            surfaceVariant = INDEPENDENT_CONTAINER_COLOR,
            danger = INDEPENDENT_CONTENT_COLOR,
            warning = INDEPENDENT_BORDER_COLOR,
        )
        return PixelThemeTokens.Default.copy(
            colors = colors,
            components = componentGraph(independentChannelTokens()),
        )
    }

    /** Creates a complete theme assigning the observable token family to all 25 registrations. */
    private fun matrixTheme(): PixelThemeTokens {
        /** Shared state-role and geometry tokens installed into every production family. */
        val matrixTokens = matrixComponentTokens()
        /** Concrete scheme whose eight matrix roles are pairwise distinct exact colors. */
        val colors = PixelColorScheme.Dark.copy(
            surface = NORMAL_COLOR,
            onSurface = NORMAL_COLOR,
            onBackground = NORMAL_COLOR,
            outline = NORMAL_COLOR,
            outlineVariant = NORMAL_COLOR,
            track = NORMAL_COLOR,
            surfaceVariant = HOVERED_COLOR,
            onSurfaceVariant = HOVERED_COLOR,
            primary = HOVERED_COLOR,
            selection = PRESSED_COLOR,
            focus = FOCUSED_COLOR,
            inactive = SELECTED_COLOR,
            disabled = DISABLED_COLOR,
            onDisabled = DISABLED_COLOR,
            danger = ERROR_COLOR,
            onDanger = ERROR_COLOR,
            warning = LOADING_COLOR,
            onWarning = LOADING_COLOR,
        )
        /** Component graph assigning the same state matrix to every canonical family. */
        val components = componentGraph(matrixTokens)
        /** Labels shared with registry lookup and structured Loading/Error semantics. */
        val labels = PixelLabelTokens.Default.copy(
            error = "MATRIX ERROR",
            loading = "MATRIX LOADING",
            decrease = "MATRIX DECREASE",
            increase = "MATRIX INCREASE",
            snackbar = "MATRIX SNACKBAR",
            progress = "MATRIX PROGRESS",
        )
        return PixelThemeTokens.Default.copy(
            colors = colors,
            components = components,
            labels = labels,
        )
    }

    /** Registry row binding an exact token-family id to real semantics and focus capability. */
    private data class ComponentRegistration(
        /** Exact [PixelComponentTokens] property name. */
        val family: String,
        /** Whether the Focused row must traverse to a real mounted focus node. */
        val focusable: Boolean,
        /** Primary semantic label emitted by the public production factory. */
        val primaryLabel: String,
        /** Additional independently actionable or focusable semantic labels. */
        val additionalLabels: List<String> = emptyList(),
        /** Actions that prove the Normal component is genuinely interactive. */
        val normalActions: Set<PixelSemanticsAction> = emptySet(),
    ) {
        /** Exact labels accepted when isolating this component from incidental descendant text. */
        val observableLabels: Set<String>
            get() = (listOf(primaryLabel) + additionalLabels).toSet()
    }

    /** One of the eight canonical control states and its independently colored semantic role. */
    private data class StateRow(
        /** Caller state supplied to the state-aware public factory. */
        val state: PixelControlState,
        /** Theme role containing this row's exact expected color. */
        val role: PixelColorRole,
    )

    private companion object {
        /** Logical width large enough for every inline and lifted production component. */
        const val MATRIX_WIDTH: Int = 160

        /** Logical height large enough for menus, safe overlays, and overflowing scroll content. */
        const val MATRIX_HEIGHT: Int = 96

        /** Normal-state exact color. */
        val NORMAL_COLOR: PixelColor = PixelColor.fromRgb(23, 37, 53)

        /** Hovered-state exact color. */
        val HOVERED_COLOR: PixelColor = PixelColor.fromRgb(43, 197, 97)

        /** Pressed-state exact color. */
        val PRESSED_COLOR: PixelColor = PixelColor.fromRgb(239, 211, 31)

        /** Focused-state exact color. */
        val FOCUSED_COLOR: PixelColor = PixelColor.fromRgb(29, 211, 227)

        /** Selected-state exact color. */
        val SELECTED_COLOR: PixelColor = PixelColor.fromRgb(137, 89, 223)

        /** Disabled-state exact color. */
        val DISABLED_COLOR: PixelColor = PixelColor.fromRgb(103, 109, 117)

        /** Error-state exact color. */
        val ERROR_COLOR: PixelColor = PixelColor.fromRgb(227, 47, 61)

        /** Loading-state exact color. */
        val LOADING_COLOR: PixelColor = PixelColor.fromRgb(241, 151, 29)

        /** Independent container-channel sentinel. */
        val INDEPENDENT_CONTAINER_COLOR: PixelColor = PixelColor.fromRgb(17, 83, 149)

        /** Independent content-channel sentinel. */
        val INDEPENDENT_CONTENT_COLOR: PixelColor = PixelColor.fromRgb(211, 53, 127)

        /** Independent border-channel sentinel. */
        val INDEPENDENT_BORDER_COLOR: PixelColor = PixelColor.fromRgb(239, 173, 41)

        /** Ordered channel sentinels inspected in actual production-component pixels. */
        val INDEPENDENT_CHANNEL_COLORS: List<PixelColor> = listOf(
            INDEPENDENT_CONTAINER_COLOR,
            INDEPENDENT_CONTENT_COLOR,
            INDEPENDENT_BORDER_COLOR,
        )

        /** Minimum dynamic evidence matching the static two-color-channel coverage contract. */
        const val MINIMUM_OBSERVABLE_COLOR_CHANNELS: Int = 2

        /** Canonical token-family order required by the 1.0 component inventory. */
        val EXPECTED_COMPONENT_FAMILIES: List<String> = listOf(
            "button",
            "textButton",
            "iconButton",
            "textField",
            "listTile",
            "checkbox",
            "radio",
            "switch",
            "slider",
            "tabs",
            "segmented",
            "navigationBar",
            "navigationRail",
            "valueAdjuster",
            "menu",
            "dropdown",
            "slidable",
            "dialog",
            "bottomSheet",
            "toast",
            "snackbar",
            "tooltip",
            "progress",
            "refresh",
            "scrollbar",
        )

        /** Explicit production registry; every row maps to a same-named public component family. */
        val COMPONENT_REGISTRY: List<ComponentRegistration> = listOf(
            ComponentRegistration("button", true, "MATRIX BUTTON", normalActions = setOf(PixelSemanticsAction.CLICK)),
            ComponentRegistration("textButton", true, "MATRIX TEXT BUTTON", normalActions = setOf(PixelSemanticsAction.CLICK)),
            ComponentRegistration(
                "iconButton",
                true,
                "MATRIX ICON BUTTON",
                normalActions = setOf(PixelSemanticsAction.CLICK),
            ),
            ComponentRegistration(
                "textField",
                true,
                "MATRIX TEXT FIELD",
                normalActions = setOf(
                    PixelSemanticsAction.CLICK,
                    PixelSemanticsAction.SET_TEXT,
                    PixelSemanticsAction.SET_SELECTION,
                ),
            ),
            ComponentRegistration("listTile", true, "MATRIX LIST TILE", normalActions = setOf(PixelSemanticsAction.CLICK)),
            ComponentRegistration("checkbox", true, "MATRIX CHECKBOX", normalActions = setOf(PixelSemanticsAction.CLICK)),
            ComponentRegistration("radio", true, "MATRIX RADIO", normalActions = setOf(PixelSemanticsAction.CLICK)),
            ComponentRegistration("switch", true, "MATRIX SWITCH", normalActions = setOf(PixelSemanticsAction.CLICK)),
            ComponentRegistration("slider", true, "MATRIX SLIDER", normalActions = setOf(PixelSemanticsAction.SET_PROGRESS)),
            ComponentRegistration(
                "tabs",
                true,
                "MATRIX TAB A",
                additionalLabels = listOf("MATRIX TAB B"),
                normalActions = setOf(PixelSemanticsAction.CLICK),
            ),
            ComponentRegistration(
                "segmented",
                true,
                "MATRIX SEGMENT A",
                additionalLabels = listOf("MATRIX SEGMENT B"),
                normalActions = setOf(PixelSemanticsAction.CLICK),
            ),
            ComponentRegistration(
                "navigationBar",
                true,
                "MATRIX NAV BAR A",
                additionalLabels = listOf("MATRIX NAV BAR B"),
                normalActions = setOf(PixelSemanticsAction.CLICK),
            ),
            ComponentRegistration(
                "navigationRail",
                true,
                "MATRIX NAV RAIL A",
                additionalLabels = listOf("MATRIX NAV RAIL B"),
                normalActions = setOf(PixelSemanticsAction.CLICK),
            ),
            ComponentRegistration(
                "valueAdjuster",
                true,
                "MATRIX ADJUSTER",
                additionalLabels = listOf("MATRIX DECREASE", "MATRIX INCREASE"),
                normalActions = setOf(PixelSemanticsAction.CLICK),
            ),
            ComponentRegistration(
                "menu",
                true,
                "MATRIX MENU",
                additionalLabels = listOf("MATRIX MENU ITEM"),
                normalActions = setOf(PixelSemanticsAction.CLICK, PixelSemanticsAction.DISMISS),
            ),
            ComponentRegistration(
                "dropdown",
                true,
                "MATRIX DROPDOWN",
                normalActions = setOf(PixelSemanticsAction.CLICK, PixelSemanticsAction.EXPAND),
            ),
            ComponentRegistration("slidable", true, "MATRIX SLIDABLE", normalActions = setOf(PixelSemanticsAction.CLICK)),
            ComponentRegistration("dialog", false, "MATRIX DIALOG", normalActions = setOf(PixelSemanticsAction.DISMISS)),
            ComponentRegistration(
                "bottomSheet",
                false,
                "MATRIX BOTTOM SHEET",
                normalActions = setOf(PixelSemanticsAction.DISMISS),
            ),
            ComponentRegistration("toast", false, "MATRIX TOAST"),
            ComponentRegistration(
                "snackbar",
                false,
                "MATRIX SNACKBAR",
                additionalLabels = listOf("MATRIX SNACKBAR ACTION"),
                normalActions = setOf(PixelSemanticsAction.CLICK),
            ),
            ComponentRegistration("tooltip", false, "MATRIX TOOLTIP"),
            ComponentRegistration("progress", false, "MATRIX PROGRESS"),
            ComponentRegistration("refresh", true, "MATRIX REFRESH", normalActions = setOf(PixelSemanticsAction.CLICK)),
            ComponentRegistration("scrollbar", false, "MATRIX SCROLLBAR"),
        )

        /** Every documented control state paired with its unique matrix role. */
        val STATE_ROWS: List<StateRow> = listOf(
            StateRow(PixelControlState.Normal, PixelColorRole.Surface),
            StateRow(PixelControlState.Hovered, PixelColorRole.SurfaceVariant),
            StateRow(PixelControlState.Pressed, PixelColorRole.Selection),
            StateRow(PixelControlState.Focused, PixelColorRole.Focus),
            StateRow(PixelControlState.Selected, PixelColorRole.Inactive),
            StateRow(PixelControlState.Disabled, PixelColorRole.Disabled),
            StateRow(PixelControlState.Error, PixelColorRole.Danger),
            StateRow(PixelControlState.Loading, PixelColorRole.Warning),
        )

        /** Stable business id for the selected navigation destination in every matrix cell. */
        const val MATRIX_NAVIGATION_PRIMARY_ID: String = "matrix-primary"

        /** Stable business id for the unselected navigation destination in every matrix cell. */
        const val MATRIX_NAVIGATION_SECONDARY_ID: String = "matrix-secondary"

        /** Opaque alpha-mask bitmap reused by icon-bearing component factories. */
        val MATRIX_ICON: PixelIconData = PixelIconData(
            bitmap = PixelBitmap(
                width = 3,
                height = 3,
                pixels = IntArray(9) { PixelColor.White.argb },
            ),
        )
    }
}
