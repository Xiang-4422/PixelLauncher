package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.AnimatedBuilder
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelDebugOverlay
import com.purride.pixelui.PixelHostFrameStats
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelAnimationController
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
import kotlin.time.Duration.Companion.milliseconds

object PerformanceLabScene : DemoScene {
    override val id = "deep_performance_lab"
    override val title = "性能实验室"
    override val summary = "Large list/grid、animation flood、rebuild stress、gesture target 与 frame stats"
    override val category = DemoCatalog.debug
    override val tags = setOf("performance", "stress", "frame", "ticker", "gesture", "large-list")
    override val apis = setOf(
        "ListViewBuilder",
        "GridViewBuilder",
        "GestureDetector",
        "AnimatedBuilder",
        "PixelAnimationController",
        "PixelHostFrameStats",
        "PixelDebugOverlay",
        "PixelTickerProvider",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = PerformanceLabBody(env))
}

private class PerformanceLabBody(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = PerformanceLabState()

    private class PerformanceLabState : State<PerformanceLabBody>() {
        private val largeListState = PixelListState()
        private val largeGridState = PixelListState()
        private val listController = ScrollController()
        private val gridController = ScrollController()
        private lateinit var controller: PixelAnimationController
        private var taps = 0
        private var rebuilds = 0

        override fun initState() {
            controller = PixelAnimationController(
                duration = 900.milliseconds,
                vsync = widget.env.vsync,
            )
            controller.repeat()
        }

        override fun dispose() {
            controller.dispose()
        }

        override fun build(context: BuildContext): Widget {
            val stats = PixelHostFrameStats(
                deltaMs = 16,
                fpsAvg = 60f,
                paintTimeNanos = 1_900_000 + rebuilds * 10_000L,
                frameCount = 10_000L + rebuilds,
            )
            return Column(
                children = listOf(
                    sectionTitle("Runtime counters"),
                    samplePanel(
                        title = "Frame stats / ticker count / rebuild",
                        color = Yellow,
                        child = Row(
                            children = listOf(
                                PixelDebugOverlay(stats = stats, activeTickerCount = widget.env.vsync.activeTickerCount),
                                Column(
                                    children = listOf(
                                        metric("frames", "${stats.frameCount}"),
                                        metric("paint", "${stats.paintTimeNanos / 1_000_000}ms"),
                                        metric("ticker", "${widget.env.vsync.activeTickerCount}"),
                                    ),
                                    spacing = 1,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                ),
                                OutlinedButton(
                                    text = "REBUILD",
                                    onPressed = { rebuilds += 1; setState {} },
                                    borderColor = Accent,
                                ),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.START,
                        ),
                    ),
                    sectionTitle("Large surfaces"),
                    samplePanel(
                        title = "Large ListViewBuilder",
                        color = Pink,
                        child = Container(
                            height = 42,
                            borderColor = Pink,
                            child = ListViewBuilder(
                                itemCount = 5_000,
                                state = largeListState,
                                controller = listController,
                                itemExtent = 8,
                                itemBuilder = { index ->
                                    Text("row $index", style = TextStyle(color = if (index % 64 == 0) Accent else PixelColor.White))
                                },
                            ),
                        ),
                    ),
                    samplePanel(
                        title = "Large GridViewBuilder",
                        color = Blue,
                        child = Container(
                            height = 38,
                            borderColor = Blue,
                            child = GridViewBuilder(
                                itemCount = 1_024,
                                cellWidth = 12,
                                cellHeight = 8,
                                state = largeGridState,
                                controller = gridController,
                                spacing = 1,
                                runSpacing = 1,
                                itemBuilder = { index ->
                                    Container(
                                        fillColor = when (index % 6) {
                                            0 -> Blue
                                            1 -> Purple
                                            2 -> Green
                                            3 -> Pink
                                            4 -> Cyan
                                            else -> Muted
                                        },
                                    )
                                },
                            ),
                        ),
                    ),
                    samplePanel(
                        title = "Animation flood / gesture target",
                        color = Cyan,
                        child = Column(
                            children = listOf(
                                AnimatedBuilder(animation = controller) { _, _ ->
                                    Row(
                                        children = (0 until 5).map { index ->
                                            ProgressBar(
                                                progress = ((controller.value + index * 0.17f) % 1f),
                                                width = 18,
                                                color = if (index % 2 == 0) Cyan else Accent,
                                            )
                                        },
                                        spacing = 1,
                                    )
                                },
                                GestureDetector(
                                    onTap = {
                                        taps += 1
                                        setState {}
                                    },
                                    child = Container(
                                        padding = EdgeInsets.all(3),
                                        borderColor = Green,
                                        child = Text("tap target count=$taps", style = TextStyle(color = Green)),
                                    ),
                                ),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
                spacing = 4,
                mainAxisSize = MainAxisSize.MIN,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }

        private fun metric(label: String, value: String): Widget =
            Row(
                children = listOf(
                    Container(width = 34, child = Text(label, style = TextStyle(color = Muted))),
                    Text(value, style = TextStyle(color = PixelColor.White)),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
            )
    }
}
