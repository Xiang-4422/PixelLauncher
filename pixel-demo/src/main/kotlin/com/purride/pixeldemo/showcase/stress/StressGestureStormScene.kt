package com.purride.pixeldemo.showcase.stress

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Axis
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PageController
import com.purride.pixelui.PageViewBuilder
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.DemoMetricsOverlay

object StressGestureStormScene : DemoScene {
    override val id = "stress_gesture_storm"
    override val title = "压测 · 手势风暴"
    override val description = "同屏并发：纵向 List + 横向 Pager + 多点 Gesture，手势识别压力"

    override fun build(env: DemoEnv): Widget = StressGestureStormWidget()
}

private class StressGestureStormWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = StressGestureStormState()

    class StressGestureStormState : State<StressGestureStormWidget>() {
        private val listState = PixelListState()
        private val listCtrl = ScrollController()
        private val pagerCtrl = PageController()
        private val pagerState = PixelPagerState(axis = PixelAxis.HORIZONTAL, pageCount = 20)
        private var tapCount = 0

        override fun build(context: BuildContext): Widget {
            val tapGrid = Row(
                children = (0 until 4).map { i ->
                    GestureDetector(
                        onTap = { setState { tapCount++ } },
                        child = Container(
                            width = 30, height = 20,
                            fillTone = if (i % 2 == 0) PixelTone.ON else PixelTone.ACCENT,
                            borderTone = PixelTone.ON,
                            child = Center(child = Text("T$i", style = TextStyle.Default)),
                        ),
                    )
                },
                spacing = 2,
            )

            return Column(
                children = listOf(
                    DemoMetricsOverlay(extraSampler = { "taps=$tapCount" }),
                    SizedBox(height = 1),
                    Padding(
                        child = Text("Pager (横滑)", style = TextStyle.Accent),
                        horizontal = 4,
                    ),
                    Container(
                        height = 40,
                        fillTone = PixelTone.OFF,
                        borderTone = PixelTone.ON,
                        child = PageViewBuilder(
                            axis = Axis.HORIZONTAL,
                            controller = pagerCtrl,
                            state = pagerState,
                            itemCount = 20,
                            itemBuilder = { i ->
                                Center(child = Text("PAGE $i", style = TextStyle.Accent))
                            },
                        ),
                    ),
                    SizedBox(height = 2),
                    Padding(
                        child = Text("Tap grid", style = TextStyle.Accent),
                        horizontal = 4,
                    ),
                    Padding(child = tapGrid, horizontal = 4, vertical = 2),
                    SizedBox(height = 2),
                    Padding(
                        child = Text("List (竖滑)", style = TextStyle.Accent),
                        horizontal = 4,
                    ),
                    Expanded(
                        child = ListViewBuilder(
                            state = listState,
                            controller = listCtrl,
                            itemCount = 2000,
                            itemBuilder = { i ->
                                Padding(
                                    child = Text(
                                        "Row $i",
                                        style = if (i % 5 == 0) TextStyle.Accent else TextStyle.Default,
                                    ),
                                    all = 2,
                                )
                            },
                        ),
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
