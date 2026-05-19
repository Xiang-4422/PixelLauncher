package com.purride.pixeldemo.showcase.scroll

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
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

object ListVariableHeightScene : DemoScene {
    override val id = "list_variable_height"
    override val title = "ListViewBuilder 变高 lazy"
    override val description = "变高 item 虚拟列表，切换 1k / 5k"

    override fun build(env: DemoEnv): Widget = ListVariableHeightWidget()
}

private val countOptions = listOf(1_000, 5_000)

private class ListVariableHeightWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ListVariableHeightState()

    class ListVariableHeightState : State<ListVariableHeightWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()
        private var countIdx = 0

        override fun build(context: BuildContext): Widget {
            val count = countOptions[countIdx]
            val controls = countOptions.mapIndexed { i, n ->
                OutlinedButton(
                    text = "${n / 1000}k",
                    onPressed = { setState { countIdx = i } },
                    selected = i == countIdx,
                )
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = ListViewBuilder(
                            itemCount = count,
                            itemBuilder = { i ->
                                val lines = (i % 3) + 1
                                Padding(
                                    child = Column(
                                        children = (1..lines).map { l ->
                                            Text("Item $i · 行 $l", style = TextStyle.Default)
                                        },
                                        spacing = 1,
                                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    ),
                                    horizontal = 6,
                                    vertical = 2,
                                )
                            },
                            state = scrollState,
                            controller = scrollController,
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
