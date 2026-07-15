package com.purride.pixelui.regression

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.FormFieldDecoration
import com.purride.pixelui.IconButton
import com.purride.pixelui.NavigationBar
import com.purride.pixelui.NavigationRail
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelNavigationDestination
import com.purride.pixelui.PixelRadioOption
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.Radio
import com.purride.pixelui.RadioGroup
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.Slidable
import com.purride.pixelui.SlidableAction
import com.purride.pixelui.SlidableActionPane
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reviewed M5-2 snapshot for the public high-value component factories.
 *
 * The fixture complements the 25-by-8 state matrix: it records exact semantic hierarchy,
 * geometry, collection metadata, structured state, and actions without duplicating every state
 * cell. Raw runtime ids are canonicalized to scene-local indexes so the snapshot remains focused
 * on public contracts instead of allocator details.
 */
class M52HighValueComponentSnapshotTest {

    /** One immutable scene whose public widget factory is mounted in a fresh runtime. */
    private data class SnapshotScene(
        /** Stable heading written to the reviewed resource. */
        val name: String,
        /** Fixed logical viewport width used for semantic geometry. */
        val width: Int,
        /** Fixed logical viewport height used for semantic geometry. */
        val height: Int,
        /** Public-factory widget builder invoked exactly once per snapshot capture. */
        val build: () -> Widget,
    )

    /** All new controls and hardened selection semantics match the checked-in review artifact. */
    @Test
    fun publicFactoriesMatchReviewedSemanticsSnapshot() {
        /** Deterministic sections captured from independent runtimes in fixed product order. */
        val sections = standardScenes().map(::captureScene) + captureSlidableStates()
        /** Complete canonical snapshot compared byte-for-byte with the reviewed resource. */
        val actual = sections.joinToString(separator = "\n")
        ReviewedGoldenVerifier.assertMatches(
            baselineFile = File(SNAPSHOT_PATH),
            actual = actual,
            reportStem = File(REPORT_DIR_PATH, "m5-2-high-value-component-semantics"),
        )
    }

    /** New visual factories match one source-controlled, human-readable ASCII pixel golden. */
    @Test
    fun newPublicFactoriesMatchReviewedPixelGolden() {
        /** Exact pixel sections rendered from compact, fixed-size versions of the public scenes. */
        val sections = pixelGoldenScenes().map(::capturePixelScene)
        /** Complete visual artifact whose every changed character requires explicit review. */
        val actual = sections.joinToString(separator = "\n")
        ReviewedGoldenVerifier.assertMatches(
            baselineFile = File(PIXEL_GOLDEN_PATH),
            actual = actual,
            reportStem = File(REPORT_DIR_PATH, "m5-2-high-value-component-pixels"),
        )
    }

    /** Builds the five static public-factory scenes covered by the reviewed snapshot. */
    private fun standardScenes(): List<SnapshotScene> {
        return listOf(
            SnapshotScene(name = "selection-controls", width = 112, height = 96) {
                Column(
                    children = listOf(
                        Radio(
                            selected = true,
                            onSelected = {},
                            semanticLabel = "Standalone favorite",
                            key = "snapshot-radio",
                        ),
                        RadioGroup(
                            options = listOf(
                                PixelRadioOption(id = "standard", label = "Standard"),
                                PixelRadioOption(id = "priority", label = "Priority"),
                                PixelRadioOption(id = "paused", label = "Paused", enabled = false),
                            ),
                            selectedId = "priority",
                            onSelected = {},
                            semanticLabel = "Delivery mode",
                            key = "snapshot-radio-group",
                        ),
                        IconButton(
                            icon = snapshotIcon(),
                            onPressed = {},
                            semanticLabel = "Save item",
                            selected = true,
                            key = "snapshot-icon-button",
                        ),
                    ),
                    spacing = 2,
                    crossAxisAlignment = CrossAxisAlignment.START,
                )
            },
            SnapshotScene(name = "decorated-text-field", width = 112, height = 52) {
                /** Controlled owner retained by the decorated TextField public factory. */
                val controller = PixelTextFieldController()
                /** Non-empty value producing stable value and selection semantics. */
                val state = controller.create(
                    initialText = "Ada",
                    selectionStart = 1,
                    selectionEnd = 3,
                )
                TextField(
                    state = state,
                    controller = controller,
                    decoration = FormFieldDecoration(
                        label = "Account",
                        helper = "Hidden helper",
                        error = "Account is invalid",
                        required = true,
                        counter = "3/20",
                    ),
                    semanticHint = "Profile identity",
                    key = "snapshot-decorated-field",
                )
            },
            SnapshotScene(name = "navigation-bar", width = 120, height = 44) {
                NavigationBar(
                    destinations = snapshotDestinations(),
                    selectedId = "settings",
                    onSelected = {},
                    semanticLabel = "Primary destinations",
                    key = "snapshot-navigation-bar",
                )
            },
            SnapshotScene(name = "navigation-rail", width = 72, height = 104) {
                NavigationRail(
                    destinations = snapshotDestinations(),
                    selectedId = "settings",
                    onSelected = {},
                    semanticLabel = "Primary rail",
                    key = "snapshot-navigation-rail",
                )
            },
            SnapshotScene(name = "single-selection-collections", width = 112, height = 56) {
                Column(
                    children = listOf(
                        Tabs(
                            labels = listOf("Recent", "Pinned", "Shared"),
                            selectedIndex = 1,
                            onSelected = {},
                            key = "snapshot-tabs",
                        ),
                        SegmentedControl(
                            labels = listOf("Day", "Week", "Month"),
                            selectedIndex = 2,
                            onSelected = {},
                            key = "snapshot-segmented",
                        ),
                    ),
                    spacing = 3,
                    crossAxisAlignment = CrossAxisAlignment.START,
                )
            },
        )
    }

