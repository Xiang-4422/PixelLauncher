package com.purride.pixeldemo.showcase.scroll

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.Axis
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PageController
import com.purride.pixelui.PageViewBuilder
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object PagerVerticalScene : DemoScene {
    override val id = "pager_vertical"
    override val title = "PageView 纵向"
    override val description = "纵向分页，切换 3 / 10 / 100 页"

    override fun build(env: DemoEnv): Widget = PagerVerticalWidget()
}

private val pageCountOptions = listOf(3, 10, 100)

private class PagerVerticalWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = PagerVerticalState()

    class PagerVerticalState : State<PagerVerticalWidget>() {
        private val pagerController = PageController()
        private var countIdx = 0
        private var pagerState = PixelPagerState(axis = PixelAxis.VERTICAL, pageCount = pageCountOptions[0])

        override fun build(context: BuildContext): Widget {
            val count = pageCountOptions[countIdx]
            val controls = pageCountOptions.mapIndexed { i, n ->
                OutlinedButton(
                    text = "$n",
                    onPressed = {
                        setState {
                            countIdx = i
                            pagerState = PixelPagerState(axis = PixelAxis.VERTICAL, pageCount = pageCountOptions[i])
                        }
                    },
                    selected = i == countIdx,
                )
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = PageViewBuilder(
                            axis = Axis.VERTICAL,
                            controller = pagerController,
                            state = pagerState,
                            itemCount = count,
                            itemBuilder = { i ->
                                Center(child = Text("Page $i", style = TextStyle.Accent))
                            },
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
