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
    officialComponentScene("layout_sized_box", "SizedBox", "指定固定宽高或作为空白占位", DemoCatalog.layout, "SizedBox") {
        Row(children = listOf(exampleBox("A", Cyan), SizedBox(width = 10, height = 8), exampleBox("B", Pink)), spacing = 1)
    },
    officialComponentScene("layout_visibility", "Visibility", "按状态切换 child 与 replacement", DemoCatalog.layout, "Visibility") {
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
    officialComponentScene("layout_row", "Row", "水平排列多个 child", DemoCatalog.layout, "Row") {
        Row(children = listOf(exampleBox("1", Cyan), exampleBox("2", Green), exampleBox("3", Pink)), spacing = 3, crossAxisAlignment = CrossAxisAlignment.CENTER)
    },
    officialComponentScene("layout_column", "Column", "垂直排列多个 child", DemoCatalog.layout, "Column") {
        Column(children = listOf(exampleBox("TOP", Cyan), exampleBox("MID", Green), exampleBox("BOT", Pink)), spacing = 2)
    },
    officialComponentScene("layout_expanded", "Expanded", "在 Row/Column 中占满剩余空间", DemoCatalog.layout, "Expanded") {
        Row(children = listOf(exampleBox("A", Cyan), Expanded(child = Container(height = 8, fillColor = Green)), exampleBox("B", Pink)), spacing = 2)
    },
    officialComponentScene("layout_flexible", "Flexible", "在 Row/Column 中参与弹性分配但保留松约束", DemoCatalog.layout, "Flexible") {
        Row(children = listOf(exampleBox("A", Cyan), Flexible(child = Container(height = 8, fillColor = Yellow)), exampleBox("B", Pink)), spacing = 2)
    },
    officialComponentScene("layout_spacer", "Spacer", "在 Flex 布局中插入弹性空白", DemoCatalog.layout, "Spacer") {
        Row(children = listOf(exampleBox("L", Cyan), Spacer(), exampleBox("R", Pink)), spacing = 1)
    },
    officialComponentScene("layout_wrap", "Wrap", "空间不足时自动换行排列 children", DemoCatalog.layout, "Wrap") {
        Wrap(children = listOf("A", "B", "C", "D", "E").map { exampleBox(it, Accent) }, spacing = 2, runSpacing = 2)
    },
    officialComponentScene("layout_stack", "Stack", "把 children 按层叠方式绘制", DemoCatalog.layout, "Stack") {
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
    officialComponentScene("layout_opacity", "Opacity", "以离散透明度绘制 child", DemoCatalog.layout, "Opacity") {
        Opacity(opacity = 0.5f, child = exampleBox("50%", Pink))
    },
    officialComponentScene("layout_clip_rect", "ClipRect", "裁剪 child 超出边界的绘制", DemoCatalog.layout, "ClipRect") {
        Container(width = 38, height = 12, borderColor = Yellow, child = ClipRect(child = Transform.translate(offset = IntOffset(8, 0), child = Text("CLIPPED", style = TextStyle(color = Yellow)))))
    },
    officialComponentScene("layout_transform_translate", "Transform.translate", "按像素偏移 child 的绘制位置", DemoCatalog.layout, "Transform.translate") {
        Container(width = 66, height = 22, borderColor = Cyan, child = Transform.translate(offset = IntOffset(8, 4), child = exampleBox("SHIFT", Cyan)))
    },
    officialComponentScene("layout_decorated_box", "DecoratedBox", "轻量装饰：填充、边框、padding 与 alignment", DemoCatalog.layout, "DecoratedBox") {
        DecoratedBox(fillColor = Panel, borderColor = Accent, padding = 3, child = Text("DecoratedBox", style = TextStyle(color = Accent)))
    },
    officialComponentScene("layout_aspect_ratio", "AspectRatio", "按比例约束 child 的宽高", DemoCatalog.layout, "AspectRatio") {
        AspectRatio(aspectRatio = 3f, child = Container(fillColor = Green, borderColor = PixelColor.White))
    },
    officialComponentScene("layout_constrained_box", "ConstrainedBox", "向 child 施加最小/最大尺寸约束", DemoCatalog.layout, "ConstrainedBox") {
        ConstrainedBox(constraints = PixelBoxConstraints(minWidth = 48, maxWidth = 48, minHeight = 14, maxHeight = 14), child = Container(fillColor = Blue, borderColor = PixelColor.White))
    },
    officialComponentScene("layout_fitted_box", "FittedBox", "把 child 放入紧约束区域中适配显示", DemoCatalog.layout, "FittedBox") {
        Container(width = 28, height = 18, borderColor = Pink, child = FittedBox(child = Text("FIT", style = TextStyle(color = Pink))))
    },
    officialComponentScene("layout_safe_area", "SafeArea", "给系统安全区域预留内容边界", DemoCatalog.layout, "SafeArea") {
        SafeArea(child = Container(width = 84, height = 22, borderColor = Muted, child = Center(child = Text("SAFE", style = TextStyle(color = Muted)))))
    },
    officialComponentScene("layout_ime_avoiding", "ImeAvoidingView", "根据 IME viewInsets 预留键盘边界", DemoCatalog.layout, "ImeAvoidingView", extraApis = setOf("KeyboardAvoidingView")) {
        Row(
            children = listOf(
                Container(
                    width = 42,
                    height = 28,
                    borderColor = Muted,
                    child = ImeAvoidingView(
                        minimum = PixelWindowInsets(bottom = 5),
                        child = Text("IME", style = TextStyle(color = Cyan)),
                    ),
                ),
                Container(
                    width = 42,
                    height = 28,
                    borderColor = Muted,
                    child = KeyboardAvoidingView(
                        minimum = PixelWindowInsets(bottom = 5),
                        child = Text("KEY", style = TextStyle(color = Green)),
                    ),
                ),
            ),
            spacing = 2,
        )
    },
    officialComponentScene("layout_gesture_detector", "GestureDetector", "给任意 child 添加点击回调", DemoCatalog.layout, "GestureDetector") {
        GestureDetector(child = exampleBox("TAP", Yellow), onTap = {})
    },
)
