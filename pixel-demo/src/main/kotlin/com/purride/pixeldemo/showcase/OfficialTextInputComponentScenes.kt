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

val TextOfficialComponentScenes: List<DemoScene> = listOf(
    officialComponentScene("text_text", "Text", "单行文本、换行、截断和对齐", DemoCatalog.text, "Text") {
        Column(
            children = listOf(
                Text("Text", style = TextStyle(color = Cyan)),
                Text("ELLIPSIS ABCDEFGHIJKLMNOP", style = TextStyle(color = Pink), overflow = PixelTextOverflow.ELLIPSIS),
                Text("WRAP TEXT DEMO", style = TextStyle(color = Green), softWrap = true, maxLines = 2),
            ),
            spacing = 2,
        )
    },
    officialComponentScene("text_rich_text", "RichText", "用 PixelTextSpan 组合多样式文本", DemoCatalog.text, "RichText", extraApis = setOf("PixelTextSpan")) {
        RichText(
            spans = listOf(
                PixelTextSpan("RICH ", TextStyle(color = Purple)),
                PixelTextSpan("TEXT ", TextStyle(color = Yellow)),
                PixelTextSpan("SPAN", TextStyle(color = Cyan)),
            ),
        )
    },
    ComponentExampleScene(
        id = "input_text_field",
        title = "TextField",
        summary = "可编辑输入、placeholder、IME 类型和提交回调",
        category = DemoCatalog.input,
        tags = setOf("component", "textfield", "input"),
        apis = setOf("TextField", "TextEditingController", "PixelTextFieldState", "PixelInputType", "PixelTextInputAction", "PixelTextEditAction"),
        bodyBuilder = { TextFieldOfficialBody() },
    ),
)
