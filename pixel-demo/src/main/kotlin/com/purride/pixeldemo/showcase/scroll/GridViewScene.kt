package com.purride.pixeldemo.showcase.scroll

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.FocusScope
import com.purride.pixelui.GridFocusTraversalPolicy
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListRestorationPolicy
import com.purride.pixelui.state.PixelListSavedState
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
        private val focusNodes = List(120) { index -> FocusNode("grid-$index") }
        private var savedState: PixelListSavedState? = null

        override fun build(context: BuildContext): Widget {
            return FocusScope(
                traversalPolicy = GridFocusTraversalPolicy(columns = 3),
                child = Column(
                    children = listOf(
                        Expanded(
                            child = Scrollbar(
                                state = scrollState,
                                thumbColor = PixelColor.White,
                                trackColor = PixelColor.fromArgb(80, 0, 0, 0),
                                width = 2,
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
                        ),
                        SizedBox(height = 2),
                        Text("GridViewBuilder: fixed cell + arrow focus + drag thumb", style = TextStyle.Default),
                        SizedBox(height = 2),
                        Row(
                            children = listOf(
                                OutlinedButton("SAVE", onPressed = {
                                    setState { savedState = scrollController.saveState(scrollState) }
                                }),
                                OutlinedButton("RESTORE", onPressed = {
                                    savedState?.let { snapshot ->
                                        setState {
                                            scrollController.restoreState(
                                                state = scrollState,
                                                savedState = snapshot,
                                                policy = PixelListRestorationPolicy.AnchorItem,
                                            )
                                        }
                                    }
                                }),
                            ),
                            spacing = 2,
                        ),
                    ),
                    mainAxisSize = MainAxisSize.MAX,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                ),
            )
        }

        private fun tile(index: Int): Widget {
            val focusNode = focusNodes[index]
            val color = when (index % 4) {
                0 -> PixelColor.fromRgb(60, 120, 220)
                1 -> PixelColor.fromRgb(220, 90, 80)
                2 -> PixelColor.fromRgb(80, 180, 110)
                else -> PixelColor.fromRgb(230, 180, 60)
            }
            return Focus(
                node = focusNode,
                autofocus = index == 0,
                child = Container(
                    fillColor = color,
                    borderColor = if (focusNode.isFocused) PixelColor.fromRgb(255, 255, 0) else PixelColor.White,
                    child = Padding(
                        child = Text("#$index", style = TextStyle(color = PixelColor.Black)),
                        horizontal = 2,
                        vertical = 2,
                    ),
                ),
            )
        }
    }
}

private fun Expanded(child: Widget): Widget = com.purride.pixelui.Expanded(child = child)