    /** Reuses the new public-factory fixtures with compact viewports suited to ASCII review. */
    private fun pixelGoldenScenes(): List<SnapshotScene> {
        /** Canonical semantic scenes whose first four entries are the newly added visual families. */
        val scenes = standardScenes()
        return listOf(
            scenes[0].copy(width = 64, height = 68),
            scenes[1].copy(width = 72, height = 40),
            scenes[2].copy(width = 96, height = 32),
            scenes[3].copy(width = 48, height = 32),
        )
    }

    /** Captures a static scene and always disposes its off-screen retained runtime. */
    private fun captureScene(scene: SnapshotScene): String {
        /** Isolated runtime preventing semantic ids or focus state leaking between sections. */
        val tester = PixelTester()
        return try {
            tester.pumpWidget(scene.build(), logicalWidth = scene.width, logicalHeight = scene.height)
            formatScene(name = scene.name, tester = tester)
        } finally {
            tester.dispose()
        }
    }

    /** Renders one fixed scene to the same brightness-bucket ASCII format as EngineGoldenTest. */
    private fun capturePixelScene(scene: SnapshotScene): String {
        /** Isolated runtime preventing retained paint or motion state leaking between golden sections. */
        val tester = PixelTester()
        return try {
            tester.pumpWidget(scene.build(), logicalWidth = scene.width, logicalHeight = scene.height)
            buildString {
                append("## ").append(scene.name).append('\n')
                append(tester.dumpPixelsAsAscii())
            }
        } finally {
            tester.dispose()
        }
    }

    /** Captures both sides of Slidable's standard expand action in one retained runtime. */
    private fun captureSlidableStates(): List<String> {
        /** Runtime retained between closed and expanded snapshots to exercise the public action. */
        val tester = PixelTester()
        return try {
            tester.pumpWidget(
                widget = Slidable(
                    child = Text("MESSAGE"),
                    states = PixelControlStateSet.Normal,
                    endActionPane = SlidableActionPane(
                        children = listOf(
                            SlidableAction(
                                label = "Delete",
                                backgroundColor = PixelColor.fromRgb(160, 48, 48),
                                foregroundColor = PixelColor.White,
                                onPressed = {},
                                key = "snapshot-delete-action",
                            ),
                        ),
                        extentRatio = 0.5f,
                        dismissible = true,
                    ),
                    onDismissed = {},
                    semanticLabel = "Message actions",
                    key = "snapshot-slidable",
                ),
                logicalWidth = 72,
                logicalHeight = 20,
            )
            /** Closed row whose EXPAND capability enters the shared pane state machine. */
            val closedNode = tester.semanticsNodesByLabel("Message actions").single()
            /** Complete closed hierarchy retained before the action replaces pane descendants. */
            val closedNodes = tester.semanticsNodes()
            /** Reviewed closed state must actually expose the action that creates the second state. */
            val expanded = tester.performSemanticsAction(closedNode.id, PixelSemanticsAction.EXPAND)
            assertTrue("Slidable snapshot fixture must expand through semantics.", expanded)
            listOf(
                formatNodes(name = "slidable-closed", nodes = closedNodes),
                formatScene(name = "slidable-expanded", tester = tester),
            )
        } finally {
            tester.dispose()
        }
    }

    /** Formats all current semantic nodes from one mounted scene. */
    private fun formatScene(name: String, tester: PixelTester): String {
        return formatNodes(name = name, nodes = tester.semanticsNodes())
    }

