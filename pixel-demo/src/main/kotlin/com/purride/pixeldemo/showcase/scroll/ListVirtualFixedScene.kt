package com.purride.pixeldemo.showcase.scroll

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.ListViewBuilder
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
import com.purride.pixelcore.PixelColor

object ListVirtualFixedScene : DemoScene {
    override val id = "list_virtual_fixed"
    override val title = "ListViewBuilder 固定高 lazy"
    override val description = "虚拟化列表，切换 1k / 5k / 10k item"

    override fun build(env: DemoEnv): Widget = ListVirtualFixedWidget()
}

private val countOptions = listOf(1_000, 5_000, 10_000)

private class ListVirtualFixedWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ListVirtualFixedState()

    class ListVirtualFixedState : State<ListVirtualFixedWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()
        private var countIdx = 0

        override fun build(context: BuildContext): Widget {
            val count = countOptions[countIdx]
            val controls = countOptions.mapIndexed { i, n ->
                OutlinedButton(
                    text = "${n / 1000}k",
                    onPressed = { setState { countIdx = i } },
                    borderColor = if (i == countIdx) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                )
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = ListViewBuilder(
                            itemCount = count,
                            itemBuilder = { i ->
                                Padding(
                                    child = Text("Item $i", style = TextStyle.Default),
                                    horizontal = 6,
                                    vertical = 2,
                                )
                            },
                            state = scrollState,
                            controller = scrollController,
                            itemExtent = 14,
                        ),
                    ),
                    SizedBox(height = 2),
                    Row(children = controls, spacing = 2),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}

private fun Expanded(child: Widget): Widget = com.purride.pixelui.Expanded(child = child)
