package com.purride.pixeldemo.showcase.extension

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.DefaultTextRasterizer
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

object CustomRasterizerScene : DemoScene {
    override val id = "custom_rasterizer"
    override val title = "自定义 Rasterizer"
    override val description = "DefaultTextRasterizer 继承树覆盖，切换 8px / 10px 字形"

    override fun build(env: DemoEnv): Widget = CustomRasterizerWidget(env)
}

private class CustomRasterizerWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = CustomRasterizerState()

    inner class CustomRasterizerState : State<CustomRasterizerWidget>() {
        private var useEmphasis = false

        override fun build(context: BuildContext): Widget {
            val rasterizer = if (useEmphasis) widget.env.rasterizers.emphasis else widget.env.rasterizers.default
            val label = if (useEmphasis) "10px emphasis" else "8px default"
            return Column(
                children = listOf(
                    Expanded(
                        child = DefaultTextRasterizer(
                            rasterizer = rasterizer,
                            child = Padding(
                                child = Column(
                                    children = listOf(
                                        Text("Rasterizer: $label", style = TextStyle.Accent),
                                        SizedBox(height = 4),
                                        Text("Hello World — 栅格文字渲染", style = TextStyle.Default),
                                        SizedBox(height = 2),
                                        Text("ABCDEFGHIJKLMNOPQRSTUVWXYZ", style = TextStyle.Default),
                                        SizedBox(height = 2),
                                        Text("0123456789 !@#\$%^&*()", style = TextStyle.Default),
                                        SizedBox(height = 2),
                                        Text("中文字符：你好世界", style = TextStyle.Default),
                                        SizedBox(height = 2),
                                        Text("混排 Mixed: Pixel Engine 像素引擎", style = TextStyle.Default, softWrap = true),
                                    ),
                                    spacing = 1,
                                    crossAxisAlignment = CrossAxisAlignment.START,
                                ),
                                all = 8,
                            ),
                        ),
                    ),
                    SizedBox(height = 2),
                    Row(
                        children = listOf(
                            OutlinedButton("8px default", onPressed = { setState { useEmphasis = false } }, selected = !useEmphasis),
                            OutlinedButton("10px emphasis", onPressed = { setState { useEmphasis = true } }, selected = useEmphasis),
                        ),
                        spacing = 2,
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
