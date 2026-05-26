package com.purride.pixeldemo.showcase.interaction

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.FocusScope
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelKey
import com.purride.pixelui.Row
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object FocusKeyScene : DemoScene {
    override val id = "focus_key"
    override val title = "Focus + Keys"
    override val description = "FocusScope traversal and key event routing"

    override fun build(env: DemoEnv): Widget = FocusKeyWidget()
}

private class FocusKeyWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FocusKeyState()

    private class FocusKeyState : State<FocusKeyWidget>() {
        private val first = FocusNode("first")
        private val second = FocusNode("second")
        private val third = FocusNode("third")
        private var last = "TAB / DPAD"

        override fun build(context: BuildContext): Widget {
            return FocusScope(
                child = Column(
                    spacing = 3,
                    children = listOf(
                        Text("FOCUS ROUTE", style = TextStyle(color = PixelColor.fromRgb(180, 180, 180))),
                        Row(
                            spacing = 2,
                            children = listOf(
                                focusButton(first, "ONE"),
                                focusButton(second, "TWO"),
                                focusButton(third, "THREE"),
                            ),
                        ),
                        Text("focused=${focusedLabel()}"),
                        Text("last=$last"),
                    ),
                ),
            )
        }

        private fun focusButton(node: FocusNode, label: String): Widget {
            val color = if (node.isFocused) PixelColor.fromRgb(255, 255, 0) else PixelColor.White
            return Focus(
                node = node,
                autofocus = node === first,
                onKeyEvent = { event ->
                    if (event.key == PixelKey.ENTER) {
                        setState { last = label }
                        true
                    } else {
                        false
                    }
                },
                child = OutlinedButton(
                    text = label,
                    onPressed = { setState { node.requestFocus(); last = label } },
                    borderColor = color,
                ),
            )
        }

        private fun focusedLabel(): String = when {
            first.isFocused -> "ONE"
            second.isFocused -> "TWO"
            third.isFocused -> "THREE"
            else -> "NONE"
        }
    }
}
