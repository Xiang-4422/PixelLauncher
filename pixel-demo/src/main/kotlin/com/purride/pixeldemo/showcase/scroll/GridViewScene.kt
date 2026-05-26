package com.purride.pixeldemo.showcase.scroll

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
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

object GridViewScene : DemoScene {
    override val id = "grid_view"
    override val title = "GridViewBuilder 固定 cell"
    override val description = "二维 lazy 网格，固定 cell 宽高和间距"

    override fun build(env: DemoEnv): Widget = GridViewWidget()
}

private class GridViewWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = GridViewState()

    class GridViewState : State<GridViewWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()

        override fun build(context: BuildContext): Widget {
            return Column(
                children = listOf(
                    Expanded(
                        child = GridViewBuilder(
                            itemCount = 120,
                            itemBuilder = { index -> tile(index) },
                            cellWidth = 24,
                            cellHeight = 16,
                            spacing = 2,
                            runSpacing = 2,
                            state = scrollState,
                            controller = scrollController,
                        ),
                    ),
                    SizedBox(height = 2),
                    Text("GridViewBuilder: fixed cell lazy window", style = TextStyle.Default),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }

        private fun tile(index: Int): Widget {
            val color = when (index % 4) {
                0 -> PixelColor.fromRgb(60, 120, 220)
                1 -> PixelColor.fromRgb(220, 90, 80)
                2 -> PixelColor.fromRgb(80, 180, 110)
                else -> PixelColor.fromRgb(230, 180, 60)
            }
            return Container(
                fillColor = color,
                borderColor = PixelColor.White,
                child = Padding(
                    child = Text("#$index", style = TextStyle(color = PixelColor.Black)),
                    horizontal = 2,
                    vertical = 2,
                ),
            )
        }
    }
}

private fun Expanded(child: Widget): Widget = com.purride.pixelui.Expanded(child = child)
