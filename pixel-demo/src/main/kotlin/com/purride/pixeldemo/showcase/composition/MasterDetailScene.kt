package com.purride.pixeldemo.showcase.composition
import com.purride.pixelcore.PixelColor


import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
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

object MasterDetailScene : DemoScene {
    override val id = "master_detail"
    override val title = "Master-Detail"
    override val description = "左列表 / 右详情，Row + Expanded 实现两栏布局"

    override fun build(env: DemoEnv): Widget = MasterDetailWidget()
}

private val items = listOf(
    "Pixel" to "8-bit nostalgia rendered live.",
    "Engine" to "Frame-accurate widget tree.",
    "Demo" to "Showcases capability boundary.",
    "Stress" to "Pushes perf to the wall.",
    "Theme" to "Palette + shape combinations.",
    "Gesture" to "Tap / drag / pager arbitration.",
    "Layout" to "Row / Column / Stack primitives.",
    "Text" to "Soft wrap / overflow / spans.",
)

private class MasterDetailWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = MasterDetailState()

    class MasterDetailState : State<MasterDetailWidget>() {
        private val listState = PixelListState()
        private val listCtrl = ScrollController()
        private var selected = 0

        override fun build(context: BuildContext): Widget {
            val (title, body) = items[selected]
            return Row(
                children = listOf(
                    Container(
                        width = 70,
                        fillColor = PixelColor.Transparent,
                        borderColor = PixelColor.White,
                        child = ListViewBuilder(
                            state = listState,
                            controller = listCtrl,
                            itemCount = items.size,
                            itemBuilder = { i ->
                                GestureDetector(
                                    onTap = { setState { selected = i } },
                                    child = Padding(
                                        child = Text(
                                            items[i].first,
                                            style = if (i == selected) TextStyle(color = PixelColor.fromRgb(200, 100, 0)) else TextStyle.Default,
                                        ),
                                        all = 3,
                                    ),
                                )
                            },
                        ),
                    ),
                    Expanded(
                        child = Padding(
                            child = Column(
                                children = listOf(
                                    Text(title, style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                                    SizedBox(height = 2),
                                    Text(body, style = TextStyle.Default, softWrap = true),
                                    SizedBox(height = 4),
                                    Center(child = Text("[$selected/${items.size - 1}]", style = TextStyle.Default)),
                                ),
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.START,
                            ),
                            all = 4,
                        ),
                    ),
                ),
                spacing = 2,
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
