package com.purride.pixeldemo.showcase.scroll

import com.purride.pixelui.BuildContext
import com.purride.pixelui.ListView
import com.purride.pixelui.Padding
import com.purride.pixelui.ScrollController
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object ListEagerScene : DemoScene {
    override val id = "list_eager"
    override val title = "ListView eager"
    override val description = "非 lazy 列表，一次性渲染所有 item"

    override fun build(env: DemoEnv): Widget = ListEagerWidget()
}

private class ListEagerWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ListEagerState()

    class ListEagerState : State<ListEagerWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()
        private val items = (1..25).map { i ->
            Padding(
                child = Text("Item $i — eager ListView", style = TextStyle.Default),
                horizontal = 6,
                vertical = 3,
            )
        }

        override fun build(context: BuildContext): Widget = ListView(
            items = items,
            state = scrollState,
            controller = scrollController,
            spacing = 1,
        )
    }
}
