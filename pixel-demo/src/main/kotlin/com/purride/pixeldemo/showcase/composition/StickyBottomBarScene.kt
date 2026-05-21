package com.purride.pixeldemo.showcase.composition
import com.purride.pixelcore.PixelColor


import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
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
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object StickyBottomBarScene : DemoScene {
    override val id = "sticky_bottom_bar"
    override val title = "Sticky Bottom Bar"
    override val description = "顶部滚动内容 + 固定底栏 + TextField — 输入与发送的经典布局"

    override fun build(env: DemoEnv): Widget = StickyBottomBarWidget()
}

private class StickyBottomBarWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = StickyBottomBarState()

    class StickyBottomBarState : State<StickyBottomBarWidget>() {
        private val listState = PixelListState()
        private val listCtrl = ScrollController()
        private val textState = PixelTextFieldState()
        private val textCtrl = TextEditingController()
        private var sendCount = 0

        override fun build(context: BuildContext): Widget {
            val total = 30 + sendCount
            return Column(
                children = listOf(
                    Expanded(
                        child = ListViewBuilder(
                            state = listState,
                            controller = listCtrl,
                            itemCount = total,
                            itemBuilder = { i ->
                                Padding(
                                    child = Text(
                                        if (i < 30) "Item $i" else "Sent #${i - 30}",
                                        style = if (i >= 30) TextStyle(color = PixelColor.fromRgb(200, 100, 0)) else TextStyle.Default,
                                    ),
                                    all = 3,
                                )
                            },
                        ),
                    ),
                    Container(
                        fillColor = PixelColor.Transparent,
                        borderColor = PixelColor.White,
                        child = Padding(
                            child = Row(
                                children = listOf(
                                    Expanded(
                                        child = TextField(
                                            state = textState,
                                            controller = textCtrl,
                                            placeholder = "type & send",
                                        ),
                                    ),
                                    SizedBox(width = 4),
                                    OutlinedButton(
                                        "SEND",
                                        onPressed = {
                                            setState { sendCount++ }
                                            textCtrl.clear(textState)
                                        },
                                    ),
                                ),
                                spacing = 2,
                            ),
                            all = 2,
                        ),
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
