package com.purride.pixeldemo.showcase.interaction

import com.purride.pixelui.BuildContext
import com.purride.pixelui.ButtonStyle
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object ButtonStatesScene : DemoScene {
    override val id = "button_states"
    override val title = "按钮状态矩阵"
    override val description = "Default / Accent / disabled / selected / pressed 五种状态"

    override fun build(env: DemoEnv): Widget = ButtonStatesWidget()
}

private val stateSpecs = listOf(
    Triple("Default", false, false),
    Triple("Selected", false, true),
    Triple("Pressed", false, false),
)

private class ButtonStatesWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ButtonStatesState()

    class ButtonStatesState : State<ButtonStatesWidget>() {
        private var styleIdx = 0

        override fun build(context: BuildContext): Widget {
            fun label(text: String) = Text(text, style = TextStyle.Default)

            val style = if (styleIdx == 0) ButtonStyle.Default else ButtonStyle.Accent

            return Column(
                children = listOf(
                    Row(
                        children = listOf(
                            OutlinedButton("Default", onPressed = {}, style = style, enabled = true, selected = false, pressed = false),
                            OutlinedButton("Accent", onPressed = {}, style = ButtonStyle.Accent, enabled = true, selected = false, pressed = false),
                            OutlinedButton("Disabled", onPressed = {}, enabled = false),
                        ),
                        spacing = 4,
                    ),
                    SizedBox(height = 4),
                    Row(
                        children = listOf(
                            OutlinedButton("Selected", onPressed = {}, style = style, selected = true),
                            OutlinedButton("Pressed", onPressed = {}, style = style, pressed = true),
                        ),
                        spacing = 4,
                    ),
                    SizedBox(height = 8),
                    Row(
                        children = listOf(
                            OutlinedButton(
                                text = "STYLE: ${if (styleIdx == 0) "Default" else "Accent"}",
                                onPressed = { setState { styleIdx = 1 - styleIdx } },
                                selected = false,
                            ),
                        ),
                        spacing = 2,
                    ),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.START,
            )
        }
    }
}
