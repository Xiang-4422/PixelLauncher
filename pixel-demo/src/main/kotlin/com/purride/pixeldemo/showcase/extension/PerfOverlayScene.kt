package com.purride.pixeldemo.showcase.extension

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.ScrollController
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.DemoMetricsOverlay

object PerfOverlayScene : DemoScene {
    override val id = "perf_overlay"
    override val title = "性能 Overlay"
    override val description = "Choreographer FPS + 堆内存采样，5000 项列表负载"

    override fun build(env: DemoEnv): Widget = PerfOverlayWidget()
}

private class PerfOverlayWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = PerfOverlayState()

    class PerfOverlayState : State<PerfOverlayWidget>() {
        private val listState = PixelListState()
        private val listCtrl = ScrollController()

        override fun build(context: BuildContext): Widget {
            return Column(
                children = listOf(
                    DemoMetricsOverlay(),
                    SizedBox(height = 1),
                    Expanded(
                        child = ListViewBuilder(
                            state = listState,
                            controller = listCtrl,
                            itemCount = 5000,
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
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
