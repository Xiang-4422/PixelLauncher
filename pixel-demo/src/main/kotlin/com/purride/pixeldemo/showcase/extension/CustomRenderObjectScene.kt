package com.purride.pixeldemo.showcase.extension
import com.purride.pixelcore.PixelColor


import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object CustomRenderObjectScene : DemoScene {
    override val id = "custom_render_object"
    override val title = "自定义 RenderObject"
    override val description = "PixelLeafRenderObjectWidget 扩展点演示"

    override fun build(env: DemoEnv): Widget = CustomRenderObjectWidget()
}

private val sideOptions = listOf(20, 40, 60)
private val toneOptions = listOf(PixelColor.White, PixelColor.fromRgb(200, 100, 0), PixelColor.Transparent)
private val toneLabels = listOf("ON", "ACCENT", "OFF")

private class CustomRenderObjectWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = CustomRenderObjectState()

    class CustomRenderObjectState : State<CustomRenderObjectWidget>() {
        private var sideIdx = 1
        private var toneIdx = 0

        override fun build(context: BuildContext): Widget {
            val sideControls = sideOptions.mapIndexed { i, s ->
                OutlinedButton("${s}px", onPressed = { setState { sideIdx = i } }, borderColor = if (i == sideIdx) PixelColor.fromRgb(200, 100, 0) else PixelColor.White)
            }
            val toneControls = toneOptions.mapIndexed { i, _ ->
                OutlinedButton(toneLabels[i], onPressed = { setState { toneIdx = i } }, borderColor = if (i == toneIdx) PixelColor.fromRgb(200, 100, 0) else PixelColor.White)
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = Center(
                            child = HollowSquareWidget(
                                side = sideOptions[sideIdx],
                                color = toneOptions[toneIdx],
                            ),
                        ),
                    ),
                    SizedBox(height = 2),
                    Text("Side", style = TextStyle.Default),
                    Row(children = sideControls, spacing = 2),
                    SizedBox(height = 2),
                    Text("Tone", style = TextStyle.Default),
                    Row(children = toneControls, spacing = 2),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
