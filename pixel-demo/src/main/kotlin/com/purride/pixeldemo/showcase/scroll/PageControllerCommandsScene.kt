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
import com.purride.pixelui.jumpToPage
import com.purride.pixelui.nextPage
import com.purride.pixelui.previousPage
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import kotlin.random.Random
import com.purride.pixelcore.PixelColor

object PageControllerCommandsScene : DemoScene {
    override val id = "page_controller_commands"
    override val title = "PageController 命令"
    override val description = "jumpToPage / nextPage / previousPage"

    override fun build(env: DemoEnv): Widget = PageControllerCommandsWidget()
}

private class PageControllerCommandsWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = PageControllerCommandsState()

    class PageControllerCommandsState : State<PageControllerCommandsWidget>() {
        private val pageCount = 20
        private val pagerState = PixelPagerState(axis = PixelAxis.HORIZONTAL, pageCount = pageCount)
        private val pagerController = PageController()

        override fun build(context: BuildContext): Widget = Column(
            children = listOf(
                Expanded(
                    child = PageViewBuilder(
                        axis = Axis.HORIZONTAL,
                        controller = pagerController,
                        state = pagerState,
                        itemCount = pageCount,
                        itemBuilder = { i ->
                            Center(child = Text("Page $i / ${pageCount - 1}", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))))
                        },
                    ),
                ),
                SizedBox(height = 2),
                Row(
                    children = listOf(
                        OutlinedButton("Prev", onPressed = { pagerController.previousPage(pagerState) }),
                        OutlinedButton("Next", onPressed = { pagerController.nextPage(pagerState) }),
                        OutlinedButton("Rand", onPressed = {
                            pagerController.jumpToPage(pagerState, Random.nextInt(pageCount))
                        }),
                    ),
                    spacing = 2,
                ),
            ),
            mainAxisSize = MainAxisSize.MAX,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }
}
