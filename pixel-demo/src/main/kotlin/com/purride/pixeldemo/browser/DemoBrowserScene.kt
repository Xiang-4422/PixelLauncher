package com.purride.pixeldemo.browser

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsActions
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoCategory
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.catalog.DemoTreeCatalog
import com.purride.pixeldemo.catalog.DemoTreeNode
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.settings.DemoSettingsScene

private class BrowserSessionState {
    var selectedNodeIdsByDepth: List<String> = DemoTreeCatalog.defaultSelectedPath()
    val columnStates: List<PixelListState> = List(4) { PixelListState() }
    val columnControllers: List<ScrollController> = List(4) { ScrollController() }
}

object DemoBrowserScene : DemoScene {
    override val id: String = "component_browser"
    override val title: String = "PIXEL ENGINE DEMO"
    override val summary: String = "UI/UX 展示库"
    override val isFullScreen: Boolean = true

    private val sessionState = BrowserSessionState()

    override fun build(env: DemoEnv): Widget = BrowserWidget(env, sessionState)
}

private class BrowserWidget(
    private val env: DemoEnv,
    val browserState: BrowserSessionState,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = BrowserState()

    inner class BrowserState : State<BrowserWidget>() {
        override fun build(context: BuildContext): Widget {
            val columns = DemoTreeCatalog.visibleColumns(widget.browserState.selectedNodeIdsByDepth)
            return Column(
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    header(),
                    Expanded(child = columnBrowser(columns = columns)),
                ),
            )
        }

        private fun header(): Widget =
            Padding(
                horizontal = 4,
                vertical = 3,
                child = Row(
                    children = listOf(
                        Expanded(
                            child = Column(
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    Text("PIXEL", style = TextStyle(color = Accent)),
                                    Text("UI UX", style = TextStyle(color = Muted), softWrap = false, overflow = PixelTextOverflow.ELLIPSIS),
                                ),
                            ),
                        ),
                        OutlinedButton(
                            text = "SET",
                            onPressed = { widget.env.navigator.push(DemoSettingsScene) },
                            borderColor = Accent,
                        ),
                    ),
                    spacing = 3,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                ),
            )

        private fun columnBrowser(
            columns: List<List<DemoTreeNode>>,
        ): Widget =
            Padding(
                horizontal = 1,
                vertical = 2,
                child = Row(
                    children = columns.mapIndexed { depth, nodes ->
                        if (depth == 0) {
                            categoryColumn(nodes = nodes, depth = depth)
                        } else {
                            Expanded(
                                child = contentColumn(
                                    nodes = nodes,
                                    depth = depth,
                                ),
                            )
                        }
                    },
                    spacing = 1,
                    mainAxisSize = MainAxisSize.MAX,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                ),
            )

        private fun categoryColumn(
            nodes: List<DemoTreeNode>,
            depth: Int,
        ): Widget =
            Container(
                alignment = Alignment.TOP_START,
                borderColor = Muted,
                fillColor = Panel,
                child = Padding(
                    all = 1,
                    child = Column(
                        children = nodes.map { node -> nodeRow(node = node, depth = depth) },
                        spacing = 2,
                        mainAxisSize = MainAxisSize.MIN,
                        crossAxisAlignment = CrossAxisAlignment.START,
                    ),
                ),
            )

        private fun contentColumn(
            nodes: List<DemoTreeNode>,
            depth: Int,
        ): Widget =
            Container(
                borderColor = Muted,
                fillColor = Panel,
                child = Column(
                    children = listOf(
                        Expanded(
                            child = SingleChildScrollView(
                                state = widget.browserState.columnStates.getOrElse(depth) { PixelListState() },
                                controller = widget.browserState.columnControllers.getOrElse(depth) { ScrollController() },
                                child = Padding(
                                    all = 1,
                                    child = Row(
                                        children = listOf(
                                            Expanded(
                                                child = Column(
                                                    children = nodes.map { node -> nodeRow(node = node, depth = depth) },
                                                    spacing = 2,
                                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                ),
                                            ),
                                        ),
                                        mainAxisSize = MainAxisSize.MAX,
                                        crossAxisAlignment = CrossAxisAlignment.START,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    mainAxisSize = MainAxisSize.MAX,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                ),
            )

        private fun nodeRow(
            node: DemoTreeNode,
            depth: Int,
        ): Widget {
            val selected = widget.browserState.selectedNodeIdsByDepth.getOrNull(depth) == node.id
            val color = node.category?.categoryColor() ?: Accent
            val row = nodeRowSurface(
                node = node,
                depth = depth,
                selected = selected,
                color = color,
            )
            return if (depth == 0) {
                row
            } else {
                Row(
                    children = listOf(Expanded(child = row)),
                    mainAxisSize = MainAxisSize.MAX,
                    crossAxisAlignment = CrossAxisAlignment.START,
                )
            }
        }

        private fun nodeRowSurface(
            node: DemoTreeNode,
            depth: Int,
            selected: Boolean,
            color: PixelColor,
        ): Widget {
            val rowSurface = GestureDetector(
                onTap = { selectNode(node = node, depth = depth) },
                child = Container(
                    padding = EdgeInsets.all(2),
                    borderColor = if (selected) Accent else color,
                    fillColor = if (selected) SelectedPanel else null,
                    child = Text(
                        node.shortTitle,
                        style = TextStyle(color = if (selected) Accent else PixelColor.White),
                        softWrap = true,
                        textAlign = if (depth == 0) TextAlign.START else TextAlign.CENTER,
                    ),
                ),
            )
            return Semantics(
                label = node.shortTitle,
                role = PixelSemanticRole.BUTTON,
                selected = selected,
                excludeDescendants = true,
                actions = PixelSemanticsActions(
                    onClick = {
                        selectNode(node = node, depth = depth)
                        true
                    },
                ),
                child = rowSurface,
                key = "browser-node-${node.id}",
            )
        }

        private fun selectNode(
            node: DemoTreeNode,
            depth: Int,
        ) {
            widget.browserState.selectedNodeIdsByDepth = widget.browserState.selectedNodeIdsByDepth.take(depth) + node.id
            node.scene?.let { scene ->
                widget.env.navigator.push(scene)
                return
            }
            setState {}
        }
    }
}

private val Accent = PixelColor.fromRgb(255, 176, 64)
private val Muted = PixelColor.fromRgb(140, 160, 170)
private val Panel = PixelColor.fromRgb(14, 18, 22)
private val SelectedPanel = PixelColor.fromRgb(28, 24, 12)

private fun DemoCategory.categoryColor(): PixelColor = when (id) {
    DemoCatalog.layout.id -> PixelColor.fromRgb(255, 176, 64)
    DemoCatalog.text.id -> PixelColor.fromRgb(92, 220, 255)
    DemoCatalog.input.id -> PixelColor.fromRgb(120, 180, 255)
    DemoCatalog.controls.id -> PixelColor.fromRgb(120, 245, 150)
    DemoCatalog.feedback.id -> PixelColor.fromRgb(255, 120, 160)
    DemoCatalog.theme.id -> PixelColor.fromRgb(90, 235, 210)
    DemoCatalog.scroll.id -> PixelColor.fromRgb(255, 230, 100)
    DemoCatalog.paint.id -> PixelColor.fromRgb(180, 150, 255)
    DemoCatalog.animation.id -> PixelColor.fromRgb(255, 120, 160)
    DemoCatalog.navigation.id -> PixelColor.fromRgb(120, 180, 255)
    DemoCatalog.debug.id -> PixelColor.fromRgb(255, 150, 80)
    else -> Accent
}
