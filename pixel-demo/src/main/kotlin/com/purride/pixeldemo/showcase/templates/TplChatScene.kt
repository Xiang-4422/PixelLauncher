package com.purride.pixeldemo.showcase.templates
import com.purride.pixelcore.PixelColor


import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisAlignment
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

object TplChatScene : DemoScene {
    override val id = "tpl_chat"
    override val title = "模板 · 聊天 UI"
    override val description = "消息流（左右气泡）+ 底部输入条 — 聊天 / 客服界面模板"

    override fun build(env: DemoEnv): Widget = TplChatWidget()
}

private data class Msg(val text: String, val fromMe: Boolean)

private val initial = listOf(
    Msg("hi! 这是 pixel-engine 演示", false),
    Msg("看起来挺像素的", true),
    Msg("可以滚、可以输入、可以发送", false),
    Msg("试试输入框？", false),
    Msg("OK", true),
)

private class TplChatWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TplChatState()

    class TplChatState : State<TplChatWidget>() {
        private val listState = PixelListState()
        private val listCtrl = ScrollController()
        private val textState = PixelTextFieldState()
        private val textCtrl = TextEditingController()
        private val messages: MutableList<Msg> = initial.toMutableList()

        private fun send() {
            val text = textState.text.trim()
            if (text.isEmpty()) return
            setState {
                messages.add(Msg(text, fromMe = true))
                if (messages.size % 2 == 0) {
                    messages.add(Msg("收到: $text", fromMe = false))
                }
            }
            textCtrl.clear(textState)
        }

        override fun build(context: BuildContext): Widget {
            return Column(
                children = listOf(
                    Expanded(
                        child = ListViewBuilder(
                            state = listState,
                            controller = listCtrl,
                            itemCount = messages.size,
                            itemBuilder = { i ->
                                val m = messages[i]
                                val bubble = Container(
                                    fillColor = if (m.fromMe) PixelColor.fromRgb(200, 100, 0) else PixelColor.Transparent,
                                    borderColor = PixelColor.White,
                                    child = Padding(
                                        child = Text(
                                            m.text,
                                            style = if (m.fromMe) TextStyle.Default else TextStyle.Default,
                                            softWrap = true,
                                        ),
                                        horizontal = 4, vertical = 2,
                                    ),
                                )
                                Padding(
                                    child = Row(
                                        children = listOf(bubble),
                                        mainAxisAlignment = if (m.fromMe) MainAxisAlignment.END else MainAxisAlignment.START,
                                    ),
                                    horizontal = 4, vertical = 2,
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
                                            placeholder = "输入消息…",
                                        ),
                                    ),
                                    SizedBox(width = 4),
                                    OutlinedButton("SEND", onPressed = ::send),
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
