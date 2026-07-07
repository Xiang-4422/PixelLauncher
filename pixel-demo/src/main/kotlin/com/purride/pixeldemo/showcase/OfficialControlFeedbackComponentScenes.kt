package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.ActivityIndicator
import com.purride.pixelui.Align
import com.purride.pixelui.AlignDirectional
import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.AnimatedPixelLoadingBar
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
import com.purride.pixelui.PixelLoadingBar
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

val ControlOfficialComponentScenes: List<DemoScene> = listOf(
    ComponentExampleScene("controls_outlined_button", "OutlinedButton", "像素边框按钮，支持 enabled/style/fill/border", DemoCatalog.controls, setOf("component", "button"), setOf("OutlinedButton", "ButtonStyle", "PixelButtonStyle")) { ButtonOfficialBody() },
    officialComponentScene("controls_list_tile", "ListTile", "带 leading/title/subtitle/trailing 的列表行", DemoCatalog.controls, "ListTile") {
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
    ComponentExampleScene("controls_section_list", "SectionList", "普通 widget 组成的分组列表", DemoCatalog.controls, setOf("component", "section", "list"), setOf("SectionList", "SectionListSection")) {
        SectionList(
            sections = listOf(
                SectionListSection(
                    header = Text("SYSTEM", style = TextStyle(color = Accent)),
                    children = listOf(
                        ListTile(title = Text("AUDIO"), trailing = Text("ON", style = TextStyle(color = Green)), onTap = {}),
                        ListTile(title = Text("DISPLAY"), trailing = Text("8PX", style = TextStyle(color = Cyan)), onTap = {}),
                    ),
                ),
                SectionListSection(
                    header = Text("PROFILE", style = TextStyle(color = Pink)),
                    children = listOf(
                        ListTile(title = Text("PLAYER"), subtitle = Text("LEVEL 12", style = TextStyle(color = Muted)), onTap = {}),
                    ),
                    footer = Text("SYNCED", style = TextStyle(color = Muted)),
                ),
            ),
        )
    },
    ComponentExampleScene("controls_value_adjuster", "ValueAdjuster", "减值加三段式数值调节", DemoCatalog.controls, setOf("component", "stepper", "adjuster"), setOf("ValueAdjuster", "Stepper")) { StepperOfficialBody() },
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
    officialComponentScene("controls_shortcut_hint", "ShortcutHint", "只渲染快捷键提示，不绑定事件", DemoCatalog.controls, "ShortcutHint") {
        Column(
            children = listOf(
                ShortcutHint(shortcut = "A", label = "OPEN", shortcutStyle = TextStyle(color = Accent)),
                ShortcutHint(shortcut = "B", label = "BACK", shortcutStyle = TextStyle(color = Cyan)),
            ),
            spacing = 2,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    },
    ComponentExampleScene("controls_slider", "Slider", "连续值拖动控件", DemoCatalog.controls, setOf("component", "slider"), setOf("Slider")) { SliderOfficialBody(showProgress = false) },
    ComponentExampleScene("feedback_progress_bar", "ProgressBar", "水平进度展示", DemoCatalog.feedback, setOf("component", "progress"), setOf("ProgressBar")) { SliderOfficialBody(showProgress = true) },
    officialComponentScene("feedback_pixel_loading_bar", "PixelLoadingBar", "点阵扫描式水平加载条", DemoCatalog.feedback, "PixelLoadingBar", extraApis = setOf("AnimatedPixelLoadingBar")) { env ->
        Column(
            children = listOf(
                AnimatedPixelLoadingBar(vsync = env.vsync, color = Pink, width = 88),
                PixelLoadingBar(progress = 0.35f, color = Yellow, width = 88),
                PixelLoadingBar(progress = 0.65f, color = Cyan, width = 88, reversed = true),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    },
    officialComponentScene("feedback_activity_indicator", "ActivityIndicator", "四帧像素加载指示器", DemoCatalog.feedback, "ActivityIndicator") {
        Row(children = listOf(ActivityIndicator(frame = 0, color = Yellow), ActivityIndicator(frame = 1, color = Yellow), ActivityIndicator(frame = 2, color = Yellow), ActivityIndicator(frame = 3, color = Yellow)), spacing = 4)
    },
    officialComponentScene("feedback_load_state_view", "LoadStateView", "统一 loading、empty、error 和 content", DemoCatalog.feedback, "LoadStateView", extraApis = setOf("PixelAsyncSnapshot")) {
        Row(
            children = listOf(
                LoadStateView(snapshot = PixelAsyncSnapshot.Loading, content = { Text(it.toString()) }, loading = Text("LOAD", style = TextStyle(color = Yellow))),
                LoadStateView(snapshot = PixelAsyncSnapshot.Success(emptyList<String>()), content = { Text("OK") }, isEmpty = { it.isEmpty() }, empty = Text("EMPTY", style = TextStyle(color = Muted))),
                LoadStateView(snapshot = PixelAsyncSnapshot.Failure(IllegalStateException("BAD")), content = { Text(it.toString()) }, error = { Text("ERR", style = TextStyle(color = Pink)) }),
            ),
            spacing = 3,
        )
    },
    officialComponentScene("feedback_badge", "Badge", "在 child 角落叠加小标签", DemoCatalog.feedback, "Badge") {
        Badge(child = OutlinedButton(text = "MAIL", onPressed = {}, borderColor = Pink), label = Text("3", style = TextStyle(color = PixelColor.White)))
    },
    officialComponentScene("layout_divider", "Divider", "一条水平分隔线", DemoCatalog.layout, "Divider") {
        Column(children = listOf(Text("ABOVE", style = TextStyle(color = Yellow)), Divider(color = Yellow), Text("BELOW", style = TextStyle(color = Yellow))), spacing = 2)
    },
    officialComponentScene("layout_gap", "Gap", "固定宽高的空白间隔", DemoCatalog.layout, "Gap") {
        Row(children = listOf(exampleBox("A", Cyan), Gap(width = 8), exampleBox("B", Pink)), spacing = 1)
    },
    officialComponentScene("paint_icon", "Icon", "用 PixelIconData 渲染小型位图图标", DemoCatalog.paint, "Icon", extraApis = setOf("PixelIconData")) {
        Icon(PixelIconData(officialTinyIcon()))
    },
    ComponentExampleScene("feedback_dialog", "Messages", "Dialog、ConfirmDialog、Toast 和 Snackbar", DemoCatalog.feedback, setOf("component", "feedback", "message"), setOf("Dialog", "ConfirmDialog", "Toast", "Snackbar")) {
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
    ComponentExampleScene("feedback_overlay_tools", "OverlayTools", "模态遮罩与 toast 队列基础能力", DemoCatalog.feedback, setOf("component", "overlay", "toast"), setOf("ModalBarrier", "ToastQueue", "PixelToastQueueController")) { ToastQueueOfficialBody() },
    ComponentExampleScene("feedback_popover_menu", "PopoverMenu", "受控弹出层、菜单、下拉和提示", DemoCatalog.feedback, setOf("component", "overlay", "menu"), setOf("Popover", "Menu", "PixelMenuItem", "Dropdown", "Tooltip")) { OverlayControlsOfficialBody() },
    officialComponentScene("navigation_app_scaffold", "AppScaffold", "标题、body、bottomBar 的页面骨架", DemoCatalog.navigation, "AppScaffold") {
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
