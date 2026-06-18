package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.ActivityIndicator
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle

object LabShowcaseScene : DemoScene {
    override val id = "components_lab"
    override val title = "压力验证"
    override val summary = "维护 engine 所需的列表、构建、动画和手势压测入口"
    override val category = DemoCatalog.lab
    override val tags = setOf("lab", "stress", "performance", "debug", "diagnostics")
    override val apis = setOf(
        "PixelDebugOverlay",
        "ListViewBuilder",
        "GridViewBuilder",
        "PageView",
        "GestureDetector",
        "PixelHostFrameStats",
        "PixelInspectorSnapshot",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = body())

    private fun body(): Widget =
        Column(
            children = listOf(
                sectionTitle("压测矩阵"),
                samplePanel(
                    title = "List scale / rebuild / animation / gesture",
                    color = Accent,
                    child = Column(
                        children = listOf(
                            metricRow("List 50k", 0.86f, Green),
                            metricRow("Rebuild tree", 0.58f, Yellow),
                            metricRow("Animation flood", 0.72f, Pink),
                            metricRow("Gesture storm", 0.64f, Cyan),
                        ),
                        spacing = 2,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
                samplePanel(
                    title = "Large lazy list",
                    color = Pink,
                    child = Container(
                        height = 42,
                        borderColor = Pink,
                        child = ListViewBuilder(
                            itemCount = 1_000,
                            state = PixelListState(),
                            controller = ScrollController(),
                            itemExtent = 8,
                            itemBuilder = { index ->
                                Text("virtual row $index", style = TextStyle(color = if (index % 8 == 0) Accent else PixelColor.White))
                            },
                        ),
                    ),
                ),
                samplePanel(
                    title = "Grid allocation surface",
                    color = Blue,
                    child = Container(
                        height = 36,
                        borderColor = Blue,
                        child = GridViewBuilder(
                            itemCount = 96,
                            cellWidth = 14,
                            cellHeight = 9,
                            state = PixelListState(),
                            controller = ScrollController(),
                            spacing = 1,
                            itemBuilder = { index ->
                                Container(
                                    fillColor = when (index % 5) {
                                        0 -> Blue
                                        1 -> Purple
                                        2 -> Green
                                        3 -> Pink
                                        else -> Muted
                                    },
                                )
                            },
                        ),
                    ),
                ),
                samplePanel(
                    title = "Diagnostics",
                    color = Purple,
                    child = Row(
                        children = listOf(
                            ActivityIndicator(frame = 2, color = Accent),
                            Text("FPS / heap / targets / ticker", style = TextStyle(color = Purple)),
                        ),
                        spacing = 3,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                    ),
                ),
            ),
            spacing = 4,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )

    private fun metricRow(label: String, progress: Float, color: PixelColor): Widget =
        Row(
            children = listOf(
                Container(width = 54, child = Text(label, style = TextStyle(color = color))),
                ProgressBar(progress = progress, width = 56, color = color),
                Container(
                    padding = EdgeInsets.symmetric(horizontal = 1, vertical = 0),
                    borderColor = color,
                    child = Text("${(progress * 100).toInt()}%", style = TextStyle(color = color)),
                ),
            ),
            spacing = 2,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
        )
}
