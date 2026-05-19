package com.purride.pixeldemo.showcase.theme

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.withTokens
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object ThemeTokensScene : DemoScene {
    override val id = "theme_tokens"
    override val title = "主题 token 调参"
    override val description = "切换 tone / spacing 维度，观察 ThemeData.withTokens 效果"

    override fun build(env: DemoEnv): Widget = ThemeTokensWidget()
}

private val tokenVariants = listOf(
    "Default" to PixelThemeData(),
    "Accent Text" to PixelThemeData().withTokens { copy(textTone = PixelTone.ACCENT) },
    "Off Surface" to PixelThemeData().withTokens { copy(surfaceTone = PixelTone.ON) },
)

private class ThemeTokensWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ThemeTokensState()

    class ThemeTokensState : State<ThemeTokensWidget>() {
        private var variantIdx = 0

        override fun build(context: BuildContext): Widget {
            val (label, theme) = tokenVariants[variantIdx]
            val controls = tokenVariants.mapIndexed { i, (name, _) ->
                OutlinedButton(
                    text = name,
                    onPressed = { setState { variantIdx = i } },
                    selected = i == variantIdx,
                )
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = Padding(
                            child = Column(
                                children = listOf(
                                    Text("Variant: $label", style = TextStyle.Accent),
                                    SizedBox(height = 4),
                                    Text("普通文本", style = TextStyle.Default, theme = theme),
                                    Text("强调文本", style = TextStyle.Accent, theme = theme),
                                    SizedBox(height = 4),
                                    Container(
                                        width = 60,
                                        height = 20,
                                        fillTone = PixelTone.OFF,
                                        borderTone = PixelTone.ON,
                                        theme = theme,
                                        child = Text("Box", style = TextStyle.Default, theme = theme),
                                    ),
                                    SizedBox(height = 4),
                                    OutlinedButton("按钮", onPressed = {}, theme = theme),
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
