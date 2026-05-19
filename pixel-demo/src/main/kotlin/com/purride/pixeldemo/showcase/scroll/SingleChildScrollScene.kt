package com.purride.pixeldemo.showcase.scroll

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Padding
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object SingleChildScrollScene : DemoScene {
    override val id = "single_child_scroll"
    override val title = "SingleChildScrollView"
    override val description = "单子节点滚动容器"

    override fun build(env: DemoEnv): Widget = SingleChildScrollWidget()
}

private class SingleChildScrollWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SingleChildScrollState()

    class SingleChildScrollState : State<SingleChildScrollWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()

        override fun build(context: BuildContext): Widget = SingleChildScrollView(
            state = scrollState,
            controller = scrollController,
            child = Padding(
                child = Column(
                    children = (1..40).map { i ->
                        Text("行 $i — SingleChildScrollView 容器内的内容", style = TextStyle.Default)
                    },
                    spacing = 2,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                ),
                all = 6,
            ),
        )
    }
}
