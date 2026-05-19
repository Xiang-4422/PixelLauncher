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
import com.purride.pixelui.fling
import com.purride.pixelui.jumpToEnd
import com.purride.pixelui.jumpToStart
import com.purride.pixelui.showItem
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import kotlin.random.Random

object ScrollControllerCommandsScene : DemoScene {
    override val id = "scroll_controller_commands"
    override val title = "ScrollController 命令"
    override val description = "jumpToStart / jumpToEnd / showItem / fling 正负速度"

    override fun build(env: DemoEnv): Widget = ScrollControllerCommandsWidget()
}

private class ScrollControllerCommandsWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ScrollControllerCommandsState()

    class ScrollControllerCommandsState : State<ScrollControllerCommandsWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()
        private val itemCount = 200

        override fun build(context: BuildContext): Widget = Column(
            children = listOf(
                Expanded(
                    child = ListViewBuilder(
                        itemCount = itemCount,
                        itemBuilder = { i ->
                            Padding(
                                child = Text("Item $i", style = TextStyle.Default),
                                horizontal = 6,
                                vertical = 3,
                            )
                        },
                        state = scrollState,
                        controller = scrollController,
                        itemExtent = 14,
                    ),
                ),
                SizedBox(height = 2),
                Row(
                    children = listOf(
                        OutlinedButton("Top", onPressed = { scrollController.jumpToStart(scrollState) }),
                        OutlinedButton("End", onPressed = { scrollController.jumpToEnd(scrollState) }),
                        OutlinedButton("Rand", onPressed = {
                            scrollController.showItem(scrollState, Random.nextInt(itemCount))
                        }),
                        OutlinedButton("Fling+", onPressed = { scrollController.fling(scrollState, 3000f) }),
                        OutlinedButton("Fling-", onPressed = { scrollController.fling(scrollState, -3000f) }),
                    ),
                    spacing = 2,
                ),
            ),
            mainAxisSize = MainAxisSize.MAX,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }
}
