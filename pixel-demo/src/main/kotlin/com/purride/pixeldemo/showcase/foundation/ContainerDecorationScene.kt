package com.purride.pixeldemo.showcase.foundation

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.ContainerDirectional
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.DecoratedBox
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.BuildContext
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object ContainerDecorationScene : DemoScene {
    override val id = "container_decoration"
    override val title = "容器与装饰"
    override val description = "Container / DecoratedBox / ContainerDirectional 对比"

    override fun build(env: DemoEnv): Widget = ContainerDecorationWidget()
}

private class ContainerDecorationWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ContainerDecorationState()

    class ContainerDecorationState : State<ContainerDecorationWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()

        override fun build(context: BuildContext): Widget {
            fun label(text: String) = Text(text, style = TextStyle.Accent)
            fun note(text: String) = Text(text, style = TextStyle.Default, softWrap = true)

            val rows: List<Widget> = listOf(
                label("Container — 全参数"),
                Container(
                    width = 80,
                    height = 30,
                    fillTone = PixelTone.OFF,
                    borderTone = PixelTone.ON,
                    padding = EdgeInsets(left = 4, top = 2, right = 4, bottom = 2),
                    alignment = Alignment.CENTER_START,
                    child = Text("fillTone=OFF, border=ON", style = TextStyle.Default),
                ),
                Container(
                    width = 80,
                    height = 30,
                    fillTone = PixelTone.ON,
                    borderTone = PixelTone.ACCENT,
                    padding = EdgeInsets(left = 4, top = 2, right = 4, bottom = 2),
                    alignment = Alignment.CENTER,
                    child = Text("fill=ON, border=ACCENT", style = TextStyle.Accent),
                ),
                SizedBox(height = 4),
                label("DecoratedBox — 无尺寸约束"),
                note("宽高由 child 决定，borderTone/fillTone 仅装饰"),
                DecoratedBox(
                    fillTone = PixelTone.OFF,
                    borderTone = PixelTone.ON,
                    padding = 4,
                    child = Text("DecoratedBox child", style = TextStyle.Default),
                ),
                DecoratedBox(
                    fillTone = PixelTone.ACCENT,
                    borderTone = null,
                    padding = 4,
                    child = Text("fill=ACCENT, no border", style = TextStyle.Accent),
                ),
                SizedBox(height = 4),
                label("ContainerDirectional — LTR / RTL 对称"),
                ContainerDirectional(
                    width = 120,
                    height = 28,
                    fillTone = PixelTone.OFF,
                    borderTone = PixelTone.ON,
                    paddingDirectional = EdgeInsetsDirectional(start = 8, end = 2, top = 2, bottom = 2),
                    alignment = AlignmentDirectional.CENTER_START,
                    child = Text("paddingStart=8", style = TextStyle.Default),
                ),
                ContainerDirectional(
                    width = 120,
                    height = 28,
                    fillTone = PixelTone.OFF,
                    borderTone = PixelTone.ACCENT,
                    paddingDirectional = EdgeInsetsDirectional(start = 2, end = 8, top = 2, bottom = 2),
                    alignment = AlignmentDirectional.CENTER_END,
                    child = Text("paddingEnd=8", style = TextStyle.Accent),
                ),
            )

            return SingleChildScrollView(
                state = scrollState,
                controller = scrollController,
                child = Padding(
                    child = Column(
                        children = rows,
                        spacing = 2,
                        crossAxisAlignment = CrossAxisAlignment.START,
                    ),
                    all = 4,
                ),
            )
        }
    }
}
