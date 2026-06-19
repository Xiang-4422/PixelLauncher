package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.ActivityIndicator
import com.purride.pixelui.Badge
import com.purride.pixelui.ButtonStyle
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Divider
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Gap
import com.purride.pixelui.Icon
import com.purride.pixelui.ListTile
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Row
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.Slider
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle

object ControlShowcaseScene : DemoScene {
    override val id = "components_controls"
    override val title = "交互控件"
    override val summary = "按钮、选择控件、分段、进度、徽标和图标"
    override val category = DemoCatalog.controls
    override val tags = setOf("button", "checkbox", "switch", "tabs", "slider", "progress", "icon")
    override val apis = setOf(
        "OutlinedButton",
        "ListTile",
        "ButtonStyle",
        "PixelButtonStyle",
        "Checkbox",
        "Switch",
        "Tabs",
        "SegmentedControl",
        "Slider",
        "ProgressBar",
        "ActivityIndicator",
        "Badge",
        "Divider",
        "Gap",
        "Icon",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = ControlBody())
}

private class ControlBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ControlState()

    private class ControlState : State<ControlBody>() {
        private var checked = true
        private var switched = false
        private var tab = 1
        private var segment = 0
        private var slider = 0.62f

        override fun build(context: com.purride.pixelui.BuildContext): Widget {
            val explicitStyle: PixelButtonStyle = ButtonStyle(
                fillColor = PixelColor.fromRgb(24, 18, 4),
                borderColor = Yellow,
                textStyle = PixelTextStyle(color = Yellow),
            )
            return Column(
                children = listOf(
                    sectionTitle("按钮与列表行"),
                    samplePanel(
                        title = "OutlinedButton / ListTile / Badge / Icon",
                        color = Green,
                        child = Column(
                            children = listOf(
                                Row(
                                    children = listOf(
                                        OutlinedButton(text = "PRIMARY", onPressed = {}, borderColor = Accent),
                                        OutlinedButton(text = "STYLE", onPressed = {}, style = explicitStyle),
                                        OutlinedButton(text = "DISABLED", onPressed = null, enabled = false),
                                        Badge(
                                            child = OutlinedButton(text = "MAIL", onPressed = {}, borderColor = Pink),
                                            label = Text("3", style = TextStyle(color = PixelColor.White)),
                                        ),
                                    ),
                                    spacing = 2,
                                ),
                                ListTile(
                                    leading = Icon(PixelIconData(tinyIcon())),
                                    title = Text("ListTile", style = TextStyle(color = Cyan)),
                                    subtitle = Text("leading / title / trailing", style = TextStyle(color = Muted)),
                                    trailing = Text(">", style = TextStyle(color = Accent)),
                                    onTap = {},
                                ),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    sectionTitle("选择与进度"),
                    samplePanel(
                        title = "Checkbox / Switch / Slider",
                        color = Cyan,
                        child = Column(
                            children = listOf(
                                Row(
                                    children = listOf(
                                        Checkbox(checked = checked, onChanged = { value -> checked = value; setState {} }, activeColor = Green),
                                        Text(if (checked) "checked" else "unchecked", style = TextStyle(color = Green)),
                                        Gap(width = 4),
                                        Switch(checked = switched, onChanged = { value -> switched = value; setState {} }),
                                        Text(if (switched) "on" else "off", style = TextStyle(color = Blue)),
                                    ),
                                    spacing = 2,
                                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                                ),
                                Slider(
                                    value = slider,
                                    onDrag = { value -> slider = value; setState {} },
                                    onRelease = { value -> slider = value; setState {} },
                                    activeColor = Accent,
                                ),
                                ProgressBar(progress = slider, width = 72, color = Accent),
                                Row(
                                    children = listOf(
                                        ActivityIndicator(frame = (slider * 4).toInt(), color = Yellow),
                                        Text("${(slider * 100).toInt()}%", style = TextStyle(color = Yellow)),
                                    ),
                                    spacing = 2,
                                ),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    samplePanel(
                        title = "Tabs / SegmentedControl",
                        color = Pink,
                        child = Column(
                            children = listOf(
                                Tabs(
                                    labels = listOf("A", "B", "C"),
                                    selectedIndex = tab,
                                    onSelected = { index -> tab = index; setState {} },
                                ),
                                SegmentedControl(
                                    labels = listOf("DAY", "NIGHT", "AUTO"),
                                    selectedIndex = segment,
                                    onSelected = { index -> segment = index; setState {} },
                                ),
                                Divider(color = Pink),
                                Container(
                                    padding = EdgeInsets.all(2),
                                    borderColor = Pink,
                                    child = Text("tab=$tab segment=$segment", style = TextStyle(color = Pink)),
                                ),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
                spacing = 4,
                mainAxisSize = MainAxisSize.MIN,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }

        private fun tinyIcon(): PixelBitmap {
            val clear = PixelColor.Transparent.argb
            val c = Accent.argb
            val pixels = intArrayOf(
                clear, clear, c, c, c, clear, clear,
                clear, c, clear, clear, clear, c, clear,
                c, clear, c, clear, c, clear, c,
                c, clear, clear, c, clear, clear, c,
                c, clear, c, clear, c, clear, c,
                clear, c, clear, clear, clear, c, clear,
                clear, clear, c, c, c, clear, clear,
            )
            return PixelBitmap(width = 7, height = 7, pixels = pixels)
        }
    }
}