    /**
     * Serializes public semantics into an allocator-independent, line-oriented review format.
     */
    private fun formatNodes(name: String, nodes: List<PixelSemanticsNode>): String {
        /** Runtime id to deterministic scene-local index used by parent references. */
        val indexesById = nodes.mapIndexed { index, node -> node.id to index }.toMap()
        return buildString {
            append("## ").append(name).append('\n')
            append("nodes=").append(nodes.size).append('\n')
            nodes.forEachIndexed { index, node ->
                /** Parent rendered as a canonical index or HOST for a scene root. */
                val parent = node.parentId?.let(indexesById::get)?.toString() ?: "HOST"
                append('[').append(index).append("] parent=").append(parent)
                append(" role=").append(node.role)
                append(" label=").append(quoted(node.label))
                append(" enabled=").append(node.enabled)
                append(" focused=").append(node.focused)
                append(" bounds=")
                    .append(node.left).append(',')
                    .append(node.top).append(',')
                    .append(node.width).append(',')
                    .append(node.height)
                append(" value=").append(quoted(node.value))
                append(" hint=").append(quoted(node.hint))
                append(" error=").append(quoted(node.error))
                append(" selected=").append(node.selected)
                append(" checked=").append(node.checked ?: "NA")
                append(" expanded=").append(node.expanded ?: "NA")
                append(" selection=")
                    .append(node.selectionStart)
                    .append("..")
                    .append(node.selectionEnd)
                append(" live=").append(node.liveRegion)
                append(" collection=").append(formatCollection(node))
                append(" item=").append(formatCollectionItem(node))
                append(" actions=")
                    .append(node.actions.map { action -> action.name }.sorted().joinToString(prefix = "[", postfix = "]"))
                append(" custom=")
                    .append(node.customActionLabels.toSortedMap().entries.joinToString(prefix = "[", postfix = "]") { entry ->
                        "${quoted(entry.key)}:${quoted(entry.value)}"
                    })
                append('\n')
            }
        }
    }

    /** Formats optional SINGLE-collection metadata without relying on data-class rendering. */
    private fun formatCollection(node: PixelSemanticsNode): String {
        /** Optional collection descriptor owned by the current semantic node. */
        val collection = node.collectionInfo ?: return "NA"
        return "${collection.rowCount}x${collection.columnCount}" +
            ":${collection.selectionMode}:hierarchical=${collection.hierarchical}"
    }

    /** Formats optional collection-item coordinates and selected state. */
    private fun formatCollectionItem(node: PixelSemanticsNode): String {
        /** Optional positional metadata owned by one collection descendant. */
        val item = node.collectionItemInfo ?: return "NA"
        return "${item.rowIndex},${item.columnIndex}" +
            ":span=${item.rowSpan}x${item.columnSpan}" +
            ":heading=${item.heading}:selected=${item.selected}"
    }

    /** Quotes nullable spoken text while escaping characters that would break one-line review. */
    private fun quoted(value: String?): String {
        if (value == null) return "NA"
        /** Escaped snapshot form that keeps whitespace and quotes unambiguous in diffs. */
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\"", "\\\"")
        return "\"$escaped\""
    }

    /** Creates one asymmetric bitmap so icon-bearing public factories paint deterministic shapes. */
    private fun snapshotIcon(): PixelIconData {
        /** Five-by-five opaque mask containing a small arrow-like pattern. */
        val pixels = IntArray(25) { index ->
            /** Current bitmap coordinate used to select opaque mask cells. */
            val x = index % 5
            /** Current bitmap coordinate used to select opaque mask cells. */
            val y = index / 5
            if (x == 2 || x == y) PixelColor.White.argb else PixelColor.Transparent.argb
        }
        return PixelIconData(PixelBitmap(width = 5, height = 5, pixels = pixels))
    }

    /** Returns the same stable business destinations for bar and rail snapshots. */
    private fun snapshotDestinations(): List<PixelNavigationDestination> {
        /** Shared deterministic icon bitmap used to isolate layout and semantic differences. */
        val icon = snapshotIcon()
        return listOf(
            PixelNavigationDestination(id = "home", label = "Home", icon = icon),
            PixelNavigationDestination(id = "search", label = "Search", icon = icon, enabled = false),
            PixelNavigationDestination(
                id = "settings",
                label = "Settings",
                icon = icon,
                selectedIcon = icon,
            ),
        )
    }

    private companion object {
        /** Reviewed snapshot path relative to the pixel-engine Gradle module. */
        const val SNAPSHOT_PATH: String =
            "src/test/resources/element-snapshots/m5-2-high-value-component-semantics.txt"

        /** Reviewed ASCII pixel golden path relative to the pixel-engine Gradle module. */
        const val PIXEL_GOLDEN_PATH: String =
            "src/test/resources/golden/m5-2-high-value-components.txt"

        /** 候选与审阅差异的构建报告目录。 */
        const val REPORT_DIR_PATH: String = "build/reports/golden/m5-2"
    }
}
