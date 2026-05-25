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
import com.purride.pixelcore.PixelColor

object ListVariableHeightScene : DemoScene {
    override val id = "list_variable_height"
    override val title = "ListViewBuilder 变高 lazy"
    override val description = "变高 item 虚拟列表，切换 1k / 5k 与 1x / 2x 高度"

    override fun build(env: DemoEnv): Widget = ListVariableHeightWidget()
}

private val countOptions = listOf(1_000, 5_000)
private val heightMultiplierOptions = listOf(1, 2)

private class ListVariableHeightWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ListVariableHeightState()

    class ListVariableHeightState : State<ListVariableHeightWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()
        private var countIdx = 0
        private var heightMultiplierIdx = 0

        override fun build(context: BuildContext): Widget {
            val count = countOptions[countIdx]
            val heightMultiplier = heightMultiplierOptions[heightMultiplierIdx]
            val countControls = countOptions.mapIndexed { i, n ->
                OutlinedButton(
                    text = "${n / 1000}k",
                    onPressed = { setState { countIdx = i } },
                    borderColor = if (i == countIdx) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                )
            }
            val heightControls = heightMultiplierOptions.mapIndexed { i, mult ->
                OutlinedButton(
                    text = "${mult}x",
                    onPressed = { setState { heightMultiplierIdx = i } },
                    borderColor = if (i == heightMultiplierIdx) {
                        PixelColor.fromRgb(200, 100, 0)
                    } else {
                        PixelColor.White
                    },
                )
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = ListViewBuilder(
                            itemCount = count,
                            estimatedItemExtent = 14 * heightMultiplier,
                            itemBuilder = { i ->
                                val lines = ((i % 3) + 1) * heightMultiplier
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
                    Row(children = countControls, spacing = 2),
                    SizedBox(height = 2),
                    Row(children = heightControls, spacing = 2),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
