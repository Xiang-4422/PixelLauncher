package com.purride.pixeldemo.showcase.interaction

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.ListTile
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Row
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Switch
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object SelectionControlsScene : DemoScene {
    override val id = "selection_controls"
    override val title = "Selection Controls"
    override val description = "ListTile / Checkbox / Switch 基础状态"

    override fun build(env: DemoEnv): Widget = SelectionControlsWidget()
}

private class SelectionControlsWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SelectionControlsState()

    class SelectionControlsState : State<SelectionControlsWidget>() {
        private var checkbox = true
        private var switch = false
        private var taps = 0

        override fun build(context: BuildContext): Widget {
            return Column(
                children = listOf(
                    ListTile(
                        leading = Checkbox(checked = checkbox, onChanged = { setState { checkbox = it } }),
                        title = Text("Checkbox", style = TextStyle.Default),
                        subtitle = Text(if (checkbox) "checked" else "unchecked", style = TextStyle(color = PixelColor.fromRgb(180, 180, 180))),
                        onTap = { setState { checkbox = !checkbox } },
                    ),
                    ListTile(
                        leading = Switch(checked = switch, onChanged = { setState { switch = it } }),
                        title = Text("Switch", style = TextStyle.Default),
                        subtitle = Text(if (switch) "on" else "off", style = TextStyle(color = PixelColor.fromRgb(180, 180, 180))),
                        onTap = { setState { switch = !switch } },
                    ),
                    Row(
                        spacing = 2,
                        children = listOf(
                            Text("Tile taps:", style = TextStyle.Default),
                            Text("$taps", style = TextStyle(color = PixelColor.fromRgb(80, 180, 110))),
                        ),
                    ),
                    ListTile(
                        title = Text("Tap target", style = TextStyle.Default),
                        trailing = Text(">", style = TextStyle.Default),
                        onTap = { setState { taps++ } },
                    ),
                ),
                spacing = 2,
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
