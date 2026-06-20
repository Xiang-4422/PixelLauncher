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
import com.purride.pixelui.PixelTextButtonStyle
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Row
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.Slider
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextButtonStyle
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

object ButtonListScene : DemoScene {
    override val id = "controls_buttons_list"
    override val title = "按钮列表"
    override val summary = "TextButton、OutlinedButton、按钮样式和 ListTile"
    override val category = DemoCatalog.controls
    override val tags = setOf("button", "listtile", "style")
    override val apis = setOf(
        "TextButton",
        "TextButtonStyle",
        "PixelTextButtonStyle",
        "OutlinedButton",
        "ButtonStyle",
        "PixelButtonStyle",
        "ListTile",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget {
        val explicitStyle: PixelButtonStyle = ButtonStyle(
            fillColor = PixelColor.fromRgb(24, 18, 4),
            borderColor = Yellow,
            textStyle = PixelTextStyle(color = Yellow),
        )
        val textButtonStyle: PixelTextButtonStyle = TextButtonStyle(
            textStyle = PixelTextStyle(color = Accent),
        )
        return ComponentShowcaseScaffold(
            item = this,
            env = env,
            body = controlPageBody(
                listOf(
                    sectionTitle("命令控件"),
                    samplePanel(
                        title = "TextButton / OutlinedButton",
                        color = Green,
                        child = Row(
                            children = listOf(
                                TextButton(text = "TEXT", onPressed = {}, style = textButtonStyle),
                                OutlinedButton(text = "PRIMARY", onPressed = {}, borderColor = Accent),
                                OutlinedButton(text = "STYLE", onPressed = {}, style = explicitStyle),
                                OutlinedButton(text = "OFF", onPressed = null, enabled = false),
                            ),
                            spacing = 2,
                        ),
                    ),
                    samplePanel(
                        title = "ListTile",
                        color = Cyan,
                        child = ListTile(
                            leading = Icon(PixelIconData(controlTinyIcon())),
                            title = Text("ListTile", style = TextStyle(color = Cyan)),
                            subtitle = Text("leading / title / trailing", style = TextStyle(color = Muted)),
                            trailing = Text(">", style = TextStyle(color = Accent)),
                            onTap = {},
                        ),
                    ),
                ),
            ),
        )
    }
}

object SelectionSwitchScene : DemoScene {
    override val id = "controls_selection_switch"
    override val title = "选择开关"
    override val summary = "Checkbox 与 Switch 二元状态控件"
    override val category = DemoCatalog.controls
    override val tags = setOf("checkbox", "switch", "selection")
    override val apis = setOf("Checkbox", "Switch")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = SelectionSwitchBody())
}

object SegmentedTabsScene : DemoScene {
    override val id = "controls_segmented_tabs"
    override val title = "分段标签"
    override val summary = "Tabs 与 SegmentedControl 模式切换"
    override val category = DemoCatalog.controls
    override val tags = setOf("tabs", "segmented", "mode")
    override val apis = setOf("Tabs", "SegmentedControl")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = SegmentedTabsBody())
}

object SliderProgressScene : DemoScene {
    override val id = "controls_slider_progress"
    override val title = "滑块进度"
    override val summary = "Slider、ProgressBar 与 ActivityIndicator"
    override val category = DemoCatalog.controls
    override val tags = setOf("slider", "progress", "activity")
    override val apis = setOf("Slider", "ProgressBar", "ActivityIndicator")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = SliderProgressBody())
}

object BadgeIconScene : DemoScene {
    override val id = "controls_badge_icon"
    override val title = "徽标图标"
    override val summary = "Badge、Icon、Divider 和 Gap"
    override val category = DemoCatalog.controls
    override val tags = setOf("badge", "icon", "divider", "gap")
    override val apis = setOf("Badge", "Icon", "PixelIconData", "Divider", "Gap")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(
            item = this,
            env = env,
            body = controlPageBody(
                listOf(
                    sectionTitle("状态装饰"),
                    samplePanel(
                        title = "Badge / Icon",
                        color = Pink,
                        child = Row(
                            children = listOf(
                                Badge(
                                    child = OutlinedButton(text = "MAIL", onPressed = {}, borderColor = Pink),
                                    label = Text("3", style = TextStyle(color = PixelColor.White)),
                                ),
                                Gap(width = 3),
                                Icon(PixelIconData(controlTinyIcon())),
                            ),
                            spacing = 2,
                            crossAxisAlignment = CrossAxisAlignment.CENTER,
                        ),
                    ),
                    samplePanel(
                        title = "Divider / Gap",
                        color = Yellow,
                        child = Column(
                            children = listOf(
                                Text("ABOVE", style = TextStyle(color = Yellow)),
                                Divider(color = Yellow),
                                Gap(height = 2),
                                Text("BELOW", style = TextStyle(color = Yellow)),
                            ),
                            spacing = 1,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
            ),
        )
}

private class SelectionSwitchBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SelectionSwitchState()

    private class SelectionSwitchState : State<SelectionSwitchBody>() {
        private var checked = true
        private var switched = false

        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            controlPageBody(
                listOf(
                    sectionTitle("二元状态"),
                    samplePanel(
                        title = "Checkbox / Switch",
                        color = Cyan,
                        child = Row(
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
                    ),
                ),
            )
    }
}

private class SegmentedTabsBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SegmentedTabsState()

    private class SegmentedTabsState : State<SegmentedTabsBody>() {
        private var tab = 1
        private var segment = 0

        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            controlPageBody(
                listOf(
                    sectionTitle("模式切换"),
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
            )
    }
}

private class SliderProgressBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SliderProgressState()

    private class SliderProgressState : State<SliderProgressBody>() {
        private var slider = 0.62f

        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            controlPageBody(
                listOf(
                    sectionTitle("连续值"),
                    samplePanel(
                        title = "Slider / ProgressBar / ActivityIndicator",
                        color = Accent,
                        child = Column(
                            children = listOf(
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
                ),
            )
    }
}

private fun controlPageBody(children: List<Widget>): Widget =
    Column(
        children = children,
        spacing = 4,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )

private fun controlTinyIcon(): PixelBitmap {
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
