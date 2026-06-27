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
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.Expanded
import com.purride.pixelui.FittedBox
import com.purride.pixelui.Flexible
import com.purride.pixelui.Gap
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.Icon
import com.purride.pixelui.LoadStateView
import com.purride.pixelui.ListTile
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
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
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.Positioned
import com.purride.pixelui.PositionedDirectional
import com.purride.pixelui.PositionedFill
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.RichText
import com.purride.pixelui.Row
import com.purride.pixelui.SafeArea
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SelectionList
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slider
import com.purride.pixelui.Snackbar
import com.purride.pixelui.Spacer
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Toast
import com.purride.pixelui.Transform
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

val LayoutOfficialComponentScenes: List<DemoScene> = listOf(
    ComponentExampleScene(
        id = "layout_padding",
        title = "Padding",
        summary = "固定与方向感知内边距",
        category = DemoCatalog.layout,
        tags = setOf("component", "padding"),
        apis = setOf("Padding", "PaddingDirectional", "EdgeInsets", "EdgeInsetsDirectional"),
        bodyBuilder = {
            officialBody(
                listOf(
                    samplePanel(title = "Padding", color = Accent, child = Padding(all = 4, child = exampleBox("PAD", Accent))),
                    samplePanel(
                        title = "PaddingDirectional",
                        color = Cyan,
                        child = Directionality(
                            textDirection = TextDirection.RTL,
                            child = PaddingDirectional(
                                padding = EdgeInsetsDirectional.only(start = 8, end = 1, top = 2, bottom = 2),
                                child = exampleBox("RTL", Cyan),
                            ),
                        ),
                    ),
                ),
            )
        },
    ),
    ComponentExampleScene(
        id = "layout_align",
        title = "Align",
        summary = "普通、居中与方向感知对齐",
        category = DemoCatalog.layout,
        tags = setOf("component", "align", "center"),
        apis = setOf("Align", "Center", "AlignDirectional", "Alignment", "AlignmentDirectional"),
        bodyBuilder = {
            officialBody(
                listOf(
                    samplePanel(
                        title = "Align",
                        color = Accent,
                        child = Container(width = 82, height = 28, borderColor = Accent, child = Align(alignment = Alignment.CENTER_END, child = exampleBox("END", Accent))),
                    ),
                    samplePanel(
                        title = "Center",
                        color = Cyan,
                        child = Container(width = 82, height = 28, borderColor = Cyan, child = Center(child = exampleBox("MID", Cyan))),
                    ),
                    samplePanel(
                        title = "AlignDirectional",
                        color = Green,
                        child = Directionality(
                            textDirection = TextDirection.RTL,
                            child = Container(width = 82, height = 28, borderColor = Green, child = AlignDirectional(alignment = AlignmentDirectional.CENTER_START, child = exampleBox("START", Green))),
                        ),
                    ),
                ),
            )
        },
    ),
    componentScene("layout_sized_box", "SizedBox", "指定固定宽高或作为空白占位", DemoCatalog.layout, "SizedBox") {
        Row(children = listOf(exampleBox("A", Cyan), SizedBox(width = 10, height = 8), exampleBox("B", Pink)), spacing = 1)
    },
    componentScene("layout_visibility", "Visibility", "按状态切换 child 与 replacement", DemoCatalog.layout, "Visibility") {
        Row(
            children = listOf(
                Visibility(visible = true, child = Text("ON", style = TextStyle(color = Green))),
                Visibility(visible = false, child = Text("OFF"), replacement = Text("HIDDEN", style = TextStyle(color = Muted))),
            ),
            spacing = 4,
        )
    },
    ComponentExampleScene(
        id = "layout_container",
        title = "Container",
        summary = "尺寸、padding、装饰与方向感知容器",
        category = DemoCatalog.layout,
        tags = setOf("component", "container"),
        apis = setOf("Container", "ContainerDirectional"),
        bodyBuilder = {
            officialBody(
                listOf(
                    samplePanel(
                        title = "Container",
                        color = Accent,
                        child = Container(width = 86, height = 28, padding = EdgeInsets.all(3), fillColor = PixelColor.fromRgb(18, 14, 8), borderColor = Accent, child = Text("Container", style = TextStyle(color = Accent))),
                    ),
                    samplePanel(
                        title = "ContainerDirectional",
                        color = Blue,
                        child = Directionality(
                            textDirection = TextDirection.RTL,
                            child = ContainerDirectional(
                                width = 86,
                                paddingDirectional = EdgeInsetsDirectional.only(start = 10, end = 2, top = 2, bottom = 2),
                                borderColor = Blue,
                                alignment = AlignmentDirectional.CENTER_START,
                                child = Text("RTL START", style = TextStyle(color = Blue)),
                            ),
                        ),
                    ),
                ),
            )
        },
    ),
    componentScene("layout_row", "Row", "水平排列多个 child", DemoCatalog.layout, "Row") {
        Row(children = listOf(exampleBox("1", Cyan), exampleBox("2", Green), exampleBox("3", Pink)), spacing = 3, crossAxisAlignment = CrossAxisAlignment.CENTER)
    },
    componentScene("layout_column", "Column", "垂直排列多个 child", DemoCatalog.layout, "Column") {
        Column(children = listOf(exampleBox("TOP", Cyan), exampleBox("MID", Green), exampleBox("BOT", Pink)), spacing = 2)
    },
    componentScene("layout_expanded", "Expanded", "在 Row/Column 中占满剩余空间", DemoCatalog.layout, "Expanded") {
        Row(children = listOf(exampleBox("A", Cyan), Expanded(child = Container(height = 8, fillColor = Green)), exampleBox("B", Pink)), spacing = 2)
    },
    componentScene("layout_flexible", "Flexible", "在 Row/Column 中参与弹性分配但保留松约束", DemoCatalog.layout, "Flexible") {
        Row(children = listOf(exampleBox("A", Cyan), Flexible(child = Container(height = 8, fillColor = Yellow)), exampleBox("B", Pink)), spacing = 2)
    },
    componentScene("layout_spacer", "Spacer", "在 Flex 布局中插入弹性空白", DemoCatalog.layout, "Spacer") {
        Row(children = listOf(exampleBox("L", Cyan), Spacer(), exampleBox("R", Pink)), spacing = 1)
    },
    componentScene("layout_wrap", "Wrap", "空间不足时自动换行排列 children", DemoCatalog.layout, "Wrap") {
        Wrap(children = listOf("A", "B", "C", "D", "E").map { exampleBox(it, Accent) }, spacing = 2, runSpacing = 2)
    },
    componentScene("layout_stack", "Stack", "把 children 按层叠方式绘制", DemoCatalog.layout, "Stack") {
        Container(
            width = 86,
            height = 34,
            borderColor = Purple,
            child = Stack(children = listOf(PositionedFill(child = Container(fillColor = PixelColor.fromRgb(12, 8, 20))), Positioned(left = 5, top = 4, child = exampleBox("A", Blue)), Positioned(left = 20, top = 14, child = exampleBox("B", Pink)))),
        )
    },
    ComponentExampleScene(
        id = "layout_positioned",
        title = "Positioned",
        summary = "Stack 内的绝对、方向感知与填充定位",
        category = DemoCatalog.layout,
        tags = setOf("component", "positioned", "stack"),
        apis = setOf("Positioned", "PositionedDirectional", "PositionedFill"),
        bodyBuilder = {
            officialBody(
                listOf(
                    samplePanel(title = "Positioned", color = Accent, child = stackStage(Positioned(left = 8, top = 8, child = exampleBox("POS", Accent)))),
                    samplePanel(
                        title = "PositionedDirectional",
                        color = Blue,
                        child = Directionality(textDirection = TextDirection.RTL, child = stackStage(PositionedDirectional(start = 8, top = 8, child = exampleBox("DIR", Blue)))),
                    ),
                    samplePanel(
                        title = "PositionedFill",
                        color = Green,
                        child = stackStage(PositionedFill(left = 6, top = 6, right = 6, bottom = 6, child = Container(fillColor = Green, borderColor = PixelColor.White))),
                    ),
                ),
            )
        },
    ),
    componentScene("layout_opacity", "Opacity", "以离散透明度绘制 child", DemoCatalog.layout, "Opacity") {
        Opacity(opacity = 0.5f, child = exampleBox("50%", Pink))
    },
    componentScene("layout_clip_rect", "ClipRect", "裁剪 child 超出边界的绘制", DemoCatalog.layout, "ClipRect") {
        Container(width = 38, height = 12, borderColor = Yellow, child = ClipRect(child = Transform.translate(offset = IntOffset(8, 0), child = Text("CLIPPED", style = TextStyle(color = Yellow)))))
    },
    componentScene("layout_transform_translate", "Transform.translate", "按像素偏移 child 的绘制位置", DemoCatalog.layout, "Transform.translate") {
        Container(width = 66, height = 22, borderColor = Cyan, child = Transform.translate(offset = IntOffset(8, 4), child = exampleBox("SHIFT", Cyan)))
    },
    componentScene("layout_decorated_box", "DecoratedBox", "轻量装饰：填充、边框、padding 与 alignment", DemoCatalog.layout, "DecoratedBox") {
        DecoratedBox(fillColor = Panel, borderColor = Accent, padding = 3, child = Text("DecoratedBox", style = TextStyle(color = Accent)))
    },
    componentScene("layout_aspect_ratio", "AspectRatio", "按比例约束 child 的宽高", DemoCatalog.layout, "AspectRatio") {
        AspectRatio(aspectRatio = 3f, child = Container(fillColor = Green, borderColor = PixelColor.White))
    },
    componentScene("layout_constrained_box", "ConstrainedBox", "向 child 施加最小/最大尺寸约束", DemoCatalog.layout, "ConstrainedBox") {
        ConstrainedBox(constraints = PixelBoxConstraints(minWidth = 48, maxWidth = 48, minHeight = 14, maxHeight = 14), child = Container(fillColor = Blue, borderColor = PixelColor.White))
    },
    componentScene("layout_fitted_box", "FittedBox", "把 child 放入紧约束区域中适配显示", DemoCatalog.layout, "FittedBox") {
        Container(width = 28, height = 18, borderColor = Pink, child = FittedBox(child = Text("FIT", style = TextStyle(color = Pink))))
    },
    componentScene("layout_safe_area", "SafeArea", "给系统安全区域预留内容边界", DemoCatalog.layout, "SafeArea") {
        SafeArea(child = Container(width = 84, height = 22, borderColor = Muted, child = Center(child = Text("SAFE", style = TextStyle(color = Muted)))))
    },
    componentScene("layout_gesture_detector", "GestureDetector", "给任意 child 添加点击回调", DemoCatalog.layout, "GestureDetector") {
        GestureDetector(child = exampleBox("TAP", Yellow), onTap = {})
    },
)

