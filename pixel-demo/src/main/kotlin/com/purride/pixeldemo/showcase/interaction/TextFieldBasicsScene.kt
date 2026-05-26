package com.purride.pixeldemo.showcase.interaction

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Padding
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextStyle
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixelcore.PixelColor

object TextFieldBasicsScene : DemoScene {
    override val id = "text_field_basics"
    override val title = "文本输入基础"
    override val description = "单行 / 多行 TextField 及 controller"

    override fun build(env: DemoEnv): Widget = TextFieldBasicsWidget()
}

private class TextFieldBasicsWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TextFieldBasicsState()

    class TextFieldBasicsState : State<TextFieldBasicsWidget>() {
        private val scrollState = PixelListState()
        private val scrollCtrl = ScrollController()
        private val singleState = PixelTextFieldState()
        private val singleCtrl = TextEditingController()
        private val multiState = PixelTextFieldState(
            initialText = "第一行\nSECOND LINE\n第三行",
            selectionStart = 0,
            selectionEnd = 9,
        )
        private val multiCtrl = TextEditingController()

        override fun build(context: BuildContext): Widget = SingleChildScrollView(
            state = scrollState,
            controller = scrollCtrl,
            child = Padding(
                child = Column(
                    children = listOf(
                        Text("单行输入", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                        TextField(
                            state = singleState,
                            controller = singleCtrl,
                            placeholder = "请输入文字…",
                        ),
                        Text("当前值: ${singleState.text}", style = TextStyle.Default),
                        Text("多行输入 (maxLines=4)", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                        TextField(
                            state = multiState,
                            controller = multiCtrl,
                            placeholder = "多行…",
                            autofocus = true,
                            minLines = 2,
                            maxLines = 4,
                        ),
                        Text("字符数: ${multiState.text.length} / 跨行选区可拖动", style = TextStyle.Default),
                    ),
                    spacing = 4,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                ),
                all = 8,
            ),
        )
    }
}
