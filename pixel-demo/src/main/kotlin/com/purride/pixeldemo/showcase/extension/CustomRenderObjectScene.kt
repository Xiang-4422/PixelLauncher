package com.purride.pixeldemo.showcase.extension

import com.purride.pixelcore.PixelTone
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
    override val description = "复用 HollowSquareWidget 演示 PixelLeafRenderObjectWidget 扩展点"

    override fun build(env: DemoEnv): Widget = CustomRenderObjectWidget()
}

private val sideOptions = listOf(20, 40, 60)
private val toneOptions = listOf(PixelTone.ON, PixelTone.ACCENT, PixelTone.OFF)
private val toneLabels = listOf("ON", "ACCENT", "OFF")

private class CustomRenderObjectWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = CustomRenderObjectState()

    class CustomRenderObjectState : State<CustomRenderObjectWidget>() {
        private var sideIdx = 1
        private var toneIdx = 0

        override fun build(context: BuildContext): Widget {
            val sideControls = sideOptions.mapIndexed { i, s ->
                OutlinedButton("${s}px", onPressed = { setState { sideIdx = i } }, selected = i == sideIdx)
            }
            val toneControls = toneOptions.mapIndexed { i, _ ->
                OutlinedButton(toneLabels[i], onPressed = { setState { toneIdx = i } }, selected = i == toneIdx)
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = Center(
                            child = HollowSquareWidget(
                                side = sideOptions[sideIdx],
                                tone = toneOptions[toneIdx],
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
