package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.ActivityIndicator
import com.purride.pixelui.Align
import com.purride.pixelui.AlignDirectional
import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.AppScaffold
import com.purride.pixelui.AspectRatio
import com.purride.pixelui.Badge
import com.purride.pixelui.BuildContext
import com.purride.pixelui.ButtonStyle
import com.purride.pixelui.Center
import com.purride.pixelui.Checkbox
import com.purride.pixelui.ClipRect
import com.purride.pixelui.Column
import com.purride.pixelui.ConfirmDialog
import com.purride.pixelui.ConstrainedBox
import com.purride.pixelui.Container
import com.purride.pixelui.ContainerDirectional
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.DecoratedBox
import com.purride.pixelui.Dialog
import com.purride.pixelui.Directionality
import com.purride.pixelui.Divider
import com.purride.pixelui.Dropdown
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.Expanded
import com.purride.pixelui.FittedBox
import com.purride.pixelui.Flexible
import com.purride.pixelui.Gap
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.Icon
import com.purride.pixelui.ImeAvoidingView
import com.purride.pixelui.KeyboardAvoidingView
import com.purride.pixelui.LoadStateView
import com.purride.pixelui.ListTile
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Menu
import com.purride.pixelui.ModalBarrier
import com.purride.pixelui.Opacity
import com.purride.pixelui.OptionList
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.PaddingDirectional
import com.purride.pixelui.PixelBoxConstraints
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.PixelAsyncSnapshot
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelToastQueueController
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.Popover
import com.purride.pixelui.Positioned
import com.purride.pixelui.PositionedDirectional
import com.purride.pixelui.PositionedFill
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.RichText
import com.purride.pixelui.Row
import com.purride.pixelui.SafeArea
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SelectionList
import com.purride.pixelui.SectionList
import com.purride.pixelui.SectionListSection
import com.purride.pixelui.ShortcutHint
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slider
import com.purride.pixelui.Snackbar
import com.purride.pixelui.Spacer
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Stepper
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Toast
import com.purride.pixelui.ToastQueue
import com.purride.pixelui.Tooltip
import com.purride.pixelui.Transform
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.Visibility
import com.purride.pixelui.Widget
import com.purride.pixelui.Wrap
import com.purride.pixelui.animation.IntOffset
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Panel
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel

fun officialComponentScene(
    id: String,
    title: String,
    summary: String,
    category: com.purride.pixeldemo.catalog.DemoCategory,
    api: String,
    extraApis: Set<String> = emptySet(),
    body: (DemoEnv) -> Widget,
): DemoScene =
    ComponentExampleScene(
        id = id,
        title = title,
        summary = summary,
        category = category,
        tags = setOf("component", title.lowercase()),
        apis = setOf(api) + extraApis,
        bodyBuilder = { env ->
            officialBody(
                listOf(
                    samplePanel(title = "Example", color = categoryColor(category.id), child = body(env)),
                ),
            )
        },
    )

fun officialBody(children: List<Widget>): Widget =
    Column(
        children = children,
        spacing = 4,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )

fun stackStage(child: Widget): Widget =
    Container(width = 86, height = 34, borderColor = Accent, child = Stack(children = listOf(PositionedFill(child = Container(fillColor = PixelColor.fromRgb(12, 12, 12))), child)))

fun exampleBox(label: String, color: PixelColor): Widget =
    Container(
        padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
        borderColor = color,
        fillColor = PixelColor.fromArgb(80, color.red, color.green, color.blue),
        child = Text(label, style = TextStyle(color = color)),
    )

private fun categoryColor(id: String): PixelColor = when (id) {
    DemoCatalog.layout.id -> Accent
    DemoCatalog.text.id -> Cyan
    DemoCatalog.input.id -> Cyan
    DemoCatalog.controls.id -> Green
    DemoCatalog.feedback.id -> Pink
    else -> Accent
}

class TextFieldOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TextFieldOfficialState()

    private class TextFieldOfficialState : State<TextFieldOfficialBody>() {
        private val controller = TextEditingController()
        private val state = controller.create("TextField")
        private var status = "READY"

        override fun build(context: BuildContext): Widget =
            officialBody(
                listOf(
                    samplePanel(
                        title = "Example",
                        color = Cyan,
                        child = Column(
                            children = listOf(
                                TextField(
                                    state = state,
                                    controller = controller,
                                    placeholder = "输入文本",
                                    inputType = PixelInputType.TEXT,
                                    textInputAction = PixelTextInputAction.DONE,
                                    onChanged = { value -> status = "typing ${value.length}"; setState {} },
                                    onSubmitted = { value -> status = "submit ${value.length}"; setState {} },
                                    borderColor = Cyan,
                                    fillColor = PixelColor.fromRgb(8, 16, 20),
                                ),
                                Text(status, style = TextStyle(color = Muted)),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
            )
    }
}

class ButtonOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ButtonOfficialState()

    private class ButtonOfficialState : State<ButtonOfficialBody>() {
        private var count = 0

        override fun build(context: BuildContext): Widget {
            val style: PixelButtonStyle = ButtonStyle(
                fillColor = PixelColor.fromRgb(24, 18, 4),
                borderColor = Yellow,
                textStyle = PixelTextStyle(color = Yellow),
            )
            return officialBody(
                listOf(
                    samplePanel(
                        title = "Example",
                        color = Green,
                        child = Column(
                            children = listOf(
                                Row(
                                    children = listOf(
                                        OutlinedButton(text = "PRESS", onPressed = { count += 1; setState {} }, borderColor = Accent),
                                        OutlinedButton(text = "STYLE", onPressed = {}, style = style),
                                        OutlinedButton(text = "OFF", onPressed = null, enabled = false),
                                    ),
                                    spacing = 2,
                                ),
                                Text("count=$count", style = TextStyle(color = Muted)),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
            )
        }
    }
}

class CheckboxOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = CheckboxOfficialState()

    private class CheckboxOfficialState : State<CheckboxOfficialBody>() {
        private var checked = true
        override fun build(context: BuildContext): Widget =
            officialBody(listOf(samplePanel("Example", color = Green, child = Row(children = listOf(Checkbox(checked = checked, onChanged = { value -> checked = value; setState {} }, activeColor = Green), Text(if (checked) "checked" else "unchecked", style = TextStyle(color = Green))), spacing = 2, crossAxisAlignment = CrossAxisAlignment.CENTER))))
    }
}

class SwitchOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SwitchOfficialState()

    private class SwitchOfficialState : State<SwitchOfficialBody>() {
        private var checked = false
        override fun build(context: BuildContext): Widget =
            officialBody(listOf(samplePanel("Example", color = Blue, child = Row(children = listOf(Switch(checked = checked, onChanged = { value -> checked = value; setState {} }), Text(if (checked) "on" else "off", style = TextStyle(color = Blue))), spacing = 2, crossAxisAlignment = CrossAxisAlignment.CENTER))))
    }
}

class TabsOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TabsOfficialState()

    private class TabsOfficialState : State<TabsOfficialBody>() {
        private var selected = 1
        override fun build(context: BuildContext): Widget =
            officialBody(listOf(samplePanel("Example", color = Pink, child = Tabs(labels = listOf("A", "B", "C"), selectedIndex = selected, onSelected = { index -> selected = index; setState {} }))))
    }
}

class SegmentedOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SegmentedOfficialState()

    private class SegmentedOfficialState : State<SegmentedOfficialBody>() {
        private var selected = 0
        override fun build(context: BuildContext): Widget =
            officialBody(listOf(samplePanel("Example", color = Pink, child = SegmentedControl(labels = listOf("DAY", "NIGHT", "AUTO"), selectedIndex = selected, onSelected = { index -> selected = index; setState {} }))))
    }
}

class StepperOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = StepperOfficialState()

    private class StepperOfficialState : State<StepperOfficialBody>() {
        private var value = 4

        override fun build(context: BuildContext): Widget =
            officialBody(
                listOf(
                    samplePanel(
                        title = "Stepper",
                        color = Accent,
                        child = Stepper(
                            value = value,
                            range = 0..10,
                            step = 2,
                            onChanged = { next -> value = next; setState {} },
                            valueText = "$value PX",
                            valueWidth = 34,
                        ),
                    ),
                    samplePanel(
                        title = "ValueAdjuster",
                        color = Cyan,
                        child = ValueAdjuster(
                            valueText = "FAST",
                            onDecrease = {},
                            onIncrease = {},
                            label = "SPEED",
                            valueWidth = 34,
                        ),
                    ),
                ),
            )
    }
}

class ToastQueueOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ToastQueueOfficialState()

    private class ToastQueueOfficialState : State<ToastQueueOfficialBody>() {
        private val queue = PixelToastQueueController()

        override fun initState() {
            queue.enqueue("QUEUED", textStyle = TextStyle(color = Accent))
        }

        override fun build(context: BuildContext): Widget =
            Stack(
                children = listOf(
                    Container(width = 92, height = 38, borderColor = Muted, child = Center(child = Text("CONTENT", style = TextStyle(color = Muted)))),
                    ModalBarrier(color = PixelColor.fromArgb(90, 0, 0, 0)),
                    ToastQueue(controller = queue),
                ),
            )
    }
}

class OverlayControlsOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = OverlayControlsOfficialState()

    private class OverlayControlsOfficialState : State<OverlayControlsOfficialBody>() {
        private var expanded = true
        private var selected = "A"

        override fun build(context: BuildContext): Widget =
            officialBody(
                listOf(
                    samplePanel(
                        title = "Popover",
                        color = Accent,
                        child = Popover(
                            anchor = Text("ANCHOR", style = TextStyle(color = Accent)),
                            content = Container(padding = EdgeInsets.all(2), fillColor = PixelColor.Black, borderColor = Accent, child = Text("POP", style = TextStyle(color = Accent))),
                            expanded = true,
                            contentOffset = IntOffset(0, 10),
                        ),
                    ),
                    samplePanel(
                        title = "Menu",
                        color = Cyan,
                        child = Menu(
                            items = listOf(
                                PixelMenuItem(label = "COPY", shortcut = "A", onSelected = {}),
                                PixelMenuItem(label = "PASTE", shortcut = "B", onSelected = {}),
                            ),
                        ),
                    ),
                    samplePanel(
                        title = "Dropdown",
                        color = Green,
                        child = Dropdown(
                            label = "MODE",
                            selectedText = selected,
                            expanded = expanded,
                            onToggle = { expanded = !expanded; setState {} },
                            items = listOf(
                                PixelMenuItem(label = "A", onSelected = { selected = "A"; expanded = false; setState {} }),
                                PixelMenuItem(label = "B", onSelected = { selected = "B"; expanded = false; setState {} }),
                            ),
                        ),
                    ),
                    samplePanel(
                        title = "Tooltip",
                        color = Pink,
                        child = Tooltip(message = "HELP", visible = true, child = Text("TARGET", style = TextStyle(color = Pink))),
                    ),
                ),
            )
    }
}

class SliderOfficialBody(
    private val showProgress: Boolean,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SliderOfficialState()

    private class SliderOfficialState : State<SliderOfficialBody>() {
        private var value = 0.62f
        override fun build(context: BuildContext): Widget =
            officialBody(
                listOf(
                    samplePanel(
                        title = "Example",
                        color = Accent,
                        child = Column(
                            children = buildList {
                                if (!widget.showProgress) {
                                    add(Slider(value = value, onDrag = { next -> value = next; setState {} }, onRelease = { next -> value = next; setState {} }, activeColor = Accent))
                                }
                                add(ProgressBar(progress = value, width = 72, color = Accent))
                                add(Text("${(value * 100).toInt()}%", style = TextStyle(color = Accent)))
                            },
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
            )
    }
}

fun officialTinyIcon(): PixelBitmap {
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