val TextOfficialComponentScenes: List<DemoScene> = listOf(
    componentScene("text_text", "Text", "单行文本、换行、截断和对齐", DemoCatalog.text, "Text") {
        Column(
            children = listOf(
                Text("Text", style = TextStyle(color = Cyan)),
                Text("ELLIPSIS ABCDEFGHIJKLMNOP", style = TextStyle(color = Pink), overflow = PixelTextOverflow.ELLIPSIS),
                Text("WRAP TEXT DEMO", style = TextStyle(color = Green), softWrap = true, maxLines = 2),
            ),
            spacing = 2,
        )
    },
    componentScene("text_rich_text", "RichText", "用 PixelTextSpan 组合多样式文本", DemoCatalog.text, "RichText", extraApis = setOf("PixelTextSpan")) {
        RichText(
            spans = listOf(
                PixelTextSpan("RICH ", TextStyle(color = Purple)),
                PixelTextSpan("TEXT ", TextStyle(color = Yellow)),
                PixelTextSpan("SPAN", TextStyle(color = Cyan)),
            ),
        )
    },
    ComponentExampleScene(
        id = "text_text_field",
        title = "TextField",
        summary = "可编辑输入、placeholder、IME 类型和提交回调",
        category = DemoCatalog.input,
        tags = setOf("component", "textfield", "input"),
        apis = setOf("TextField", "TextEditingController", "PixelTextFieldState", "PixelInputType", "PixelTextInputAction", "PixelTextEditAction"),
        bodyBuilder = { TextFieldOfficialBody() },
    ),
)

