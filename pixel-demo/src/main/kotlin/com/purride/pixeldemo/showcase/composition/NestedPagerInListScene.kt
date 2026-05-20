package com.purride.pixeldemo.showcase.composition

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.Axis
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PageController
import com.purride.pixelui.PageViewBuilder
import com.purride.pixelui.Padding
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object NestedPagerInListScene : DemoScene {
    override val id = "nested_scroll_pager_in_list"
    override val title = "嵌套 · Pager-in-List"
    override val description = "List item 内嵌横向 Pager — 常见 tab 卡片 / 横向 reel 场景"

    override fun build(env: DemoEnv): Widget = NestedPagerInListWidget()
}

private class NestedPagerInListWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = NestedPagerInListState()

    class NestedPagerInListState : State<NestedPagerInListWidget>() {
        private val listState = PixelListState()
        private val listCtrl = ScrollController()
        private val pagerCtrls = List(20) { PageController() }
        private val pagerStates = List(20) { PixelPagerState(axis = PixelAxis.HORIZONTAL, pageCount = 5) }

        override fun build(context: BuildContext): Widget {
            return ListViewBuilder(
                state = listState,
                controller = listCtrl,
                itemCount = 20,
                itemBuilder = { row ->
                    Padding(
                        child = Column(
                            children = listOf(
                                Text("Row $row · 横滑切卡片", style = TextStyle.Accent),
                                SizedBox(height = 2),
                                Container(
                                    height = 40,
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ON,
                                    child = PageViewBuilder(
                                        axis = Axis.HORIZONTAL,
                                        controller = pagerCtrls[row],
                                        state = pagerStates[row],
                                        itemCount = 5,
                                        itemBuilder = { p ->
                                            Center(child = Text("R$row · P$p", style = TextStyle.Accent))
                                        },
                                    ),
                                ),
                            ),
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                            mainAxisSize = MainAxisSize.MIN,
                        ),
                        all = 4,
                    )
                },
            )
        }
    }
}
