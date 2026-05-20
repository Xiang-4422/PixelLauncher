package com.purride.pixeldemo.showcase.stress

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
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
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.DemoMetricsOverlay

object StressListScaleScene : DemoScene {
    override val id = "stress_list_scale"
    override val title = "压测 · 列表规模"
    override val description = "档位切换 1k / 5k / 20k / 50k，观察虚拟列表在大数据量下的 FPS / Heap"

    override fun build(env: DemoEnv): Widget = StressListScaleWidget()
}

private val tiers = listOf(1_000, 5_000, 20_000, 50_000)

private class StressListScaleWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = StressListScaleState()

    class StressListScaleState : State<StressListScaleWidget>() {
        private val listState = PixelListState()
        private val listCtrl = ScrollController()
        private var tierIndex = 1

        override fun build(context: BuildContext): Widget {
            val count = tiers[tierIndex]
            val controls = tiers.mapIndexed { i, n ->
                OutlinedButton(
                    text = if (n >= 1000) "${n / 1000}k" else "$n",
                    onPressed = { setState { tierIndex = i } },
                    selected = i == tierIndex,
                )
            }
            return Column(
                children = listOf(
                    DemoMetricsOverlay(extraSampler = { "N=$count" }),
                    SizedBox(height = 1),
                    Expanded(
                        child = ListViewBuilder(
                            state = listState,
                            controller = listCtrl,
                            itemCount = count,
                            itemBuilder = { i ->
                                Padding(
                                    child = Center(
                                        child = Text(
                                            "Item $i",
                                            style = if (i % 10 == 0) TextStyle.Accent else TextStyle.Default,
                                        ),
                                    ),
                                    all = 2,
                                )
                            },
                        ),
                    ),
                    SizedBox(height = 2),
                    Padding(
                        child = Row(
                            children = controls,
                            spacing = 2,
                            mainAxisAlignment = MainAxisAlignment.CENTER,
                        ),
                        horizontal = 4,
                        vertical = 2,
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