val ControlOfficialComponentScenes: List<DemoScene> = listOf(
    ComponentExampleScene("controls_outlined_button", "OutlinedButton", "像素边框按钮，支持 enabled/style/fill/border", DemoCatalog.controls, setOf("component", "button"), setOf("OutlinedButton", "ButtonStyle", "PixelButtonStyle")) { ButtonOfficialBody() },
    componentScene("controls_list_tile", "ListTile", "带 leading/title/subtitle/trailing 的列表行", DemoCatalog.controls, "ListTile") {
        ListTile(leading = Icon(PixelIconData(officialTinyIcon())), title = Text("ListTile", style = TextStyle(color = Cyan)), subtitle = Text("subtitle", style = TextStyle(color = Muted)), trailing = Text(">", style = TextStyle(color = Accent)), onTap = {})
    },
    ComponentExampleScene("controls_selection_list", "SelectionList", "受控单选列表与字符串选项列表", DemoCatalog.controls, setOf("component", "selection"), setOf("SelectionList", "OptionList")) {
        officialBody(
            listOf(
                samplePanel(
                    title = "SelectionList",
                    color = Cyan,
                    child = SelectionList(
                        items = listOf("LOW", "MID", "HIGH"),
                        selectedIndex = 1,
                        onSelected = { _, _ -> },
                        itemLabel = { it },
                    ),
                ),
                samplePanel(
                    title = "OptionList",
                    color = Green,
                    child = OptionList(
                        options = listOf("A", "B"),
                        selectedIndex = 0,
                        onSelected = {},
                    ),
                ),
            ),
        )
    },
    ComponentExampleScene("controls_checkbox", "Selection", "Checkbox 和 Switch 二元选择", DemoCatalog.controls, setOf("component", "checkbox", "switch"), setOf("Checkbox", "Switch")) {
        officialBody(
            listOf(
                CheckboxOfficialBody(),
                SwitchOfficialBody(),
            ),
        )
    },
    ComponentExampleScene("controls_tabs", "Tabs", "Tabs 和 SegmentedControl 分段选择", DemoCatalog.controls, setOf("component", "tabs", "segmented"), setOf("Tabs", "SegmentedControl")) {
        officialBody(
            listOf(
                TabsOfficialBody(),
                SegmentedOfficialBody(),
            ),
        )
    },
    ComponentExampleScene("controls_slider", "Slider", "连续值拖动控件", DemoCatalog.controls, setOf("component", "slider"), setOf("Slider")) { SliderOfficialBody(showProgress = false) },
    ComponentExampleScene("controls_progress_bar", "ProgressBar", "水平进度展示", DemoCatalog.feedback, setOf("component", "progress"), setOf("ProgressBar")) { SliderOfficialBody(showProgress = true) },
    componentScene("controls_activity_indicator", "ActivityIndicator", "四帧像素加载指示器", DemoCatalog.feedback, "ActivityIndicator") {
        Row(children = listOf(ActivityIndicator(frame = 0, color = Yellow), ActivityIndicator(frame = 1, color = Yellow), ActivityIndicator(frame = 2, color = Yellow), ActivityIndicator(frame = 3, color = Yellow)), spacing = 4)
    },
    componentScene("controls_load_state_view", "LoadStateView", "统一 loading、empty、error 和 content", DemoCatalog.feedback, "LoadStateView", extraApis = setOf("PixelAsyncSnapshot")) {
        Row(
            children = listOf(
                LoadStateView(snapshot = PixelAsyncSnapshot.Loading, content = { Text(it.toString()) }, loading = Text("LOAD", style = TextStyle(color = Yellow))),
                LoadStateView(snapshot = PixelAsyncSnapshot.Success(emptyList<String>()), content = { Text("OK") }, isEmpty = { it.isEmpty() }, empty = Text("EMPTY", style = TextStyle(color = Muted))),
                LoadStateView(snapshot = PixelAsyncSnapshot.Failure(IllegalStateException("BAD")), content = { Text(it.toString()) }, error = { Text("ERR", style = TextStyle(color = Pink)) }),
            ),
            spacing = 3,
        )
    },
    componentScene("controls_badge", "Badge", "在 child 角落叠加小标签", DemoCatalog.feedback, "Badge") {
        Badge(child = OutlinedButton(text = "MAIL", onPressed = {}, borderColor = Pink), label = Text("3", style = TextStyle(color = PixelColor.White)))
    },
    componentScene("controls_divider", "Divider", "一条水平分隔线", DemoCatalog.layout, "Divider") {
        Column(children = listOf(Text("ABOVE", style = TextStyle(color = Yellow)), Divider(color = Yellow), Text("BELOW", style = TextStyle(color = Yellow))), spacing = 2)
    },
    componentScene("controls_gap", "Gap", "固定宽高的空白间隔", DemoCatalog.layout, "Gap") {
        Row(children = listOf(exampleBox("A", Cyan), Gap(width = 8), exampleBox("B", Pink)), spacing = 1)
    },
    componentScene("controls_icon", "Icon", "用 PixelIconData 渲染小型位图图标", DemoCatalog.paint, "Icon", extraApis = setOf("PixelIconData")) {
        Icon(PixelIconData(officialTinyIcon()))
    },
    ComponentExampleScene("controls_dialog", "Messages", "Dialog、ConfirmDialog、Toast 和 Snackbar", DemoCatalog.feedback, setOf("component", "feedback", "message"), setOf("Dialog", "ConfirmDialog", "Toast", "Snackbar")) {
        officialBody(
            listOf(
                samplePanel(
                    title = "Dialog",
                    color = Accent,
                    child = Dialog(title = Text("TITLE", style = TextStyle(color = Accent)), content = Text("CONTENT", style = TextStyle(color = Muted)), actions = listOf(OutlinedButton(text = "OK", onPressed = {}, borderColor = Accent))),
                ),
                samplePanel(
                    title = "ConfirmDialog",
                    color = Blue,
                    child = ConfirmDialog(title = "DELETE", message = "ARE YOU SURE", onConfirm = {}, onCancel = {}, confirmText = "OK", cancelText = "BACK", borderColor = Blue, width = 54),
                ),
                samplePanel(
                    title = "Toast",
                    color = Yellow,
                    child = Toast(message = "Saved", textStyle = TextStyle(color = Accent)),
                ),
                samplePanel(
                    title = "Snackbar",
                    color = Pink,
                    child = Snackbar(message = "Message", action = OutlinedButton(text = "UNDO", onPressed = {}, borderColor = Accent), textStyle = TextStyle(color = PixelColor.White)),
                ),
            ),
        )
    },
    componentScene("controls_app_scaffold", "AppScaffold", "标题、body、bottomBar 的页面骨架", DemoCatalog.navigation, "AppScaffold") {
        Container(
            width = 100,
            height = 62,
            borderColor = Blue,
            child = AppScaffold(
                title = Text("TITLE", style = TextStyle(color = Blue)),
                body = Center(child = Text("BODY", style = TextStyle(color = Muted))),
                bottomBar = Text("BOTTOM", style = TextStyle(color = Accent)),
            ),
        )
    },
)

