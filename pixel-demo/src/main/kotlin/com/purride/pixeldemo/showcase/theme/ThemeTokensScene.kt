package com.purride.pixeldemo.showcase.theme

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
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

object ThemeTokensScene : DemoScene {
    override val id = "theme_tokens"
    override val title = "颜色方案"
    override val description = "切换显式颜色方案，观察 PixelColor 直接驱动渲染效果"

    override fun build(env: DemoEnv): Widget = ColorSchemeWidget()
}

private data class ColorScheme(
    val label: String,
    val primary: PixelColor,
    val accent: PixelColor,
    val surface: PixelColor,
)

private val schemes = listOf(
    ColorScheme("Default", PixelColor.White, PixelColor.fromRgb(200, 100, 0), PixelColor.Transparent),
    ColorScheme("Ocean", PixelColor.fromRgb(0, 200, 255), PixelColor.fromRgb(0, 100, 200), PixelColor.Transparent),
    ColorScheme("Amber", PixelColor.fromRgb(255, 200, 0), PixelColor.fromRgb(200, 100, 0), PixelColor.Transparent),
)

private class ColorSchemeWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ColorSchemeState()

    class ColorSchemeState : State<ColorSchemeWidget>() {
        private var schemeIdx = 0

        override fun build(context: BuildContext): Widget {
            val scheme = schemes[schemeIdx]
            val controls = schemes.mapIndexed { i, s ->
                OutlinedButton(
                    text = s.label,
                    onPressed = { setState { schemeIdx = i } },
                    borderColor = if (i == schemeIdx) scheme.accent else scheme.primary,
                )
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = Padding(
                            child = Column(
                                children = listOf(
                                    Text("Scheme: ${scheme.label}", style = TextStyle(color = scheme.primary)),
                                    SizedBox(height = 4),
                                    Text("主色文本", style = TextStyle(color = scheme.primary)),
                                    Text("强调文本", style = TextStyle(color = scheme.accent)),
                                    SizedBox(height = 4),
                                    Container(
                                        width = 60,
                                        height = 20,
                                        fillColor = scheme.surface,
                                        borderColor = scheme.primary,
                                        child = Text("Box", style = TextStyle(color = scheme.primary)),
                                    ),
                                    SizedBox(height = 4),
                                    OutlinedButton(
                                        "按钮",
                                        onPressed = {},
                                        borderColor = scheme.accent,
                                    ),
                                ),
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.START,
                            ),
                            all = 8,
                        ),
                    ),
                    SizedBox(height = 2),
                    Row(children = controls, spacing = 2),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
