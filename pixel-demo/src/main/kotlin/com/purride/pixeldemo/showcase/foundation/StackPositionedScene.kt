package com.purride.pixeldemo.showcase.foundation
import com.purride.pixelcore.PixelColor


import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Positioned
import com.purride.pixelui.PositionedFill
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object StackPositionedScene : DemoScene {
    override val id = "stack_positioned"
    override val title = "Stack 与 Positioned"
    override val description = "角标 / 弹层 / 铺满三种典型叠层用法"

    override fun build(env: DemoEnv): Widget = StackPositionedWidget()
}

private val modeLabels = listOf("BADGES", "MODAL", "FILL")

private class StackPositionedWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = StackPositionedState()

    class StackPositionedState : State<StackPositionedWidget>() {
        private var modeIndex = 0

        override fun build(context: BuildContext): Widget {
            val base = Container(
                width = 160,
                height = 100,
                fillColor = PixelColor.Transparent,
                borderColor = PixelColor.White,
                child = Center(child = Text("BASE", style = TextStyle.Default)),
            )

            val content: Widget = when (modeIndex) {
                0 -> Stack(
                    children = listOf(
                        base,
                        Positioned(
                            left = 0, top = 0,
                            child = Container(width = 12, height = 12, fillColor = PixelColor.fromRgb(200, 100, 0), borderColor = null),
                        ),
                        Positioned(
                            right = 0, top = 0,
                            child = Container(width = 12, height = 12, fillColor = PixelColor.fromRgb(200, 100, 0), borderColor = null),
                        ),
                        Positioned(
                            left = 0, bottom = 0,
                            child = Container(width = 12, height = 12, fillColor = PixelColor.fromRgb(200, 100, 0), borderColor = null),
                        ),
                        Positioned(
                            right = 0, bottom = 0,
                            child = Container(width = 12, height = 12, fillColor = PixelColor.fromRgb(200, 100, 0), borderColor = null),
                        ),
                    ),
                )
                1 -> Stack(
                    alignment = Alignment.CENTER,
                    children = listOf(
                        base,
                        Positioned(
                            left = 30, top = 30, right = 30, bottom = 30,
                            child = Container(
                                fillColor = PixelColor.White,
                                borderColor = PixelColor.fromRgb(200, 100, 0),
                                child = Center(child = Text("DIALOG", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0)))),
                            ),
                        ),
                    ),
                )
                else -> Stack(
                    children = listOf(
                        base,
                        PositionedFill(
                            child = Container(
                                fillColor = PixelColor.fromRgb(200, 100, 0),
                                borderColor = PixelColor.White,
                                child = Center(child = Text("FILL", style = TextStyle.Default)),
                            ),
                        ),
                    ),
                )
            }

            val controls = modeLabels.mapIndexed { i, label ->
                OutlinedButton(
                    text = label,
                    onPressed = { setState { modeIndex = i } },
                    borderColor = if (i == modeIndex) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                )
            }

            return Column(
                children = listOf(
                    Expanded(child = Center(child = content)),
                    SizedBox(height = 2),
                    Padding(
                        child = Row(
                            children = controls,
                            spacing = 2,
                            mainAxisAlignment = MainAxisAlignment.CENTER,
                        ),
                        horizontal = 4,
                        vertical = 2,
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