private fun componentScene(
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

private fun officialBody(children: List<Widget>): Widget =
    Column(
        children = children,
        spacing = 4,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )

private fun stackStage(child: Widget): Widget =
    Container(width = 86, height = 34, borderColor = Accent, child = Stack(children = listOf(PositionedFill(child = Container(fillColor = PixelColor.fromRgb(12, 12, 12))), child)))

private fun exampleBox(label: String, color: PixelColor): Widget =
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

private class TextFieldOfficialBody(
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

private class ButtonOfficialBody(
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

private class CheckboxOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = CheckboxOfficialState()

    private class CheckboxOfficialState : State<CheckboxOfficialBody>() {
        private var checked = true
        override fun build(context: BuildContext): Widget =
            officialBody(listOf(samplePanel("Example", color = Green, child = Row(children = listOf(Checkbox(checked = checked, onChanged = { value -> checked = value; setState {} }, activeColor = Green), Text(if (checked) "checked" else "unchecked", style = TextStyle(color = Green))), spacing = 2, crossAxisAlignment = CrossAxisAlignment.CENTER))))
    }
}

private class SwitchOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SwitchOfficialState()

    private class SwitchOfficialState : State<SwitchOfficialBody>() {
        private var checked = false
        override fun build(context: BuildContext): Widget =
            officialBody(listOf(samplePanel("Example", color = Blue, child = Row(children = listOf(Switch(checked = checked, onChanged = { value -> checked = value; setState {} }), Text(if (checked) "on" else "off", style = TextStyle(color = Blue))), spacing = 2, crossAxisAlignment = CrossAxisAlignment.CENTER))))
    }
}

private class TabsOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TabsOfficialState()

    private class TabsOfficialState : State<TabsOfficialBody>() {
        private var selected = 1
        override fun build(context: BuildContext): Widget =
            officialBody(listOf(samplePanel("Example", color = Pink, child = Tabs(labels = listOf("A", "B", "C"), selectedIndex = selected, onSelected = { index -> selected = index; setState {} }))))
    }
}

private class SegmentedOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SegmentedOfficialState()

    private class SegmentedOfficialState : State<SegmentedOfficialBody>() {
        private var selected = 0
        override fun build(context: BuildContext): Widget =
            officialBody(listOf(samplePanel("Example", color = Pink, child = SegmentedControl(labels = listOf("DAY", "NIGHT", "AUTO"), selectedIndex = selected, onSelected = { index -> selected = index; setState {} }))))
    }
}

private class SliderOfficialBody(
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

private fun officialTinyIcon(): PixelBitmap {
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
