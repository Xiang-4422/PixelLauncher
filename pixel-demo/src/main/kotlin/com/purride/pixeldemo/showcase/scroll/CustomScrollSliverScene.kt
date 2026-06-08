package com.purride.pixeldemo.showcase.scroll

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CustomScrollView
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.SliverList
import com.purride.pixelui.SliverPinnedHeader
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.DemoScaffold

object CustomScrollSliverScene : DemoScene {
    override val id = "custom_scroll_slivers"
    override val title = "CustomScrollView Sliver"
    override val description = "SliverList 与 pinned header 的组合滚动"

    override fun build(env: DemoEnv): Widget = CustomScrollSliverWidget()
}

private class CustomScrollSliverWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = CustomScrollSliverState()

    class CustomScrollSliverState : State<CustomScrollSliverWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()

        override fun build(context: BuildContext): Widget {
            val items = List(32) { index -> row(index) }
            return DemoScaffold(
                title = "CustomScrollView",
                description = "Pinned header stays interactive while SliverList scrolls",
                body = Scrollbar(
                    state = scrollState,
                    thumbColor = PixelColor.White,
                    trackColor = PixelColor.fromArgb(70, 0, 0, 0),
                    width = 2,
                    child = CustomScrollView(
                        slivers = listOf(
                            SliverPinnedHeader(child = header()),
                            SliverList(items = items, spacing = 1),
                        ),
                        state = scrollState,
                        controller = scrollController,
                    ),
                ),
                controls = listOf(
                    OutlinedButton("TOP", onPressed = {
                        setState { scrollController.scrollItemIntoView(scrollState, 0) }
                    }),
                    OutlinedButton("MID", onPressed = {
                        setState { scrollController.scrollItemIntoView(scrollState, 16) }
                    }),
                    OutlinedButton("BOTTOM", onPressed = {
                        setState { scrollController.scrollItemIntoView(scrollState, 32) }
                    }),
                ),
            )
        }

        private fun header(): Widget {
            return Container(
                fillColor = PixelColor.fromRgb(230, 180, 60),
                borderColor = PixelColor.White,
                child = Padding(
                    child = Column(
                        children = listOf(
                            Text("PINNED HEADER", style = TextStyle(color = PixelColor.Black)),
                            Text("drag list; tap controls", style = TextStyle(color = PixelColor.Black)),
                        ),
                        spacing = 1,
                        mainAxisSize = MainAxisSize.MIN,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                    horizontal = 2,
                    vertical = 2,
                ),
            )
        }

        private fun row(index: Int): Widget {
            val color = when (index % 4) {
                0 -> PixelColor.fromRgb(60, 120, 220)
                1 -> PixelColor.fromRgb(220, 90, 80)
                2 -> PixelColor.fromRgb(80, 180, 110)
                else -> PixelColor.fromRgb(120, 120, 120)
            }
            return SizedBox(
                height = 10,
                child = Container(
                    fillColor = color,
                    borderColor = PixelColor.fromArgb(120, 255, 255, 255),
                    child = Padding(
                        horizontal = 2,
                        vertical = 2,
                        child = Row(
                            children = listOf(
                                SizedBox(width = 12, child = Text("#$index", style = TextStyle(color = PixelColor.White))),
                                Expanded(child = Text("SliverList row $index", style = TextStyle(color = PixelColor.White))),
                            ),
                            spacing = 2,
                        ),
                    ),
                ),
            )
        }
    }
}
