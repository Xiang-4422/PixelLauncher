package com.purride.pixeldemo.showcase.theme

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelShape
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
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

object PaletteToggleScene : DemoScene {
    override val id = "palette_toggle"
    override val title = "像素形状"
    override val description = "切换 PixelShape 全枚举，观察不同点阵渲染风格"

    override fun build(env: DemoEnv): Widget = ShapeToggleWidget(env)
}

private class ShapeToggleWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ShapeToggleState()

    inner class ShapeToggleState : State<ShapeToggleWidget>() {
        private val shapes = PixelShape.entries
        private var shapeIdx = 0

        override fun build(context: BuildContext): Widget {
            val shapeControls = shapes.mapIndexed { i, s ->
                OutlinedButton(
                    text = s.name.take(3),
                    onPressed = {
                        setState { shapeIdx = i }
                        widget.env.applyPreferredProfile(
                            PixelHostProfilePreference(dotSizePx = 12, pixelShape = shapes[i]),
                        )
                    },
                    borderColor = if (i == shapeIdx) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                )
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = Padding(
                            child = Column(
                                children = listOf(
                                    Text("Shape: ${shapes[shapeIdx].name}", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                                    Text("像素形状演示", style = TextStyle.Default),
                                ),
                                spacing = 4,
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                            ),
                            all = 8,
                        ),
                    ),
                    SizedBox(height = 2),
                    Text("PixelShape", style = TextStyle.Default),
                    Row(children = shapeControls, spacing = 2),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
