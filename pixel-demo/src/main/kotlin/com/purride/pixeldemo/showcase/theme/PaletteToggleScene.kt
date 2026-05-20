package com.purride.pixeldemo.showcase.theme

import com.purride.pixelcore.PixelPalette
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelTheme
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
    override val title = "调色板与像素形状"
    override val description = "切换 PixelTheme 全枚举 + PixelShape 全枚举"

    override fun build(env: DemoEnv): Widget = PaletteToggleWidget(env)
}

private class PaletteToggleWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = PaletteToggleState()

    inner class PaletteToggleState : State<PaletteToggleWidget>() {
        private val themes = PixelTheme.entries
        private val shapes = PixelShape.entries
        private var themeIdx = 0
        private var shapeIdx = 0

        override fun build(context: BuildContext): Widget {
            // 主题按钮拆成 3+2 两行，避免 5 个按钮单行溢出
            fun themeBtn(i: Int, t: PixelTheme) = OutlinedButton(
                text = t.name.take(4),
                onPressed = {
                    setState { themeIdx = i }
                    widget.env.hostView.setPalette(PixelPalette.fromTheme(themes[i]))
                },
                selected = i == themeIdx,
            )
            val themeRow1 = themes.take(3).mapIndexed { i, t -> themeBtn(i, t) }
            val themeRow2 = themes.drop(3).mapIndexed { i, t -> themeBtn(3 + i, t) }

            // 形状按钮缩写为 3 字符，3 个按钮单行可容纳
            val shapeControls = shapes.mapIndexed { i, s ->
                OutlinedButton(
                    text = s.name.take(3),
                    onPressed = {
                        setState { shapeIdx = i }
                        widget.env.applyPreferredProfile(
                            PixelHostProfilePreference(dotSizePx = 4, pixelShape = shapes[i]),
                        )
                    },
                    selected = i == shapeIdx,
                )
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = Padding(
                            child = Column(
                                children = listOf(
                                    Text("Theme: ${themes[themeIdx].name}", style = TextStyle.Accent),
                                    Text("Shape: ${shapes[shapeIdx].name}", style = TextStyle.Default),
                                ),
                                spacing = 4,
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                            ),
                            all = 8,
                        ),
                    ),
                    SizedBox(height = 2),
                    Text("PixelTheme", style = TextStyle.Default),
                    Row(children = themeRow1, spacing = 2),
                    SizedBox(height = 2),
                    Row(children = themeRow2, spacing = 2),
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
