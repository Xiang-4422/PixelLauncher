package com.purride.pixeldemo.showcase.interaction

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
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

object TextInputHostCommandsScene : DemoScene {
    override val id = "text_input_host_commands"
    override val title = "宿主侧输入命令"
    override val description = "update / clear / submit 宿主输入命令"

    override fun build(env: DemoEnv): Widget = TextInputHostCommandsWidget(env)
}

private class TextInputHostCommandsWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TextInputHostCommandsState()

    inner class TextInputHostCommandsState : State<TextInputHostCommandsWidget>() {
        private val scrollState = PixelListState()
        private val scrollCtrl = ScrollController()
        private val fieldState = PixelTextFieldState()
        private val controller = TextEditingController()
        private var lastCommand = "—"

        override fun build(context: BuildContext): Widget = SingleChildScrollView(
            state = scrollState,
            controller = scrollCtrl,
            child = Padding(
                child = Column(
                    children = listOf(
                        Text("聚焦 TextField 后点击下方按钮", style = TextStyle.Default, softWrap = true),
                        SizedBox(height = 4),
                        TextField(
                            state = fieldState,
                            controller = controller,
                            placeholder = "聚焦这里…",
                            autofocus = false,
                        ),
                        SizedBox(height = 8),
                        Text("上次命令: $lastCommand", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                        SizedBox(height = 4),
                        Row(
                            children = listOf(
                                OutlinedButton(
                                    text = "update",
                                    onPressed = {
                                        widget.env.hostView.updateFocusedTextInput("HOST_UPDATE")
                                        setState { lastCommand = "updateFocusedTextInput" }
                                    },
                                ),
                                OutlinedButton(
                                    text = "clear",
                                    onPressed = {
                                        widget.env.hostView.clearFocusedTextInput()
                                        setState { lastCommand = "clearFocusedTextInput" }
                                    },
                                ),
                                OutlinedButton(
                                    text = "submit",
                                    onPressed = {
                                        widget.env.hostView.submitFocusedTextInput()
                                        setState { lastCommand = "submitFocusedTextInput" }
                                    },
                                ),
                            ),
                            spacing = 4,
                        ),
                    ),
                    spacing = 2,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                ),
                all = 8,
            ),
        )
    }
}
