package com.purride.pixeldemo.showcase.interaction

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.ButtonStyle
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
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object ButtonStatesScene : DemoScene {
    override val id = "button_states"
    override val title = "按钮状态矩阵"
    override val description = "Default / Accent / disabled / active 四种视觉状态"

    override fun build(env: DemoEnv): Widget = ButtonStatesWidget()
}

private val accentStyle = ButtonStyle(
    borderColor = PixelColor.fromRgb(200, 100, 0),
    textStyle = com.purride.pixelui.TextStyle(color = PixelColor.fromRgb(200, 100, 0)),
)

private class ButtonStatesWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ButtonStatesState()

    class ButtonStatesState : State<ButtonStatesWidget>() {
        private val scrollState = PixelListState()
        private val scrollCtrl = ScrollController()
        private var useAccent = false

        override fun build(context: BuildContext): Widget {
            val style = if (useAccent) accentStyle else ButtonStyle.Default

            return SingleChildScrollView(
                state = scrollState,
                controller = scrollCtrl,
                child = Padding(
                    child = Column(
                        children = listOf(
                            Text("单次渲染示例", style = TextStyle.Default),
                            SizedBox(height = 4),
                            Row(
                                children = listOf(
                                    OutlinedButton("Default", onPressed = {}),
                                    OutlinedButton("Active", onPressed = {}, borderColor = PixelColor.fromRgb(200, 100, 0)),
                                    OutlinedButton("Disabled", onPressed = {}, enabled = false),
                                ),
                                spacing = 4,
                            ),
                            SizedBox(height = 8),
                            Text("切换样式", style = TextStyle.Default),
                            SizedBox(height = 4),
                            Row(
                                children = listOf(
                                    OutlinedButton("Style A", onPressed = {}, style = style),
                                    OutlinedButton("Style B", onPressed = {}, style = style, fillColor = PixelColor.fromRgb(30, 30, 30)),
                                ),
                                spacing = 4,
                            ),
                            SizedBox(height = 8),
                            OutlinedButton(
                                text = "STYLE: ${if (useAccent) "Accent" else "Default"}",
                                onPressed = { setState { useAccent = !useAccent } },
                                borderColor = if (useAccent) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                            ),
                        ),
                        spacing = 2,
                        crossAxisAlignment = CrossAxisAlignment.START,
                    ),
                    all = 8,
                ),
            )
        }
    }
}
