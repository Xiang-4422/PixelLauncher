package com.purride.pixelui.widgets

import com.purride.pixelui.ActivityIndicator
import com.purride.pixelui.BottomSheet
import com.purride.pixelui.Checkbox
import com.purride.pixelui.ConfirmDialog
import com.purride.pixelui.Dialog
import com.purride.pixelui.Dropdown
import com.purride.pixelui.EmptyState
import com.purride.pixelui.ListTile
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.Menu
import com.purride.pixelui.ModalBarrier
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelSizeTokens
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slider
import com.purride.pixelui.Slidable
import com.purride.pixelui.Snackbar
import com.purride.pixelui.Stack
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextField
import com.purride.pixelui.Toast
import com.purride.pixelui.Tooltip
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Production behavior coverage for foundation labels and icon-size tokens. */
class ThemeFoundationProductionBehaviorTest {
    /** Interactive controls expose every omitted semantic label through the mounted theme. */
    @Test
    fun interactiveControlsResolveEveryThemeLabelFallback() {
        /** Complete theme graph carrying the unique localization sentinels. */
        val tokens = PixelThemeTokens.Default.copy(labels = ProductionLabels)
        /** Text input controller retained while its themed fallback is observed. */
        val textController = PixelTextFieldController()
        /** Empty text input state that cannot provide a content-derived accessibility name. */
        val textState = textController.create()
        /** List controller retained while the themed Scrollbar is rendered. */
        val listController = PixelListController()
        /** Overflowing list state shared by the viewport and Scrollbar. */
        val listState = listController.create()
        /** Refresh controller retained while its themed action boundary is rendered. */
        val refreshController = PixelRefreshIndicatorController()
        /** Idle refresh state that still exports the localized action label. */
        val refreshState = refreshController.create()
        /** Off-screen production runtime used for every semantic observation. */
        val tester = PixelTester()
        try {
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.button),
                child = OutlinedButton(
                    text = "",
                    onPressed = {},
                    states = PixelControlStateSet.Normal,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.textButton),
                child = TextButton(
                    text = "",
                    onPressed = {},
                    states = PixelControlStateSet.Normal,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.textField),
                child = TextField(
                    state = textState,
                    controller = textController,
                    states = PixelControlStateSet.Normal,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.listTile),
                child = ListTile(
                    title = Text("ROW"),
                    states = PixelControlStateSet.Normal,
                    onTap = {},
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.checkbox),
                child = Checkbox(
                    checked = false,
                    onChanged = {},
                    states = PixelControlStateSet.Normal,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.switch),
                child = Switch(
                    checked = false,
                    onChanged = {},
                    states = PixelControlStateSet.Normal,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.slider),
                child = Slider(
                    value = 0.5f,
                    states = PixelControlStateSet.Normal,
                    onDrag = {},
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.tabs),
                child = Tabs(
                    labels = listOf("TAB"),
                    selectedIndex = 0,
                    onSelected = {},
                    states = PixelControlStateSet.Normal,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.segmentedControl),
                child = SegmentedControl(
                    labels = listOf("SEGMENT"),
                    selectedIndex = 0,
                    onSelected = {},
                    states = PixelControlStateSet.Normal,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(
                    ProductionLabels.valueAdjuster,
                    ProductionLabels.decrease,
                    ProductionLabels.increase,
                ),
                child = ValueAdjuster(
                    valueText = "1",
                    onDecrease = {},
                    onIncrease = {},
                    states = PixelControlStateSet.Normal,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.refresh),
                child = RefreshIndicator(
                    child = Text("REFRESH BODY"),
                    state = refreshState,
                    controller = refreshController,
                    states = PixelControlStateSet.Normal,
                    onRefresh = {},
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.scrollbar),
                child = Scrollbar(
                    child = ListViewBuilder(
                        itemCount = 20,
                        itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                        itemExtent = 6,
                        state = listState,
                        controller = listController,
                    ),
                    state = listState,
                    states = PixelControlStateSet.Normal,
                ),
                logicalHeight = 18,
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.slidable),
                child = Slidable(
                    child = SizedBox(width = 40, height = 10, child = Text("SLIDABLE BODY")),
                    states = PixelControlStateSet.Normal,
                    onTap = {},
                ),
            )
        } finally {
            tester.dispose()
        }
    }

    /** Overlay, notification, status, and progress components expose the remaining label fields. */
    @Test
    fun overlaysAndStatusesResolveEveryThemeLabelFallback() {
        /** Complete theme graph carrying the unique localization sentinels. */
        val tokens = PixelThemeTokens.Default.copy(labels = ProductionLabels)
        /** Off-screen production runtime used for overlay and status semantics. */
        val tester = PixelTester()
        try {
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.confirm, ProductionLabels.cancel),
                child = ConfirmDialog(
                    title = "CONFIRM HOST",
                    message = "",
                    onConfirm = {},
                    states = PixelControlStateSet.Normal,
                    onCancel = {},
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.dismiss),
                child = Stack(
                    children = listOf(
                        ModalBarrier(
                            states = PixelControlStateSet.Normal,
                            dismissible = true,
                            onDismiss = {},
                        ),
                    ),
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.empty),
                child = EmptyState(states = PixelControlStateSet.Normal),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.menu),
                child = Menu(
                    items = listOf(PixelMenuItem(label = "ITEM", onSelected = {})),
                    states = PixelControlStateSet.Normal,
                    modal = false,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.dropdown),
                child = Dropdown(
                    label = "",
                    selectedText = "",
                    expanded = false,
                    onToggle = {},
                    items = emptyList(),
                    states = PixelControlStateSet.Normal,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.dialog),
                child = Dialog(
                    content = Text("DIALOG BODY"),
                    states = PixelControlStateSet.Normal,
                    modal = false,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.bottomSheet),
                child = BottomSheet(
                    content = Text("SHEET BODY"),
                    states = PixelControlStateSet.Normal,
                    modal = false,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.toast),
                child = Toast(message = "", states = PixelControlStateSet.Normal),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.snackbar),
                child = Snackbar(message = "", states = PixelControlStateSet.Normal),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.tooltip),
                child = Tooltip(
                    message = "",
                    visible = true,
                    child = SizedBox(width = 12, height = 12),
                    states = PixelControlStateSet.Normal,
                ),
            )
            assertSemanticLabels(
                tester = tester,
                tokens = tokens,
                expectedLabels = listOf(ProductionLabels.progress),
                child = ActivityIndicator(states = PixelControlStateSet.of(PixelControlState.Loading)),
            )

            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = tokens,
                    child = Toast(
                        message = "STATUS HOST",
                        states = PixelControlStateSet.of(
                            PixelControlState.Loading,
                            PixelControlState.Error,
                        ),
                    ),
                ),
                logicalWidth = 128,
                logicalHeight = 80,
            )
            /** Toast node carrying independent Loading and Error announcements. */
            val statusNode = tester.semanticsNodesByLabel("STATUS HOST").single()
            assertEquals(ProductionLabels.loading, statusNode.value)
            assertEquals(ProductionLabels.error, statusNode.error)
        } finally {
            tester.dispose()
        }
    }

    /** ActivityIndicator maps iconMedium to dot extent and iconLarge to its width envelope. */
    @Test
    fun activityIndicatorConsumesMediumAndLargeIconSizesInRenderedGeometry() {
        /** Runtime used to inspect production semantic bounds after layout. */
        val tester = PixelTester()
        try {
            /** Size scale where medium-icon dots exceed the deliberately tiny large-icon floor. */
            val mediumDominantSizes = PixelSizeTokens.Default.copy(iconMedium = 20, iconLarge = 1)
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(
                        labels = ProductionLabels,
                        sizes = mediumDominantSizes,
                    ),
                    child = ActivityIndicator(
                        states = PixelControlStateSet.of(PixelControlState.Loading),
                    ),
                ),
                logicalWidth = 64,
                logicalHeight = 16,
            )
            /** Indicator bounds derived from four five-pixel dots and three one-pixel gaps. */
            val mediumDominantNode = tester.semanticsNodesByLabel(ProductionLabels.progress).single()
            assertEquals(23, mediumDominantNode.width)
            assertEquals(5, mediumDominantNode.height)

            /** Size scale where the large-icon envelope exceeds the natural four-dot row. */
            val largeDominantSizes = PixelSizeTokens.Default.copy(iconMedium = 4, iconLarge = 29)
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(
                        labels = ProductionLabels,
                        sizes = largeDominantSizes,
                    ),
                    child = ActivityIndicator(
                        states = PixelControlStateSet.of(PixelControlState.Loading),
                    ),
                ),
                logicalWidth = 64,
                logicalHeight = 16,
            )
            /** Indicator bounds retaining one-pixel dots inside the large-icon width floor. */
            val largeDominantNode = tester.semanticsNodesByLabel(ProductionLabels.progress).single()
            assertEquals(29, largeDominantNode.width)
            assertEquals(1, largeDominantNode.height)
        } finally {
            tester.dispose()
        }
    }

    /**
     * Pumps one real component under [tokens] and verifies all expected semantic labels.
     *
     * @param tester Reused off-screen production runtime.
     * @param tokens Complete theme graph mounted above [child].
     * @param expectedLabels Unique label sentinels that the component must export.
     * @param child Production component whose final semantic tree is inspected.
     * @param logicalWidth Logical test viewport width.
     * @param logicalHeight Logical test viewport height.
     */
    private fun assertSemanticLabels(
        tester: PixelTester,
        tokens: PixelThemeTokens,
        expectedLabels: List<String>,
        child: Widget,
        logicalWidth: Int = 128,
        logicalHeight: Int = 80,
    ) {
        tester.pumpWidget(
            widget = PixelTheme(tokens = tokens, child = child),
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
        expectedLabels.forEach { expectedLabel ->
            assertTrue(
                "Expected production semantics to contain theme label '$expectedLabel'",
                tester.semanticsNodesByLabel(expectedLabel).isNotEmpty(),
            )
        }
    }

    private companion object {
        /** Unique localization sentinels covering every public label-token field. */
        val ProductionLabels: PixelLabelTokens = PixelLabelTokens(
            confirm = "TOKEN CONFIRM",
            cancel = "TOKEN CANCEL",
            dismiss = "TOKEN DISMISS",
            empty = "TOKEN EMPTY",
            error = "TOKEN ERROR",
            loading = "TOKEN LOADING",
            button = "TOKEN BUTTON",
            textButton = "TOKEN TEXT BUTTON",
            textField = "TOKEN TEXT FIELD",
            listTile = "TOKEN LIST TILE",
            checkbox = "TOKEN CHECKBOX",
            switch = "TOKEN SWITCH",
            slider = "TOKEN SLIDER",
            tabs = "TOKEN TABS",
            segmentedControl = "TOKEN SEGMENTED CONTROL",
            valueAdjuster = "TOKEN VALUE ADJUSTER",
            decrease = "TOKEN DECREASE",
            increase = "TOKEN INCREASE",
            menu = "TOKEN MENU",
            dropdown = "TOKEN DROPDOWN",
            dialog = "TOKEN DIALOG",
            bottomSheet = "TOKEN BOTTOM SHEET",
            toast = "TOKEN TOAST",
            snackbar = "TOKEN SNACKBAR",
            tooltip = "TOKEN TOOLTIP",
            progress = "TOKEN PROGRESS",
            refresh = "TOKEN REFRESH",
            scrollbar = "TOKEN SCROLLBAR",
            slidable = "TOKEN SLIDABLE",
        )
    }
}
