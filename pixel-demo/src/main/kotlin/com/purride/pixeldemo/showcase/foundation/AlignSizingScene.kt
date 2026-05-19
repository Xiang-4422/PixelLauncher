package com.purride.pixeldemo.showcase.foundation

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Align
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.Flexible
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Spacer
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object AlignSizingScene : DemoScene {
    override val id = "align_sizing"
    override val title = "对齐与尺寸"
    override val description = "9 种布局原语：Padding / Align / Center / SizedBox / Expanded / Flexible / Spacer"

    override fun build(env: DemoEnv): Widget = AlignSizingWidget()
}

private class AlignSizingWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AlignSizingState()

    class AlignSizingState : State<AlignSizingWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()

        private fun marker() = Container(width = 6, height = 6, fillTone = PixelTone.ACCENT, borderTone = null)

        private fun box(label: String, child: Widget): Widget = Column(
            children = listOf(
                Text(label, style = TextStyle.Default),
                SizedBox(
                    height = 40,
                    child = Container(fillTone = PixelTone.OFF, borderTone = PixelTone.ON, child = child),
                ),
            ),
            spacing = 1,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )

        override fun build(context: BuildContext): Widget {
            val items = listOf(
                box("Padding(all=4)", Padding(child = marker(), all = 4)),
                box("Align(topStart)", Align(child = marker(), alignment = Alignment.TOP_START)),
                box("Align(bottomEnd)", Align(child = marker(), alignment = Alignment.BOTTOM_END)),
                box("Align(center)", Align(child = marker(), alignment = Alignment.CENTER)),
                box("Center", Center(child = marker())),
                box("SizedBox(20x20)", Center(child = SizedBox(width = 20, height = 20, child = marker()))),
                box(
                    "Expanded",
                    Row(
                        children = listOf(
                            Expanded(child = Container(fillTone = PixelTone.ACCENT, borderTone = null)),
                            SizedBox(width = 8),
                        ),
                        mainAxisSize = MainAxisSize.MAX,
                    ),
                ),
                box(
                    "Flexible(loose)",
                    Row(
                        children = listOf(
                            Flexible(child = Container(width = 100, fillTone = PixelTone.ACCENT, borderTone = null)),
                            SizedBox(width = 8),
                        ),
                        mainAxisSize = MainAxisSize.MAX,
                    ),
                ),
                box(
                    "Spacer",
                    Row(children = listOf(marker(), Spacer(), marker()), mainAxisSize = MainAxisSize.MAX),
                ),
            )

            return SingleChildScrollView(
                state = scrollState,
                controller = scrollController,
                child = Padding(
                    child = Column(
                        children = items,
                        spacing = 4,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                    all = 4,
                ),
            )
        }
    }
}
