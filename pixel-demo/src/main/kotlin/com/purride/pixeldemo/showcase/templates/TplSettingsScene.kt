package com.purride.pixeldemo.showcase.templates
import com.purride.pixelcore.PixelColor


import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object TplSettingsScene : DemoScene {
    override val id = "tpl_settings"
    override val title = "模板 · 设置页"
    override val description = "分组标题 + 开关行 + 选项行 — 完整的滚动设置页模板"

    override fun build(env: DemoEnv): Widget = TplSettingsWidget()
}

private class TplSettingsWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TplSettingsState()

    class TplSettingsState : State<TplSettingsWidget>() {
        private val scrollState = PixelListState()
        private val scrollCtrl = ScrollController()
        private var notifications = true
        private var sound = false
        private var hapticOn = true
        private var brightness = 2
        private var fontSize = 1

        override fun build(context: BuildContext): Widget {
            fun groupTitle(text: String) = Padding(
                child = Text(text, style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                horizontal = 4, vertical = 3,
            )

            fun toggleRow(label: String, value: Boolean, onToggle: () -> Unit): Widget =
                GestureDetector(
                    onTap = onToggle,
                    child = Container(
                        fillColor = PixelColor.Transparent,
                        borderColor = PixelColor.White,
                        child = Padding(
                            child = Row(
                                children = listOf(
                                    Expanded(child = Text(label, style = TextStyle.Default)),
                                    Text(
                                        if (value) "[ON]" else "[OFF]",
                                        style = if (value) TextStyle(color = PixelColor.fromRgb(200, 100, 0)) else TextStyle.Default,
                                    ),
                                ),
                                spacing = 2,
                            ),
                            horizontal = 4, vertical = 3,
                        ),
                    ),
                )

            fun optionRow(label: String, options: List<String>, idx: Int, onSelect: (Int) -> Unit): Widget {
                val buttons = options.mapIndexed { i, opt ->
                    OutlinedButton(
                        text = opt,
                        onPressed = { onSelect(i) },
                        borderColor = if (i == idx) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                    )
                }
                return Container(
                    fillColor = PixelColor.Transparent,
                    borderColor = PixelColor.White,
                    child = Padding(
                        child = Column(
                            children = listOf(
                                Text(label, style = TextStyle.Default),
                                SizedBox(height = 2),
                                Row(
                                    children = buttons,
                                    spacing = 2,
                                    mainAxisAlignment = MainAxisAlignment.START,
                                ),
                            ),
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                        horizontal = 4, vertical = 3,
                    ),
                )
            }

            val rows: List<Widget> = listOf(
                groupTitle("通知"),
                toggleRow("启用通知", notifications) { setState { notifications = !notifications } },
                SizedBox(height = 2),
                toggleRow("通知声音", sound) { setState { sound = !sound } },

                groupTitle("反馈"),
                toggleRow("触感震动", hapticOn) { setState { hapticOn = !hapticOn } },

                groupTitle("显示"),
                optionRow("亮度", listOf("L", "M", "H"), brightness.coerceIn(0, 2)) {
                    setState { brightness = it }
                },
                SizedBox(height = 2),
                optionRow("字号", listOf("S", "M", "L"), fontSize.coerceIn(0, 2)) {
                    setState { fontSize = it }
                },

                groupTitle("关于"),
                Container(
                    fillColor = PixelColor.Transparent,
                    borderColor = PixelColor.White,
                    child = Padding(
                        child = Column(
                            children = listOf(
                                Text("pixel-demo", style = TextStyle.Default),
                                Text("v1.0.0", style = TextStyle.Default),
                                Text("powered by pixel-engine", style = TextStyle.Default),
                            ),
                            crossAxisAlignment = CrossAxisAlignment.START,
                            spacing = 1,
                        ),
                        horizontal = 4, vertical = 3,
                    ),
                ),
                SizedBox(height = 8),
            )

            return SingleChildScrollView(
                state = scrollState,
                controller = scrollCtrl,
                child = Padding(
                    child = Column(
                        children = rows,
                        spacing = 2,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                    all = 4,
                ),
            )
        }
    }
}
